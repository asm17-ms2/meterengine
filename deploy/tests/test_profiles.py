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
CORE = {"frontend", "backend"}
AGENTS = {"node-exporter", "cadvisor"}
# 예열은 값이 없으면 건너뛰는 선택 기능이라 빈 값을 그대로 넘긴다.
OPTIONAL_EMPTY = {"INGEST_WARMUP_API_KEY", "INGEST_WARMUP_ORGANIZATION_ID", "INGEST_WARMUP_CUSTOMER_ID"}
# 가입 메일과 캡차는 켤 때만 값이 필요하고, 켰는데 비어 있으면 백엔드가 뜨지 않는다.
OPTIONAL_EMPTY |= {"MAIL_FROM", "TURNSTILE_SECRET_KEY", "TURNSTILE_ALLOWED_HOSTNAMES", "TURNSTILE_SITE_KEY"}
# 국세청 인증키는 사업자 조회를 켤 때만 필요하고, 켰는데 비어 있으면 백엔드가 뜨지 않는다.
OPTIONAL_EMPTY |= {"NTS_SERVICE_KEY"}
CORE_ENV = {
    "DB_HOST": "db", "DB_PORT": "5432", "DB_NAME": "app", "DB_USERNAME": "owner", "DB_PASSWORD": "secret",
    "CRYPTO_ENCRYPTION_KEY": "key", "OPERATOR_EMAIL": "a@example.invalid", "OPERATOR_PASSWORD": "secret",
    "BACKEND_MAX_RAM_PERCENTAGE": "70", "BACKEND_MEM_LIMIT": "2g", "ECR_REGISTRY": "registry", "IMAGE_REPOSITORY_PREFIX": "meterengine-staging",
    "IMAGE_TAG": "tag", "SITE_DOMAIN": "example.invalid",
}


def compose(*arguments, **variables):
    environment = {name: value for name, value in os.environ.items() if name not in {*CORE_ENV, "COMPOSE_PROFILES"}}
    return subprocess.run(["docker", "compose", "-f", str(COMPOSE_FILE), *arguments],
                          env={**environment, **CORE_ENV, **variables}, text=True, capture_output=True, timeout=30)


def services(**variables):
    result = compose("config", "--services", **variables)
    if result.returncode != 0:
        raise AssertionError(result.stderr)
    return set(result.stdout.split())


@unittest.skipUnless(shutil.which("docker"), "Docker required")
class ProfilesTest(unittest.TestCase):
    def test_every_profile_is_one_deploy_accepts(self):
        declared = set(re.findall(r"profiles: \[([a-z]+)\]", COMPOSE_FILE.read_text()))
        self.assertEqual(declared, deploy_environment.PROFILES)

    def test_no_value_is_left_optional_for_deploy_to_check(self):
        self.assertEqual(set(re.findall(r"\$\{([A-Z0-9_]+):-\}", COMPOSE_FILE.read_text())) - OPTIONAL_EMPTY, set())

    def test_no_profile_lists_core_services(self):
        self.assertEqual(services(), CORE)

    def test_agents_adds_node_exporter_and_cadvisor(self):
        self.assertEqual(services(COMPOSE_PROFILES="agents"), CORE | AGENTS)

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
