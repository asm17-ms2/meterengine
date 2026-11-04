import json
from pathlib import Path
import re
import unittest


DEPLOY = Path(__file__).resolve().parents[1]
DASHBOARDS = DEPLOY / "grafana/dashboards"
OVERVIEW = DASHBOARDS / "meterengine-overview.json"
MAX_TITLE_LENGTH = 20
METRIC_SELECTOR = re.compile(r"([a-z_:][a-z0-9_:]*)\{")
RANGE_WINDOW = re.compile(r"\[([^\]]+)\]")
HISTOGRAM_SERIES_SUFFIX = re.compile(r"_(bucket|count|sum)$")


def metric_names(panel):
    return {name for target in panel.get("targets", []) for name in METRIC_SELECTOR.findall(target["expr"])}


def metric_family(name):
    return HISTOGRAM_SERIES_SUFFIX.sub("", name)


class OverviewDashboardTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.dashboard = json.loads(OVERVIEW.read_text())
        cls.panels = cls.dashboard["panels"]

    def targets_reading(self, metric):
        return [target for panel in self.panels for target in panel.get("targets", [])
                if metric in target["expr"]]

    def expressions_reading(self, metric):
        return [target["expr"] for target in self.targets_reading(metric)]

    def test_replaces_the_slo_dashboard(self):
        self.assertEqual("meterengine-overview", self.dashboard["uid"])
        self.assertEqual("MeterEngine 서비스 개요", self.dashboard["title"])
        self.assertFalse((DASHBOARDS / "meterengine-slo.json").exists())

    def test_links_to_the_other_meterengine_dashboards(self):
        self.assertIn("meterengine", self.dashboard["tags"])
        link = next(link for link in self.dashboard["links"] if link["type"] == "dashboards")
        self.assertEqual(["meterengine"], link["tags"])

    def test_every_panel_has_a_short_title(self):
        for panel in self.panels:
            self.assertTrue(0 < len(panel["title"]) <= MAX_TITLE_LENGTH, panel["title"])

    def test_every_panel_description_names_its_source_metric(self):
        for panel in self.panels:
            families = {metric_family(name) for name in metric_names(panel)}
            self.assertTrue(families, panel["title"])
            description = panel.get("description", "")
            self.assertTrue(any(family in description for family in families), panel["title"])

    def test_every_panel_reads_the_provisioned_prometheus(self):
        for panel in self.panels:
            self.assertEqual({"type": "prometheus", "uid": "prometheus"}, panel["datasource"], panel["title"])

    def test_backend_latency_uses_successful_api_responses_only(self):
        expressions = self.expressions_reading("http_server_requests_seconds_bucket")
        self.assertTrue(expressions)
        for expression in expressions:
            self.assertIn('outcome="SUCCESS"', expression)
            self.assertIn('uri!~"/actuator.*|UNKNOWN"', expression)

    def test_stat_panels_read_one_value_at_the_end_of_the_range(self):
        stats = [panel for panel in self.panels if panel["type"] == "stat"]
        self.assertTrue(stats)
        for stat in stats:
            for target in stat["targets"]:
                self.assertTrue(target.get("instant"), stat["title"])
                self.assertIs(False, target.get("range"), stat["title"])

    def test_top_ten_tables_read_the_whole_range_at_once(self):
        tables = [panel for panel in self.panels if panel["type"] == "table"]
        self.assertTrue(tables)
        for table in tables:
            for target in table["targets"]:
                self.assertTrue(target.get("instant"), table["title"])
                self.assertIn("[$__range]", target["expr"], table["title"])

    def test_time_series_use_the_grafana_rate_interval(self):
        for panel in self.panels:
            if panel["type"] != "timeseries":
                continue
            for target in panel["targets"]:
                self.assertEqual({"$__rate_interval"}, set(RANGE_WINDOW.findall(target["expr"])), panel["title"])


if __name__ == "__main__":
    unittest.main()
