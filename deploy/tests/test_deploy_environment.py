import json
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
BASE_PARAMETERS = {"db-host": "db.example.invalid", "db-port": "5432", "db-name": "app", "db-username": "owner", "db-password": "secret",
                   "crypto-encryption-key": "key", "operator-email": "a@example.invalid", "operator-password": "secret"}

FAKE_DOCKER = r'''#!/usr/bin/env bash
printf 'docker %s\n' "$*" >>"$TRACE"
case "$1" in
# 표준 입력을 읽지 않으면 pipefail 아래에서 보내는 쪽이 SIGPIPE로 실패한다.
login) cat >/dev/null; exit 0 ;;
inspect) printf '928618307647.dkr.ecr.ap-northeast-2.amazonaws.com/%s-backend:%s\n' "${RUNNING_IMAGE_PREFIX:-meterengine}" "$SHA"; exit 0 ;;
ps) echo backend1; exit 0 ;;
compose)
    # 서비스 이름 없이 부르는 up -d는 감시 에이전트까지 올린다.
    case "$*" in *"up -d") exit "${AGENTS_EXIT:-0}" ;; esac
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
*8080/actuator/health*) [ -z "$BACKEND_DOWN" ] || exit 7; echo '{"status":"UP"}' ;;
*3000/health*) echo ok ;;
esac
'''
FAKE_GIT = r'''#!/usr/bin/env bash
printf 'git %s\n' "$*" >>"$TRACE"
[ "$1" != rev-parse ] || echo abc1234
'''
FAKE_SYSTEMCTL = r'''#!/usr/bin/env bash
printf 'systemctl %s\n' "$*" >>"$TRACE"
exit "${SYSTEMCTL_EXIT:-0}"
'''


