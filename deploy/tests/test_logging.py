import http.client
import ipaddress
import json
import os
from pathlib import Path
import shutil
import ssl
import subprocess
import tempfile
import time
import unittest
import uuid


IMAGE = "caddy:2.11.7-alpine"
SOURCE = Path(__file__).resolve().parents[1] / "caddy/Caddyfile"
TOKEN = "registration-token-for-log-privacy-regression"
API_TOKEN = "api-registration-token-for-log-privacy-regression"
AUTH_KEY = "callback-auth-key-for-log-privacy-regression"
CUSTOMER_KEY = "customer-key-for-log-privacy-regression"
STATE = "callback-state-for-log-privacy-regression"
AUTHORIZATION = "Bearer authorization-for-log-privacy-regression"
COOKIE = "me_session=cookie-for-log-privacy-regression"
PROXY_AUTHORIZATION = "Basic proxy-authorization-for-log-privacy-regression"
SET_COOKIE = "fixture_session=response-cookie-for-log-privacy-regression"
CALLBACK = f"/payment-method-registrations/{TOKEN}/success?authKey={AUTH_KEY}&customerKey={CUSTOMER_KEY}&state={STATE}"
API_PATH = f"/v1/payment-method-registrations/{API_TOKEN}"
INVITATION_TOKEN = "invitation-token-for-log-privacy-regression"
INVITATION_API_TOKEN = "api-invitation-token-for-log-privacy-regression"
INVITATION_QUERY = "invitation-query-for-log-privacy-regression"
INVITATION = f"/organization-invitations/{INVITATION_TOKEN}?token={INVITATION_QUERY}"
INVITATION_API = f"/v1/organization-invitations/{INVITATION_API_TOKEN}/accept?token={INVITATION_QUERY}"


