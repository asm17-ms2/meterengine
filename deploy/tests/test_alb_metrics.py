from pathlib import Path
import re
import unittest

from test_monitoring_server import scrape_jobs


DEPLOY = Path(__file__).resolve().parents[1]
CLOUDWATCH_CONFIG = DEPLOY / "cloudwatch/config.yml"
OVERVIEW = DEPLOY / "grafana/dashboards/meterengine-overview.json"
APP_FLEET = DEPLOY / "terraform/modules/app_fleet"
PRODUCTION_GRAFANA = DEPLOY / "terraform/production/grafana.tf"
NAMESPACE = "AWS/ApplicationELB"
ALB_METRIC = re.compile(r"aws_applicationelb_[a-z0-9_]+")
RELABEL = re.compile(r"- source_labels: \[(\w+)\]\n\s+regex: (\S+)\n\s+target_label: (\w+)(?:\n\s+replacement: (\S+))?")
SELECT_REGEX = re.compile(r"(LoadBalancer|TargetGroup): \[\"([^\"]+)\"\]")


def exporter_name(metric, statistic):
    """cloudwatch exporter가 지표 이름을 짓는 규칙을 따른다. 예: HTTPCode_Target_5XX_Count, Sum -> ..._httpcode_target_5_xx_count_sum"""
    snake = re.sub(r"([a-z0-9])([A-Z])", r"\1_\2", metric).lower()
    return re.sub(r"_+", "_", re.sub(r"[^a-z0-9_]", "_", f"{NAMESPACE.lower()}_{snake}")) + f"_{statistic.lower()}"


def alb_rules():
    blocks = CLOUDWATCH_CONFIG.read_text().split("\n  - aws_namespace: ")[1:]
    return [block for block in blocks if block.startswith(NAMESPACE)]


def produced_names():
    names = set()
    for block in alb_rules():
        metric = re.search(r"aws_metric_name: (\S+)", block).group(1)
        statistics = re.search(r"aws_(?:extended_)?statistics: \[([^\]]+)\]", block).group(1)
        names |= {exporter_name(metric, statistic.strip()) for statistic in statistics.split(",")}
    return names


def select_regexes():
    """앵커로 이어 쓴 선택 정규식까지 차원별로 모은다."""
    return {dimension: re.compile(regex) for dimension, regex in SELECT_REGEX.findall(CLOUDWATCH_CONFIG.read_text())}


def relabel(labels):
    labels = dict(labels)
    for source, regex, target, replacement in RELABEL.findall(scrape_jobs()["cloudwatch"]):
        match = re.fullmatch(regex, labels.get(source, ""))
        if match:
            labels[target] = match.expand((replacement or "$1").replace("$1", r"\1"))
    return labels


def alb_dimension(environment):
    name = re.search(r'name = "(meterengine-)\$\{var.environment\}"', (APP_FLEET / "network.tf").read_text()).group(1) + environment
    suffix = re.search(r'name\s+= "\$\{local.name\}(-alb)"', (APP_FLEET / "alb.tf").read_text()).group(1)
    return f"app/{name}{suffix}/50dc6c495c0c9188"


def target_group_dimension(prefix):
    return f"targetgroup/{prefix}20261105000000000000000001/73e2d6bc24d8a067"


def name_prefixes(path):
    return re.findall(r'name_prefix\s+= "([a-z]+-)"', path.read_text())


class AlbMetricsTest(unittest.TestCase):
    def test_exporter_rule_names_follow_the_exporter(self):
        self.assertEqual("aws_applicationelb_httpcode_target_5_xx_count_sum", exporter_name("HTTPCode_Target_5XX_Count", "Sum"))
        self.assertEqual("aws_applicationelb_un_healthy_host_count_maximum", exporter_name("UnHealthyHostCount", "Maximum"))

    def test_overview_reads_only_alb_metrics_the_exporter_produces(self):
        used = set(ALB_METRIC.findall(OVERVIEW.read_text()))
        self.assertTrue(used)
        self.assertLessEqual(used, produced_names())

    def test_relabel_turns_terraform_names_into_env_and_service(self):
        for environment in ("staging", "production"):
            for prefix, service in (("back-", "backend"), ("front-", "frontend")):
                labels = relabel({"load_balancer": alb_dimension(environment), "target_group": target_group_dimension(prefix)})
                self.assertEqual((environment, service), (labels.get("env"), labels.get("service")))

    def test_relabel_covers_every_app_target_group_prefix(self):
        self.assertEqual({"front-", "back-"}, set(name_prefixes(APP_FLEET / "alb.tf")))

    def test_exporter_reads_app_target_groups_but_not_grafana(self):
        regex = select_regexes()["TargetGroup"]
        for prefix in name_prefixes(APP_FLEET / "alb.tf"):
            self.assertTrue(regex.fullmatch(target_group_dimension(prefix)), prefix)
        for prefix in name_prefixes(PRODUCTION_GRAFANA):
            self.assertIsNone(regex.fullmatch(target_group_dimension(prefix)), prefix)

    def test_exporter_reads_every_environment_alb(self):
        regex = select_regexes()["LoadBalancer"]
        for environment in ("staging", "production"):
            self.assertTrue(regex.fullmatch(alb_dimension(environment)), environment)

    def test_every_alb_rule_selects_meterengine_albs_only(self):
        for block in alb_rules():
            self.assertRegex(block, r"aws_dimension_select_regex: [&*]\w+", block.splitlines()[1])


if __name__ == "__main__":
    unittest.main()
