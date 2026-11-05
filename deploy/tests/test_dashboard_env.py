import json
from pathlib import Path
import unittest

from promql import selectors


DASHBOARDS = sorted((Path(__file__).resolve().parents[1] / "grafana/dashboards").glob("*.json"))
ENV_FILTER = 'env=~"$env"'
# 관측 시스템 자신은 환경마다 따로 있지 않아 env로 고르지 않는다.
SHARED_PANELS = {("meterengine-resources.json", "관측")}


def environment_panels(path):
    row = None
    for panel in json.loads(path.read_text())["panels"]:
        if panel["type"] == "row":
            row = panel["title"]
            yield from ((row, nested) for nested in panel.get("panels", []))
        else:
            yield row, panel


def is_shared(path, row, panel):
    return (path.name, row) in SHARED_PANELS or (path.name, panel["title"].split()[0]) in SHARED_PANELS


class DashboardEnvTest(unittest.TestCase):
    def test_every_dashboard_starts_with_the_env_variable(self):
        for path in DASHBOARDS:
            variable = json.loads(path.read_text())["templating"]["list"][0]
            self.assertEqual({key: variable.get(key) for key in ("name", "type", "includeAll", "allValue")},
                             {"name": "env", "type": "query", "includeAll": True, "allValue": ".*"}, path.name)
            self.assertEqual(variable["query"]["query"], "label_values(up, env)", path.name)

    def test_every_selector_filters_by_env(self):
        for path in DASHBOARDS:
            for row, panel in environment_panels(path):
                if is_shared(path, row, panel):
                    continue
                for target in panel["targets"]:
                    for name, matchers in selectors(target["expr"]):
                        self.assertIn(ENV_FILTER, matchers, f"{path.name} {panel['title']}: {name}")

    def test_variables_list_values_of_the_chosen_env(self):
        for path in DASHBOARDS:
            for variable in json.loads(path.read_text())["templating"]["list"][1:]:
                if variable["type"] == "query":
                    self.assertIn(ENV_FILTER, variable["query"]["query"], f"{path.name} {variable['name']}")


if __name__ == "__main__":
    unittest.main()
