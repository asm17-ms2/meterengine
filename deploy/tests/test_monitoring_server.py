import json
import os
from pathlib import Path
import re
import shutil
import stat
import subprocess
import tempfile
import unittest


DEPLOY = Path(__file__).resolve().parents[1]
COMPOSE_FILE = DEPLOY / "compose.monitoring.yml"
PARAMETERS = json.loads((DEPLOY / "monitoring/parameters.json").read_text())
SHARED_PROMETHEUS_CONFIG = DEPLOY / "monitoring/prometheus/prometheus.yml"
START = DEPLOY / "monitoring/start.sh"
TEST_SJPARK = DEPLOY.parent / "measurements/test-sjpark"
SHA = "a" * 40
CENTRAL = {"prometheus", "grafana", "cloudwatch-exporter"}
EXPORTERS = {f"postgres-exporter-{env}" for env in PARAMETERS["environments"]}
SECRETS = {"GRAFANA_ADMIN_PASSWORD": "admin", "GRAFANA_GITHUB_CLIENT_ID": "client",
           "GRAFANA_GITHUB_CLIENT_SECRET": "secret", "SLACK_WEBHOOK_URL": "https://hook.invalid"}


def parameter_names(group):
    names = PARAMETERS["monitoring"] if group == "monitoring" else PARAMETERS["environment_parameters"]
    return [f"{PARAMETERS['path_prefix']}/{group}/{name}" for name in names]


def scrape_jobs():
    return dict(re.findall(r"^  - job_name: (\S+)\n((?:(?!  - job_name).*\n)*)", SHARED_PROMETHEUS_CONFIG.read_text(), re.M))


@unittest.skipUnless(shutil.which("docker"), "Docker required")
class MonitoringComposeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        environment = {name: value for name, value in os.environ.items() if name not in SECRETS}
        result = subprocess.run(["docker", "compose", "-f", str(COMPOSE_FILE), "config", "--format", "json"],
                                env={**environment, **SECRETS}, text=True, capture_output=True, timeout=30)
        if result.returncode != 0:
            raise AssertionError(result.stderr)
        cls.services = json.loads(result.stdout)["services"]

    def test_one_postgres_exporter_per_environment(self):
        self.assertEqual(set(self.services), CENTRAL | EXPORTERS)

    def test_images_are_pinned(self):
        for name, service in self.services.items():
            self.assertRegex(service["image"], r":v?\d+\.\d+\.\d+$", name)

    def test_only_grafana_listens_beyond_loopback(self):
        published = {name: {(p.get("host_ip"), p["published"]) for p in service.get("ports", [])}
                     for name, service in self.services.items() if service.get("ports")}
        self.assertEqual(published, {"prometheus": {("127.0.0.1", "9090")}, "grafana": {(None, "3000")}})

    def test_prometheus_mounts_the_config_directory(self):
        sources = [volume["source"] for volume in self.services["prometheus"]["volumes"] if volume["target"] == "/etc/prometheus"]
        self.assertEqual(sources, [str(SHARED_PROMETHEUS_CONFIG.parent)])

    def test_grafana_admits_only_the_github_org(self):
        env = self.services["grafana"]["environment"]
        self.assertEqual({key: env[key] for key in (
            "GF_AUTH_GITHUB_ENABLED", "GF_AUTH_GITHUB_ALLOWED_ORGANIZATIONS", "GF_AUTH_DISABLE_LOGIN_FORM",
            "GF_AUTH_BASIC_ENABLED", "GF_SERVER_ROOT_URL")}, {
            "GF_AUTH_GITHUB_ENABLED": "true", "GF_AUTH_GITHUB_ALLOWED_ORGANIZATIONS": "asm17-ms2",
            "GF_AUTH_DISABLE_LOGIN_FORM": "true", "GF_AUTH_BASIC_ENABLED": "false",
            "GF_SERVER_ROOT_URL": "https://grafana.meterengine.com/"})


