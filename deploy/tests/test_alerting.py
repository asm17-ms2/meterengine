from pathlib import Path
import re
import unittest

from promql import selectors


ALERTING = Path(__file__).resolve().parents[1] / "grafana/provisioning/alerting/alerting.yml"
MEASUREMENT_EXCLUDED = 'env!~"test-.*"'
FOLDED_EXPR = re.compile(r"^ +expr: >-\n((?: {16}.*\n)+)", re.M)
SINGLE_EXPR = re.compile(r"^ +expr: (?!>-)(.*)$", re.M)


def rules():
    for block in ALERTING.read_text().split("\n    rules:\n")[1].split("\n      - uid: ")[1:]:
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

    def test_every_summary_names_the_environment(self):
        for uid, _, summary in rules():
            self.assertIn("{{ $labels.env }}", summary, uid)


if __name__ == "__main__":
    unittest.main()
