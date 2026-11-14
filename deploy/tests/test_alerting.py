from pathlib import Path
import re
import unittest

from promql import selectors


ALERTING = Path(__file__).resolve().parents[1] / "grafana/provisioning/alerting/alerting.yml"
MEASUREMENT_EXCLUDED = 'env!~"test-.*"'
KAFKA_RULES = ("ingest-fleet-empty", "api-fleet-empty", "consumer-fleet-empty", "event-consume-stalled", "event-consume-retrying",
               "event-storage-delay-high", "event-dead-letter", "event-publish-failing", "kafka-broker-down", "kafka-broker-disk")
FOLDED_EXPR = re.compile(r"^ +expr: >-\n((?: {16}.*\n)+)", re.M)
SINGLE_EXPR = re.compile(r"^ +expr: (?!>-)(.*)$", re.M)


def rule_blocks():
    return ALERTING.read_text().split("\n    rules:\n")[1].split("\n      - uid: ")[1:]


def rules():
    for block in rule_blocks():
        uid = block.split("\n", 1)[0]
        folded = FOLDED_EXPR.search(block)
        expr = " ".join(line.strip() for line in folded.group(1).splitlines()) if folded else SINGLE_EXPR.search(block).group(1)
        yield uid, expr, re.search(r"summary: (.*)", block).group(1)


class AlertingTest(unittest.TestCase):
    def test_every_selector_excludes_measurement_environments(self):
        for uid, expr, _ in rules():
            for name, matchers in selectors(expr):
                self.assertIn(MEASUREMENT_EXCLUDED, matchers, f"{uid}: {name}")

    def test_no_rule_selects_environments_by_inclusion(self):
        for uid, expr, _ in rules():
            self.assertNotRegex(expr, r"\benv=", uid)

    def test_every_aggregation_and_match_keeps_env(self):
        for uid, expr, _ in rules():
            for clause in re.findall(r"\b(?:by|on) \(([^)]*)\)", expr):
                self.assertIn("env", clause.split(", "), f"{uid}: {clause}")
            self.assertNotRegex(expr, r"\b(?:sum|max|min|avg|count) ?\((?!\s*by)", uid)

    def test_removed_two_color_rule_is_deleted_from_running_grafana(self):
        source = ALERTING.read_text()
        self.assertIn("deleteRules:\n  - orgId: 1\n    uid: backend-both-colors-up\n", source)
        self.assertNotIn("backend-both-colors-up", [uid for uid, _, _ in rules()])

    def test_empty_fleet_counts_zero_for_each_service_environment(self):
        expr = dict((uid, expr) for uid, expr, _ in rules())["app-fleet-empty"]
        self.assertIn('count by (env) (up{env!~"test-.*", job="backend"})', expr)
        for env in ("staging", "production"):
            self.assertIn(f'or label_replace(vector(0), "env", "{env}", "", "")', expr)

    # 카프카를 켜지 않은 환경에는 역할 라벨도 브로커 지표도 없다. NoData나 조회 오류로 울리면 켜지 않은 환경이 같이 울린다.
    def test_kafka_rules_stay_quiet_where_kafka_is_off(self):
        states = {block.split("\n", 1)[0]: re.search(r"noDataState: (\w+)\n +execErrState: (\w+)", block).groups() for block in rule_blocks()}
        self.assertEqual({uid: states.get(uid) for uid in KAFKA_RULES}, dict.fromkeys(KAFKA_RULES, ("OK", "Error")))

    # 한 역할의 서버가 0대면 그 계열이 사라진다. 다른 역할의 서버가 있는 환경에 0을 깔아야 울린다.
    # 켜고 끄는 apply 중에는 역할 태그가 없는 옛 서버가 api 일을 맡고 있어, api 규칙만 태그 없는 서버도 센다.
    def test_role_fleet_rules_count_zero_beside_the_other_roles(self):
        exprs = dict((uid, expr) for uid, expr, _ in rules())
        thresholds = {block.split("\n", 1)[0]: re.search(r"evaluator: (.*)", block).group(1) for block in rule_blocks()}
        for role, counted, others in (("ingest", '="ingest"', "api|consumer"), ("api", '=~"api|"', "ingest|consumer"),
                                      ("consumer", '="consumer"', "api|ingest")):
            expr = exprs[f"{role}-fleet-empty"]
            self.assertIn(f'count by (env) (up{{env!~"test-.*", job="backend", server_role{counted}}} == 1)', expr)
            self.assertIn(f'or count by (env) (up{{env!~"test-.*", job="backend", server_role=~"{others}"}}) * 0', expr)
            self.assertEqual(thresholds[f"{role}-fleet-empty"], "{ type: lt, params: [1] }")

    # 브로커 계열이 아예 없으면 등록을 빠뜨린 것이고, 역할 서버 없이 브로커만 0이면 끈 뒤 남은 대상 파일이다.
    def test_broker_rule_follows_environments_that_run_role_servers(self):
        expr = dict((uid, expr) for uid, expr, _ in rules())["kafka-broker-down"]
        role_servers = 'count by (env) (up{env!~"test-.*", job="backend", server_role=~"api|ingest|consumer"})'
        self.assertEqual(expr, f'min by (env) (up{{env!~"test-.*", job="kafka-broker"}}) and on (env) {role_servers} or {role_servers} * 0')

    def test_every_summary_names_the_environment(self):
        for uid, _, summary in rules():
            self.assertIn("{{ $labels.env }}", summary, uid)


if __name__ == "__main__":
    unittest.main()
