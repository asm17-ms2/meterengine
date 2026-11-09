import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import unittest


COMPOSE_FILE = Path(__file__).resolve().parents[1] / "compose.prod.yml"


def required_names():
    return set(re.findall(r"\$\{([A-Z0-9_]+):?\?", COMPOSE_FILE.read_text()))


def backend_environment(overrides):
    environment = {name: value for name, value in os.environ.items() if name != "WEBHOOK_ENABLED"}
    environment.update({name: "dummy" for name in required_names()})
    environment["BACKEND_MEM_LIMIT"] = "768m"
    environment["SITE_DOMAIN"] = "billing.example.com"
    environment.update(overrides)
    result = subprocess.run(
        ["docker", "compose", "-f", str(COMPOSE_FILE), "config", "--format", "json"],
        capture_output=True, text=True, env=environment, timeout=30)
    if result.returncode != 0:
        raise AssertionError(f"compose config failed: {result.stderr}")
    return json.loads(result.stdout)["services"]["backend"]["environment"]


@unittest.skipUnless(shutil.which("docker"), "Docker required")
class WebhookEnvironmentTest(unittest.TestCase):
    def test_webhook_defaults_to_enabled(self):
        self.assertEqual(backend_environment({})["WEBHOOK_ENABLED"], "true")

    def test_webhook_switch_reaches_backend(self):
        self.assertEqual(backend_environment({"WEBHOOK_ENABLED": "false"})["WEBHOOK_ENABLED"], "false")


if __name__ == "__main__":
    unittest.main()