@unittest.skipUnless(shutil.which("docker"), "Docker required")
class CaddyLoggingPrivacyTest(unittest.TestCase):
    @classmethod
    def docker(cls, *arguments, check=True):
        return subprocess.run(["docker", *arguments], capture_output=True, text=True,
                              check=check, timeout=30)

    @classmethod
    def setUpClass(cls):
        if cls.docker("image", "inspect", IMAGE, check=False).returncode != 0:
            raise unittest.SkipTest(f"Local {IMAGE} image required")
        cls.temp = tempfile.TemporaryDirectory(prefix="meterengine-caddy-privacy-",
                                               dir=os.environ.get("CADDY_LOG_TEST_TMP"))
        cls.root = Path(cls.temp.name)
        suffix = uuid.uuid4().hex[:12]
        cls.network = f"meterengine-caddy-privacy-{suffix}"
        cls.proxy = f"{cls.network}-proxy"
        cls.upstream = f"{cls.network}-upstream"
        cls.addClassCleanup(cls.cleanup)
        shutil.copyfile(SOURCE, cls.root / "Caddyfile")
        (cls.root / "upstreams.caddy").write_text("to backend-blue:8080\n")
        (cls.root / "access-log.caddy").write_text("output stdout\n")
        (cls.root / "Upstreamfile").write_text(
            "{\n admin off\n auto_https off\n}\n"
            ":3000, :8080 {\n"
            ' header X-Observed-Client-IP "{http.request.header.X-MeterEngine-Client-IP}"\n'
            f' header Set-Cookie "{SET_COOKIE}"\n'
            ' header Location "{http.request.uri}"\n'
            ' respond "{http.request.uri}|{http.request.header.Authorization}" 200\n}\n')
        cls.mount = f"{cls.root}:/etc/caddy:ro"
        validated = cls.docker("run", "--rm", "--network", "none", "-v", cls.mount,
                               "-e", "SITE_DOMAIN=localhost", "-e", "SITE_ALIASES=",
                               IMAGE, "caddy", "validate", "--config", "/etc/caddy/Caddyfile", check=False)
        if validated.returncode != 0:
            raise AssertionError(f"Caddy source configuration is invalid: {validated.stderr}")
        cls.docker("network", "create", cls.network)
        cls.docker("run", "--rm", "-d", "--name", cls.upstream, "--network", cls.network,
                   "--network-alias", "frontend", "--network-alias", "backend-blue",
                   "-v", cls.mount, IMAGE, "caddy", "run", "--adapter", "caddyfile", "--config", "/etc/caddy/Upstreamfile")
        cls.frontend_address = cls.docker(
            "inspect", "-f", "{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}",
            cls.upstream).stdout.strip()
        cls.docker("run", "--rm", "-d", "--name", cls.proxy, "--network", cls.network,
                   "-p", "127.0.0.1::443", "-p", "127.0.0.1::8081",
                   "-e", "SITE_DOMAIN=localhost", "-e", "SITE_ALIASES=",
                   "-e", f"FRONTEND_ADDRESS={cls.frontend_address}", "-v", cls.mount,
                   IMAGE, "caddy", "run", "--config", "/etc/caddy/Caddyfile")
        cls.public_port = cls.port("443/tcp")
        cls.internal_port = cls.port("8081/tcp")
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline:
            try:
                if cls.request("/__privacy_ready")[0] == 200:
                    return
            except (OSError, http.client.HTTPException):
                pass
            time.sleep(0.1)
        raise AssertionError("Local Caddy fixture did not become ready")

    @classmethod
    def cleanup(cls):
        cls.docker("rm", "-f", cls.proxy, cls.upstream, check=False)
        cls.docker("network", "rm", cls.network, check=False)
        cls.temp.cleanup()

    @classmethod
    def port(cls, container_port):
        result = cls.docker("port", cls.proxy, container_port, check=False)
        if result.returncode != 0:
            diagnostic = cls.docker("logs", cls.proxy, check=False)
            raise AssertionError(f"Caddy fixture port unavailable: {result.stderr}{diagnostic.stderr}")
        published = result.stdout.strip()
        return int(published.rsplit(":", 1)[1])

    @classmethod
    def request(cls, path, internal=False, referer=CALLBACK, case=None):
        connection = (http.client.HTTPConnection("127.0.0.1", cls.internal_port, timeout=12)
                      if internal else http.client.HTTPSConnection(
                          "localhost", cls.public_port, timeout=12,
                          context=ssl._create_unverified_context()))
        try:
            connection.request("GET", path, headers={
                "Host": "localhost", "Authorization": AUTHORIZATION, "Cookie": COOKIE,
                "Proxy-Authorization": PROXY_AUTHORIZATION,
                "Referer": f"https://localhost{referer}",
                **({"X-Test-Case": case} if case else {}),
            })
            response = connection.getresponse()
            return response.status, response.read().decode()
        finally:
            connection.close()

    def test_public_and_internal_proxies_overwrite_client_ip_sent_by_other_hosts(self):
        for internal in (False, True):
            connection = (http.client.HTTPConnection("127.0.0.1", self.internal_port, timeout=12)
                          if internal else http.client.HTTPSConnection(
                              "localhost", self.public_port, timeout=12,
                              context=ssl._create_unverified_context()))
            try:
                connection.request("GET", "/__client_ip", headers={
                    "Host": "localhost", "X-MeterEngine-Client-IP": "203.0.113.77",
                    "Authorization": AUTHORIZATION, "Cookie": COOKIE,
                    "Proxy-Authorization": PROXY_AUTHORIZATION, "Referer": f"https://localhost{CALLBACK}"})
                response = connection.getresponse()
                self.assertEqual(response.status, 200)
                observed = response.getheader("X-Observed-Client-IP")
                response.read()
                self.assertNotEqual(observed, "203.0.113.77", f"internal={internal}")
                ipaddress.ip_address(observed)
            finally:
                connection.close()

    def test_internal_proxy_preserves_client_ip_sent_by_frontend(self):
        result = self.docker("exec", self.upstream, "wget", "-S", "-qO", "/dev/null",
                             "--header", "X-MeterEngine-Client-IP: 203.0.113.77",
                             f"http://{self.proxy}:8081/__client_ip")
        self.assertIn("x-observed-client-ip: 203.0.113.77", (result.stdout + result.stderr).lower())

    def test_variant_paths_do_not_leak_tokens_into_logs(self):
        variants = {
            "encoded-token-for-log-privacy-regression": "/payment%2Dmethod-registrations/{}",
            "semicolon-token-for-log-privacy-regression": "/v1/organization-invitations;x/{}/accept",
            "dot-token-for-log-privacy-regression": "/v1/./payment-method-registrations/{}",
            "segment-dot-value-for-log-privacy-regression": "/payment-method-registrations/./{}",
            "segment-semicolon-value-for-log-privacy-regression": "/v1/organization-invitations/;x/{}/accept",
        }
        referer_token = "referer-token-for-log-privacy-regression"
        before = len(self.access_entries())
        for token, template in variants.items():
            self.request(template.format(token), referer=f"/%70ayment-method-registrations/{referer_token}")
        deadline = time.monotonic() + 5
        while time.monotonic() < deadline and len(self.access_entries()) < before + len(variants):
            time.sleep(0.05)
        logs = self.logs()
        self.assertGreaterEqual(len(self.access_entries()), before + len(variants))
        for token in (*variants, referer_token):
            self.assertNotIn(token, logs)

    @classmethod
    def logs(cls):
        result = cls.docker("logs", cls.proxy)
        return result.stdout + result.stderr

    @classmethod
    def access_entries(cls):
        entries = [json.loads(line) for line in cls.logs().splitlines() if line.startswith("{")]
        return [entry for entry in entries if entry.get("logger", "").startswith("http.log.access")]

    def test_source_configuration_validates_on_caddy_2_11_7(self):
        version = self.docker("run", "--rm", "--network", "none", IMAGE, "caddy", "version")
        self.assertTrue(version.stdout.startswith("v2.11.7 "))
        validated = self.docker("run", "--rm", "--network", "none", "-v", self.mount,
                                "-e", "SITE_DOMAIN=localhost", "-e", "SITE_ALIASES=",
                                IMAGE, "caddy", "validate", "--config", "/etc/caddy/Caddyfile")
        self.assertIn("Valid configuration", validated.stdout + validated.stderr)
        self.assertEqual(SOURCE.read_bytes(), (self.root / "Caddyfile").read_bytes())

    def test_success_and_upstream_failure_logs_hide_registration_credentials(self):
        normal = "/v1/customers?page=2"
        protected = ((CALLBACK, False, CALLBACK), (API_PATH, False, CALLBACK),
                     (API_PATH, True, CALLBACK), (INVITATION, False, INVITATION),
                     (INVITATION_API, False, INVITATION), (INVITATION_API, True, INVITATION))
        for path, internal, referer in (*protected, (normal, False, CALLBACK)):
            status, body = self.request(path, internal, referer, case="success")
            self.assertEqual(status, 200)
            self.assertTrue(body == f"{path}|{AUTHORIZATION}", "Log filters must preserve upstream requests")
        self.docker("stop", self.upstream)
        for path, internal, referer in protected:
            self.assertIn(self.request(path, internal, referer, case="success")[0], (502, 503))

        deadline = time.monotonic() + 5
        entries = []
        while time.monotonic() < deadline:
            result = self.docker("logs", self.proxy)
            logs = result.stdout + result.stderr
            entries = [json.loads(line) for line in logs.splitlines() if line.startswith("{")]
            entries = [entry for entry in entries
                       if entry.get("request", {}).get("headers", {}).get("X-Test-Case") == ["success"]]
            access = [entry for entry in entries if entry.get("logger", "").startswith("http.log.access")]
            errors = [entry for entry in entries if entry.get("logger", "").startswith("http.log.error")]
            if len(access) >= 9 and len(errors) >= 6:
                break
            time.sleep(0.05)
        self.assertGreaterEqual(len(access), 9)
        self.assertGreaterEqual(len(errors), 6)
        forbidden = (TOKEN, API_TOKEN, AUTH_KEY, CUSTOMER_KEY, STATE, AUTHORIZATION,
                     COOKIE, PROXY_AUTHORIZATION, SET_COOKIE, INVITATION_TOKEN,
                     INVITATION_API_TOKEN, INVITATION_QUERY)
        self.assertTrue(all(value not in logs for value in forbidden), "Credentials leaked into Caddy logs")
        callback_uri = "/payment-method-registrations/REDACTED/success"
        api_uri = "/v1/payment-method-registrations/REDACTED"
        invitation_uri = "/organization-invitations/REDACTED"
        invitation_api_uri = "/v1/organization-invitations/REDACTED/accept"
        for records in (access, errors):
            uris = [entry.get("request", {}).get("uri") for entry in records]
            self.assertIn(callback_uri, uris)
            self.assertIn(api_uri, uris)
            self.assertIn(invitation_uri, uris)
            self.assertIn(invitation_api_uri, uris)
            for entry in records:
                headers = entry.get("request", {}).get("headers", {})
                self.assertEqual(headers.get("Authorization"), ["REDACTED"])
                self.assertEqual(headers.get("Cookie"), ["REDACTED"])
                self.assertEqual(headers.get("Proxy-Authorization"), ["REDACTED"])
                expected_referer = (invitation_uri if "organization-invitations" in entry["request"]["uri"]
                                    else callback_uri)
                self.assertEqual(headers.get("Referer"), [f"https://localhost{expected_referer}"])
        normal_access = next(entry for entry in access if entry["request"]["uri"] == normal)
        self.assertEqual(normal_access["resp_headers"]["Set-Cookie"], ["REDACTED"])
        callback_access = next(entry for entry in access if entry["request"]["uri"] == callback_uri and entry["status"] == 200)
        self.assertEqual(callback_access["resp_headers"]["Location"], [callback_uri])
        for uri in (invitation_uri, invitation_api_uri):
            successful = next(entry for entry in access if entry["request"]["uri"] == uri and entry["status"] == 200)
            self.assertEqual(successful["resp_headers"]["Location"], [uri])


if __name__ == "__main__":
    unittest.main()
