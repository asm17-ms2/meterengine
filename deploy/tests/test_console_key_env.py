import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import unittest


COMPOSE_FILE = Path(__file__).resolve().parents[1] / "compose.prod.yml"
CONSOLE_KEY_ENV = ("FRONTEND_API_KEY", "ORGANIZATION_ID", "ORGANIZATION_NAME")


def required_names():
    return set(re.findall(r"\$\{([A-Z0-9_]+):?\?", COMPOSE_FILE.read_text())) - set(CONSOLE_KEY_ENV)


@unittest.skipUnless(shutil.which("docker"), "Docker required")
class ConsoleKeyEnvironmentTest(unittest.TestCase):
    def test_compose_runs_without_the_console_api_key_and_organization(self):
        environment = {name: value for name, value in os.environ.items() if name not in CONSOLE_KEY_ENV}
        environment.update({name: "dummy" for name in required_names()})
        environment["BACKEND_MEM_LIMIT"] = "768m"
        environment["SITE_DOMAIN"] = "billing.example.com"
        result = subprocess.run(
            ["docker", "compose", "-f", str(COMPOSE_FILE), "config", "--format", "json"],
            capture_output=True, text=True, env=environment, timeout=30)
        if result.returncode != 0:
            raise AssertionError(f"compose config failed: {result.stderr}")
        services = json.loads(result.stdout)["services"]
        frontend = services["frontend"]["environment"]
        for name in ("METERENGINE_API_KEY", "METERENGINE_ORGANIZATION_ID", "METERENGINE_ORGANIZATION_NAME"):
            self.assertNotIn(name, frontend)
        for backend in ("backend-blue", "backend-green"):
            for name in ("FRONTEND_API_KEY", "FRONTEND_ORGANIZATION_ID"):
                self.assertNotIn(name, services[backend]["environment"])


if __name__ == "__main__":
    unittest.main()
