import json
from pathlib import Path
import re
import unittest


DEPLOY = Path(__file__).resolve().parents[1]
DB_DASHBOARD = DEPLOY / "grafana/dashboards/meterengine-db.json"
CLOUDWATCH_CONFIG = DEPLOY / "cloudwatch/config.yml"
MAX_TITLE_LENGTH = 20
RDS_METRIC_SELECTOR = re.compile(r"\b(aws_rds_[a-z0-9_]+)(\{[^}]*\})?")
SELECTED_INSTANCE = 'dbinstance_identifier="$db"'
DAYS_UNTIL_FULL_TITLE = "저장 공간 소진까지 일수"
SECONDS_PER_DAY = "86400"


def panels():
    dashboard = json.loads(DB_DASHBOARD.read_text())
    return [panel for panel in dashboard["panels"] if panel["type"] != "row"]


def expressions():
    return [(panel["title"], target["expr"]) for panel in panels() for target in panel["targets"]]


def snake_case(name):
    return re.sub(r"([a-z0-9])([A-Z])", r"\1_\2", name).lower()


def safe_name(name):
    return re.sub(r"__+", "_", re.sub(r"[^a-zA-Z0-9:_]", "_", name))


# cloudwatch_exporter v0.16.0이 지표 이름을 만드는 규칙을 옮겼다. 통계 접미사는 safe_name 뒤에 붙어 밑줄 둘이 남기도 한다.
def exported_name(namespace, metric, statistic):
    return safe_name(namespace.lower() + "_" + snake_case(metric)) + "_" + safe_name(snake_case(statistic))


def cloudwatch_metric_names():
    names = set()
    rule = {}
    for line in CLOUDWATCH_CONFIG.read_text().splitlines():
        key, _, value = line.strip().removeprefix("- ").partition(": ")
        if key == "aws_namespace":
            rule = {"namespace": value}
        elif key == "aws_metric_name":
            rule["metric"] = value
        elif key in ("aws_statistics", "aws_extended_statistics"):
            for statistic in value.strip("[]").split(","):
                names.add(exported_name(rule["namespace"], rule["metric"], statistic.strip()))
    return names


class DbDashboardTest(unittest.TestCase):
    def test_every_panel_has_description(self):
        for panel in panels():
            self.assertTrue(panel.get("description", "").strip(), panel["title"])

    def test_every_panel_title_is_short_name(self):
        for panel in panels():
            self.assertLessEqual(len(panel["title"]), MAX_TITLE_LENGTH, panel["title"])

    def test_every_panel_reads_prometheus_datasource(self):
        for panel in panels():
            self.assertEqual({"type": "prometheus", "uid": "prometheus"}, panel["datasource"], panel["title"])

    def test_days_until_full_is_not_divided_by_seconds_per_day_again(self):
        panel = next(panel for panel in panels() if panel["title"] == DAYS_UNTIL_FULL_TITLE)
        self.assertEqual("d", panel["fieldConfig"]["defaults"]["unit"])
        for target in panel["targets"]:
            self.assertNotIn(SECONDS_PER_DAY, target["expr"])

    def test_rds_panels_read_metrics_the_cloudwatch_config_exports(self):
        exported = cloudwatch_metric_names()
        for title, expr in expressions():
            for metric, _ in RDS_METRIC_SELECTOR.findall(expr):
                self.assertIn(metric, exported, title)

    def test_rds_metrics_filter_selected_instance(self):
        for title, expr in expressions():
            for _, selector in RDS_METRIC_SELECTOR.findall(expr):
                self.assertIn(SELECTED_INSTANCE, selector, title)

    def test_postgres_panels_ignore_instance_variable(self):
        for title, expr in expressions():
            if not RDS_METRIC_SELECTOR.search(expr):
                self.assertNotIn("$db", expr, title)


if __name__ == "__main__":
    unittest.main()
