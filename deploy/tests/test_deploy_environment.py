import os
from pathlib import Path
import re
import stat
import subprocess
import tempfile
import unittest


SOURCE = Path(__file__).resolve().parents[1] / "deploy.sh"
MEASUREMENTS = Path(__file__).resolve().parents[2] / "measurements"
SHA = "a" * 40
REQUIRED = "DB_HOST DB_PORT DB_NAME DB_USERNAME DB_PASSWORD CRYPTO_ENCRYPTION_KEY OPERATOR_EMAIL OPERATOR_PASSWORD"
MONITORING_REQUIRED = "DB_EXPORTER_PASSWORD GRAFANA_ADMIN_PASSWORD SLACK_WEBHOOK_URL OAUTH2_PROXY_CLIENT_ID OAUTH2_PROXY_CLIENT_SECRET OAUTH2_PROXY_COOKIE_SECRET"
BASE_PARAMETERS = {"db-host": "db.example.invalid", "db-port": "5432", "db-name": "app", "db-username": "owner", "db-password": "secret",
                   "crypto-encryption-key": "key", "operator-email": "a@example.invalid", "operator-password": "secret"}
MONITORING_PARAMETERS = {"db-exporter-password": "secret", "grafana-admin-password": "secret", "slack-webhook-url": "https://hook.invalid",
                         "oauth2-proxy-client-id": "id", "oauth2-proxy-client-secret": "secret", "oauth2-proxy-cookie-secret": "secret"}

FAKE_DOCKER = r'''#!/usr/bin/env bash
printf 'docker %s\n' "$*" >>"$TRACE"
case "$1" in
# 표준 입력을 읽지 않으면 pipefail 아래에서 보내는 쪽이 SIGPIPE로 실패한다.
login) cat >/dev/null; exit 0 ;;
image) exit 0 ;;
inspect) printf '928618307647.dkr.ecr.ap-northeast-2.amazonaws.com/%s-backend:%s\n' "${RUNNING_IMAGE_PREFIX:-meterengine}" "$SHA"; exit 0 ;;
ps)
    case "$*" in
    *service=caddy*) echo caddy1 ;;
    *service=backend-blue*) [ -f "$ROOT/blue-up" ] && echo blue1 ;;
    esac
    exit 0
    ;;
exec)
    url=${!#}
    case "$url" in
    http://localhost:8081/*) printf '  X-Upstream: backend-blue:8080\n' >&2 ;;
    *) printf '{"status":"UP"}\n' ;;
    esac
    exit 0
    ;;
compose)
    case "$*" in
    *"config --services"*) printf 'caddy\nfrontend\nbackend-blue\nbackend-green\n' ;;
    *"up -d backend-blue"*) touch "$ROOT/blue-up" ;;
    esac
    exit 0
    ;;
esac
exit 0
'''
FAKE_AWS = r'''#!/usr/bin/env bash
printf 'aws %s\n' "$*" >>"$TRACE"
case "$*" in
*get-parameters-by-path*) cat "$ROOT/parameters" ;;
*get-login-password*) echo password ;;
esac
'''
FAKE_CURL = r'''#!/usr/bin/env bash
printf 'curl %s\n' "$*" >>"$TRACE"
case "$*" in
*"-K -"*) cat >/dev/null ;;
*3000/health*) echo ok ;;
*3001/api/health*) echo '{"database":"ok"}' ;;
esac
'''
FAKE_GIT = r'''#!/usr/bin/env bash
printf 'git %s\n' "$*" >>"$TRACE"
[ "$1" != rev-parse ] || echo abc1234
'''


