import json
import re
from pathlib import Path
import unittest


BILLING_DASHBOARD = Path(__file__).resolve().parents[1] / "grafana/dashboards/meterengine-billing.json"
MAX_TITLE_LENGTH = 20
MODE_METRICS = (
    "meterengine_payment_attempt_total",
    "meterengine_payment_retry_exhausted_total",
    "meterengine_payment_cancellation_total",
    "meterengine_payment_reconciliation_mismatch_total",
    "meterengine_payment_stale_pending",
)
EXCLUDED_URIS = '"/actuator.*|UNKNOWN"'
PINNED_API_URI = re.compile(r'uri="/v1/[^"]+"')
HOURLY_HTTP_COUNT = re.compile(r"increase\(http_server_requests_seconds_count\{.*?\}\[1h\]\)")
HOURLY_HTTP_COUNT_PANELS = ["인보이스 확정 결과", "토스 웹훅 응답 시간과 5xx"]


def load_dashboard():
    return json.loads(BILLING_DASHBOARD.read_text())


def content_panels(dashboard):
    return [panel for panel in dashboard["panels"] if panel["type"] != "row"]


def expressions(panel):
    return [target["expr"] for target in panel.get("targets", [])]


def expression(panel, ref_id):
    return next(target["expr"] for target in panel["targets"] if target["refId"] == ref_id)


def panel_titled(dashboard, title):
    return next(panel for panel in content_panels(dashboard) if panel["title"] == title)


class BillingDashboardTest(unittest.TestCase):
    def test_dashboard_is_tagged_and_has_meterengine_dashboard_dropdown(self):
        dashboard = load_dashboard()
        self.assertEqual("meterengine-billing", dashboard["uid"])
        self.assertTrue(dashboard["title"].startswith("MeterEngine "))
        self.assertIn("meterengine", dashboard["tags"])
        self.assertIn(
            {
                "type": "dashboards",
                "tags": ["meterengine"],
                "asDropdown": True,
                "title": "MeterEngine 대시보드",
                "includeVars": False,
                "keepTime": True,
            },
            dashboard["links"],
        )

    def test_every_panel_has_short_title_description_and_prometheus_datasource(self):
        for panel in content_panels(load_dashboard()):
            self.assertLessEqual(len(panel["title"]), MAX_TITLE_LENGTH, panel["title"])
            self.assertTrue(panel.get("description", "").strip(), panel["title"])
            self.assertEqual("prometheus", panel["datasource"]["uid"], panel["title"])

    def test_http_latency_uses_only_successful_api_responses(self):
        for panel in content_panels(load_dashboard()):
            for expr in expressions(panel):
                if "histogram_quantile" in expr and "http_server_requests_seconds_bucket" in expr:
                    self.assertIn('outcome="SUCCESS"', expr, panel["title"])
                    self.assertTrue(
                        EXCLUDED_URIS in expr or PINNED_API_URI.search(expr), panel["title"]
                    )

    def test_toss_latency_uses_only_successful_calls(self):
        for panel in content_panels(load_dashboard()):
            for expr in expressions(panel):
                if "histogram_quantile" in expr and "meterengine_toss_payments_request" in expr:
                    self.assertIn('outcome="success"', expr, panel["title"])

    def test_quantile_panels_show_request_count_of_the_same_window(self):
        for panel in content_panels(load_dashboard()):
            exprs = expressions(panel)
            if any("histogram_quantile" in expr for expr in exprs):
                self.assertTrue(
                    any("_count" in expr and "[$__rate_interval]" in expr for expr in exprs),
                    panel["title"],
                )

    def test_rates_use_rate_interval_and_range_queries_are_instant(self):
        for panel in content_panels(load_dashboard()):
            for target in panel["targets"]:
                for window in re.findall(r"\brate\([^\[]*\[([^\]]+)\]", target["expr"]):
                    self.assertEqual("$__rate_interval", window, panel["title"])
                if "$__range" in target["expr"]:
                    self.assertTrue(target.get("instant"), panel["title"])

    def test_hourly_http_counts_include_series_created_within_the_hour(self):
        for panel in content_panels(load_dashboard()):
            for expr in expressions(panel):
                if HOURLY_HTTP_COUNT.search(expr):
                    self.assertIn("unless", expr, panel["title"])
                    self.assertIn("offset 1h", expr, panel["title"])

    def test_hourly_http_count_pattern_matches_every_hourly_http_count_expression(self):
        matched_titles = [
            panel["title"]
            for panel in content_panels(load_dashboard())
            for expr in expressions(panel)
            if HOURLY_HTTP_COUNT.search(expr)
        ]
        self.assertEqual(HOURLY_HTTP_COUNT_PANELS, matched_titles)

    def test_payment_success_rate_divides_only_by_positive_totals(self):
        expr = expression(panel_titled(load_dashboard(), "결제 성공률"), "A")
        self.assertTrue(expr.endswith(" > 0)"), expr)

    def test_recovery_rate_divides_only_by_positive_first_failures_and_caps_at_one(self):
        expr = expression(panel_titled(load_dashboard(), "재시도 회복률"), "A")
        self.assertTrue(expr.startswith("clamp_max("), expr)
        self.assertTrue(expr.endswith(" > 0), 1)"), expr)

    def test_first_attempt_success_rate_counts_only_first_attempts_with_positive_totals(self):
        expr = expression(panel_titled(load_dashboard(), "재시도 회복률"), "B")
        self.assertEqual(3, expr.count('attempt="first"'), expr)
        self.assertNotIn('attempt="retry"', expr)
        self.assertTrue(expr.endswith(" > 0)"), expr)

    def test_stale_pending_age_is_measured_from_oldest_request_time(self):
        (expr,) = expressions(panel_titled(load_dashboard(), "결과 모름 최장 경과"))
        self.assertTrue(expr.startswith("time() - ("), expr)
        self.assertIn("meterengine_payment_stale_pending_oldest_requested_seconds", expr)

    def test_mode_variable_applies_only_to_panels_whose_metrics_carry_mode(self):
        dashboard = load_dashboard()
        mode = next(
            variable for variable in dashboard["templating"]["list"] if variable["name"] == "mode"
        )
        self.assertEqual("test,live", mode["query"])
        self.assertTrue(mode["description"].strip())
        for panel in content_panels(dashboard):
            for expr in expressions(panel):
                if any(metric in expr for metric in MODE_METRICS):
                    self.assertIn('mode="$mode"', expr, panel["title"])
                else:
                    self.assertNotIn("$mode", expr, panel["title"])


if __name__ == "__main__":
    unittest.main()
