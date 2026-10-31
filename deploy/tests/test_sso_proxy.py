import http.client
import json
import os
from pathlib import Path
import re
import shutil
import ssl
import subprocess
import tempfile
import time
import unittest
import urllib.parse
import uuid

from test_monitoring_access import compose_config


CADDY_IMAGE = "caddy:2.11.7-alpine"
OAUTH2_PROXY_IMAGE = "quay.io/oauth2-proxy/oauth2-proxy:v7.15.5"
DEPLOY = Path(__file__).resolve().parents[1]
SOURCE = DEPLOY / "caddy/Caddyfile"
SSO_SITES = DEPLOY / "caddy/sso-sites.caddy"
SUBNET = "172.30.1.0/24"
IP_RANGE = "172.30.1.128/25"
CADDY_ADDRESS = "172.30.1.10"
HOSTS = ("localhost", "grafana.localhost", "prometheus.localhost")
AUTHED = {"Cookie": "_oauth2_proxy=ok"}
FORGED = {"X-WEBAUTH-USER": "admin"}
UPSTREAM = (
    "{\n admin off\n auto_https off\n}\n"
    ":3000, :8080, :9090 {\n"
    ' header X-Observed-User "{http.request.header.X-WEBAUTH-USER}"\n'
    ' respond "upstream:{http.request.uri}" 200\n}\n')
FAKE_AUTH = (
    "{\n admin off\n auto_https off\n}\n"
    ":4180 {\n"
    " @authed {\n  path /oauth2/auth\n  header Cookie *_oauth2_proxy=ok*\n }\n"
    " handle @authed {\n  header X-Auth-Request-User sso-user\n  respond 202\n }\n"
    " handle /oauth2/auth {\n  respond 401\n }\n"
    ' handle /oauth2/start {\n  respond "start:{http.request.header.X-Auth-Request-Redirect}" 200\n }\n'
    ' handle /oauth2/* {\n  respond "oauth2:{http.request.uri}|{http.request.header.X-WEBAUTH-USER}" 200\n }\n'
    "}\n")


class SniConnection(http.client.HTTPSConnection):
    def __init__(self, server_name, port):
        super().__init__("127.0.0.1", port, timeout=12, context=ssl._create_unverified_context())
        self.server_name = server_name

    def connect(self):
        http.client.HTTPConnection.connect(self)
        self.sock = self._context.wrap_socket(self.sock, server_hostname=self.server_name)


class CaddySsoFixture:
    @classmethod
    def docker(cls, *arguments, check=True):
        return subprocess.run(["docker", *arguments], capture_output=True, text=True,
                              check=check, timeout=30)

    @classmethod
    def require_image(cls, image):
        if cls.docker("image", "inspect", image, check=False).returncode != 0:
            raise unittest.SkipTest(f"Local {image} image required")

    @classmethod
    def start_auth(cls):
        raise NotImplementedError

    @classmethod
    def auth_ready(cls):
        return True

    @classmethod
    def setUpClass(cls):
        cls.require_image(CADDY_IMAGE)
        cls.temp = tempfile.TemporaryDirectory(prefix="meterengine-caddy-sso-")
        cls.root = Path(cls.temp.name)
        cls.network = f"meterengine-caddy-sso-{uuid.uuid4().hex[:12]}"
        cls.proxy = f"{cls.network}-proxy"
        cls.upstream = f"{cls.network}-upstream"
        cls.auth = f"{cls.network}-auth"
        cls.addClassCleanup(cls.cleanup)
        shutil.copyfile(SOURCE, cls.root / "Caddyfile")
        shutil.copyfile(SSO_SITES, cls.root / "sso-sites.caddy")
        (cls.root / "sso.caddy").write_text("import sso-sites.caddy\n")
        (cls.root / "upstreams.caddy").write_text("to backend-blue:8080\n")
        (cls.root / "access-log.caddy").write_text("output stdout\n")
        (cls.root / "Upstreamfile").write_text(UPSTREAM)
        (cls.root / "FakeAuthfile").write_text(FAKE_AUTH)
        cls.mount = f"{cls.root}:/etc/caddy:ro"
        created = cls.docker("network", "create", "--subnet", SUBNET, "--ip-range", IP_RANGE,
                             cls.network, check=False)
        if created.returncode != 0:
            raise AssertionError(f"SSO test network needs {SUBNET} free: {created.stderr}")
        cls.docker("run", "--rm", "-d", "--name", cls.upstream, "--network", cls.network,
                   "--network-alias", "grafana", "--network-alias", "prometheus",
                   "--network-alias", "frontend", "--network-alias", "backend-blue",
                   "-v", cls.mount, CADDY_IMAGE, "caddy", "run", "--adapter", "caddyfile",
                   "--config", "/etc/caddy/Upstreamfile")
        cls.start_auth()
        cls.docker("run", "--rm", "-d", "--name", cls.proxy, "--network", cls.network,
                   "--ip", CADDY_ADDRESS, "-p", "127.0.0.1::443",
                   "-e", "SITE_DOMAIN=localhost", "-e", "SITE_ALIASES=", "-v", cls.mount,
                   CADDY_IMAGE, "caddy", "run", "--config", "/etc/caddy/Caddyfile")
        result = cls.docker("port", cls.proxy, "443/tcp", check=False)
        if result.returncode != 0:
            raise AssertionError(f"Caddy fixture port unavailable: {result.stderr}{cls.logs(cls.proxy)}")
        cls.public_port = int(result.stdout.strip().splitlines()[0].rsplit(":", 1)[1])
        if not cls.wait_until(lambda: cls.request("localhost", "/")[0] == 200, 20):
            raise AssertionError(f"SSO fixture did not become ready: {cls.logs(cls.proxy)}")
        # SSO 사이트와 인증 컨테이너가 늦게 뜨면 각 시험이 원인을 드러내도록 여기서는 기다리기만 한다.
        cls.wait_until(lambda: all(cls.request(host, "/oauth2/sign_in")[0] < 500 for host in HOSTS[1:])
                       and cls.auth_ready(), 10)

    @classmethod
    def wait_until(cls, ready, seconds):
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            try:
                if ready():
                    return True
            except (OSError, http.client.HTTPException):
                pass
            time.sleep(0.2)
        return False

    @classmethod
    def cleanup(cls):
        cls.docker("rm", "-f", cls.proxy, cls.upstream, cls.auth, check=False)
        cls.docker("network", "rm", cls.network, check=False)
        cls.temp.cleanup()

    @classmethod
    def logs(cls, container):
        result = cls.docker("logs", container, check=False)
        return result.stdout + result.stderr

    @classmethod
    def request(cls, host, path, headers=None):
        connection = SniConnection(host, cls.public_port)
        try:
            connection.request("GET", path, headers={"Host": host, **(headers or {})})
            response = connection.getresponse()
            return response.status, response.headers, response.read().decode()
        finally:
            connection.close()


