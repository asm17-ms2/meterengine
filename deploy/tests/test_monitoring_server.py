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
SHA = "a" * 40
MIB = 1024 * 1024
# t4g.medium이 운영체제에 보여 주는 메모리와, 운영체제와 파일 캐시에 남길 몫이다.
SERVER_MEMORY_BYTES = 3800 * MIB
RESERVED_FOR_SERVER_BYTES = 900 * MIB
CENTRAL = {"prometheus", "grafana", "cloudwatch-exporter"}
EXPORTERS = {f"postgres-exporter-{env}" for env in PARAMETERS["environments"]}
SECRETS = {"GRAFANA_ADMIN_PASSWORD": "admin", "GRAFANA_GITHUB_CLIENT_ID": "client",
           "GRAFANA_GITHUB_CLIENT_SECRET": "secret", "SLACK_WEBHOOK_URL": "https://hook.invalid"}


def parameter_names(group):
    names = PARAMETERS["monitoring"] if group == "monitoring" else PARAMETERS["environment_parameters"]
    return [f"{PARAMETERS['path_prefix']}/{group}/{name}" for name in names]


def kafka_parameter_name(env):
    return f"{PARAMETERS['path_prefix']}/{env}/{PARAMETERS['kafka_bootstrap_parameter']}"


def scrape_jobs():
    return dict(re.findall(r"^  - job_name: (\S+)\n((?:(?!  - job_name).*\n)*)", SHARED_PROMETHEUS_CONFIG.read_text(), re.M))


