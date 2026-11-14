import json
import re
from pathlib import Path
import unittest

from promql import selectors
from test_alerting import rules
from test_monitoring_server import kept_broker_metrics


KAFKA_DASHBOARD = Path(__file__).resolve().parents[1] / "grafana/dashboards/meterengine-kafka.json"
MAX_TITLE_LENGTH = 20
# backend 안의 카프카 클라이언트가 내는 지표다. kafka_로 시작하는 나머지는 브로커의 JMX 지표로 본다.
CLIENT_METRIC_PREFIXES = ("kafka_consumer_fetch_manager_", "kafka_consumer_coordinator_", "kafka_producer_")
# 브로커의 JMX 지표는 60초마다 긁는다. 타깃의 최소 간격을 알려 줘야 rate 창이 그 네 배로 잡혀 표본을 건너뛰지 않는다.
BROKER_SCRAPE_INTERVAL = "1m"
NAME_MATCHER = re.compile(r'name=~?"([^"]+)"')
ANY_ENVIRONMENT = "[^;]*;"
MEASUREMENT_ENVIRONMENTS = "test-[^;]*;.*"


def chart_panels():
    return [panel for panel in json.loads(KAFKA_DASHBOARD.read_text())["panels"] if panel["type"] != "row"]


def dashboard_targets():
    return [target for panel in chart_panels() for target in panel["targets"]]


def is_broker_jmx(metric):
    return metric.startswith("kafka_") and not metric.startswith(CLIENT_METRIC_PREFIXES)


def broker_jmx_pairs(expr):
    """식이 읽는 브로커 JMX 지표를 거르기 규칙의 항목 모양(지표;name)으로 돌려준다."""
    for metric, matchers in selectors(expr):
        if is_broker_jmx(metric):
            names = NAME_MATCHER.search(matchers)
            if names is None:
                raise AssertionError(f"{metric} 에 name 매처가 없어 거르기 목록과 견줄 수 없다: {expr}")
            for name in names.group(1).split("|"):
                yield f"{metric};{name}"


def pairs_read_by_dashboard_and_alerts():
    expressions = [target["expr"] for target in dashboard_targets()] + [expr for _, expr, _ in rules()]
    return {pair for expr in expressions for pair in broker_jmx_pairs(expr)}


class KafkaDashboardTest(unittest.TestCase):
    def test_dashboard_is_identified_as_meterengine_kafka(self):
        dashboard = json.loads(KAFKA_DASHBOARD.read_text())
        self.assertEqual("meterengine-kafka", dashboard["uid"])
        self.assertEqual("MeterEngine 카프카 경로", dashboard["title"])
        self.assertIn("meterengine", dashboard["tags"])
        self.assertTrue(all("panels" not in panel or not panel["panels"] for panel in dashboard["panels"]), "접힌 행은 시험이 보지 않는다")

    def test_every_panel_has_a_short_title_and_a_description_naming_its_metric(self):
        for panel in chart_panels():
            self.assertLessEqual(len(panel["title"]), MAX_TITLE_LENGTH, panel["title"])
            self.assertIn("지표: `", panel["description"], panel["title"])
            self.assertEqual("prometheus", panel["datasource"]["uid"], panel["title"])

    def test_rate_windows_follow_the_scrape_interval(self):
        for target in dashboard_targets():
            for window in re.findall(r"\[([^\]]+)\]", target["expr"]):
                self.assertEqual("$__rate_interval", window, target["expr"])

    def test_broker_counter_targets_tell_grafana_the_slower_scrape(self):
        for target in dashboard_targets():
            reads_broker_counter = any(is_broker_jmx(metric) for metric, _ in selectors(target["expr"])) and "rate(" in target["expr"]
            self.assertEqual(target.get("interval"), BROKER_SCRAPE_INTERVAL if reads_broker_counter else None, target["expr"])

    # 환경을 여럿 고르면 한 환경의 0대나 밀린 양이 다른 환경 값에 가려진다. 상태를 보는 패널은 환경별로 줄을 나눈다.
    def test_state_panels_keep_environments_apart(self):
        panels = {panel["title"]: panel for panel in chart_panels()}
        for title in ("밀린 메시지 수", "역할별 서버 수", "역할별 CPU"):
            for target in panels[title]["targets"]:
                self.assertRegex(target["expr"], r"by \(env[,)]", title)
                self.assertIn("{{env}}", target["legendFormat"], title)

    # 늘 켜 두는 환경은 브로커 지표를 걸러 받는다. 화면이나 경보가 읽는 지표가 걸러지면 빈 채로 조용하다.
    def test_scrape_filter_keeps_exactly_the_broker_metrics_read(self):
        read = pairs_read_by_dashboard_and_alerts()
        self.assertTrue(read)
        by_metric = {}
        for pair in sorted(read):
            metric, name = pair.split(";")
            by_metric.setdefault(metric, []).append(name)
        listed = dict(re.findall(r"\[\^;\]\*;(\w+);\(([\w|]+)\)", kept_broker_metrics()))
        self.assertEqual({metric: sorted(names.split("|")) for metric, names in listed.items()}, by_metric)
        # 목록 밖의 넓은 항목(예: 지표 전체를 남기는 .*)이 끼어들지 않았는지 정규식 전체를 다시 지어 견준다.
        rebuilt = "|".join([MEASUREMENT_ENVIRONMENTS] + [f"{ANY_ENVIRONMENT}{metric};({names})" for metric, names in listed.items()])
        self.assertEqual(kept_broker_metrics(), rebuilt)


if __name__ == "__main__":
    unittest.main()
