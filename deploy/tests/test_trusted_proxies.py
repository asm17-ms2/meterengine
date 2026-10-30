import ipaddress
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import unittest


COMPOSE_FILE = Path(__file__).resolve().parents[1] / "compose.prod.yml"
APPLICATION_PROPERTIES = (Path(__file__).resolve().parents[2]
                          / "backend/src/main/resources/application.properties")
AUTH_RATE_LIMIT_SETTING = re.compile(r"\$\{(METERENGINE_AUTH_RATE_LIMIT_[A-Z_]+):([^}]*)\}")
BACKENDS = ("backend-blue", "backend-green")
DUMMY_ENV = {
    name: "dummy" for name in (
        "DB_HOST", "DB_PORT", "DB_NAME", "DB_USERNAME", "DB_PASSWORD", "DB_EXPORTER_PASSWORD",
        "CRYPTO_ENCRYPTION_KEY", "OPERATOR_EMAIL", "OPERATOR_PASSWORD", "FRONTEND_API_KEY",
        "ORGANIZATION_ID", "ORGANIZATION_NAME", "BACKEND_MAX_RAM_PERCENTAGE", "ECR_REGISTRY",
        "BACKEND_BLUE_TAG", "BACKEND_GREEN_TAG", "IMAGE_TAG", "SITE_DOMAIN", "SITE_ALIASES",
        "GRAFANA_ADMIN_PASSWORD", "SLACK_WEBHOOK_URL")
}
DUMMY_ENV["BACKEND_MEM_LIMIT"] = "768m"


@unittest.skipUnless(shutil.which("docker"), "Docker required")
class TrustedProxyDefaultTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        environment = {**os.environ, **DUMMY_ENV}
        for name in [name for name in environment if name.startswith("METERENGINE_AUTH_RATE_LIMIT_")]:
            environment.pop(name)
        result = subprocess.run(
            ["docker", "compose", "-f", str(COMPOSE_FILE), "config", "--format", "json"],
            capture_output=True, text=True, env=environment, timeout=30)
        if result.returncode != 0:
            raise AssertionError(f"compose config failed: {result.stderr}")
        cls.config = json.loads(result.stdout)

    def trusted_networks(self, backend):
        value = self.config["services"][backend]["environment"]["METERENGINE_AUTH_RATE_LIMIT_TRUSTED_PROXIES"]
        return [ipaddress.ip_network(item.strip()) for item in value.split(",") if item.strip()]

    def test_caddy_reaches_backends_from_a_fixed_trusted_address(self):
        caddy_networks = self.config["services"]["caddy"]["networks"]
        for backend in BACKENDS:
            trusted = self.trusted_networks(backend)
            for network in self.config["services"][backend]["networks"]:
                fixed = (caddy_networks.get(network) or {}).get("ipv4_address")
                self.assertIsNotNone(fixed, f"Caddy needs a fixed address on {network}")
                address = ipaddress.ip_address(fixed)
                self.assertTrue(any(address in band for band in trusted),
                                f"{backend} must trust Caddy address {address}")

    def test_dynamic_addresses_stay_outside_the_trusted_band(self):
        for backend in BACKENDS:
            for network in self.config["services"][backend]["networks"]:
                pools = self.config["networks"][network].get("ipam", {}).get("config", [])
                self.assertTrue(pools and all("ip_range" in pool for pool in pools),
                                f"{network} needs a fixed subnet with a separate dynamic range")
                dynamic = [ipaddress.ip_network(pool["ip_range"]) for pool in pools]
                for band in self.trusted_networks(backend):
                    self.assertFalse(any(band.overlaps(pool) for pool in dynamic),
                                     f"{network} hands out trusted addresses {band} to other containers")

    def test_every_auth_rate_limit_setting_reaches_backends_with_the_application_default(self):
        defaults = dict(AUTH_RATE_LIMIT_SETTING.findall(APPLICATION_PROPERTIES.read_text()))
        self.assertGreaterEqual(len(defaults), 8)
        for backend in BACKENDS:
            environment = self.config["services"][backend]["environment"]
            for name, default in defaults.items():
                self.assertIn(name, environment, f"{backend} drops {name}")
                if name != "METERENGINE_AUTH_RATE_LIMIT_TRUSTED_PROXIES":
                    self.assertEqual(environment[name], default, f"{backend} {name}")


if __name__ == "__main__":
    unittest.main()
