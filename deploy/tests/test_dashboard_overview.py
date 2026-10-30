import http.client
import json
from pathlib import Path
import re
import shutil
import ssl
import subprocess
import tempfile
import time
import unittest
import uuid


DEPLOY = Path(__file__).resolve().parents[1]
DASHBOARDS = DEPLOY / "grafana/dashboards"
OVERVIEW = DASHBOARDS / "meterengine-overview.json"
CADDYFILE = DEPLOY / "caddy/Caddyfile"
COMPOSE_FILE = DEPLOY / "compose.prod.yml"
PROMETHEUS_CONFIG = DEPLOY / "prometheus/prometheus.yml"
CADDY_IMAGE = "caddy:2.11.7-alpine"
CADDY_ADMIN_PORT = 2019
MAX_TITLE_LENGTH = 20
METRIC_SELECTOR = re.compile(r"([a-z_:][a-z0-9_:]*)\{")
RANGE_WINDOW = re.compile(r"\[([^\]]+)\]")
HISTOGRAM_SERIES_SUFFIX = re.compile(r"_(bucket|count|sum)$")
CADDY_SCRAPE_TARGET = re.compile(r'"caddy:(\d+)"')
SAMPLE = re.compile(r"^([a-zA-Z_:][a-zA-Z0-9_:]*)(?:\{(.*)\})? (\S+)$")
LABEL = re.compile(r'(\w+)="([^"]*)"')
CADDY_SITE_HANDLER = 'handler="subroute"'
WAIT_SECONDS = 15


def metric_names(panel):
    return {name for target in panel.get("targets", []) for name in METRIC_SELECTOR.findall(target["expr"])}


def metric_family(name):
    return HISTOGRAM_SERIES_SUFFIX.sub("", name)


def caddy_metrics_port():
    target = CADDY_SCRAPE_TARGET.search(PROMETHEUS_CONFIG.read_text())
    if target is None:
        raise AssertionError("Prometheus does not scrape Caddy")
    return int(target.group(1))


def sample_total(exposition, name, **labels):
    total = 0.0
    for line in exposition.splitlines():
        sample = SAMPLE.match(line)
        if sample is None or sample.group(1) != name:
            continue
        found = dict(LABEL.findall(sample.group(2) or ""))
        if all(found.get(key) == value for key, value in labels.items()):
            total += float(sample.group(3))
    return total


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

    def test_caddy_latency_uses_successful_responses_only(self):
        expressions = self.expressions_reading("caddy_http_request_duration_seconds_bucket")
        self.assertTrue(expressions)
        for expression in expressions:
            self.assertIn('code=~"2.."', expression)

    def test_upstream_health_shows_only_the_colors_caddy_proxies_to_now(self):
        targets = self.targets_reading("caddy_reverse_proxy_upstreams_healthy")
        self.assertTrue(targets)
        for target in targets:
            self.assertTrue(target.get("instant"), target["expr"])
            self.assertIs(False, target.get("range"), target["expr"])

    def test_stat_panels_read_one_value_at_the_end_of_the_range(self):
        stats = [panel for panel in self.panels if panel["type"] == "stat"]
        self.assertTrue(stats)
        for stat in stats:
            for target in stat["targets"]:
                self.assertTrue(target.get("instant"), stat["title"])
                self.assertIs(False, target.get("range"), stat["title"])

    def test_caddy_request_panels_count_each_request_once_per_site(self):
        expressions = self.expressions_reading("caddy_http_")
        self.assertTrue(expressions)
        for expression in expressions:
            self.assertIn(CADDY_SITE_HANDLER, expression)

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


