import ipaddress
import json
import os
from pathlib import Path
import shutil
import subprocess
import unittest


COMPOSE_FILE = Path(__file__).resolve().parents[1] / "compose.prod.yml"
DUMMY_ENV = {
    name: "dummy" for name in (
        "DB_HOST", "DB_PORT", "DB_NAME", "DB_USERNAME", "DB_PASSWORD", "DB_EXPORTER_PASSWORD",
        "CRYPTO_ENCRYPTION_KEY", "OPERATOR_EMAIL", "OPERATOR_PASSWORD", "FRONTEND_API_KEY",
        "ORGANIZATION_ID", "ORGANIZATION_NAME", "BACKEND_MAX_RAM_PERCENTAGE", "ECR_REGISTRY",
        "BACKEND_BLUE_TAG", "BACKEND_GREEN_TAG", "IMAGE_TAG", "SITE_DOMAIN", "SITE_ALIASES",
        "GRAFANA_ADMIN_PASSWORD", "SLACK_WEBHOOK_URL",
        "OAUTH2_PROXY_CLIENT_ID", "OAUTH2_PROXY_CLIENT_SECRET", "OAUTH2_PROXY_COOKIE_SECRET")
}
DUMMY_ENV["BACKEND_MEM_LIMIT"] = "768m"
MONITORING_ONLY = ("grafana", "node-exporter", "postgres-exporter", "cloudwatch-exporter")
APPS = ("frontend", "backend-blue", "backend-green")
WRITE_FLAGS = ("--web.enable-admin-api", "--web.enable-lifecycle",
               "--web.enable-remote-write-receiver", "--web.enable-otlp-receiver")


def compose_config(**overrides):
    environment = {**os.environ, **DUMMY_ENV, **overrides}
    for name in [name for name, value in environment.items() if value is None]:
        environment.pop(name)
    result = subprocess.run(
        ["docker", "compose", "-f", str(COMPOSE_FILE), "config", "--format", "json"],
        capture_output=True, text=True, env=environment, timeout=30)
    if result.returncode != 0:
        raise AssertionError(f"compose config failed: {result.stderr}")
    return json.loads(result.stdout)


@unittest.skipUnless(shutil.which("docker"), "Docker required")
class MonitoringNetworkTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.config = compose_config()
        cls.example = compose_config(SITE_DOMAIN="example.com")

    def test_caddy_holds_the_only_fixed_address_on_monitoring(self):
        pool = self.config["networks"]["monitoring"]["ipam"]["config"]
        self.assertEqual(pool, [{"subnet": "172.30.1.0/24", "ip_range": "172.30.1.128/25"}])
        caddy = ipaddress.ip_address(self.config["services"]["caddy"]["networks"]["monitoring"]["ipv4_address"])
        self.assertEqual(str(caddy), "172.30.1.10")
        self.assertNotIn(caddy, ipaddress.ip_network("172.30.1.128/25"))

    def test_monitoring_services_stay_off_app_networks(self):
        for service in MONITORING_ONLY:
            self.assertEqual(set(self.config["services"][service]["networks"]), {"monitoring"}, service)
        for app in APPS:
            self.assertNotIn("monitoring", self.config["services"][app]["networks"], app)

    def test_prometheus_reaches_every_scrape_target(self):
        self.assertEqual(set(self.config["services"]["prometheus"]["networks"]), {"default", "proxy", "monitoring"})

    def test_only_caddy_publishes_ports_beyond_loopback(self):
        loopback = set()
        for name, service in self.config["services"].items():
            for port in service.get("ports", []):
                if name != "caddy":
                    self.assertEqual(port.get("host_ip"), "127.0.0.1", f"{name} {port}")
                    loopback.add(port["published"])
        self.assertEqual({p["published"] for p in self.config["services"]["caddy"]["ports"]}, {"80", "443"})
        self.assertEqual(loopback, {"3000", "3001"})

    def test_prometheus_keeps_write_endpoints_off(self):
        command = self.config["services"]["prometheus"]["command"]
        for flag in WRITE_FLAGS:
            self.assertFalse(any(arg.startswith(flag) for arg in command), flag)

    def test_sso_trusts_only_the_caddy_address(self):
        caddy = self.config["services"]["caddy"]["networks"]["monitoring"]["ipv4_address"]
        self.assertEqual(self.config["services"]["grafana"]["environment"]["GF_AUTH_PROXY_WHITELIST"], caddy)
        trusted = [a for a in self.config["services"]["oauth2-proxy"]["command"] if a.startswith("--trusted-proxy-ip")]
        self.assertEqual(trusted, [f"--trusted-proxy-ip={caddy}"])
        self.assertEqual(set(self.config["services"]["oauth2-proxy"]["networks"]), {"monitoring"})
        self.assertNotIn("ports", self.config["services"]["oauth2-proxy"])

    def test_grafana_signs_up_sso_users_as_editor(self):
        env = self.example["services"]["grafana"]["environment"]
        self.assertEqual({k: env.get(k) for k in (
            "GF_AUTH_PROXY_ENABLED", "GF_AUTH_PROXY_HEADER_NAME", "GF_AUTH_PROXY_HEADER_PROPERTY",
            "GF_AUTH_PROXY_AUTO_SIGN_UP", "GF_USERS_AUTO_ASSIGN_ORG_ROLE", "GF_SERVER_ROOT_URL")}, {
            "GF_AUTH_PROXY_ENABLED": "true", "GF_AUTH_PROXY_HEADER_NAME": "X-WEBAUTH-USER",
            "GF_AUTH_PROXY_HEADER_PROPERTY": "username", "GF_AUTH_PROXY_AUTO_SIGN_UP": "true",
            "GF_USERS_AUTO_ASSIGN_ORG_ROLE": "Editor", "GF_SERVER_ROOT_URL": "https://grafana.example.com/"})
        self.assertNotIn("GF_AUTH_PROXY_HEADERS", env)
        self.assertNotEqual(env.get("GF_USERS_AUTO_ASSIGN_ORG"), "false")
        self.assertIn("GF_SECURITY_ADMIN_PASSWORD", env)
        self.assertEqual([(p["host_ip"], p["published"]) for p in self.example["services"]["grafana"]["ports"]],
                         [("127.0.0.1", "3001")])

    def test_oauth2_proxy_admits_only_the_github_org(self):
        command = self.example["services"]["oauth2-proxy"]["command"]
        for arg in ("--provider=github", "--github-org=asm17-ms2", "--email-domain=*",
                    "--http-address=0.0.0.0:4180", "--reverse-proxy=true", "--set-xauthrequest=true",
                    "--upstream=static://202", "--redirect-url=https://grafana.example.com/oauth2/callback",
                    "--cookie-domain=.example.com", "--whitelist-domain=.example.com",
                    "--cookie-secure=true", "--cookie-expire=24h", "--exclude-logging-path=/oauth2/callback"):
            self.assertIn(arg, command)
        self.assertFalse(any("secret" in arg or "client-id" in arg for arg in command))
        self.assertFalse(any(arg.split("=")[0] == "--trusted-ip" for arg in command))
        self.assertFalse(any(arg.startswith("--skip-auth") for arg in command))

    def test_oauth2_proxy_refuses_to_start_without_secrets(self):
        for name in ("OAUTH2_PROXY_CLIENT_ID", "OAUTH2_PROXY_CLIENT_SECRET", "OAUTH2_PROXY_COOKIE_SECRET"):
            with self.assertRaisesRegex(AssertionError, name):
                compose_config(**{name: None})  # None은 env에서 뺀다는 뜻


if __name__ == "__main__":
    unittest.main()
