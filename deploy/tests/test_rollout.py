import json
import os
from pathlib import Path
import re
import stat
import subprocess
import tempfile
import unittest


SOURCE = Path(__file__).resolve().parents[1] / "rollout.sh"
GROUP = "meterengine-staging-app"
PARAMETER = "/meterengine/staging/app-version"
SHA = "a" * 40
PREVIOUS = "b" * 40
TARGET_GROUPS = ["arn:tg/front", "arn:tg/back"]
ALB_DNS_NAME = "meterengine-staging-alb-1.ap-northeast-2.elb.amazonaws.com"

# 시나리오 파일을 읽고 바꾸며 부른 명령을 남긴다. 상태 목록은 부를 때마다 앞에서 하나씩 꺼내고 마지막 값은 남긴다.
FAKE_AWS = r'''#!/usr/bin/env python3
import json, os, sys
args = sys.argv[1:]
path = os.environ["FAKE_STATE"]
state = json.load(open(path))
with open(os.environ["TRACE"], "a") as trace:
    trace.write(" ".join(args) + "\n")


def arg(name):
    return args[args.index(name) + 1]


def pop(values):
    return values.pop(0) if len(values) > 1 else values[0]


def finish(output="", code=0, error=""):
    json.dump(state, open(path, "w"))
    if output:
        print(output)
    if error:
        print(error, file=sys.stderr)
    sys.exit(code)


command = " ".join(args[:2])
if command == "autoscaling describe-auto-scaling-groups":
    query = arg("--query")
    if not state["group"]:
        finish("None")
    if "AutoScalingGroupName" in query:
        finish(f"{state['group']}\t{state['min']}\t{state['desired']}")
    if "TargetGroupARNs" in query:
        finish("\t".join(state["target_groups"]))
    if "InService" in query:
        finish("\t".join(state["in_service"]))
elif command == "autoscaling describe-instance-refreshes":
    if "--instance-refresh-ids" in args:
        refresh = arg("--instance-refresh-ids")
        if "StatusReason" in arg("--query"):
            finish("reason")
        finish(pop(state["statuses"][refresh]))
    finish(state["active"])
elif command == "autoscaling start-instance-refresh":
    result = pop(state["starts"])
    if "error" in result:
        finish(code=254, error=result["error"])
    finish(result["id"])
elif command in ("autoscaling cancel-instance-refresh", "autoscaling describe-scaling-activities"):
    finish()
elif command == "elbv2 describe-target-groups":
    finish("arn:lb/app")
elif command == "elbv2 describe-load-balancers":
    finish(state["dns"])
elif command == "elbv2 describe-target-health":
    finish("\t".join(pop(state["healthy"][arg("--target-group-arn")])))
elif command == "ssm get-parameter":
    finish(state["parameter"])
elif command == "ssm put-parameter":
    state["parameter"] = arg("--value")
    state["writes"].append(arg("--value"))
    finish()
finish(code=99, error="unexpected call")
'''