@unittest.skipUnless(shutil.which("docker"), "Docker required")
class CaddyMetricsTest(unittest.TestCase):
    @classmethod
    def docker(cls, *arguments, check=True):
        return subprocess.run(["docker", *arguments], capture_output=True, text=True,
                              check=check, timeout=30)

    @classmethod
    def setUpClass(cls):
        if cls.docker("image", "inspect", CADDY_IMAGE, check=False).returncode != 0:
            raise unittest.SkipTest(f"Local {CADDY_IMAGE} image required")
        cls.metrics_port = caddy_metrics_port()
        cls.temp = tempfile.TemporaryDirectory(prefix="meterengine-caddy-metrics-")
        root = Path(cls.temp.name)
        cls.network = f"meterengine-caddy-metrics-{uuid.uuid4().hex[:12]}"
        cls.proxy = f"{cls.network}-proxy"
        cls.upstream = f"{cls.network}-upstream"
        cls.addClassCleanup(cls.cleanup)
        shutil.copyfile(CADDYFILE, root / "Caddyfile")
        (root / "upstreams.caddy").write_text("to backend-blue:8080\n")
        (root / "access-log.caddy").write_text("output discard\n")
        (root / "Upstreamfile").write_text("{\n admin off\n auto_https off\n}\n:8080 {\n respond 200\n}\n")
        mount = f"{root}:/etc/caddy:ro"
        cls.docker("network", "create", cls.network)
        # 프론트를 띄우지 않아 공개 사이트의 화면 요청은 Caddy가 직접 502로 끝낸다.
        cls.docker("run", "--rm", "-d", "--name", cls.upstream, "--network", cls.network,
                   "--network-alias", "backend-blue", "-v", mount, CADDY_IMAGE,
                   "caddy", "run", "--adapter", "caddyfile", "--config", "/etc/caddy/Upstreamfile")
        cls.docker("run", "--rm", "-d", "--name", cls.proxy, "--network", cls.network,
                   "-p", "127.0.0.1::443", "-p", "127.0.0.1::8081", "-p", f"127.0.0.1::{cls.metrics_port}",
                   "-e", "SITE_DOMAIN=localhost", "-e", "SITE_ALIASES=", "-v", mount,
                   CADDY_IMAGE, "caddy", "run", "--config", "/etc/caddy/Caddyfile")
        cls.public_port = cls.published(443)
        cls.internal_port = cls.published(8081)
        cls.scrape_port = cls.published(cls.metrics_port)
        deadline = time.monotonic() + WAIT_SECONDS
        while time.monotonic() < deadline:
            try:
                if cls.public_request("/v1/ready") == 200:
                    return
            except (OSError, http.client.HTTPException):
                pass
            time.sleep(0.1)
        raise AssertionError("Local Caddy fixture did not become ready")

    @classmethod
    def cleanup(cls):
        cls.docker("rm", "-f", cls.proxy, cls.upstream, check=False)
        cls.docker("network", "rm", cls.network, check=False)
        cls.temp.cleanup()

    @classmethod
    def published(cls, container_port):
        result = cls.docker("port", cls.proxy, f"{container_port}/tcp", check=False)
        if result.returncode != 0:
            diagnostic = cls.docker("logs", cls.proxy, check=False)
            raise AssertionError(f"Caddy fixture port unavailable: {result.stderr}{diagnostic.stderr}")
        return int(result.stdout.strip().rsplit(":", 1)[1])

    @classmethod
    def public_request(cls, path):
        return cls.status(http.client.HTTPSConnection(
            "localhost", cls.public_port, timeout=12, context=ssl._create_unverified_context()), path)

    @classmethod
    def internal_request(cls, path):
        return cls.status(http.client.HTTPConnection("127.0.0.1", cls.internal_port, timeout=12), path)

    @classmethod
    def status(cls, connection, path):
        try:
            connection.request("GET", path, headers={"Host": "localhost"})
            response = connection.getresponse()
            response.read()
            return response.status
        finally:
            connection.close()

    @classmethod
    def metrics_listener_request(cls, path):
        return cls.status(http.client.HTTPConnection("127.0.0.1", cls.scrape_port, timeout=12), path)

    @classmethod
    def scrape(cls):
        connection = http.client.HTTPConnection("127.0.0.1", cls.scrape_port, timeout=12)
        try:
            connection.request("GET", "/metrics")
            response = connection.getresponse()
            return response.read().decode()
        finally:
            connection.close()

    def wait_for_sample(self, expected, name, **labels):
        deadline = time.monotonic() + WAIT_SECONDS
        value = sample_total(self.scrape(), name, **labels)
        while value < expected and time.monotonic() < deadline:
            time.sleep(0.1)
            value = sample_total(self.scrape(), name, **labels)
        return value

    def test_metrics_listener_exposes_every_caddy_metric_the_dashboard_reads(self):
        self.assertEqual(200, self.public_request("/v1/customers"))
        panels = json.loads(OVERVIEW.read_text())["panels"]
        families = {metric_family(name) for panel in panels for name in metric_names(panel)
                    if name.startswith("caddy_")}
        self.assertTrue(families)
        exposition = self.scrape()
        for family in families:
            self.assertIn(f"# TYPE {family} ", exposition)

    def test_each_request_is_counted_once_per_site(self):
        before = self.scrape()
        sent = {"public": 2, "internal": 1}
        self.assertEqual(200, self.public_request("/v1/customers"))
        self.assertEqual(200, self.public_request("/v1/usage"))
        self.assertEqual(200, self.internal_request("/v1/customers"))
        for server, count in sent.items():
            labels = {"server": server, "handler": "subroute"}
            expected = sample_total(before, "caddy_http_requests_total", **labels) + count
            self.assertEqual(expected, self.wait_for_sample(expected, "caddy_http_requests_total", **labels), server)

    def test_proxy_failures_are_counted_with_their_status_code(self):
        labels = {"server": "public", "handler": "subroute", "code": "502"}
        expected = sample_total(self.scrape(), "caddy_http_request_duration_seconds_count", **labels) + 1
        self.assertEqual(502, self.public_request("/usage"))
        self.assertEqual(expected, self.wait_for_sample(expected, "caddy_http_request_duration_seconds_count", **labels))

    def test_upstream_health_is_reported_per_backend_color(self):
        self.assertEqual(1, self.wait_for_sample(1, "caddy_reverse_proxy_upstreams_healthy", upstream="backend-blue:8080"))

    def test_metrics_listener_serves_nothing_but_metrics(self):
        self.assertEqual(404, self.metrics_listener_request("/config/"))


@unittest.skipUnless(shutil.which("docker"), "Docker required")
class CaddyMetricsExposureTest(unittest.TestCase):
    def test_compose_keeps_the_metrics_and_admin_ports_inside_the_server(self):
        result = subprocess.run(
            ["docker", "compose", "-f", str(COMPOSE_FILE), "config", "--no-interpolate", "--format", "json"],
            capture_output=True, text=True, timeout=30)
        if result.returncode != 0:
            raise AssertionError(f"compose config failed: {result.stderr}")
        published = {port["target"] for port in json.loads(result.stdout)["services"]["caddy"].get("ports", [])}
        self.assertNotIn(caddy_metrics_port(), published)
        self.assertNotIn(CADDY_ADMIN_PORT, published)


if __name__ == "__main__":
    unittest.main()
