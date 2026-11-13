import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import unittest


COMPOSE_FILE = Path(__file__).resolve().parents[1] / "compose.prod.yml"
SIGNUP_NAMES = (
    "EMAIL_VERIFICATION_ENABLED", "MAIL_SENDER", "MAIL_FROM",
    "TURNSTILE_ENABLED", "TURNSTILE_SECRET_KEY", "TURNSTILE_ALLOWED_HOSTNAMES", "TURNSTILE_SITE_KEY")


def services(overrides):
    environment = {name: value for name, value in os.environ.items() if name not in SIGNUP_NAMES}
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
class SignupEnvironmentTest(unittest.TestCase):
    def test_without_parameters_signup_verification_and_captcha_stay_off(self):
        found = services({})
        backend, frontend = found["backend"]["environment"], found["frontend"]["environment"]
        self.assertEqual(backend["EMAIL_VERIFICATION_ENABLED"], "false")
        self.assertEqual(backend["TURNSTILE_ENABLED"], "false")
        self.assertEqual(backend["MAIL_SENDER"], "log")
        self.assertEqual(frontend["TURNSTILE_SITE_KEY"], "")

    def test_verification_link_points_at_the_site_domain(self):
        self.assertEqual(services({})["backend"]["environment"]["METERENGINE_PUBLIC_ORIGIN"], "https://billing.example.com")

    def test_parameters_reach_backend_and_frontend(self):
        found = services({
            "EMAIL_VERIFICATION_ENABLED": "true", "MAIL_SENDER": "ses", "MAIL_FROM": "MeterEngine <no-reply@example.com>",
            "TURNSTILE_ENABLED": "true", "TURNSTILE_SECRET_KEY": "secret", "TURNSTILE_ALLOWED_HOSTNAMES": "billing.example.com",
            "TURNSTILE_SITE_KEY": "site-key"})
        backend = found["backend"]["environment"]
        self.assertEqual(backend["EMAIL_VERIFICATION_ENABLED"], "true")
        self.assertEqual(backend["MAIL_SENDER"], "ses")
        self.assertEqual(backend["MAIL_FROM"], "MeterEngine <no-reply@example.com>")
        self.assertEqual(backend["TURNSTILE_SECRET_KEY"], "secret")
        self.assertEqual(backend["TURNSTILE_ALLOWED_HOSTNAMES"], "billing.example.com")
        self.assertEqual(found["frontend"]["environment"]["TURNSTILE_SITE_KEY"], "site-key")
        self.assertNotIn("TURNSTILE_SECRET_KEY", found["frontend"]["environment"])


if __name__ == "__main__":
    unittest.main()