class RolloutTest(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory(prefix="meterengine-rollout-")
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        source = SOURCE.read_text()
        for name, value in (("POLL_SECONDS", 0), ("REFRESH_WAIT_POLLS", 3), ("TARGET_HEALTH_WAIT_POLLS", 2), ("RECOVERY_WAIT_POLLS", 3)):
            source = re.sub(rf"^{name}=.*$", f"{name}={value}", source, flags=re.M)
        self.script = self.root / "rollout.sh"
        self.script.write_text(source)
        bin_dir = self.root / "bin"
        bin_dir.mkdir()
        for name, body in (("aws", FAKE_AWS), ("sleep", "#!/bin/sh\nexit 0\n")):
            path = bin_dir / name
            path.write_text(body)
            path.chmod(path.stat().st_mode | stat.S_IEXEC)
        self.state_file = self.root / "state.json"
        self.trace = self.root / "trace"
        self.trace.touch()
        self.env = dict(os.environ, PATH=f"{bin_dir}{os.pathsep}{os.environ['PATH']}", FAKE_STATE=str(self.state_file), TRACE=str(self.trace))
        self.set_state()

    def set_state(self, **changes):
        state = {"group": GROUP, "min": 1, "desired": 1, "target_groups": TARGET_GROUPS, "in_service": ["i-new"],
                 "active": "", "parameter": PREVIOUS, "writes": [], "dns": ALB_DNS_NAME,
                 "starts": [{"id": "r-mine"}, {"id": "r-recovery"}],
                 "statuses": {"r-mine": ["InProgress", "Successful"], "r-recovery": ["Successful"]},
                 "healthy": {group: [["i-new"]] for group in TARGET_GROUPS}}
        state.update(changes)
        self.state_file.write_text(json.dumps(state))

    def state(self):
        return json.loads(self.state_file.read_text())

    def rollout(self, *arguments):
        return subprocess.run(["bash", str(self.script), *(arguments or (GROUP, PARAMETER, SHA))],
                              env=self.env, text=True, capture_output=True, timeout=30)

    def calls(self, command):
        return [line for line in self.trace.read_text().splitlines() if line.startswith(command)]

    def test_rejects_bad_arguments_before_any_call(self):
        for arguments in ((GROUP, PARAMETER), (GROUP, PARAMETER, "abc"), (GROUP, PARAMETER, SHA.upper())):
            result = self.rollout(*arguments)
            self.assertNotEqual(result.returncode, 0, arguments)
            self.assertIn("사용법", result.stderr)
        self.assertEqual(self.trace.read_text(), "")

    def test_success_writes_the_version_refreshes_and_prints_the_alb(self):
        result = self.rollout()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout, f"recorded_only=false\nalb_dns_name={ALB_DNS_NAME}\n")
        self.assertEqual(self.state()["writes"], [SHA])
        start = self.calls("autoscaling start-instance-refresh")
        self.assertEqual(len(start), 1)
        self.assertIn('"MinHealthyPercentage":100,"MaxHealthyPercentage":200,"SkipMatching":false', start[0])
        self.assertEqual(len(self.calls("elbv2 describe-target-health")), len(TARGET_GROUPS))

    def test_group_without_target_groups_is_refreshed_without_an_alb(self):
        self.set_state(target_groups=[], healthy={})
        result = self.rollout()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout, "recorded_only=false\nalb_dns_name=\n")
        start = self.calls("autoscaling start-instance-refresh")
        self.assertEqual(len(start), 1)
        self.assertIn('"MinHealthyPercentage":50,"SkipMatching":false', start[0])
        self.assertEqual(self.calls("elbv2"), [])

    def test_later_group_restores_the_version_read_before_the_first_group(self):
        self.set_state(parameter=SHA, statuses={"r-mine": ["Failed"], "r-recovery": ["Successful"]})
        result = subprocess.run(["bash", str(self.script), GROUP, PARAMETER, SHA], env=dict(self.env, ROLLOUT_PREVIOUS=PREVIOUS),
                                text=True, capture_output=True, timeout=30)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.state()["writes"], [SHA, PREVIOUS])

    def test_minimum_zero_only_records_the_version(self):
        self.set_state(min=0, desired=0)
        result = self.rollout()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout, f"recorded_only=true\nalb_dns_name={ALB_DNS_NAME}\n")
        self.assertEqual(self.state()["writes"], [SHA])
        self.assertEqual(self.calls("autoscaling start-instance-refresh"), [])

    def test_minimum_zero_with_running_servers_warns(self):
        self.set_state(min=0, desired=2)
        result = self.rollout()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("recorded_only=true", result.stdout)
        self.assertIn("경고", result.stderr)

    def test_missing_group_stops_before_writing(self):
        self.set_state(group="")
        result = self.rollout()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("없다", result.stderr)
        self.assertEqual(self.calls("ssm"), [])

    def test_refresh_in_progress_stops_before_writing(self):
        self.set_state(active="r-other")
        result = self.rollout()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("r-other", result.stderr)
        self.assertEqual(self.calls("ssm"), [])

    def test_failed_refresh_restores_the_previous_version_and_recovers(self):
        self.set_state(statuses={"r-mine": ["InProgress", "Failed"], "r-recovery": ["Successful"]})
        result = self.rollout()
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(result.stdout, "")
        self.assertEqual(self.state()["writes"], [SHA, PREVIOUS])
        self.assertEqual(len(self.calls("autoscaling start-instance-refresh")), 2)
        self.assertIn("이전 버전으로 되돌렸다", result.stderr)

    def test_own_timeout_is_cancelled_then_restored(self):
        self.set_state(statuses={"r-mine": ["InProgress", "InProgress", "InProgress", "Cancelled"], "r-recovery": ["Successful"]})
        result = self.rollout()
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(len(self.calls("autoscaling cancel-instance-refresh")), 1)
        self.assertEqual(self.state()["writes"], [SHA, PREVIOUS])

    def test_refresh_cancelled_by_someone_else_keeps_the_version(self):
        self.set_state(statuses={"r-mine": ["InProgress", "Cancelled"]})
        result = self.rollout()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("끼어들었다", result.stderr)
        self.assertEqual(self.state()["writes"], [SHA])
        self.assertEqual(self.calls("autoscaling cancel-instance-refresh"), [])

    def test_refresh_started_by_someone_else_keeps_the_version(self):
        self.set_state(starts=[{"error": "An error occurred (InstanceRefreshInProgress)"}])
        result = self.rollout()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("끼어들었다", result.stderr)
        self.assertEqual(self.state()["writes"], [SHA])

    def test_start_failure_restores_the_version_without_a_refresh(self):
        self.set_state(starts=[{"error": "An error occurred (AccessDenied)"}])
        result = self.rollout()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("AccessDenied", result.stderr)
        self.assertEqual(self.state()["writes"], [SHA, PREVIOUS])
        self.assertEqual(len(self.calls("autoscaling start-instance-refresh")), 1)

    def test_unhealthy_targets_after_success_restore_the_version(self):
        self.set_state(healthy={"arn:tg/front": [["i-new"]], "arn:tg/back": [[]]})
        result = self.rollout()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("healthy", result.stderr)
        self.assertEqual(self.state()["writes"], [SHA, PREVIOUS])

    def test_targets_that_become_healthy_late_pass(self):
        self.set_state(healthy={"arn:tg/front": [["i-new"]], "arn:tg/back": [[], ["i-new"]]})
        result = self.rollout()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.state()["writes"], [SHA])

    def test_previous_value_that_is_not_a_sha_is_left_alone(self):
        self.set_state(parameter="unset", statuses={"r-mine": ["Failed"]})
        result = self.rollout()
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.state()["writes"], [SHA])
        self.assertIn("deploy/rollout.sh", result.stderr)

    def test_failed_recovery_prints_the_manual_steps(self):
        self.set_state(statuses={"r-mine": ["Failed"], "r-recovery": ["Failed"]})
        result = self.rollout()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn(f"deploy/rollout.sh {GROUP} {PARAMETER} <SHA>", result.stderr)

    def test_script_is_executable(self):
        self.assertTrue(os.access(SOURCE, os.X_OK))


if __name__ == "__main__":
    unittest.main()
