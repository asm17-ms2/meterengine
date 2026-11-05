import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import unittest


DEPLOY = Path(__file__).resolve().parents[1]
COMPOSE_FILE = DEPLOY / "compose.prod.yml"
APP_FLEET = DEPLOY / "terraform/modules/app_fleet"
DUMMY_ENV = {
    name: "dummy" for name in (
        "DB_HOST", "DB_PORT", "DB_NAME", "DB_USERNAME", "DB_PASSWORD", "CRYPTO_ENCRYPTION_KEY", "OPERATOR_EMAIL", "OPERATOR_PASSWORD",
        "BACKEND_MAX_RAM_PERCENTAGE", "ECR_REGISTRY", "IMAGE_REPOSITORY_PREFIX", "IMAGE_TAG", "SITE_DOMAIN")
}
DUMMY_ENV["BACKEND_MEM_LIMIT"] = "768m"
DUMMY_ENV["COMPOSE_PROFILES"] = "agents"
# 앱 서버 보안 그룹이 이 둘만 들인다. 서비스가 다른 포트를 열면 닿지 않거나 보안 그룹을 같이 고쳐야 한다.
ALB_TARGETS = {"frontend": 3000, "backend": 8080}
AGENTS = {"node-exporter": ("9100", 9100), "cadvisor": ("9101", 8080)}


def compose_config():
    environment = {**os.environ, **DUMMY_ENV}
    result = subprocess.run(["docker", "compose", "-f", str(COMPOSE_FILE), "config", "--format", "json"],
                            capture_output=True, text=True, env=environment, timeout=30)
    if result.returncode != 0:
        raise AssertionError(f"compose config failed: {result.stderr}")
    return json.loads(result.stdout)


def target_group_ports():
    source = (APP_FLEET / "alb.tf").read_text()
    return {name: int(re.search(r"^  port\s+=\s+(\d+)$", source[source.index(f'resource "aws_lb_target_group" "{name}"'):], re.M).group(1))
            for name in ALB_TARGETS}


def monitoring_ports():
    source = (APP_FLEET / "monitoring.tf").read_text()
    block = source[source.index('"app_from_monitoring"'):]
    return set(re.search(r"toset\(\[([^\]]*)\]\)", block).group(1).replace('"', "").replace(" ", "").split(","))


def alb_idle_timeout():
    return int(re.search(r"^\s+idle_timeout\s+=\s+(\d+)$", (APP_FLEET / "alb.tf").read_text(), re.M).group(1))


@unittest.skipUnless(shutil.which("docker"), "Docker required")
class AppPortsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.services = compose_config()["services"]

    def published(self, service):
        return {(port.get("host_ip"), port["published"], port["target"]) for port in self.services[service].get("ports", [])}

    def test_only_the_app_and_agents_run(self):
        self.assertEqual(set(self.services), set(ALB_TARGETS) | set(AGENTS))

    def test_alb_targets_listen_on_their_target_group_ports(self):
        ports = target_group_ports()
        self.assertEqual(ports, ALB_TARGETS)
        for service, port in ports.items():
            self.assertEqual(self.published(service), {(None, str(port), port)}, service)

    def test_agents_publish_the_ports_the_monitoring_server_scrapes(self):
        for service, (host, target) in AGENTS.items():
            self.assertEqual(self.published(service), {(None, host, target)}, service)
        self.assertEqual(monitoring_ports(), {str(ALB_TARGETS["backend"])} | {host for host, _ in AGENTS.values()})

    def test_keep_alive_outlasts_the_alb_idle_timeout(self):
        # 서버가 먼저 연결을 닫으면 ALB가 그 연결을 재사용하다 502를 낸다.
        idle = alb_idle_timeout()
        backend = self.services["backend"]["environment"]["SERVER_TOMCAT_KEEPALIVETIMEOUT"]
        self.assertRegex(backend, r"^\d+s$")
        self.assertGreater(int(backend[:-1]), idle)
        self.assertGreater(int(self.services["frontend"]["environment"]["KEEP_ALIVE_TIMEOUT"]) / 1000, idle)

    def test_frontend_calls_the_backend_on_the_same_server(self):
        self.assertEqual(self.services["frontend"]["environment"]["METERENGINE_API_BASE_URL"], "http://backend:8080")


if __name__ == "__main__":
    unittest.main()
