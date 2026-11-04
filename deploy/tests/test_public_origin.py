import json
import os
from pathlib import Path
import shutil
import subprocess
import unittest


COMPOSE_FILE = Path(__file__).resolve().parents[1] / "compose.prod.yml"
DUMMY_ENV = {
    name: "dummy" for name in (
        "DB_HOST", "DB_PORT", "DB_NAME", "DB_USERNAME", "DB_PASSWORD", "CRYPTO_ENCRYPTION_KEY", "OPERATOR_EMAIL", "OPERATOR_PASSWORD",
        "BACKEND_MAX_RAM_PERCENTAGE", "ECR_REGISTRY", "IMAGE_REPOSITORY_PREFIX", "IMAGE_TAG")
}
DUMMY_ENV["BACKEND_MEM_LIMIT"] = "768m"
DUMMY_ENV["SITE_DOMAIN"] = "billing.example.com"


@unittest.skipUnless(shutil.which("docker"), "Docker required")
class PublicOriginTest(unittest.TestCase):
    def test_frontend_accepts_public_invoice_requests_from_the_site_domain(self):
        environment = {**os.environ, **DUMMY_ENV}
        environment.pop("METERENGINE_PUBLIC_ORIGIN", None)
        result = subprocess.run(
            ["docker", "compose", "-f", str(COMPOSE_FILE), "config", "--format", "json"],
            capture_output=True, text=True, env=environment, timeout=30)
        if result.returncode != 0:
            raise AssertionError(f"compose config failed: {result.stderr}")
        frontend = json.loads(result.stdout)["services"]["frontend"]["environment"]
        self.assertEqual("https://billing.example.com", frontend.get("METERENGINE_PUBLIC_ORIGIN"))


if __name__ == "__main__":
    unittest.main()