@unittest.skipUnless(shutil.which("docker"), "Docker required")
class CaddySsoTest(CaddySsoFixture, unittest.TestCase):
    @classmethod
    def start_auth(cls):
        cls.docker("run", "--rm", "-d", "--name", cls.auth, "--network", cls.network,
                   "--network-alias", "oauth2-proxy", "-v", cls.mount, CADDY_IMAGE,
                   "caddy", "run", "--adapter", "caddyfile", "--config", "/etc/caddy/FakeAuthfile")

    def test_authenticated_request_carries_the_github_login(self):
        for host in ("grafana.localhost", "prometheus.localhost"):
            status, headers, body = self.request(host, "/api/health", AUTHED)
            self.assertEqual((status, headers["X-Observed-User"], body), (200, "sso-user", "upstream:/api/health"))

    def test_forged_user_header_is_dropped_on_every_path(self):
        _, headers, _ = self.request("grafana.localhost", "/api/user", {**AUTHED, **FORGED})
        self.assertEqual(headers["X-Observed-User"], "sso-user")
        _, _, body = self.request("grafana.localhost", "/oauth2/callback?code=x", FORGED)
        self.assertEqual(body, "oauth2:/oauth2/callback?code=x|")
        status, _, body = self.request("grafana.localhost", "/api/user", FORGED)
        self.assertTrue(body.startswith("start:"), body)

    def test_login_keeps_the_full_original_url(self):
        _, _, body = self.request("grafana.localhost", "/d/abc?orgId=1&from=now-6h")
        self.assertEqual(body, "start:https://grafana.localhost/d/abc?orgId=1&from=now-6h")

    def test_main_site_still_skips_sso(self):
        status, _, body = self.request("localhost", "/")
        self.assertEqual((status, body), (200, "upstream:/"))


@unittest.skipUnless(shutil.which("docker"), "Docker required")
class RealOAuth2ProxyLoginTest(CaddySsoFixture, unittest.TestCase):
    @classmethod
    def start_auth(cls):
        cls.require_image(OAUTH2_PROXY_IMAGE)
        service = compose_config(SITE_DOMAIN="localhost",
                                 OAUTH2_PROXY_COOKIE_SECRET="cookie-secret-32-bytes-for-tests")["services"]["oauth2-proxy"]
        if service["image"] != OAUTH2_PROXY_IMAGE:
            raise AssertionError(f"compose uses {service['image']}, test pulls {OAUTH2_PROXY_IMAGE}")
        environment = [argument for name, value in service["environment"].items()
                       for argument in ("-e", f"{name}={value}")]
        cls.docker("run", "--rm", "-d", "--name", cls.auth, "--network", cls.network,
                   "--network-alias", "oauth2-proxy", *environment, OAUTH2_PROXY_IMAGE, *service["command"])

    @classmethod
    def auth_ready(cls):
        return cls.request("grafana.localhost", "/")[0] == 302

    def test_real_oauth2_proxy_puts_the_full_url_in_state(self):
        status, headers, _ = self.request("grafana.localhost", "/d/abc?orgId=1&from=now-6h")
        self.assertEqual(status, 302)
        location = urllib.parse.urlsplit(headers["Location"])
        query = urllib.parse.parse_qs(location.query)
        self.assertEqual((location.netloc, location.path), ("github.com", "/login/oauth/authorize"))
        self.assertEqual(query["redirect_uri"], ["https://grafana.localhost/oauth2/callback"])
        self.assertEqual(query["state"][0].split(":", 1)[1], "https://grafana.localhost/d/abc?orgId=1&from=now-6h")

    def test_prometheus_login_uses_the_grafana_callback(self):
        status, headers, _ = self.request("prometheus.localhost", "/query?g0.expr=up&g0.tab=graph")
        query = urllib.parse.parse_qs(urllib.parse.urlsplit(headers["Location"]).query)
        self.assertEqual(query["redirect_uri"], ["https://grafana.localhost/oauth2/callback"])
        self.assertEqual(query["state"][0].split(":", 1)[1], "https://prometheus.localhost/query?g0.expr=up&g0.tab=graph")
        self.assertIn("domain=localhost", headers["Set-Cookie"].lower())


