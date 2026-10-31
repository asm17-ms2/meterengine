import json
import re
from pathlib import Path
import unittest


INGEST_DASHBOARD = Path(__file__).resolve().parents[1] / "grafana/dashboards/meterengine-ingest.json"
INGEST_URIS = 'uri=~"/v1/events|/v1/event-batches"'
SINGLE_INGEST_SUCCESS = 'method="POST", uri="/v1/events", outcome="SUCCESS"'
DB_ROUND_TRIP_ROW = "DB 왕복"
MAX_TITLE_LENGTH = 20


def load_dashboard():
    return json.loads(INGEST_DASHBOARD.read_text())


def chart_panels():
    return [panel for panel in load_dashboard()["panels"] if panel["type"] != "row"]


# 접힌 행의 패널은 맨 위 panels가 아니라 그 행의 panels에 들어 있다.
def collapsed_chart_panels():
    rows = [panel for panel in load_dashboard()["panels"] if panel["type"] == "row"]
    return [panel for row in rows for panel in row.get("panels", [])]


def all_chart_panels():
    return chart_panels() + collapsed_chart_panels()


def db_round_trip_panels():
    row = load_dashboard()["panels"][-1]
    return row.get("panels", [])


def expressions(panels):
    return [target["expr"] for panel in panels for target in panel["targets"]]


def expressions_containing(panel, metric):
    return [target["expr"] for target in panel["targets"] if metric in target["expr"]]


class IngestDashboardTest(unittest.TestCase):
    def test_dashboard_is_identified_as_meterengine_ingest(self):
        dashboard = load_dashboard()
        self.assertEqual("meterengine-ingest", dashboard["uid"])
        self.assertEqual("MeterEngine 이벤트 수집", dashboard["title"])
        self.assertIn("meterengine", dashboard["tags"])

    def test_every_panel_has_a_short_title_and_a_description_naming_its_metric(self):
        for panel in all_chart_panels():
            self.assertLessEqual(len(panel["title"]), MAX_TITLE_LENGTH, panel["title"])
            self.assertTrue(panel.get("description", "").strip(), panel["title"])
            self.assertIn("지표: `", panel["description"], panel["title"])

    def test_every_panel_reads_the_prometheus_datasource(self):
        for panel in all_chart_panels():
            self.assertEqual("prometheus", panel["datasource"]["uid"], panel["title"])

    def test_latency_counts_only_successful_ingest_requests(self):
        latency = [
            expr
            for expr in expressions(chart_panels())
            if "http_server_requests_seconds_bucket" in expr
        ]
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
        for expr in expressions(all_chart_panels()):
            for window in re.findall(r"\[([^\]]+)\]", expr):
                self.assertEqual("$__rate_interval", window, expr)

    def test_db_round_trip_row_is_collapsed_at_the_bottom(self):
        row = load_dashboard()["panels"][-1]
        self.assertEqual("row", row["type"])
        self.assertEqual(DB_ROUND_TRIP_ROW, row["title"])
        self.assertTrue(row["collapsed"])
        self.assertEqual(2, len(row["panels"]))

    def test_db_round_trip_panels_split_by_operation_and_table_beside_single_ingest(self):
        for panel in db_round_trip_panels():
            database = expressions_containing(panel, "meterengine_db_operation_seconds")
            requests = expressions_containing(panel, "http_server_requests_seconds")
            self.assertEqual(1, len(database), panel["title"])
            self.assertEqual(1, len(requests), panel["title"])
            self.assertIn("operation, table", database[0], panel["title"])
            self.assertIn(SINGLE_INGEST_SUCCESS, requests[0], panel["title"])

    def test_db_round_trip_panels_draw_average_then_p99(self):
        average, p99 = db_round_trip_panels()
        for expr in expressions([average]):
            self.assertIn("_seconds_sum", expr)
            self.assertIn("_seconds_count", expr)
        for expr in expressions([p99]):
            self.assertIn("histogram_quantile(0.99, ", expr)
            self.assertIn("_seconds_bucket", expr)
        # 분자와 분모를 같은 묶음으로 나누고, 분위수는 le를 남겨 묶어야 줄이 그려진다.
        database_average = expressions_containing(average, "meterengine_db_operation_seconds")[0]
        self.assertEqual(2, database_average.count("sum by (operation, table)"))
        database_p99 = expressions_containing(p99, "meterengine_db_operation_seconds")[0]
        self.assertIn("sum by (le, operation, table)", database_p99)


if __name__ == "__main__":
    unittest.main()
