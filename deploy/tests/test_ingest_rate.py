import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "deploy/server/ingest-rate.sh"
UNIT = ROOT / "deploy/server/ingest-rate.service"
ASG = ROOT / "deploy/terraform/modules/app_fleet/asg.tf"

PROMETHEUS_TEXT = """\
http_server_requests_seconds_count{error="none",method="POST",outcome="SUCCESS",status="202",uri="/v1/events"} 120.0
http_server_requests_seconds_count{error="none",method="POST",outcome="CLIENT_ERROR",status="400",uri="/v1/events"} 3.0
http_server_requests_seconds_count{error="none",method="POST",outcome="SUCCESS",status="202",uri="/v1/event-batches"} 7.0
http_server_requests_seconds_count{error="none",method="GET",outcome="SUCCESS",status="200",uri="/v1/events"} 50.0
http_server_requests_seconds_count{error="none",method="POST",outcome="SUCCESS",status="201",uri="/v1/customers"} 9.0
"""

NO_BASELINE = {"previous_total": "", "previous_slot": ""}


def baseline(total, slot):
    return {"previous_total": str(total), "previous_slot": str(slot)}


def gnu_date_available():
    return subprocess.run(["date", "-u", "-d", "@0", "+%Y"], capture_output=True).returncode == 0


class FakeCommandTestCase(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.bin = Path(self.temp.name) / "bin"
        self.bin.mkdir()
        self.trace = Path(self.temp.name) / "trace"
        self.trace.touch()
        self.env = dict(os.environ, PATH=f"{self.bin}:{os.environ['PATH']}", TRACE=str(self.trace))

    def fake(self, name, body):
        path = self.bin / name
        path.write_text("#!/usr/bin/env bash\n" + body)
        path.chmod(0o755)


class IngestRateTest(FakeCommandTestCase):
    def run_bash(self, script, stdin=""):
        return subprocess.run(["bash", "-c", script], env=self.env, input=stdin, text=True, capture_output=True, timeout=10)

    def test_server_outside_an_asg_exits_cleanly_without_aws_calls(self):
        self.fake("curl", "exit 22\n")
        self.fake("aws", 'echo "aws $*" >> "$TRACE"\n')
        result = subprocess.run(["bash", str(SCRIPT)], env=self.env, text=True, capture_output=True, timeout=10)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.trace.read_text(), "")

    def test_total_counts_only_ingest_posts(self):
        result = self.run_bash(f"source {SCRIPT}; ingest_request_total", stdin=PROMETHEUS_TEXT)
        self.assertEqual(result.stdout, "130")

    def test_sourcing_does_not_start_the_loop(self):
        self.fake("curl", 'echo "curl $*" >> "$TRACE"; exit 22\n')
        result = self.run_bash(f"source {SCRIPT}; echo loaded")
        self.assertEqual(result.stdout, "loaded\n")
        self.assertEqual(self.trace.read_text(), "")

    @unittest.skipUnless(gnu_date_available(), "GNU date required")
    def test_metrics_are_divided_by_desired_capacity_at_the_slot_time(self):
        self.fake("aws", 'printf "%s\\n" "$@" >> "$TRACE"\n')
        result = self.run_bash(f"source {SCRIPT}; ASG_NAME=meterengine-staging-app REGION=ap-northeast-2; put_metrics 1700000000 3 900.0")
        self.assertEqual(result.returncode, 0, result.stderr)
        args = self.trace.read_text().splitlines()
        self.assertEqual(args[args.index("--namespace") + 1], "MeterEngine/Scaling")
        metrics = {item["MetricName"]: item for item in json.loads(args[args.index("--metric-data") + 1])}
        scaling = metrics["IngestRequestsPerSecondPerDesiredInstance"]
        self.assertEqual(scaling["Value"], 300.0)
        self.assertEqual(scaling["Timestamp"], "2023-11-14T22:13:20Z")
        self.assertEqual(scaling["Dimensions"], [{"Name": "AutoScalingGroupName", "Value": "meterengine-staging-app"}])
        self.assertEqual(metrics["IngestRequestsPerSecond"]["Value"], 900.0)

    def test_unit_runs_the_repository_script(self):
        self.assertIn("ExecStart=/opt/meterengine/deploy/server/ingest-rate.sh\n", UNIT.read_text())
        self.assertTrue(os.access(SCRIPT, os.X_OK))


