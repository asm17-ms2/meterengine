import json
from pathlib import Path
import re
import unittest


DASHBOARDS = Path(__file__).resolve().parents[1] / "grafana/dashboards"
JOBS_DASHBOARD = DASHBOARDS / "meterengine-jobs.json"
BILLING_DASHBOARD = DASHBOARDS / "meterengine-billing.json"
MAX_TITLE_LENGTH = 30
FORBIDDEN_CHARACTERS = "\u00b7\u2014\u2013\u007e\u2018\u2019\u201c\u201d"
RANGE_WINDOW = re.compile(r"\b(?:rate|increase)\([^\[]*\[([^\]]+)\]")
JOB_METRIC = "meterengine_scheduled_job_"
# 청구와 결제의 확정 대기 패널은 자기 계산 작업의 실패 수만 같이 그린다.
BILLING_OWN_JOB = 'job_name="invoice_finalization_backlog"'


def load(path):
    return json.loads(path.read_text())


def panels(dashboard):
    return [panel for panel in dashboard["panels"] if panel["type"] != "row"]


def expressions(dashboard):
    return [target["expr"] for panel in panels(dashboard) for target in panel["targets"]]


class JobsDashboardTest(unittest.TestCase):
    def setUp(self):
        self.dashboard = load(JOBS_DASHBOARD)

    def test_dashboard_follows_meterengine_conventions(self):
        self.assertEqual("meterengine-jobs", self.dashboard["uid"])
        self.assertTrue(self.dashboard["title"].startswith("MeterEngine "))
        self.assertEqual(39, self.dashboard["schemaVersion"])
        self.assertIn("meterengine", self.dashboard["tags"])
        self.assertIn({"type": "dashboards", "tags": ["meterengine"], "asDropdown": True,
                       "title": "MeterEngine 대시보드", "includeVars": False, "keepTime": True},
                      self.dashboard["links"])

    def test_every_panel_has_short_title_and_description_naming_its_metric(self):
        for panel in panels(self.dashboard):
            self.assertLessEqual(len(panel["title"]), MAX_TITLE_LENGTH, panel["title"])
            self.assertIn("지표:", panel.get("description", ""), panel["title"])

    def test_every_panel_and_variable_reads_prometheus(self):
        sources = [panel["datasource"] for panel in panels(self.dashboard)]
        sources += [variable["datasource"] for variable in self.dashboard["templating"]["list"]]
        for source in sources:
            self.assertEqual({"type": "prometheus", "uid": "prometheus"}, source)

    def test_latency_quantiles_merge_instances(self):
        for expr in expressions(self.dashboard):
            if "histogram_quantile" not in expr:
                continue
            self.assertIn("sum by (le", expr)

    def test_rate_windows_follow_grafana_interval(self):
        for expr in expressions(self.dashboard):
            for window in RANGE_WINDOW.findall(expr):
                self.assertIn(window, ("$__rate_interval", "$__range"), expr)

    def test_job_name_variable_applies_only_to_scheduled_job_metrics(self):
        for expr in expressions(self.dashboard):
            self.assertEqual(JOB_METRIC in expr, '$job_name' in expr, expr)

    # 조회식의 정규식 연산자는 PromQL 문법이라 글자 검사에서 뺀다.
    def test_prose_has_no_forbidden_characters(self):
        texts = [self.dashboard["__comment"]]
        texts += [variable["description"] for variable in self.dashboard["templating"]["list"]]
        texts += [panel["title"] for panel in self.dashboard["panels"]]
        texts += [panel["description"] for panel in panels(self.dashboard)]
        for text in texts:
            for character in FORBIDDEN_CHARACTERS:
                self.assertNotIn(character, text)


class BillingDashboardTest(unittest.TestCase):
    def test_job_and_webhook_delivery_panels_moved_to_jobs_dashboard(self):
        for expr in expressions(load(BILLING_DASHBOARD)):
            self.assertNotIn("meterengine_webhook_delivery", expr)
            if JOB_METRIC in expr:
                self.assertIn(BILLING_OWN_JOB, expr)


if __name__ == "__main__":
    unittest.main()