class DeployEnvironmentTest(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory(prefix="meterengine-deploy-")
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        self.repo = self.root / "repo"
        (self.repo / "deploy/server").mkdir(parents=True)
        (self.repo / "deploy/server/ingest-rate.service").write_text("[Unit]\n")
        self.env_file = self.root / "prod.env"
        self.unit_file = self.root / "meterengine-ingest-rate.service"
        self.trace = self.root / "trace"
        self.trace.touch()
        source = SOURCE.read_text()
        for name, value in (("REPO_DIR", self.repo), ("ENV_FILE", self.env_file), ("UNIT_FILE", self.unit_file),
                            ("LOCK_FILE", self.root / "deploy-command.lock")):
            source = re.sub(rf"^{name}=.*$", f"{name}={value}", source, flags=re.M)
        self.script = self.repo / "deploy/deploy.sh"
        self.script.write_text(source)
        self.script.chmod(self.script.stat().st_mode | stat.S_IEXEC)
        bin_dir = self.root / "bin"
        bin_dir.mkdir()
        for name, body in (("docker", FAKE_DOCKER), ("aws", FAKE_AWS), ("curl", FAKE_CURL), ("git", FAKE_GIT), ("systemctl", FAKE_SYSTEMCTL),
                           ("flock", "#!/bin/sh\nexit 0\n"), ("sleep", "#!/bin/sh\nexit 0\n")):
            path = bin_dir / name
            path.write_text(body)
            path.chmod(path.stat().st_mode | stat.S_IEXEC)
        self.env = dict(os.environ, PATH=f"{bin_dir}{os.pathsep}{os.environ['PATH']}", TRACE=str(self.trace), ROOT=str(self.root), SHA=SHA)
        self.set_parameters(BASE_PARAMETERS)
        self.set_preflight()

    def set_parameters(self, parameters):
        (self.root / "parameters").write_text("".join(f"/meterengine/test/{name}\t{value}\n" for name, value in parameters.items()))

    def set_preflight(self, profiles="agents", exit_code=0, expected_db_identifier="db", image_prefix="meterengine"):
        values = {"PARAM_PATH": "/meterengine/test", "SITE_DOMAIN": "test.example.invalid", "SITE_ALIASES": "", "COMPOSE_PROFILES": profiles,
                  "BACKEND_MEM_LIMIT": "2g", "BACKEND_MAX_RAM_PERCENTAGE": "70",
                  "EXPECTED_DB_IDENTIFIER": expected_db_identifier, "REQUIRED_PARAMETERS": REQUIRED, "IMAGE_REPOSITORY_PREFIX": image_prefix}
        lines = "\n".join(f"print({f'{key}={chr(39)}{value}{chr(39)}'!r})" for key, value in values.items())
        body = f"import sys\nprint('stop', file=sys.stderr)\nsys.exit({exit_code})\n" if exit_code else lines + "\n"
        (self.repo / "deploy/environment.py").write_text(body)

    def deploy(self, *arguments, **env):
        return subprocess.run(["bash", str(self.script), *arguments], env={**self.env, **env}, text=True, capture_output=True, timeout=30)

    def traced(self):
        return self.trace.read_text()

    def trace_index(self, text):
        return self.traced().index(text)

    def assert_nothing_written(self):
        self.assertFalse(self.env_file.exists())
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
        self.set_parameters({**BASE_PARAMETERS, "operator-password": ""})
        self.env_file.write_text("IMAGE_TAG=previous\n")
        result = self.deploy("production", SHA)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("OPERATOR_PASSWORD", result.stderr)
        self.assertNotIn("docker compose", self.traced())
        self.assertEqual(self.env_file.read_text(), "IMAGE_TAG=previous\n")

    def test_env_file_has_one_image_tag(self):
        result = self.deploy("staging", SHA)
        self.assertEqual(result.returncode, 0, result.stderr)
        content = self.env_file.read_text()
        self.assertIn(f"IMAGE_TAG={SHA}\n", content)
        self.assertIn("COMPOSE_PROFILES=agents\n", content)
        self.assertIn("BACKEND_MEM_LIMIT=2g\n", content)
        self.assertNotIn("BLUE", content)

    def test_optional_warmup_parameters_reach_env_file(self):
        self.set_parameters({**BASE_PARAMETERS, "ingest-warmup-api-key": "me_live_warmup",
                             "ingest-warmup-organization-id": "org", "ingest-warmup-customer-id": "customer"})
        result = self.deploy("staging", SHA)
        self.assertEqual(result.returncode, 0, result.stderr)
        content = self.env_file.read_text()
        for line in ("INGEST_WARMUP_API_KEY=me_live_warmup\n", "INGEST_WARMUP_ORGANIZATION_ID=org\n", "INGEST_WARMUP_CUSTOMER_ID=customer\n"):
            self.assertIn(line, content)

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

    def test_app_starts_before_the_agents(self):
        result = self.deploy("staging", SHA)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("pull --quiet backend frontend\n", self.traced())
        app = self.trace_index("up -d --remove-orphans backend frontend\n")
        self.assertLess(app, self.trace_index("8080/actuator/health"))
        self.assertLess(self.trace_index("3000/health"), self.trace_index("compose.prod.yml up -d\n"))

    def test_agent_failure_keeps_the_app(self):
        result = self.deploy("staging", SHA, AGENTS_EXIT="1")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("감시 에이전트", result.stdout)

    def test_without_profiles_skips_agents(self):
        self.set_preflight(profiles="")
        result = self.deploy("staging", SHA)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertNotIn("compose.prod.yml up -d\n", self.traced())

    def test_backend_that_never_gets_ready_fails_before_the_unit(self):
        result = self.deploy("staging", SHA, BACKEND_DOWN="1")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("180초", result.stderr)
        self.assertIn("logs --tail 50 backend", self.traced())
        self.assertNotIn("systemctl", self.traced())

    def test_ingest_rate_unit_is_installed_and_restarted_after_checkout(self):
        result = self.deploy("staging", SHA)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.unit_file.read_text(), "[Unit]\n")
        for command in ("systemctl daemon-reload", "systemctl enable --quiet meterengine-ingest-rate.service",
                        "systemctl restart meterengine-ingest-rate.service"):
            self.assertIn(command + "\n", self.traced())
        self.assertLess(self.trace_index("git checkout"), self.trace_index("systemctl restart"))

    def test_unit_failure_keeps_the_app(self):
        result = self.deploy("staging", SHA, SYSTEMCTL_EXIT="1")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("수집량 지표", result.stdout)

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