class MeasureSlotTest(FakeCommandTestCase):
    def setUp(self):
        super().setUp()
        self.fake(
            "curl",
            'case "$*" in\n'
            '*/actuator/health/readiness*) echo readiness >> "$TRACE"; printf "%s" "$READINESS"; [ "$READINESS" != 000 ] ;;\n'
            '*/actuator/prometheus*) echo prometheus >> "$TRACE"; [ "$TOTAL" != down ] || exit 22; '
            'printf \'http_server_requests_seconds_count{method="POST",uri="/v1/events"} %s\\n\' "$TOTAL" ;;\n'
            "*) exit 22 ;;\n"
            "esac\n",
        )

    def measure(self, readiness, total, baseline, slot):
        env = dict(self.env, READINESS=readiness, TOTAL=str(total))
        script = (
            f"source {SCRIPT}; rate=stale; previous_total={baseline['previous_total']}; "
            f"previous_slot={baseline['previous_slot']}; "
            f"measure_slot {slot}; printf '%s|%s|%s' \"$rate\" \"$previous_total\" \"$previous_slot\""
        )
        result = subprocess.run(["bash", "-c", script], env=env, text=True, capture_output=True, timeout=10)
        self.assertEqual(result.returncode, 0, result.stderr)
        rate, previous_total, previous_slot = result.stdout.split("|")
        return {"rate": rate, "previous_total": previous_total, "previous_slot": previous_slot}

    def test_first_ready_slot_only_sets_the_baseline(self):
        state = self.measure("200", 130, NO_BASELINE, 1700000010)
        self.assertEqual(state, {"rate": "", "previous_total": "130", "previous_slot": "1700000010"})

    def test_slot_reports_the_increase_per_second(self):
        state = self.measure("200", 130, baseline(30, 1700000000), 1700000010)
        self.assertEqual(state, {"rate": "10.0", "previous_total": "130", "previous_slot": "1700000010"})

    def test_counter_drop_skips_the_slot_and_resets_the_baseline(self):
        state = self.measure("200", 5, baseline(130, 1700000000), 1700000010)
        self.assertEqual(state, {"rate": "", "previous_total": "5", "previous_slot": "1700000010"})

    def test_missed_slot_is_divided_by_the_real_elapsed_seconds(self):
        state = self.measure("200", 300, baseline(100, 1700000000), 1700000020)
        self.assertEqual(state["rate"], "10.0")

    def test_not_ready_slot_puts_nothing_and_forgets_the_baseline(self):
        state = self.measure("503", 130, baseline(30, 1700000000), 1700000010)
        self.assertEqual(state, {"rate": "", **NO_BASELINE})

    def test_not_ready_slot_does_not_read_the_counter(self):
        self.measure("503", 130, baseline(30, 1700000000), 1700000010)
        self.assertEqual(self.trace.read_text().splitlines(), ["readiness"])

    def test_first_ready_slot_after_not_ready_only_sets_the_baseline(self):
        not_ready = self.measure("503", 130, baseline(30, 1700000000), 1700000010)
        state = self.measure("200", 150, not_ready, 1700000020)
        self.assertEqual(state, {"rate": "", "previous_total": "150", "previous_slot": "1700000020"})

    def test_unanswered_readiness_skips_the_slot_and_keeps_the_baseline(self):
        state = self.measure("000", 130, baseline(30, 1700000000), 1700000010)
        self.assertEqual(state, {"rate": "", **baseline(30, 1700000000)})

    def test_other_readiness_code_skips_the_slot_and_keeps_the_baseline(self):
        state = self.measure("404", 130, baseline(30, 1700000000), 1700000010)
        self.assertEqual(state, {"rate": "", **baseline(30, 1700000000)})

    def test_failed_counter_read_skips_the_slot_and_keeps_the_baseline(self):
        state = self.measure("200", "down", baseline(30, 1700000000), 1700000010)
        self.assertEqual(state, {"rate": "", **baseline(30, 1700000000)})

    def test_readiness_is_checked_before_the_counter(self):
        self.measure("200", 130, baseline(30, 1700000000), 1700000010)
        self.assertEqual(self.trace.read_text().splitlines(), ["readiness", "prometheus"])


class ScalingPolicyContractTest(unittest.TestCase):
    def script_constant(self, name):
        return re.search(rf"^readonly {name}=(\S+)$", SCRIPT.read_text(), re.M).group(1)

    def policy(self):
        return re.search(r'resource "aws_autoscaling_policy" "ingest_requests" \{.*?^\}', ASG.read_text(), re.M | re.S).group()

    def test_policy_reads_the_metric_the_script_puts(self):
        policy = self.policy()
        self.assertIn(f'namespace   = "{self.script_constant("NAMESPACE")}"', policy)
        self.assertIn(f'metric_name = "{self.script_constant("SCALING_METRIC")}"', policy)
        self.assertIn('name  = "AutoScalingGroupName"', policy)

    def test_policy_sums_the_ten_second_slots(self):
        policy = self.policy()
        self.assertIn('statistic   = "Sum"', policy)
        self.assertIn(f"period      = {self.script_constant('PERIOD_SECONDS')}", policy)

if __name__ == "__main__":
    unittest.main()
