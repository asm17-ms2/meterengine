import json
import re
from pathlib import Path
import unittest


INGEST_DASHBOARD = Path(__file__).resolve().parents[1] / "grafana/dashboards/meterengine-ingest.json"
INGEST_URIS = 'uri=~"/v1/events|/v1/event-batches"'
MAX_TITLE_LENGTH = 20


def load_dashboard():
    return json.loads(INGEST_DASHBOARD.read_text())


def chart_panels():
    return [panel for panel in load_dashboard()["panels"] if panel["type"] != "row"]


def expressions():
    return [target["expr"] for panel in chart_panels() for target in panel["targets"]]


class IngestDashboardTest(unittest.TestCase):
    def test_dashboard_is_identified_as_meterengine_ingest(self):
        dashboard = load_dashboard()
        self.assertEqual("meterengine-ingest", dashboard["uid"])
        self.assertEqual("MeterEngine 이벤트 수집", dashboard["title"])
        self.assertIn("meterengine", dashboard["tags"])

    def test_every_panel_has_a_short_title_and_a_description_naming_its_metric(self):
        for panel in chart_panels():
            self.assertLessEqual(len(panel["title"]), MAX_TITLE_LENGTH, panel["title"])
            self.assertTrue(panel.get("description", "").strip(), panel["title"])
            self.assertIn("지표: `", panel["description"], panel["title"])

    def test_every_panel_reads_the_prometheus_datasource(self):
        for panel in chart_panels():
            self.assertEqual("prometheus", panel["datasource"]["uid"], panel["title"])

    def test_latency_counts_only_successful_ingest_requests(self):
        latency = [expr for expr in expressions() if "http_server_requests_seconds_bucket" in expr]
        self.assertTrue(latency)
        for expr in latency:
            self.assertIn('outcome="SUCCESS"', expr)
            self.assertIn(INGEST_URIS, expr)

    def test_ingest_api_panels_split_single_and_batch_by_uri(self):
        api_panels = [
            panel
            for panel in chart_panels()
            if any("http_server_requests" in target["expr"] for target in panel["targets"])
        ]
        self.assertEqual(3, len(api_panels))
        for panel in api_panels:
            for target in panel["targets"]:
                self.assertIn(INGEST_URIS, target["expr"], panel["title"])
                self.assertEqual("{{uri}}", target["legendFormat"], panel["title"])

    def test_rate_windows_follow_the_scrape_interval(self):
        for expr in expressions():
            for window in re.findall(r"\[([^\]]+)\]", expr):
                self.assertEqual("$__rate_interval", window, expr)


if __name__ == "__main__":
    unittest.main()
