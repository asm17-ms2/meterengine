import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "deploy/server/cpu-metric.sh"
UNIT = ROOT / "deploy/server/cpu-metric.service"

# user nice system idle iowait irq softirq steal guest guest_nice
PROC_STAT = """\
cpu  100 10 40 800 20 5 15 10 7 0
cpu0 50 5 20 400 10 2 8 5 3 0
intr 12345
"""

NO_BASELINE = {"previous_busy": "", "previous_total": "", "ready_since": ""}


def baseline(busy, total, ready_since):
    return {"previous_busy": str(busy), "previous_total": str(total), "ready_since": str(ready_since)}


def proc_stat(busy, idle):
    return f"cpu  {busy} 0 0 {idle} 0 0 0 0 0 0\n"


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
        self.stat = Path(self.temp.name) / "stat"
        self.env = dict(os.environ, PATH=f"{self.bin}:{os.environ['PATH']}", TRACE=str(self.trace), PROC_STAT=str(self.stat))

    def fake(self, name, body):
        path = self.bin / name
        path.write_text("#!/usr/bin/env bash\n" + body)
        path.chmod(0o755)


class CpuMetricTest(FakeCommandTestCase):
    def run_bash(self, script, stdin=""):
        return subprocess.run(["bash", "-c", script], env=self.env, input=stdin, text=True, capture_output=True, timeout=10)

    def test_server_outside_an_asg_exits_cleanly_without_aws_calls(self):
        self.fake("curl", "exit 22\n")
        self.fake("aws", 'echo "aws $*" >> "$TRACE"\n')
        result = subprocess.run(["bash", str(SCRIPT)], env=self.env, text=True, capture_output=True, timeout=10)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.trace.read_text(), "")

    def test_counters_treat_idle_and_iowait_as_not_busy(self):
        result = self.run_bash(f"source {SCRIPT}; cpu_counters", stdin=PROC_STAT)
        self.assertEqual(result.stdout, "180 1000")

    def test_sourcing_does_not_start_the_loop(self):
        self.fake("curl", 'echo "curl $*" >> "$TRACE"; exit 22\n')
        result = self.run_bash(f"source {SCRIPT}; echo loaded")
        self.assertEqual(result.stdout, "loaded\n")
        self.assertEqual(self.trace.read_text(), "")

    @unittest.skipUnless(gnu_date_available(), "GNU date required")
    def test_cpu_is_divided_by_desired_capacity_at_the_slot_time(self):
        self.fake("aws", 'printf "%s\\n" "$@" >> "$TRACE"\n')
        result = self.run_bash(f"source {SCRIPT}; ASG_NAME=meterengine-staging-app REGION=ap-northeast-2; put_metrics 1700000000 2 70.0")
        self.assertEqual(result.returncode, 0, result.stderr)
        args = self.trace.read_text().splitlines()
        self.assertEqual(args[args.index("--namespace") + 1], "MeterEngine/Scaling")
        metrics = {item["MetricName"]: item for item in json.loads(args[args.index("--metric-data") + 1])}
        scaling = metrics["CPUUtilizationPerDesiredInstance"]
        self.assertEqual(scaling["Value"], 35.0)
        self.assertEqual(scaling["Unit"], "Percent")
        self.assertEqual(scaling["StorageResolution"], 1)
        self.assertEqual(scaling["Timestamp"], "2023-11-14T22:13:20Z")
        self.assertEqual(scaling["Dimensions"], [{"Name": "AutoScalingGroupName", "Value": "meterengine-staging-app"}])
        self.assertEqual(metrics["CPUUtilization"]["Value"], 70.0)

    def test_unit_runs_the_repository_script(self):
        self.assertIn("ExecStart=/opt/meterengine/deploy/server/cpu-metric.sh\n", UNIT.read_text())
        self.assertTrue(os.access(SCRIPT, os.X_OK))


