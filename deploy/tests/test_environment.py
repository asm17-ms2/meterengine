import json
import os
from pathlib import Path
import shutil
import stat
import subprocess
import sys
import tempfile
import unittest


DEPLOY = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(DEPLOY))

import environment  # noqa: E402


ENVIRONMENTS = DEPLOY / "environments"
BASE_PARAMETERS = ["DB_HOST", "DB_PORT", "DB_NAME", "DB_USERNAME", "DB_PASSWORD", "CRYPTO_ENCRYPTION_KEY", "OPERATOR_EMAIL", "OPERATOR_PASSWORD"]
MONITORING_PARAMETERS = ["DB_EXPORTER_PASSWORD", "GRAFANA_ADMIN_PASSWORD", "SLACK_WEBHOOK_URL", "OAUTH2_PROXY_CLIENT_ID", "OAUTH2_PROXY_CLIENT_SECRET", "OAUTH2_PROXY_COOKIE_SECRET"]


def staging():
    return json.loads((ENVIRONMENTS / "staging.json").read_text())


def write_target(directory, name, **changes):
    target = {**staging(), **changes}
    if name.startswith("test-"):
        del target["instance_id"]
    (directory / f"{name}.json").write_text(json.dumps(target))
    return target


class LoadTargetTest(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.directory = Path(temp.name)

    def test_load_rejects_unknown_environment(self):
        with self.assertRaisesRegex(ValueError, "환경 이름"):
            environment.load_target("../staging", ENVIRONMENTS)

    def test_load_rejects_missing_field(self):
        target = write_target(self.directory, "staging")
        del target["instance_id"]
        (self.directory / "staging.json").write_text(json.dumps(target))
        with self.assertRaisesRegex(ValueError, "필드"):
            environment.load_target("staging", self.directory)

    def test_load_rejects_instance_id_for_sut(self):
        target = write_target(self.directory, "test-sjpark", parameter_path="/meterengine/test-sjpark")
        (self.directory / "test-sjpark.json").write_text(json.dumps({**target, "instance_id": "i-0123456789abcdef0"}))
        with self.assertRaisesRegex(ValueError, "필드"):
            environment.load_target("test-sjpark", self.directory)

    def test_load_rejects_unknown_profile(self):
        write_target(self.directory, "staging", compose_profiles=["agents", "everything"])
        with self.assertRaisesRegex(ValueError, "compose_profiles"):
            environment.load_target("staging", self.directory)

    def test_load_rejects_service_path_for_sut(self):
        for path in ("/meterengine/prod", "/meterengine/production", "/meterengine/staging"):
            write_target(self.directory, "test-sjpark", parameter_path=path)
            with self.assertRaisesRegex(ValueError, "parameter_path"):
                environment.load_target("test-sjpark", self.directory)

    def test_every_environment_file_loads(self):
        for name in ("staging", "production", "test-sjpark", "test-storage"):
            self.assertEqual(environment.load_target(name, ENVIRONMENTS)["parameter_path"], f"/meterengine/{name}")

    def test_database_identifier_rejects_full_address(self):
        with tempfile.TemporaryDirectory() as directory:
            write_target(Path(directory), "staging", database_identifier="meterengine-staging-db.abc.ap-northeast-2.rds.amazonaws.com")
            with self.assertRaisesRegex(ValueError, "database_identifier"):
                environment.load_target("staging", Path(directory))

    def test_observe_keeps_only_the_identifier(self):
        host = "meterengine-staging-db.abc.ap-northeast-2.rds.amazonaws.com"
        self.assertEqual(environment.database_identifier(host), "meterengine-staging-db")

    def test_only_staging_has_its_own_image_repositories(self):
        prefixes = {name: environment.load_target(name, ENVIRONMENTS)["image_repository_prefix"]
                    for name in ("staging", "production", "test-sjpark", "test-storage")}
        self.assertEqual(prefixes, {"staging": "meterengine-staging", "production": "meterengine", "test-sjpark": "meterengine", "test-storage": "meterengine"})

    def test_production_memory_fits_the_current_server(self):
        # 서버 메모리에 맞춘 값이다. 사양을 바꾸면 같이 바꾼다.
        target = environment.load_target("production", ENVIRONMENTS)
        self.assertEqual((target["backend_mem_limit"], target["backend_max_ram_percentage"]), ("768m", 50))

    def test_production_path_is_production(self):
        write_target(self.directory, "production", parameter_path="/meterengine/prod")
        with self.assertRaisesRegex(ValueError, "parameter_path"):
            environment.load_target("production", self.directory)


class ValidateTargetTest(unittest.TestCase):
    def setUp(self):
        self.target = environment.load_target("staging", ENVIRONMENTS)
        self.observed = {"instance_id": self.target["instance_id"], "database_identifier": self.target["database_identifier"]}

    def test_validate_accepts_matching_server(self):
        environment.validate_target(self.target, self.observed)

    def test_validate_rejects_other_instance(self):
        production = environment.load_target("production", ENVIRONMENTS)
        with self.assertRaisesRegex(ValueError, "instance_id"):
            environment.validate_target(self.target, {**self.observed, "instance_id": production["instance_id"]})

    def test_validate_rejects_other_database(self):
        production = environment.load_target("production", ENVIRONMENTS)
        with self.assertRaisesRegex(ValueError, "database_identifier"):
            environment.validate_target(self.target, {**self.observed, "database_identifier": production["database_identifier"]})

    def test_validate_ignores_instance_for_sut(self):
        target = environment.load_target("test-storage", ENVIRONMENTS)
        observed = {"instance_id": "i-0123456789abcdef0", "database_identifier": target["database_identifier"]}
        environment.validate_target(target, observed)

    def test_validate_rejects_other_database_for_sut(self):
        target = environment.load_target("test-storage", ENVIRONMENTS)
        with self.assertRaisesRegex(ValueError, "database_identifier"):
            environment.validate_target(target, {"instance_id": "i-0123456789abcdef0", "database_identifier": "meterengine-db"})

    def test_validate_rejects_missing_observation(self):
        with self.assertRaisesRegex(ValueError, "database_identifier"):
            environment.validate_target(self.target, {"instance_id": self.target["instance_id"]})


class RequiredParametersTest(unittest.TestCase):
    def test_required_parameters_without_monitoring(self):
        target = environment.load_target("staging", ENVIRONMENTS)
        self.assertEqual(environment.required_parameters(target), BASE_PARAMETERS)

    def test_required_parameters_with_monitoring(self):
        target = environment.load_target("production", ENVIRONMENTS)
        self.assertEqual(environment.required_parameters(target), BASE_PARAMETERS + MONITORING_PARAMETERS)


class PreflightTest(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        (self.root / "environments").mkdir()
        shutil.copy(DEPLOY / "environment.py", self.root)
        self.target = write_target(self.root / "environments", "staging", site_aliases=["www.one.example", "www.two.example"])
        bin_dir = self.root / "bin"
        bin_dir.mkdir()
        self.fake(bin_dir / "curl", 'case "$*" in *api/token*) echo token ;; *) echo "$FAKE_INSTANCE_ID" ;; esac\n')
        self.fake(bin_dir / "aws", 'echo "$FAKE_DB_HOST"\n')
        self.env = dict(os.environ, PATH=f"{bin_dir}{os.pathsep}{os.environ['PATH']}",
                        FAKE_INSTANCE_ID=self.target["instance_id"], FAKE_DB_HOST=f"{self.target['database_identifier']}.example.invalid")

    @staticmethod
    def fake(path, body):
        path.write_text("#!/bin/sh\n" + body)
        path.chmod(path.stat().st_mode | stat.S_IEXEC)

    def preflight(self, shell_tail="", **env):
        script = f'set -e; output=$(python3 environment.py preflight --environment staging); eval "$output"; {shell_tail}'
        return subprocess.run(["bash", "-c", script], cwd=self.root, env={**self.env, **env}, text=True, capture_output=True)

    def test_preflight_prints_shell_quoted_values(self):
        result = self.preflight('printf "%s|%s|%s|%s|%s|%s" "$PARAM_PATH" "$SITE_ALIASES" "$COMPOSE_PROFILES" "$CADDY_SSO_SITES" "$BACKEND_MEM_LIMIT" "$EXPECTED_DB_IDENTIFIER"')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout, "|".join(["/meterengine/staging", "www.one.example www.two.example", "agents", "off", "768m", self.target["database_identifier"]]))

    def test_preflight_prints_image_repository_prefix(self):
        result = self.preflight('printf "%s" "$IMAGE_REPOSITORY_PREFIX"')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout, "meterengine-staging")

    def test_preflight_prints_required_parameters(self):
        result = self.preflight('printf "%s" "$REQUIRED_PARAMETERS"')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout.split(), BASE_PARAMETERS)

    def test_restored_db_is_rejected_without_recovery_value(self):
        result = self.preflight(FAKE_DB_HOST="restored.example.invalid")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("database_identifier", result.stderr)

    def test_recovery_value_replaces_expected_identifier(self):
        result = self.preflight('printf "%s" "$EXPECTED_DB_IDENTIFIER"', FAKE_DB_HOST="restored.example.invalid", RECOVERY_DB_IDENTIFIER="restored")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout, "restored")
        self.assertIn("restored", result.stderr)

    def test_recovery_value_must_match_ssm(self):
        result = self.preflight(FAKE_DB_HOST="other.example.invalid", RECOVERY_DB_IDENTIFIER="restored")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("database_identifier", result.stderr)

    def test_empty_recovery_value_is_ignored(self):
        result = self.preflight(FAKE_DB_HOST="restored.example.invalid", RECOVERY_DB_IDENTIFIER="")
        self.assertNotEqual(result.returncode, 0)

    def test_preflight_passes_sut_on_any_instance(self):
        write_target(self.root / "environments", "test-storage", parameter_path="/meterengine/test-storage")
        result = subprocess.run([sys.executable, "environment.py", "preflight", "--environment", "test-storage"],
                                cwd=self.root, env={**self.env, "FAKE_INSTANCE_ID": "i-00000000000000000"},
                                text=True, capture_output=True)
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_preflight_failure_exits_2(self):
        result = subprocess.run([sys.executable, "environment.py", "preflight", "--environment", "staging"],
                                cwd=self.root, env={**self.env, "FAKE_INSTANCE_ID": "i-00000000000000000"},
                                text=True, capture_output=True)
        self.assertEqual(result.returncode, 2)
        self.assertEqual(result.stdout, "")
        self.assertIn("instance_id", result.stderr)


if __name__ == "__main__":
    unittest.main()
