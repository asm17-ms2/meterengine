import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import unittest


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import environment as deploy_environment  # noqa: E402


COMPOSE_FILE = Path(__file__).resolve().parents[1] / "compose.prod.yml"
CORE = {"caddy", "frontend", "backend-blue", "backend-green"}
AGENTS = {"node-exporter", "cadvisor"}
MONITORING = {"prometheus", "grafana", "oauth2-proxy", "postgres-exporter", "cloudwatch-exporter"}
CORE_ENV = {
    "DB_HOST": "db", "DB_PORT": "5432", "DB_NAME": "app", "DB_USERNAME": "owner", "DB_PASSWORD": "secret",
    "CRYPTO_ENCRYPTION_KEY": "key", "OPERATOR_EMAIL": "a@example.invalid", "OPERATOR_PASSWORD": "secret",
    "BACKEND_MAX_RAM_PERCENTAGE": "70", "BACKEND_MEM_LIMIT": "2g", "ECR_REGISTRY": "registry", "IMAGE_REPOSITORY_PREFIX": "meterengine-staging",
    "BACKEND_BLUE_TAG": "tag", "BACKEND_GREEN_TAG": "tag", "IMAGE_TAG": "tag",
    "SITE_DOMAIN": "example.invalid", "SITE_ALIASES": "",
}
MONITORING_ENV = {
    "DB_EXPORTER_PASSWORD": "exporter-secret", "GRAFANA_ADMIN_PASSWORD": "grafana-secret", "SLACK_WEBHOOK_URL": "https://hook.invalid",
    "OAUTH2_PROXY_CLIENT_ID": "client", "OAUTH2_PROXY_CLIENT_SECRET": "client-secret", "OAUTH2_PROXY_COOKIE_SECRET": "cookie-secret",
}


def compose(*arguments, **variables):
    environment = {name: value for name, value in os.environ.items()
                   if name not in {*CORE_ENV, *MONITORING_ENV, "COMPOSE_PROFILES"}}
    return subprocess.run(["docker", "compose", "-f", str(COMPOSE_FILE), *arguments],
                          env={**environment, **CORE_ENV, **variables}, text=True, capture_output=True, timeout=30)


def services(**variables):
    result = compose("config", "--services", **variables)
    if result.returncode != 0:
        raise AssertionError(result.stderr)
    return set(result.stdout.split())


@unittest.skipUnless(shutil.which("docker"), "Docker required")
class ProfilesTest(unittest.TestCase):
    def test_every_relaxed_value_is_checked_by_deploy(self):
        relaxed = set(re.findall(r"\$\{([A-Z0-9_]+):-\}", COMPOSE_FILE.read_text()))
        self.assertEqual(relaxed, set(deploy_environment.MONITORING_PARAMETERS))

    def test_no_profile_lists_core_services(self):
        self.assertEqual(services(), CORE)

    def test_agents_adds_node_exporter_and_cadvisor(self):
        self.assertEqual(services(COMPOSE_PROFILES="agents"), CORE | AGENTS)

    def test_monitoring_adds_central_services(self):
        self.assertEqual(services(COMPOSE_PROFILES="monitoring"), CORE | MONITORING)

    def test_config_succeeds_without_monitoring_secrets(self):
        result = compose("config", "--services", COMPOSE_PROFILES="agents")
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_monitoring_secrets_reach_their_services(self):
        result = compose("config", "--format", "json", COMPOSE_PROFILES="agents,monitoring", **MONITORING_ENV)
        self.assertEqual(result.returncode, 0, result.stderr)
        config = json.loads(result.stdout)["services"]
        self.assertEqual(config["postgres-exporter"]["environment"]["DATA_SOURCE_PASS"], "exporter-secret")
        self.assertEqual(config["grafana"]["environment"]["GF_SECURITY_ADMIN_PASSWORD"], "grafana-secret")
        self.assertEqual(config["grafana"]["environment"]["SLACK_WEBHOOK_URL"], "https://hook.invalid")
        for name, value in (("CLIENT_ID", "client"), ("CLIENT_SECRET", "client-secret"), ("COOKIE_SECRET", "cookie-secret")):
            self.assertEqual(config["oauth2-proxy"]["environment"][f"OAUTH2_PROXY_{name}"], value)

    def test_core_values_stay_required(self):
        variables = {**CORE_ENV, "DB_PASSWORD": ""}
        result = compose("config", "--services", **variables)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("DB_PASSWORD", result.stderr)

    def test_profiles_read_from_env_file(self):
        with tempfile.TemporaryDirectory() as directory:
            env_file = Path(directory) / "prod.env"
            env_file.write_text("COMPOSE_PROFILES=agents\n")
            result = compose("--env-file", str(env_file), "config", "--services")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(set(result.stdout.split()), CORE | AGENTS)


if __name__ == "__main__":
    unittest.main()