class MeasureSlotTest(FakeCommandTestCase):
    def setUp(self):
        super().setUp()
        self.fake(
            "curl",
            'case "$*" in\n'
            '*/actuator/health/readiness*) echo readiness >> "$TRACE"; printf "%s" "$READINESS"; [ "$READINESS" != 000 ] ;;\n'
            "*) exit 22 ;;\n"
            "esac\n",
        )

    def measure(self, readiness, stat, state, slot):
        if stat is None:
            self.stat.unlink(missing_ok=True)
        else:
            self.stat.write_text(stat)
        env = dict(self.env, READINESS=readiness)
        script = (
            f"source {SCRIPT}; cpu=stale; previous_busy={state['previous_busy']}; "
            f"previous_total={state['previous_total']}; ready_since={state['ready_since']}; "
            f"measure_slot {slot}; printf '%s|%s|%s|%s' \"$cpu\" \"$previous_busy\" \"$previous_total\" \"$ready_since\""
        )
        result = subprocess.run(["bash", "-c", script], env=env, text=True, capture_output=True, timeout=10)
        self.assertEqual(result.returncode, 0, result.stderr)
        cpu, previous_busy, previous_total, ready_since = result.stdout.split("|")
        return {"cpu": cpu, "previous_busy": previous_busy, "previous_total": previous_total, "ready_since": ready_since}

    def test_first_ready_slot_starts_the_hold_and_sets_the_baseline(self):
        state = self.measure("200", proc_stat(300, 700), NO_BASELINE, 1700000010)
        self.assertEqual(state, {"cpu": "", **baseline(300, 1000, 1700000010)})

    def test_slot_reports_busy_share_since_the_baseline(self):
        state = self.measure("200", proc_stat(325, 1675), baseline(300, 1000, 1699999900), 1700000010)
        self.assertEqual(state, {"cpu": "2.5", **baseline(325, 2000, 1699999900)})

    def test_slots_within_a_minute_of_becoming_ready_are_held_back(self):
        state = self.measure("200", proc_stat(900, 1100), baseline(300, 1000, 1700000000), 1700000050)
        self.assertEqual(state, {"cpu": "", **baseline(900, 2000, 1700000000)})

    def test_slot_a_minute_after_becoming_ready_is_put(self):
        state = self.measure("200", proc_stat(900, 1100), baseline(300, 1000, 1700000000), 1700000060)
        self.assertEqual(state["cpu"], "60.0")

    def test_not_ready_slot_puts_nothing_and_restarts_the_hold(self):
        state = self.measure("503", proc_stat(325, 1675), baseline(300, 1000, 1699999900), 1700000010)
        self.assertEqual(state, {"cpu": "", **NO_BASELINE})

    def test_not_ready_slot_does_not_read_the_counters(self):
        self.measure("503", None, baseline(300, 1000, 1699999900), 1700000010)
        self.assertEqual(self.trace.read_text().splitlines(), ["readiness"])

    def test_unanswered_readiness_skips_the_slot_and_keeps_the_state(self):
        state = self.measure("000", proc_stat(325, 1675), baseline(300, 1000, 1699999900), 1700000010)
        self.assertEqual(state, {"cpu": "", **baseline(300, 1000, 1699999900)})

    def test_other_readiness_code_skips_the_slot_and_keeps_the_state(self):
        state = self.measure("404", proc_stat(325, 1675), baseline(300, 1000, 1699999900), 1700000010)
        self.assertEqual(state, {"cpu": "", **baseline(300, 1000, 1699999900)})

    def test_unreadable_counters_skip_the_slot_and_keep_the_state(self):
        state = self.measure("200", None, baseline(300, 1000, 1699999900), 1700000010)
        self.assertEqual(state, {"cpu": "", **baseline(300, 1000, 1699999900)})

    def test_counters_that_did_not_advance_skip_the_slot(self):
        state = self.measure("200", proc_stat(300, 700), baseline(300, 1000, 1699999900), 1700000010)
        self.assertEqual(state, {"cpu": "", **baseline(300, 1000, 1699999900)})


if __name__ == "__main__":
    unittest.main()
