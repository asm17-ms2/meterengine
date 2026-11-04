import json
from pathlib import Path
import unittest

from promql import selectors


DASHBOARD = Path(__file__).resolve().parents[1] / "grafana/dashboards/meterengine-resources.json"
PROMETHEUS = {"type": "prometheus", "uid": "prometheus"}
TITLE_MAX_LENGTH = 20
SERVER_FILTER = 'instance=~"$instance"'
OBSERVABILITY_ROW = "관측"


class ResourcesDashboardTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        dashboard = json.loads(DASHBOARD.read_text())
        cls.panels, cls.app_server_panels, row = [], [], None
        for panel in dashboard["panels"]:
            if panel["type"] == "row":
                row = panel["title"]
                continue
            cls.panels.append(panel)
            if row != OBSERVABILITY_ROW:
                cls.app_server_panels.append(panel)
        cls.variables = {variable["name"]: variable for variable in dashboard["templating"]["list"]}

    def test_every_panel_title_is_short(self):
        for panel in self.panels:
            self.assertLessEqual(len(panel["title"]), TITLE_MAX_LENGTH, panel["title"])

    def test_every_panel_has_a_description(self):
        for panel in self.panels:
            self.assertTrue(panel.get("description", "").strip(), panel["title"])

    def test_every_panel_reads_the_provisioned_prometheus(self):
        for panel in self.panels:
            self.assertEqual(panel["datasource"], PROMETHEUS, panel["title"])

    def test_server_variable_lists_backend_servers_of_the_chosen_env(self):
        self.assertEqual(self.variables["instance"]["query"]["query"], 'label_values(up{job="backend", env=~"$env"}, instance)')

    def test_server_variable_filters_every_app_server_metric(self):
        for panel in self.app_server_panels:
            for target in panel["targets"]:
                for name, matchers in selectors(target["expr"]):
                    self.assertIn(SERVER_FILTER, matchers, f"{panel['title']}: {name}")

    def test_every_app_server_series_is_labeled_by_server(self):
        for panel in self.app_server_panels:
            for target in panel["targets"]:
                self.assertIn("{{instance}}", target["legendFormat"], panel["title"])


if __name__ == "__main__":
    unittest.main()
