import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import unittest


COMPOSE_FILE = Path(__file__).resolve().parents[1] / "compose.prod.yml"
LIVE_ENVIRONMENT_NAMES = (
    "BUSINESS_REGISTRATION_LOOKUP_ENABLED", "NTS_SERVICE_KEY", "API_KEY_TEST_RATE_LIMIT_PER_MINUTE")


def services(overrides):
    environment = {name: value for name, value in os.environ.items() if name not in LIVE_ENVIRONMENT_NAMES}
    environment.update({name: "dummy" for name in re.findall(r"\$\{([A-Z0-9_]+):?\?", COMPOSE_FILE.read_text())})
    environment["BACKEND_MEM_LIMIT"] = "768m"
    environment["SITE_DOMAIN"] = "billing.example.com"
    environment.update(overrides)
    result = subprocess.run(
        ["docker", "compose", "-f", str(COMPOSE_FILE), "config", "--format", "json"],
        capture_output=True, text=True, env=environment, timeout=30)
    if result.returncode != 0:
        raise AssertionError(f"compose config failed: {result.stderr}")
    return json.loads(result.stdout)["services"]


@unittest.skipUnless(shutil.which("docker"), "Docker required")
class LiveEnvironmentEnvironmentTest(unittest.TestCase):
    def test_without_parameters_lookup_stays_off_and_test_keys_keep_the_default_limit(self):
        backend = services({})["backend"]["environment"]
        self.assertEqual(backend["BUSINESS_REGISTRATION_LOOKUP_ENABLED"], "false")
        self.assertEqual(backend["NTS_SERVICE_KEY"], "")
        self.assertEqual(backend["API_KEY_TEST_RATE_LIMIT_PER_MINUTE"], "300")

    def test_parameters_reach_backend_only(self):
        found = services({
            "BUSINESS_REGISTRATION_LOOKUP_ENABLED": "true", "NTS_SERVICE_KEY": "service-key",
            "API_KEY_TEST_RATE_LIMIT_PER_MINUTE": "120"})
        backend = found["backend"]["environment"]
        self.assertEqual(backend["BUSINESS_REGISTRATION_LOOKUP_ENABLED"], "true")
        self.assertEqual(backend["NTS_SERVICE_KEY"], "service-key")
        self.assertEqual(backend["API_KEY_TEST_RATE_LIMIT_PER_MINUTE"], "120")
        self.assertNotIn("NTS_SERVICE_KEY", found["frontend"]["environment"])


if __name__ == "__main__":
    unittest.main()
