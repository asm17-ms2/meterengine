import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import unittest


COMPOSE_FILE = Path(__file__).resolve().parents[1] / "compose.prod.yml"
WARMUP_IDENTITY_ENV = ("INGEST_WARMUP_API_KEY", "INGEST_WARMUP_ORGANIZATION_ID", "INGEST_WARMUP_CUSTOMER_ID")
WARMUP_LIMIT_ENV = ("INGEST_WARMUP_REQUESTS", "INGEST_WARMUP_TIME_LIMIT")
WARMUP_ENV = (*WARMUP_IDENTITY_ENV, *WARMUP_LIMIT_ENV)


def required_names():
    return set(re.findall(r"\$\{([A-Z0-9_]+):?\?", COMPOSE_FILE.read_text()))


def backend_environment(warmup_values):
    environment = {name: value for name, value in os.environ.items() if name not in WARMUP_ENV}
    environment.update({name: "dummy" for name in required_names()})
    environment["BACKEND_MEM_LIMIT"] = "768m"
    environment["SITE_DOMAIN"] = "billing.example.com"
    environment.update(warmup_values)
    result = subprocess.run(
        ["docker", "compose", "-f", str(COMPOSE_FILE), "config", "--format", "json"],
        capture_output=True, text=True, env=environment, timeout=30)
    if result.returncode != 0:
        raise AssertionError(f"compose config failed: {result.stderr}")
    return json.loads(result.stdout)["services"]["backend"]["environment"]


@unittest.skipUnless(shutil.which("docker"), "Docker required")
class IngestWarmupEnvironmentTest(unittest.TestCase):
    def test_compose_passes_empty_warmup_identity_when_unset(self):
        backend = backend_environment({})
        for name in WARMUP_IDENTITY_ENV:
            self.assertEqual(backend.get(name), "", name)

    def test_compose_passes_default_warmup_limits_when_unset(self):
        backend = backend_environment({})
        self.assertEqual(backend.get("INGEST_WARMUP_REQUESTS"), "1000")
        self.assertEqual(backend.get("INGEST_WARMUP_TIME_LIMIT"), "20s")

    def test_compose_passes_the_warmup_values_when_set(self):
        values = {"INGEST_WARMUP_API_KEY": "me_live_warmup", "INGEST_WARMUP_ORGANIZATION_ID": "org",
                  "INGEST_WARMUP_CUSTOMER_ID": "customer", "INGEST_WARMUP_REQUESTS": "2000", "INGEST_WARMUP_TIME_LIMIT": "20s"}
        backend = backend_environment(values)
        for name, value in values.items():
            self.assertEqual(backend.get(name), value, name)


if __name__ == "__main__":
    unittest.main()