class DeployEnvironmentTest(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory(prefix="meterengine-deploy-")
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        self.repo = self.root / "repo"
        (self.repo / "deploy/caddy").mkdir(parents=True)
        self.env_file = self.root / "prod.env"
        self.trace = self.root / "trace"
        self.trace.touch()
        source = SOURCE.read_text()
        for name, value in (("REPO_DIR", self.repo), ("ENV_FILE", self.env_file), ("CONFIG_FILE", self.root / "deploy.conf"),
                            ("STATE_FILE", self.root / "deploy-state"), ("LOCK_FILE", self.root / "deploy-command.lock"), ("DRAIN_SECONDS", 0)):
            source = re.sub(rf"^{name}=.*$", f"{name}={value}", source, flags=re.M)
        self.script = self.repo / "deploy/deploy.sh"
        self.script.write_text(source)
        self.script.chmod(self.script.stat().st_mode | stat.S_IEXEC)
        bin_dir = self.root / "bin"
        bin_dir.mkdir()
        for name, body in (("docker", FAKE_DOCKER), ("aws", FAKE_AWS), ("curl", FAKE_CURL), ("git", FAKE_GIT),
                           ("flock", "#!/bin/sh\nexit 0\n"), ("sleep", "#!/bin/sh\nexit 0\n")):
            path = bin_dir / name
            path.write_text(body)
            path.chmod(path.stat().st_mode | stat.S_IEXEC)
        self.env = dict(os.environ, PATH=f"{bin_dir}{os.pathsep}{os.environ['PATH']}", TRACE=str(self.trace), ROOT=str(self.root), SHA=SHA)
        self.set_parameters(BASE_PARAMETERS)
        self.set_preflight()

    def set_parameters(self, parameters):
        (self.root / "parameters").write_text("".join(f"/meterengine/test/{name}\t{value}\n" for name, value in parameters.items()))

    def set_preflight(self, profiles="agents", required=REQUIRED, exit_code=0, expected_db_identifier="db", image_prefix="meterengine"):
        values = {"PARAM_PATH": "/meterengine/test", "SITE_DOMAIN": "test.example.invalid", "SITE_ALIASES": "", "COMPOSE_PROFILES": profiles,
                  "CADDY_SSO_SITES": "off", "BACKEND_MEM_LIMIT": "2g", "BACKEND_MAX_RAM_PERCENTAGE": "70",
                  "EXPECTED_DB_IDENTIFIER": expected_db_identifier, "REQUIRED_PARAMETERS": required, "IMAGE_REPOSITORY_PREFIX": image_prefix}
        lines = "\n".join(f"print({f'{key}={chr(39)}{value}{chr(39)}'!r})" for key, value in values.items())
        body = f"import sys\nprint('stop', file=sys.stderr)\nsys.exit({exit_code})\n" if exit_code else lines + "\n"
        (self.repo / "deploy/environment.py").write_text(body)

    def deploy(self, *arguments):
        return subprocess.run(["bash", str(self.script), *arguments], env=self.env, text=True, capture_output=True, timeout=30)

    def traced(self):
        return self.trace.read_text()

    def assert_nothing_written(self):
        self.assertFalse(self.env_file.exists())
        self.assertFalse((self.repo / "deploy/caddy/upstreams.caddy").exists())
        self.assertFalse((self.repo / "deploy/caddy/sso.caddy").exists())
        self.assertNotIn("git ", self.traced())
        self.assertNotIn("docker compose", self.traced())

    def test_missing_environment_stops_before_any_write(self):
        for arguments in ((), (SHA,), ("staging",), ("staging", "not-a-sha")):
            result = self.deploy(*arguments)
            self.assertNotEqual(result.returncode, 0, arguments)
            self.assertIn("사용법", result.stderr, arguments)
            self.assert_nothing_written()

    def test_preflight_failure_stops_before_any_write(self):
        self.set_preflight(exit_code=2)
        result = self.deploy("staging", SHA)
        self.assertNotEqual(result.returncode, 0)
        self.assert_nothing_written()

    def test_missing_required_parameter_stops_before_compose(self):
        self.set_preflight(profiles="agents,monitoring", required=f"{REQUIRED} {MONITORING_REQUIRED}")
        self.set_parameters({**BASE_PARAMETERS, **MONITORING_PARAMETERS, "grafana-admin-password": ""})
        self.env_file.write_text("IMAGE_TAG=previous\n")
        result = self.deploy("production", SHA)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("GRAFANA_ADMIN_PASSWORD", result.stderr)
        self.assertNotIn("docker compose", self.traced())
        self.assertEqual(self.env_file.read_text(), "IMAGE_TAG=previous\n")

    def test_log_shows_only_the_identifier(self):
        result = self.deploy("staging", SHA)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("DB: db\n", result.stdout + result.stderr)
        self.assertNotIn("db.example.invalid", result.stdout + result.stderr)

    def test_other_db_host_stops_before_compose(self):
        self.set_preflight(expected_db_identifier="other")
        result = self.deploy("staging", SHA)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("other", result.stderr)
        self.assertNotIn("docker compose", self.traced())

    def test_without_monitoring_skips_monitoring_steps(self):
        result = self.deploy("staging", SHA)
        self.assertEqual(result.returncode, 0, result.stderr)
        for word in ("prometheus", "cloudwatch-exporter", "3001"):
            self.assertNotIn(word, self.traced())
        self.assertIn("COMPOSE_PROFILES=agents\n", self.env_file.read_text())
        self.assertIn("BACKEND_MEM_LIMIT=2g\n", self.env_file.read_text())

    def test_with_monitoring_runs_monitoring_steps(self):
        self.set_preflight(profiles="agents,monitoring", required=f"{REQUIRED} {MONITORING_REQUIRED}")
        self.set_parameters({**BASE_PARAMETERS, **MONITORING_PARAMETERS})
        result = self.deploy("production", SHA)
        self.assertEqual(result.returncode, 0, result.stderr)
        for word in ("exec -T prometheus kill -HUP 1", "exec -T cloudwatch-exporter kill -HUP 1", "3001/api/health", "provisioning/alerting/reload"):
            self.assertIn(word, self.traced())

    def test_image_prefix_reaches_env_file_and_image_check(self):
        self.set_preflight(image_prefix="meterengine-staging")
        self.env["RUNNING_IMAGE_PREFIX"] = "meterengine-staging"
        result = self.deploy("staging", SHA)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("IMAGE_REPOSITORY_PREFIX=meterengine-staging\n", self.env_file.read_text())

    def test_image_from_other_repository_stops_deploy(self):
        self.set_preflight(image_prefix="meterengine-staging")
        result = self.deploy("staging", SHA)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("meterengine-staging-backend", result.stderr)

    def test_preflight_values_override_deploy_conf(self):
        (self.root / "deploy.conf").write_text("PARAM_PATH=/meterengine/prod\nBACKEND_MEM_LIMIT=768m\nCADDY_SSO_SITES=on\n")
        result = self.deploy("staging", SHA)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("--path /meterengine/test ", self.traced())
        self.assertIn("BACKEND_MEM_LIMIT=2g\n", self.env_file.read_text())
        self.assertEqual((self.repo / "deploy/caddy/sso.caddy").read_text(), "# CADDY_SSO_SITES=off\n")


class SutDeployTest(unittest.TestCase):
    SUTS = ("test-sjpark", "test-storage")

    def setUp(self):
        temp = tempfile.TemporaryDirectory(prefix="meterengine-sut-")
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        self.repo = self.root / "repo"
        (self.repo / "deploy").mkdir(parents=True)
        self.trace = self.root / "trace"
        self.trace.touch()
        bin_dir = self.root / "bin"
        bin_dir.mkdir()
        fake_git = '''#!/bin/sh
printf 'git %s\\n' "$*" >>"$TRACE"
case "$1" in cat-file) exit "$HAS_ENVIRONMENT_FILE" ;; esac
'''
        for path, body in ((bin_dir / "git", fake_git), (bin_dir / "flock", "#!/bin/sh\nexit 0\n"),
                           (self.repo / "deploy/deploy.sh", '#!/bin/sh\nprintf \'deploy %s\\n\' "$*" >>"$TRACE"\n')):
            path.write_text(body)
            path.chmod(path.stat().st_mode | stat.S_IEXEC)
        self.config = self.root / "deploy.conf"
        self.env = dict(os.environ, PATH=f"{bin_dir}{os.pathsep}{os.environ['PATH']}", TRACE=str(self.trace))

    def run_command(self, sut, has_environment_file):
        self.trace.write_text("")
        source = (MEASUREMENTS / sut / "sut/scripts/sut-deploy.sh").read_text()
        function = re.search(r"^deploy_command\(\) \{.*?^\}", source, re.M | re.S).group()
        generate = (f"REMOTE_DEPLOY_CONFIG={self.config}\nREMOTE_REPO_DIR=/opt/meterengine\nFIRST_SHA_WITH_DEPLOY_CONF={'c' * 40}\n"
                    f"{function}\ndeploy_command {SHA} /meterengine/{sut} {sut}.meterengine.com\n")
        command = subprocess.run(["bash", "-c", generate], text=True, capture_output=True, check=True).stdout
        command = command.replace("/etc/meterengine/deploy-command.lock", str(self.root / "lock")).replace("/opt/meterengine", str(self.repo))
        return subprocess.run(["sh", "-c", command], env={**self.env, "HAS_ENVIRONMENT_FILE": "0" if has_environment_file else "1"},
                              text=True, capture_output=True, timeout=10)

    def test_sut_deploy_passes_environment(self):
        for sut in self.SUTS:
            result = self.run_command(sut, has_environment_file=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn(f"deploy {sut} {SHA}\n", self.trace.read_text())

    def test_sut_old_commit_uses_old_call(self):
        for sut in self.SUTS:
            self.config.write_text(f"PARAM_PATH=/meterengine/{sut}\nSITE_DOMAIN={sut}.meterengine.com\n")
            result = self.run_command(sut, has_environment_file=False)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn(f"deploy {SHA}\n", self.trace.read_text())

    def test_sut_old_commit_needs_sut_deploy_conf(self):
        for sut in self.SUTS:
            self.config.write_text("")
            result = self.run_command(sut, has_environment_file=False)
            self.assertNotEqual(result.returncode, 0)
            self.assertNotIn("deploy ", self.trace.read_text().replace("deploy-", ""))
            self.assertNotIn("git checkout", self.trace.read_text())

    def test_sut_db_check_compares_identifiers(self):
        source = (MEASUREMENTS / "test-sjpark/sut/scripts/sut-deploy.sh").read_text()
        functions = re.search(r"^deployed_db_host\(\) \{.*?^require_sut_db\(\) \{.*?^\}", source, re.M | re.S).group()
        script = f"die() {{ echo \"$1\" >&2; exit 1; }}\nlog() {{ :; }}\nDEPLOY_DB_LINE_PREFIX='[deploy] DB: '\n{functions}\nrequire_sut_db \"$1\" sut-db.abc.example.invalid\n"
        for line, code in (("sut-db", 0), ("sut-db.abc.example.invalid", 0), ("other-db", 1)):
            output = self.root / "deploy-output"
            output.write_text(f"[deploy] DB: {line}\n")
            result = subprocess.run(["bash", "-c", script, "bash", str(output)], text=True, capture_output=True)
            self.assertEqual(result.returncode, code, (line, result.stderr))

    def test_sut_cannot_read_either_production_path(self):
        for sut in self.SUTS:
            iam = (MEASUREMENTS / sut / "sut/terraform/iam.tf").read_text()
            locals_file = (MEASUREMENTS / sut / "sut/terraform/locals.tf").read_text()
            for name, path in (("dev_param_path", "/meterengine/prod"), ("production_param_path", "/meterengine/production")):
                self.assertIn(f'{name}', locals_file)
                self.assertRegex(locals_file, rf'{name}\s*=\s*"{path}"')
                self.assertIn(f"parameter${{local.{name}}}\"", iam)
                self.assertIn(f"parameter${{local.{name}}}/*\"", iam)
            for relative in ("lib/common.sh", "sut/scripts/remote/upsert-exporter-user.sh"):
                check = (MEASUREMENTS / sut / relative).read_text()
                self.assertIn("readonly PRODUCTION_PARAM_PATH=/meterengine/production", check)
                self.assertIn('"$PRODUCTION_PARAM_PATH" | "$PRODUCTION_PARAM_PATH"/*', check)


if __name__ == "__main__":
    unittest.main()
