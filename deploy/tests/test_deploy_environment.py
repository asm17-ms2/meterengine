import json
import os
from pathlib import Path
import re
import shutil
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
        (self.repo / "deploy/server/cpu-metric.service").write_text("[Unit]\n")
        self.env_file = self.root / "prod.env"
        self.unit_file = self.root / "meterengine-cpu-metric.service"
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

    def test_cpu_metric_unit_is_installed_and_restarted_after_checkout(self):
        result = self.deploy("staging", SHA)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.unit_file.read_text(), "[Unit]\n")
        for command in ("systemctl daemon-reload", "systemctl enable --quiet meterengine-cpu-metric.service",
                        "systemctl restart meterengine-cpu-metric.service"):
            self.assertIn(command + "\n", self.traced())
        self.assertLess(self.trace_index("git checkout"), self.trace_index("systemctl restart"))

    def test_unit_failure_keeps_the_app(self):
        result = self.deploy("staging", SHA, SYSTEMCTL_EXIT="1")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("CPU 지표 유닛을 띄우지 못했다", result.stdout)

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
    SUTS = ("test-sjpark-m7g", "test-sjpark-t4g", "test-storage")
    SPEC_SCRIPTS = ("sut-deploy.sh", "sut-start.sh", "sut-stop.sh", "create-exporter-user.sh", "sut-instances.sh")
    FAKE_TERRAFORM = r"""#!/bin/sh
printf 'terraform %s\n' "$*" >>"$TRACE"
name=$(basename "${1#-chdir=}")
case "$4" in
asg_name) echo "asg-$name" ;;
app_version_parameter_name) echo "/meterengine/$name/app-version" ;;
domain) echo "$name.example.invalid" ;;
db_identifier) echo "db-$name" ;;
*) exit 1 ;;
esac
"""
    # MISSING_PATH가 든 cat-file 검사는 실패하고, NO_WEBHOOK이면 compose.prod.yml에 스위치가 없다.
    # 환경 파일은 체크아웃의 파일을 보여 주고, OLD_PREFIX면 이 PR 이전처럼 staging 접두를 보여 준다.
    FAKE_GIT = r"""#!/bin/sh
case "$3" in
cat-file) [ -n "${MISSING_PATH:-}" ] && case "$5" in *"$MISSING_PATH"*) exit 1 ;; esac ;;
show) case "$4" in
	*.json) if [ -n "${OLD_PREFIX:-}" ]; then sed 's/"image_repository_prefix": "[^"]*"/"image_repository_prefix": "meterengine-staging"/' "$2/${4#*:}"; else cat "$2/${4#*:}"; fi ;;
	*) [ -n "${NO_WEBHOOK:-}" ] || echo "      WEBHOOK_ENABLED: x" ;;
	esac ;;
esac
exit 0
"""
    FAKE_AWS = r"""#!/bin/sh
printf 'aws %s\n' "$*" >>"$TRACE"
case "$*" in *describe-db-instances*) echo available ;; esac
"""

    @staticmethod
    def measurement(sut):
        """SUT마다 (측정 폴더, Terraform 코드 폴더). 측정 두 환경은 모듈 하나를 같이 쓴다."""
        if sut == "test-storage":
            return "test-storage", "test-storage/sut/terraform"
        return "test-sjpark", "test-sjpark/sut/module"

    @staticmethod
    def deploy_script(sut):
        """SUT마다 (sut-deploy.sh 경로, 인자, 상태를 읽는 Terraform 폴더)."""
        if sut == "test-storage":
            return "measurements/test-storage/sut/scripts/sut-deploy.sh", [], "measurements/test-storage/sut/terraform"
        spec = sut.rsplit("-", 1)[1]
        return "measurements/test-sjpark/sut/scripts/sut-deploy.sh", [spec], f"measurements/test-sjpark/sut/{spec}"

    def setUp(self):
        temp = tempfile.TemporaryDirectory(prefix="meterengine-sut-")
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        # 스크립트가 자기 위치로 레포 루트를 찾으므로 레포 모양을 그대로 옮긴다.
        self.repo = self.root / "repo"
        for directory in ("test-sjpark", "test-storage"):
            for relative in ("lib", "sut/scripts"):
                shutil.copytree(MEASUREMENTS / directory / relative, self.repo / "measurements" / directory / relative)
        for sut in self.SUTS:
            tf_dir = self.repo / self.deploy_script(sut)[2]
            tf_dir.mkdir(parents=True, exist_ok=True)
            (tf_dir / "terraform.tfstate").write_text("{}\n")
            environment = Path("deploy/environments") / f"{sut}.json"
            (self.repo / environment).parent.mkdir(parents=True, exist_ok=True)
            shutil.copy(MEASUREMENTS.parent / environment, self.repo / environment)
        self.trace = self.root / "trace"
        self.trace.touch()
        bin_dir = self.root / "bin"
        bin_dir.mkdir()
        for path, body in ((bin_dir / "terraform", self.FAKE_TERRAFORM), (bin_dir / "aws", self.FAKE_AWS),
                           (bin_dir / "git", self.FAKE_GIT), (bin_dir / "curl", '#!/bin/sh\nprintf \'curl %s\\n\' "$*" >>"$TRACE"\n'),
                           (self.repo / "deploy/rollout.sh", '#!/bin/sh\nprintf \'rollout %s\\n\' "$*" >>"$TRACE"\necho recorded_only=true\n')):
            path.write_text(body)
            path.chmod(path.stat().st_mode | stat.S_IEXEC)
        self.env = dict(os.environ, PATH=f"{bin_dir}{os.pathsep}{os.environ['PATH']}", TRACE=str(self.trace))

    def run_script(self, script, args):
        self.trace.write_text("")
        return subprocess.run(["bash", str(self.repo / script), *args], env=self.env, cwd=self.root,
                              text=True, capture_output=True, timeout=30)

    def test_each_sut_deploys_through_rollout(self):
        for sut in self.SUTS:
            with self.subTest(sut=sut):
                script, args, tf_dir = self.deploy_script(sut)
                result = self.run_script(script, [*args, SHA])
                self.assertEqual(result.returncode, 0, result.stderr)
                trace = self.trace.read_text()
                name = Path(tf_dir).name
                self.assertIn(f"rollout asg-{name} /meterengine/{name}/app-version {SHA}\n", trace)
                prefix = json.loads((MEASUREMENTS.parent / f"deploy/environments/{sut}.json").read_text())["image_repository_prefix"]
                self.assertIn(f"--repository-name {prefix}-backend --image-ids imageTag={SHA}", trace)
                chdirs = set(re.findall(r"^terraform -chdir=(\S+) ", trace, re.M))
                self.assertEqual(chdirs, {str(self.repo / tf_dir)})

    def test_undeployable_commit_is_rejected_before_rollout(self):
        cases = (("deploy/rollout.sh", {"MISSING_PATH": "deploy/rollout.sh"}, "deploy/rollout.sh 가 없는 옛 커밋"),
                 ("environment file", {"MISSING_PATH": "deploy/environments/test-sjpark-m7g.json"},
                  "deploy/environments/test-sjpark-m7g.json 이 없는 커밋"),
                 ("webhook switch", {"NO_WEBHOOK": "1"}, "WEBHOOK_ENABLED 가 없는 커밋"),
                 ("image prefix", {"OLD_PREFIX": "1"}, "image_repository_prefix 가 지금과 다른 옛 커밋"))
        script, args, _ = self.deploy_script("test-sjpark-m7g")
        for name, extra, reason in cases:
            with self.subTest(case=name):
                self.env.update(extra)
                try:
                    result = self.run_script(script, [*args, SHA])
                finally:
                    for key in extra:
                        del self.env[key]
                self.assertNotEqual(result.returncode, 0)
                self.assertIn(reason, result.stderr)
                self.assertNotIn("rollout ", self.trace.read_text())

    def test_unknown_spec_stops_before_aws(self):
        for name in self.SPEC_SCRIPTS:
            for args in (["x4g"], []):
                with self.subTest(script=name, args=args):
                    result = self.run_script(f"measurements/test-sjpark/sut/scripts/{name}", args)
                    self.assertNotEqual(result.returncode, 0)
                    self.assertIn("m7g나 t4g", result.stderr)
                    self.assertEqual(self.trace.read_text(), "")

    def test_sut_cannot_read_either_production_path(self):
        for sut in self.SUTS:
            directory, terraform = self.measurement(sut)
            iam = (MEASUREMENTS / terraform / "iam.tf").read_text()
            locals_file = (MEASUREMENTS / terraform / "locals.tf").read_text()
            for name, path in (("dev_param_path", "/meterengine/prod"), ("production_param_path", "/meterengine/production")):
                self.assertRegex(locals_file, rf'{name}\s*=\s*"{path}"')
                self.assertIn(f"local.{name}", iam)
                # test-storage는 경로마다 한 줄씩, 측정 모듈은 두 경로를 for로 돌며 같은 모양을 만든다.
                self.assertRegex(iam, rf'parameter\$\{{(?:local\.{name}|path)\}}"')
                self.assertRegex(iam, rf'parameter\$\{{(?:local\.{name}|path)\}}/\*"')
            for relative in ("lib/common.sh", "sut/scripts/remote/upsert-exporter-user.sh"):
                check = (MEASUREMENTS / directory / relative).read_text()
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