class SharedPrometheusConfigTest(unittest.TestCase):
    def test_app_servers_are_found_by_tag_on_agent_ports(self):
        jobs = scrape_jobs()
        for job, port in (("backend", 8080), ("node", 9100), ("cadvisor", 9101)):
            self.assertIn(f"port: {port}\n", jobs[job], job)
            self.assertRegex(jobs[job], r"filters: (&|\*)app_servers", job)
        for job in ("node", "cadvisor"):
            self.assertRegex(jobs[job], r"relabel_configs: (&|\*)app_labels", job)
        self.assertIn("name: tag:Role\n            values: [app]", jobs["backend"])
        self.assertIn("source_labels: [__meta_ec2_tag_Environment]\n        target_label: env", jobs["backend"])
        self.assertIn("- *env_from_tag\n      - *instance_from_id\n", jobs["node"])

    def test_test_sjpark_backend_is_not_scraped(self):
        jobs = scrape_jobs()
        self.assertIn("- source_labels: [__meta_ec2_tag_Environment]\n        regex: test-sjpark\n        action: drop\n", jobs["backend"])
        for job in ("node", "cadvisor"):
            self.assertNotIn("action: drop", jobs[job], job)

    def test_test_sjpark_opens_the_ports_its_jobs_scrape(self):
        block = re.search(r"app_scrape_ports = \{(.*?)\}", (TEST_SJPARK / "sut/terraform/locals.tf").read_text(), re.S).group(1)
        ports = {job: int(port) for job, port in re.findall(r"(\w+)\s*=\s*(\d+)", block)}
        jobs = scrape_jobs()
        self.assertEqual(set(ports), {"node", "cadvisor"})
        for job, port in ports.items():
            self.assertIn(f"port: {port}\n", jobs[job], job)

    def test_test_sjpark_deploy_looks_for_a_marker_the_compose_file_has(self):
        marker = re.search(r"^readonly NODE_EXPORTER_PORT_MARKER='(.+)'$", (TEST_SJPARK / "sut/scripts/sut-deploy.sh").read_text(), re.M).group(1)
        self.assertIn(f"- {marker}\n", (DEPLOY / "compose.prod.yml").read_text())

    def test_every_environment_database_is_scraped_with_its_env(self):
        targets = re.findall(r'targets: \["postgres-exporter-([a-z-]+):9187"\]\n\s+labels: \{ env: ([a-z-]+) \}', scrape_jobs()["postgres"])
        self.assertEqual(targets, [(env, env) for env in PARAMETERS["environments"]])