class SutDeployTest(unittest.TestCase):
    SUTS = ("test-sjpark", "test-storage")
    # test-storage는 서버에 SSM 명령을 보내지 않고 rollout.sh로 배포한다.
    SSM_DEPLOY_SUTS = ("test-sjpark",)
    SERVER_PROFILES = '"compose_profiles": ["agents", "monitoring"]'
    SCRAPE_PORTS = 'deploy/compose.prod.yml "9100:9100"'

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
        # deploy.sh가 돌 때의 환경 파일을 남겨 덮어쓰기가 그보다 먼저인지 본다.
        fake_deploy = '#!/bin/sh\nprintf \'deploy %s\\n\' "$*" >>"$TRACE"\n[ ! -f "deploy/environments/$1.json" ] || cat "deploy/environments/$1.json" >>"$TRACE"\n'
        for path, body in ((bin_dir / "git", fake_git), (bin_dir / "flock", "#!/bin/sh\nexit 0\n"),
                           (bin_dir / "docker", '#!/bin/sh\nprintf \'docker %s\\n\' "$*" >>"$TRACE"\n'),
                           (self.repo / "deploy/deploy.sh", fake_deploy)):
            path.write_text(body)
            path.chmod(path.stat().st_mode | stat.S_IEXEC)
        (self.repo / "deploy/environments").mkdir()
        for sut in self.SUTS:
            (self.repo / f"deploy/environments/{sut}.json").write_text(f'{{"site_domain": "{sut}.meterengine.com", {self.SERVER_PROFILES}}}\n')
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
        for sut in self.SSM_DEPLOY_SUTS:
            result = self.run_command(sut, has_environment_file=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn(f"deploy {sut} {SHA}\n", self.trace.read_text())

    def test_sut_old_commit_uses_old_call(self):
        for sut in self.SSM_DEPLOY_SUTS:
            self.config.write_text(f"PARAM_PATH=/meterengine/{sut}\nSITE_DOMAIN={sut}.meterengine.com\n")
            result = self.run_command(sut, has_environment_file=False)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn(f"deploy {SHA}\n", self.trace.read_text())

    def test_test_sjpark_deploy_turns_off_server_monitoring_before_deploy_sh(self):
        result = self.run_command("test-sjpark", has_environment_file=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        seen_by_deploy = self.trace.read_text().split(f"deploy test-sjpark {SHA}\n")[1]
        self.assertEqual(json.loads(seen_by_deploy), {"site_domain": "test-sjpark.meterengine.com", "compose_profiles": ["agents"]})

    def test_test_sjpark_old_commit_keeps_the_environment_file(self):
        self.config.write_text("PARAM_PATH=/meterengine/test-sjpark\nSITE_DOMAIN=test-sjpark.meterengine.com\n")
        result = self.run_command("test-sjpark", has_environment_file=False)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn(self.SERVER_PROFILES, (self.repo / "deploy/environments/test-sjpark.json").read_text())

    def test_sut_old_commit_needs_sut_deploy_conf(self):
        for sut in self.SSM_DEPLOY_SUTS:
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

    def test_monitoring_cleanup_holds_the_lock_and_keeps_volumes(self):
        source = (MEASUREMENTS / "test-sjpark/sut/scripts/sut-deploy.sh").read_text()
        constants = "\n".join(re.findall(r"^readonly (?:[A-Z_]+)=.*$", source, re.M))
        function = re.search(r"^monitoring_cleanup_command\(\) \{.*?^\}", source, re.M | re.S).group()
        command = subprocess.run(["bash", "-c", f"{constants}\n{function}\nmonitoring_cleanup_command\n"],
                                 text=True, capture_output=True, check=True).stdout
        self.assertTrue(command.startswith("exec 8>/etc/meterengine/deploy-command.lock"))
        command = command.replace("/etc/meterengine/deploy-command.lock", str(self.root / "lock")).replace("/opt/meterengine", str(self.repo))
        result = subprocess.run(["sh", "-c", command], env=self.env, text=True, capture_output=True, timeout=10)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.trace.read_text(),
                         "docker compose --env-file /etc/meterengine/prod.env -f deploy/compose.prod.yml --profile monitoring "
                         "rm --stop --force prometheus grafana oauth2-proxy postgres-exporter cloudwatch-exporter\n")

    def test_monitoring_cleanup_runs_only_after_the_deploy_is_confirmed(self):
        main = re.search(r"^main\(\) \{.*?^\}", (MEASUREMENTS / "test-sjpark/sut/scripts/sut-deploy.sh").read_text(), re.M | re.S).group()
        self.assertLess(main.index('require_sut_db "$deploy_output_file" "$db_address"\n'), main.index("$(monitoring_cleanup_command)"))

    def run_commit_selection(self, call, commits):
        """commits: SHA마다 그 트리에 있는 경로 목록. ECR에는 두 저장소 모두 이 순서(최신부터)로 있다."""
        source = (MEASUREMENTS / "test-sjpark/sut/scripts/sut-deploy.sh").read_text()
        constants = "\n".join(re.findall(r"^readonly (?:[A-Z_]+)=.*$", source, re.M))
        functions = "\n".join(re.search(rf"^{name}\(\) \{{.*?^\}}", source, re.M | re.S).group()
                              for name in ("ecr_sha_tags_newest_first", "commit_problem", "require_deployable_commit", "latest_deployable_sha"))
        script = (f"set -euo pipefail\nSCRIPT_DIR=/operator\n{constants}\n"
                  "log() { printf '[sut-deploy] %s\\n' \"$*\"; }\ndie() { echo \"$*\" >&2; exit 1; }\n"
                  f"{functions}\n{call}\n")
        tree = self.root / "tree"
        tree.write_text("".join(f"{sha}:{path}\n" for sha, paths in commits.items() for path in paths)
                        + "".join(f"{sha}^{{commit}}\n" for sha in commits))
        fake_git = self.root / "bin/git"
        fake_git.write_text('#!/bin/sh\nprintf \'git %s\\n\' "$*" >>"$TRACE"\ncase "$3" in\n'
                            'cat-file) grep -Fxq -- "$5" "$TREE" ;;\n'
                            'grep) grep -Fxq -- "$8:${10} $7" "$TREE" ;;\n'
                            'esac\n')
        fake_aws = self.root / "bin/aws"
        fake_aws.write_text('#!/bin/sh\nprintf \'aws %s\\n\' "$*" >>"$TRACE"\nprintf \'%s\\n\' $COMMITS\n')
        for path in (fake_git, fake_aws):
            path.chmod(path.stat().st_mode | stat.S_IEXEC)
        self.trace.write_text("")
        return subprocess.run(["bash", "-c", script], env={**self.env, "TREE": str(tree), "COMMITS": " ".join(commits)},
                              text=True, capture_output=True, timeout=10)

    def test_selection_skips_commits_without_caddy_or_environment_file(self):
        deployable = ["deploy/caddy/Caddyfile", "deploy/environments/test-sjpark.json", self.SCRAPE_PORTS]
        commits = {"1" * 40: ["deploy/environments/test-sjpark.json", self.SCRAPE_PORTS], "2" * 40: ["deploy/caddy/Caddyfile", self.SCRAPE_PORTS],
                   "3" * 40: deployable, "4" * 40: deployable}
        result = self.run_commit_selection("latest_deployable_sha", commits)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout, "3" * 40)
        self.assertIn(f"{'1' * 40} 건너뜀: Caddy가 빠진 커밋", result.stderr)
        self.assertIn(f"{'2' * 40} 건너뜀: test-sjpark 환경 파일이 없는 커밋", result.stderr)

    def test_selection_skips_commits_without_scrape_ports(self):
        caddy_and_environment = ["deploy/caddy/Caddyfile", "deploy/environments/test-sjpark.json"]
        commits = {"1" * 40: caddy_and_environment, "2" * 40: [*caddy_and_environment, self.SCRAPE_PORTS]}
        result = self.run_commit_selection("latest_deployable_sha", commits)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout, "2" * 40)
        self.assertIn(f"{'1' * 40} 건너뜀: 공용 감시가 긁을 포트가 없는 커밋이다", result.stderr)

    def test_given_sha_without_scrape_ports_is_rejected(self):
        result = self.run_commit_selection(f"require_deployable_commit {'1' * 40}",
                                           {"1" * 40: ["deploy/caddy/Caddyfile", "deploy/environments/test-sjpark.json"]})
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("공용 감시가 긁을 포트가 없는 커밋이다", result.stderr)

    def test_selection_without_any_caddy_commit_points_to_the_kept_images(self):
        result = self.run_commit_selection("latest_deployable_sha", {"1" * 40: ["deploy/environments/test-sjpark.json"]})
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("보존 이미지", result.stderr)

    def test_given_sha_without_caddy_is_rejected(self):
        result = self.run_commit_selection(f"require_deployable_commit {'1' * 40}", {"1" * 40: ["deploy/environments/test-sjpark.json"]})
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Caddy가 빠진 커밋이라 test-sjpark에 배포하지 않는다", result.stderr)

    def test_given_sha_without_environment_file_is_rejected(self):
        result = self.run_commit_selection(f"require_deployable_commit {'1' * 40}", {"1" * 40: ["deploy/caddy/Caddyfile"]})
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("test-sjpark 환경 파일이 없는 커밋이다", result.stderr)

    def test_given_sha_is_checked_before_any_remote_command(self):
        main = re.search(r"^main\(\) \{.*?^\}", (MEASUREMENTS / "test-sjpark/sut/scripts/sut-deploy.sh").read_text(), re.M | re.S).group()
        self.assertLess(main.index("fetch_operator_checkout"), main.index("latest_deployable_sha"))
        self.assertLess(main.index('require_deployable_commit "$sha"'), main.index("run_remote_command"))
        self.assertLess(main.index('require_deployable_commit "$sha"'), main.index("wait_ssm_online"))

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


