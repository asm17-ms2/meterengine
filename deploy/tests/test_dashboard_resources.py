import json
from pathlib import Path
import re
import unittest


DASHBOARD = Path(__file__).resolve().parents[1] / "grafana/dashboards/meterengine-resources.json"
PROMETHEUS = {"type": "prometheus", "uid": "prometheus"}
TITLE_MAX_LENGTH = 20
COLOR_FILTER = 'instance=~"$instance"'
METRICS_SPLIT_BY_COLOR = ("jvm_", "tomcat_", "hikaricp_", "http_server_requests_active_",
                          "process_start_time_seconds", "logback_events_")
SPLIT_BY_COLOR_METRIC = re.compile(r"\b(?:" + "|".join(map(re.escape, METRICS_SPLIT_BY_COLOR)) + ")")


class ResourcesDashboardTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        dashboard = json.loads(DASHBOARD.read_text())
        cls.panels = [panel for panel in dashboard["panels"] if panel["type"] != "row"]
        cls.expressions = [(panel["title"], target["expr"])
                           for panel in cls.panels for target in panel["targets"]]

    def test_every_panel_title_is_short(self):
        for panel in self.panels:
            self.assertLessEqual(len(panel["title"]), TITLE_MAX_LENGTH, panel["title"])

    def test_every_panel_has_a_description(self):
        for panel in self.panels:
            self.assertTrue(panel.get("description", "").strip(), panel["title"])

    def test_every_panel_reads_the_provisioned_prometheus(self):
        for panel in self.panels:
            self.assertEqual(panel["datasource"], PROMETHEUS, panel["title"])

    def test_color_variable_filters_only_metrics_split_by_color(self):
        for title, expression in self.expressions:
            split_by_color = SPLIT_BY_COLOR_METRIC.search(expression) is not None
            self.assertEqual(COLOR_FILTER in expression, split_by_color, f"{title}: {expression}")


if __name__ == "__main__":
    unittest.main()