def kept_broker_metrics():
    """브로커 job이 남기는 지표의 정규식을 YAML의 줄 이음을 풀어 돌려준다."""
    keep = re.search(r'regex: "((?:.*\\\n)*.*)"\n\s+action: keep', scrape_jobs()["kafka-broker"]).group(1)
    return re.sub(r"\\\n\s*", "", keep)


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

    def test_every_container_has_a_memory_limit_that_leaves_room_for_the_server(self):
        limits = {name: int(service.get("mem_limit", 0)) for name, service in self.services.items()}
        self.assertEqual([name for name, limit in limits.items() if limit == 0], [])
        self.assertLessEqual(sum(limits.values()), SERVER_MEMORY_BYTES - RESERVED_FOR_SERVER_BYTES)

    def test_runtimes_collect_garbage_before_the_container_limit(self):
        for name in ("prometheus", "grafana"):
            self.assertEqual(self.services[name]["environment"]["GOMEMLIMIT"], "900MiB", name)
            self.assertLess(900 * MIB, int(self.services[name]["mem_limit"]), name)
        self.assertEqual(self.services["cloudwatch-exporter"]["environment"]["JAVA_TOOL_OPTIONS"], "-Xmx256m")
        self.assertLess(256 * MIB, int(self.services["cloudwatch-exporter"]["mem_limit"]))

    # 브로커 주소는 저장소에 없고 서버의 이 디렉터리에만 있다. 마운트가 빠지면 대상 파일을 써도 긁지 않는다.
    def test_prometheus_mounts_the_written_targets_directory(self):
        sources = [volume["source"] for volume in self.services["prometheus"]["volumes"] if volume["target"] == "/etc/prometheus-targets"]
        self.assertEqual(sources, ["/etc/meterengine/prometheus-targets"])

    def test_grafana_admits_only_the_github_org(self):
        env = self.services["grafana"]["environment"]
        self.assertEqual({key: env[key] for key in (
            "GF_AUTH_GITHUB_ENABLED", "GF_AUTH_GITHUB_ALLOWED_ORGANIZATIONS", "GF_AUTH_DISABLE_LOGIN_FORM",
            "GF_AUTH_BASIC_ENABLED", "GF_SERVER_ROOT_URL", "GF_USERS_AUTO_ASSIGN_ORG_ROLE")}, {
            "GF_AUTH_GITHUB_ENABLED": "true", "GF_AUTH_GITHUB_ALLOWED_ORGANIZATIONS": "asm17-ms2",
            "GF_AUTH_DISABLE_LOGIN_FORM": "true", "GF_AUTH_BASIC_ENABLED": "false",
            "GF_SERVER_ROOT_URL": "https://grafana.meterengine.com/", "GF_USERS_AUTO_ASSIGN_ORG_ROLE": "Admin"})


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

    # 카프카를 켠 환경은 서버 그룹이 역할로 나뉜다. 역할별 경보가 이 라벨로 그룹을 가른다.
    def test_app_servers_carry_their_server_role_tag(self):
        jobs = scrape_jobs()
        self.assertIn("source_labels: [__meta_ec2_tag_ServerRole]\n        target_label: server_role", jobs["backend"])
        for job in ("backend", "node"):
            self.assertRegex(jobs[job], r"- (&|\*)role_from_tag", job)

    def test_no_environment_backend_is_dropped(self):
        self.assertNotIn("action: drop", scrape_jobs()["backend"])

    def test_kafka_brokers_are_read_from_written_target_files(self):
        jobs = scrape_jobs()
        self.assertIn("files: [/etc/prometheus-targets/kafka-jmx-*.json]", jobs["kafka-broker"])
        self.assertIn("files: [/etc/prometheus-targets/kafka-node-*.json]", jobs["kafka-node"])

    # AWS는 브로커의 JMX 지표를 60초보다 자주 긁지 말라고 한다. 전체 기본값 15초가 그대로 적용되면 안 된다.
    def test_kafka_broker_jmx_is_scraped_no_faster_than_a_minute(self):
        self.assertIn("scrape_interval: 60s\n", scrape_jobs()["kafka-broker"])

    def test_kafka_broker_metrics_are_trimmed_except_in_measurement_environments(self):
        kept = re.compile(kept_broker_metrics())
        self.assertIn("source_labels: [env, __name__, name]", scrape_jobs()["kafka-broker"])
        for sample, expected in (
            ("staging;kafka_consumer_group_ConsumerLagMetrics_Value;SumOffsetLag", True),
            ("production;kafka_server_BrokerTopicMetrics_Count;MessagesInPerSec", True),
            ("staging;kafka_network_RequestMetrics_Count;TotalTimeMs", False),
            ("production;java_lang_Memory_HeapMemoryUsage_used;", False),
            ("test-storage;kafka_network_RequestMetrics_Count;TotalTimeMs", True),
        ):
            self.assertEqual(bool(kept.fullmatch(sample)), expected, sample)

    def test_every_environment_database_is_scraped_with_its_env(self):
        targets = re.findall(r'targets: \["postgres-exporter-([a-z0-9-]+):9187"\]\n\s+labels: \{ env: ([a-z0-9-]+) \}', scrape_jobs()["postgres"])
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
                   # 읽기 권한이 없는 이름이 하나라도 섞이면 AWS는 호출 전체를 거절한다.
                   'if any(values.get(n) == "access-denied" for n in names): sys.exit(254)\n'
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
        kafka = [kafka_parameter_name(env) for env in PARAMETERS["environments"]]
        self.assertEqual(self.calls("aws"), [*groups, kafka])
        self.assertTrue(all(len(names) <= 10 for names in [*groups, kafka]))
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

    # 브로커 주소는 클러스터를 만들 때마다 바뀌고 저장소에 없다. 파라미터가 있는 환경만 대상 파일이 있어야 한다.
    def test_broker_target_files_follow_each_environment_kafka_parameter(self):
        targets = self.root / "etc/prometheus-targets"
        targets.mkdir()
        (targets / "kafka-jmx-production.json").write_text("[]")
        self.values[kafka_parameter_name("staging")] = "b-1.example.com:9094,b-2.example.com:9094"
        self.assertEqual(self.run_start().returncode, 0)
        self.assertEqual(sorted(path.name for path in targets.iterdir()), ["kafka-jmx-staging.json", "kafka-node-staging.json"])
        for kind, port in (("jmx", 11001), ("node", 11002)):
            self.assertEqual(json.loads((targets / f"kafka-{kind}-staging.json").read_text()),
                             [{"targets": [f"b-1.example.com:{port}", f"b-2.example.com:{port}"], "labels": {"env": "staging"}}])

    # 뒤 환경의 값이 어긋나도 앞 환경의 파일까지 바꾼 채로 멈추지 않는다.
    def test_malformed_broker_address_stops_before_compose_and_writes_nothing(self):
        self.values[kafka_parameter_name("production")] = "b-1.example.com:9094"
        self.values[kafka_parameter_name("staging")] = 'b-1.example.com:9094", "x'
        self.assertNotEqual(self.run_start().returncode, 0)
        self.assertEqual(self.calls("docker"), [])
        self.assertFalse((self.root / "etc/prometheus-targets").exists())

    # 새 start.sh를 모니터링 Terraform apply보다 먼저 올리면 브로커 주소를 읽을 권한이 없다. 돌던 컨테이너는 그대로 둔다.
    def test_unreadable_broker_parameters_stop_before_compose(self):
        self.values[kafka_parameter_name("staging")] = "access-denied"
        result = self.run_start()
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.calls("docker"), [])
        self.assertIn("deploy/terraform/monitoring", result.stderr)

    # 설정 파일만 바뀌면 up -d가 컨테이너를 다시 만들지 않는다. 다시 띄워야 새로 읽을 대상 그룹이 반영된다.
    def test_cloudwatch_exporter_restarts_to_reread_its_config(self):
        self.assertEqual(self.run_start().returncode, 0)
        self.assertIn(["restart", "cloudwatch-exporter"], [call[-2:] for call in self.calls("docker")])

    def test_exporter_ports_match_the_cluster_module(self):
        module = (DEPLOY / "terraform/modules/kafka/main.tf").read_text()
        ports = dict(re.findall(r"^readonly KAFKA_(JMX|NODE)_PORT=(\d+)$", START.read_text(), re.M))
        self.assertEqual(ports, {"JMX": re.search(r"jmx\s+= (\d+)", module).group(1), "NODE": re.search(r"node\s+= (\d+)", module).group(1)})

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