class StorageSutDeployTest(unittest.TestCase):
    SOURCE = MEASUREMENTS / "test-storage/sut/scripts/sut-deploy.sh"

    def setUp(self):
        temp = tempfile.TemporaryDirectory(prefix="meterengine-storage-")
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        self.trace = self.root / "trace"
        bin_dir = self.root / "bin"
        bin_dir.mkdir()
        fake_git = bin_dir / "git"
        fake_git.write_text('#!/bin/sh\n[ "$3" = cat-file ] || exit 0\ngrep -Fxq -- "$5" "$TREE"\n')
        fake_rollout = self.root / "rollout.sh"
        fake_rollout.write_text('#!/bin/sh\nprintf \'rollout %s\\n\' "$*" >>"$TRACE"\n')
        for path in (fake_git, fake_rollout):
            path.chmod(path.stat().st_mode | stat.S_IEXEC)
        self.env = dict(os.environ, PATH=f"{bin_dir}{os.pathsep}{os.environ['PATH']}", TRACE=str(self.trace))

    def run_functions(self, call, tree=()):
        source = self.SOURCE.read_text()
        functions = "\n".join(re.search(rf"^{name}\(\) \{{.*?^\}}", source, re.M | re.S).group()
                              for name in ("commit_problem", "require_deployable_commit", "rollout"))
        constant = re.search(r"^readonly ROLLOUT_PATH=.*$", source, re.M).group()
        script = (f"set -euo pipefail\nREPO_ROOT=/operator\nROLLOUT_SCRIPT={self.root / 'rollout.sh'}\n{constant}\n"
                  "die() { echo \"$*\" >&2; exit 1; }\nrequire_shell_safe() { :; }\nrequire_not_dev_server_path() { :; }\n"
                  "tf_out() { case \"$1\" in asg_name) echo meterengine-test-storage-sut ;; app_version_parameter_name) echo /meterengine/test-storage/app-version ;; esac; }\n"
                  f"{functions}\n{call}\n")
        tree_file = self.root / "tree"
        tree_file.write_text("".join(f"{line}\n" for line in tree))
        return subprocess.run(["bash", "-c", script], env={**self.env, "TREE": str(tree_file)}, text=True, capture_output=True, timeout=10)

    def test_rollout_gets_group_parameter_and_sha_in_cd_order(self):
        result = self.run_functions(f"rollout {SHA}")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.trace.read_text(), f"rollout meterengine-test-storage-sut /meterengine/test-storage/app-version {SHA}\n")

    def test_commit_without_rollout_script_is_rejected(self):
        result = self.run_functions(f"require_deployable_commit {SHA}", tree=[f"{SHA}^{{commit}}"])
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("deploy/rollout.sh 가 없는 옛 커밋", result.stderr)

    def test_commit_with_rollout_script_is_accepted(self):
        result = self.run_functions(f"require_deployable_commit {SHA}", tree=[f"{SHA}^{{commit}}", f"{SHA}:deploy/rollout.sh"])
        self.assertEqual(result.returncode, 0, result.stderr)


if __name__ == "__main__":
    unittest.main()
