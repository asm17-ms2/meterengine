import json
from pathlib import Path
import re
import unittest


API_DASHBOARD = Path(__file__).resolve().parents[1] / "grafana/dashboards/meterengine-api.json"
MAX_TITLE_LENGTH = 20
SUCCESS_ONLY = 'outcome="SUCCESS"'
WITHOUT_FILTERED_AND_ACTUATOR = 'uri!~"/actuator.*|UNKNOWN"'
RATE_WINDOW = re.compile(r"\brate\([^\[]*\[([^\]]+)\]\)")
INCREASE_WINDOW = re.compile(r"\bincrease\([^\[]*\[([^\]]+)\]\)")
MIN_COUNT_INTERVAL = "5m"
URI_MATCHER = re.compile(r"\buri\s*(=|!=|=~|!~)")
API_ROW = "API"
AUTH_ROW = "인증"
AUTH_STATUS_PANEL = "401, 403, 429 응답 수"


def load_dashboard():
    return json.loads(API_DASHBOARD.read_text())


def chart_panels(dashboard):
    return [panel for panel in dashboard["panels"] if panel["type"] != "row"]


def panels_by_row(dashboard):
    by_row = {}
    current_row = None
    in_layout_order = sorted(
        dashboard["panels"], key=lambda panel: (panel["gridPos"]["y"], panel["gridPos"]["x"])
    )
    for panel in in_layout_order:
        if panel["type"] == "row":
            current_row = panel["title"]
            by_row[current_row] = []
        else:
            by_row[current_row].append(panel)
    return by_row


def expressions(panel):
    return [target["expr"] for target in panel["targets"]]


class ApiDashboardTest(unittest.TestCase):
    def test_every_panel_has_short_title_and_description(self):
        for panel in chart_panels(load_dashboard()):
            title = panel["title"]
            self.assertLessEqual(len(title), MAX_TITLE_LENGTH, title)
            self.assertNotIn("(", title)
            self.assertTrue(panel.get("description", "").strip(), title)
            self.assertIn("지표:", panel["description"], title)

    def test_every_panel_and_variable_reads_prometheus(self):
        dashboard = load_dashboard()
        sources = [panel["datasource"] for panel in chart_panels(dashboard)]
        sources += [variable["datasource"] for variable in dashboard["templating"]["list"]]
        for source in sources:
            self.assertEqual({"type": "prometheus", "uid": "prometheus"}, source)

    def test_latency_uses_only_successful_responses_outside_filters_and_actuator(self):
        latency = [
            expr
            for panel in chart_panels(load_dashboard())
            for expr in expressions(panel)
            if "_bucket" in expr
        ]
        self.assertGreaterEqual(len(latency), 3)
        for expr in latency:
            self.assertIn(SUCCESS_ONLY, expr)
            self.assertIn(WITHOUT_FILTERED_AND_ACTUATOR, expr)

    def test_rate_windows_follow_grafana_rate_interval(self):
        for panel in chart_panels(load_dashboard()):
            for expr in expressions(panel):
                for window in RATE_WINDOW.findall(expr):
                    self.assertEqual("$__rate_interval", window, panel["title"])

    def test_increase_windows_cover_each_point_interval_without_gaps(self):
        for panel in chart_panels(load_dashboard()):
            windows = [
                window for expr in expressions(panel) for window in INCREASE_WINDOW.findall(expr)
            ]
            if not windows:
                continue
            self.assertEqual(MIN_COUNT_INTERVAL, panel.get("interval"), panel["title"])
            for window in windows:
                self.assertEqual("$__interval", window, panel["title"])

    def test_api_row_follows_uri_and_method_variables(self):
        api_panels = panels_by_row(load_dashboard())[API_ROW]
        self.assertTrue(api_panels)
        for panel in api_panels:
            for expr in expressions(panel):
                self.assertIn("$uri", expr, panel["title"])
                self.assertIn("$method", expr, panel["title"])

    def test_auth_row_ignores_uri_and_method_variables(self):
        auth_panels = panels_by_row(load_dashboard())[AUTH_ROW]
        self.assertTrue(auth_panels)
        for panel in auth_panels:
            for expr in expressions(panel):
                self.assertNotIn("$uri", expr, panel["title"])
                self.assertNotIn("$method", expr, panel["title"])

    def test_auth_status_counts_keep_unknown_and_path_uris(self):
        auth_panels = panels_by_row(load_dashboard())[AUTH_ROW]
        status_panel = next(panel for panel in auth_panels if panel["title"] == AUTH_STATUS_PANEL)
        for expr in expressions(status_panel):
            self.assertIsNone(URI_MATCHER.search(expr), expr)


if __name__ == "__main__":
    unittest.main()