@unittest.skipUnless(shutil.which("flock"), "flock required")
class StartScriptTest(unittest.TestCase):
    def setUp(self):
        self.root = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.root)
        repo, etc, bin_dir = self.root / "repo", self.root / "etc", self.root / "bin"
        (repo / "deploy/monitoring").mkdir(parents=True)
        etc.mkdir()
        bin_dir.mkdir()
        shutil.copy(DEPLOY / "monitoring/parameters.json", repo / "deploy/monitoring/parameters.json")
        self.script = self.root / "start.sh"
        self.script.write_text(START.read_text().replace("/opt/meterengine", str(repo)).replace("/etc/meterengine", str(etc)))
        self.env_file, self.trace = etc / "monitoring.env", self.root / "trace"
        self.values = {name: "value" for group in ("monitoring", *PARAMETERS["environments"]) for name in parameter_names(group)}
        fakes = {
            "git": 'echo "$HEAD"',
            "docker": 'echo "docker $*" >> "$TRACE"',
            # READY_AFTER번 실패한 뒤부터 준비됐다고 답한다.
            "curl": 'echo "curl $*" >> "$TRACE"\n'
                    '[ "$(grep -c "^curl " "$TRACE")" -gt "$READY_AFTER" ]',
            "sleep": 'echo "sleep $*" >> "$TRACE"',
            "aws": 'exec python3 -c \'import json, os, sys\n'
                   'names = sys.argv[sys.argv.index("--names") + 1:]\n'
                   'open(os.environ["TRACE"], "a").write("aws " + " ".join(names) + "\\n")\n'
                   'values = json.load(open(os.environ["VALUES"]))\n'
                   'print(json.dumps({"Parameters": [{"Name": n, "Value": values[n]} for n in names if n in values],'
                   ' "InvalidParameters": [n for n in names if n not in values]}))\' "$@"',
        }
        for name, body in fakes.items():
            (bin_dir / name).write_text(f"#!/usr/bin/env bash\n{body}\n")
            (bin_dir / name).chmod(0o755)
        self.env = {**os.environ, "PATH": f"{bin_dir}:{os.environ['PATH']}", "TRACE": str(self.trace),
                    "VALUES": str(self.root / "values.json"), "HEAD": SHA}

    def run_start(self, head=SHA, ready_after=0):
        (self.root / "values.json").write_text(json.dumps(self.values))
        self.trace.touch()
        return subprocess.run(["bash", str(self.script), SHA], env={**self.env, "HEAD": head, "READY_AFTER": str(ready_after)},
                              text=True, capture_output=True, timeout=10)

    def reload_signals(self):
        return [call for call in self.calls("docker") if "SIGHUP" in call]

    def calls(self, tool):
        return [line.split()[1:] for line in self.trace.read_text().splitlines() if line.startswith(tool + " ")]

    def test_reads_each_group_in_one_call_within_the_api_limit(self):
        self.assertEqual(self.run_start().returncode, 0)
        groups = [parameter_names(group) for group in ("monitoring", *PARAMETERS["environments"])]
        self.assertEqual(self.calls("aws"), groups)
        self.assertTrue(all(len(names) <= 10 for names in groups))
        self.assertEqual(stat.S_IMODE(self.env_file.stat().st_mode), 0o600)
        self.assertIn("TEST_STORAGE_DB_HOST=value\n", self.env_file.read_text())

    def test_environment_without_parameters_runs_no_exporter(self):
        del self.values["/meterengine/staging/db-exporter-password"]
        self.assertEqual(self.run_start().returncode, 0)
        up = next(call for call in self.calls("docker") if "up" in call)
        self.assertEqual(set(up[up.index("-d") + 1:]), CENTRAL | EXPORTERS - {"postgres-exporter-staging"})
        self.assertIn(["compose", "-f", str(self.root / "repo/deploy/compose.monitoring.yml"), "--env-file", str(self.env_file),
                       "rm", "--stop", "--force", "postgres-exporter-staging"], self.calls("docker"))
        self.assertNotIn("STAGING_", self.env_file.read_text())

    def test_placeholder_secret_stops_before_compose(self):
        self.values["/meterengine/monitoring/grafana-github-client-id"] = "unset"
        self.assertNotEqual(self.run_start().returncode, 0)
        self.assertEqual(self.calls("docker"), [])
        self.assertFalse(self.env_file.exists())

    def test_other_checkout_stops_before_reading_parameters(self):
        self.assertNotEqual(self.run_start(head="b" * 40).returncode, 0)
        self.assertEqual(self.calls("aws"), [])

    def test_prometheus_reloads_only_after_it_answers_ready(self):
        self.assertEqual(self.run_start(ready_after=2).returncode, 0)
        tools = [line.split()[0] for line in self.trace.read_text().splitlines() if "SIGHUP" in line or line.startswith("curl ")]
        self.assertEqual(tools, ["curl", "curl", "curl", "docker"])

    def test_prometheus_never_ready_stops_without_reload(self):
        result = self.run_start(ready_after=10_000)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.reload_signals(), [])
        self.assertIn("Prometheus", result.stderr)


if __name__ == "__main__":
    unittest.main()