def write_sso_file(repo, value=None, param_path="/meterengine/prod"):
    source = (DEPLOY / "deploy.sh").read_text()
    functions = repo / "functions.sh"
    functions.write_text(source[source.index("log() {"):source.rindex('\nmain "$@"')])
    (repo / "deploy/caddy").mkdir(parents=True, exist_ok=True)
    environment = {name: text for name, text in os.environ.items() if name != "CADDY_SSO_SITES"}
    environment["PARAM_PATH"] = param_path
    if value is not None:
        environment["CADDY_SSO_SITES"] = value
    return subprocess.run(
        ["bash", "-c", 'set -euo pipefail; source "$1"; REPO_DIR=$2; write_sso_file',
         "sso-test", str(functions), str(repo)],
        env=environment, capture_output=True, text=True, timeout=10)


class SsoSitesSwitchTest(unittest.TestCase):
    ON = "import sso-sites.caddy\n"
    OFF = "# CADDY_SSO_SITES=off\n"

    def setUp(self):
        temp = tempfile.TemporaryDirectory(prefix="meterengine-sso-switch-")
        self.addCleanup(temp.cleanup)
        self.repo = Path(temp.name)

    def written(self, **arguments):
        result = write_sso_file(self.repo, **arguments)
        self.assertEqual(result.returncode, 0, result.stderr)
        return (self.repo / "deploy/caddy/sso.caddy").read_text()

    def test_default_is_on_only_for_the_production_parameter_path(self):
        source = (DEPLOY / "deploy.sh").read_text()
        production = re.findall(r"^PARAM_PATH=(.+)$", source, re.M)[0]
        self.assertEqual(self.written(param_path=production), self.ON)
        for path in ("/meterengine/test-slo", "/meterengine/test-storage"):
            self.assertEqual(self.written(param_path=path), self.OFF, path)

    def test_deploy_conf_value_overrides_the_default(self):
        self.assertEqual(self.written(value="off", param_path="/meterengine/prod"), self.OFF)
        self.assertEqual(self.written(value="on", param_path="/meterengine/test-slo"), self.ON)

    def test_unknown_value_stops_the_deploy(self):
        for value in ("", "yes", "OFF"):
            result = write_sso_file(self.repo, value)
            self.assertEqual(result.returncode, 1, value)
            self.assertIn("CADDY_SSO_SITES", result.stderr)
            self.assertFalse((self.repo / "deploy/caddy/sso.caddy").exists(), value)

    @unittest.skipUnless(shutil.which("docker"), "Docker required")
    def test_switch_decides_whether_caddy_serves_sso_sites(self):
        CaddySsoFixture.require_image(CADDY_IMAGE)
        caddy = self.repo / "deploy/caddy"
        caddy.mkdir(parents=True)
        shutil.copyfile(SOURCE, caddy / "Caddyfile")
        shutil.copyfile(SSO_SITES, caddy / "sso-sites.caddy")
        (caddy / "upstreams.caddy").write_text("to backend-blue:8080\n")
        (caddy / "access-log.caddy").write_text("output discard\n")
        sso = {"grafana.localhost", "prometheus.localhost"}
        for value, expected in (("on", sso), ("off", set())):
            written = write_sso_file(self.repo, value)
            self.assertEqual(written.returncode, 0, written.stderr)
            adapted = CaddySsoFixture.docker(
                "run", "--rm", "--network", "none", "-v", f"{caddy}:/etc/caddy:ro",
                "-e", "SITE_DOMAIN=localhost", "-e", "SITE_ALIASES=", CADDY_IMAGE,
                "caddy", "adapt", "--config", "/etc/caddy/Caddyfile")
            servers = json.loads(adapted.stdout)["apps"]["http"]["servers"].values()
            hosts = {host for server in servers for route in server.get("routes", [])
                     for match in route.get("match", []) for host in match.get("host", [])}
            self.assertEqual(hosts & sso, expected, value)
            self.assertIn("localhost", hosts, value)


if __name__ == "__main__":
    unittest.main()
