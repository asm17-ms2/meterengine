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
LAUNCH_TEMPLATE = DEPLOY / "terraform/modules/app_fleet/asg.tf"
BASE_PARAMETERS = ["DB_HOST", "DB_PORT", "DB_NAME", "DB_USERNAME", "DB_PASSWORD", "CRYPTO_ENCRYPTION_KEY", "OPERATOR_EMAIL", "OPERATOR_PASSWORD"]


def staging():
    return json.loads((ENVIRONMENTS / "staging.json").read_text())


def write_target(directory, name, **changes):
    target = {**staging(), "autoscaling_group_name": f"meterengine-{name}-app", **changes}
    if name not in environment.GROUP_ENVIRONMENTS:
        del target["autoscaling_group_name"]
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
        del target["autoscaling_group_name"]
        (self.directory / "staging.json").write_text(json.dumps(target))
        with self.assertRaisesRegex(ValueError, "필드"):
            environment.load_target("staging", self.directory)

    def test_load_rejects_group_for_sut(self):
        target = write_target(self.directory, "test-storage", parameter_path="/meterengine/test-storage")
        (self.directory / "test-storage.json").write_text(json.dumps({**target, "autoscaling_group_name": "meterengine-test-storage-app"}))
        with self.assertRaisesRegex(ValueError, "필드"):
            environment.load_target("test-storage", self.directory)

    def test_load_rejects_removed_test_sjpark(self):
        with self.assertRaisesRegex(ValueError, "환경 이름은"):
            environment.load_target("test-sjpark", ENVIRONMENTS)

    def test_load_requires_group_for_measurement_environments(self):
        target = write_target(self.directory, "test-sjpark-t4g", parameter_path="/meterengine/test-sjpark-t4g")
        del target["autoscaling_group_name"]
        (self.directory / "test-sjpark-t4g.json").write_text(json.dumps(target))
        with self.assertRaisesRegex(ValueError, "필드"):
            environment.load_target("test-sjpark-t4g", self.directory)

    def test_load_rejects_group_of_the_other_measurement_environment(self):
        write_target(self.directory, "test-sjpark-t4g", parameter_path="/meterengine/test-sjpark-t4g",
                     autoscaling_group_name="meterengine-test-sjpark-m7g-app")
        with self.assertRaisesRegex(ValueError, "autoscaling_group_name"):
            environment.load_target("test-sjpark-t4g", self.directory)

    def test_measurement_targets_match_their_module(self):
        for name in ("test-sjpark-m7g", "test-sjpark-t4g"):
            target = environment.load_target(name, ENVIRONMENTS)
            self.assertEqual(target["database_identifier"], f"meterengine-{name}-db")
            self.assertEqual(target["parameter_path"], f"/meterengine/{name}")
            self.assertEqual(target["site_domain"], f"{name}.meterengine.com")

    def test_load_rejects_monitoring_profile(self):
        write_target(self.directory, "staging", compose_profiles=["agents", "monitoring"])
        with self.assertRaisesRegex(ValueError, "compose_profiles"):
            environment.load_target("staging", self.directory)

    def test_load_rejects_group_of_another_environment(self):
        write_target(self.directory, "production", parameter_path="/meterengine/production", autoscaling_group_name="meterengine-staging-app")
        with self.assertRaisesRegex(ValueError, "autoscaling_group_name"):
            environment.load_target("production", self.directory)

    def test_group_environments_name_their_own_group(self):
        for name in ("staging", "production", "test-sjpark-m7g", "test-sjpark-t4g"):
            self.assertEqual(environment.load_target(name, ENVIRONMENTS)["autoscaling_group_name"], f"meterengine-{name}-app")

    def test_launch_template_exposes_tags_to_the_server(self):
        # 서버가 자기 오토스케일링 그룹 이름을 인스턴스 메타데이터 태그로 읽는다. 끄면 부팅 배포가 늘 멈춘다.
        self.assertRegex(LAUNCH_TEMPLATE.read_text(), r'instance_metadata_tags\s*=\s*"enabled"')

    def test_load_rejects_unknown_profile(self):
        write_target(self.directory, "staging", compose_profiles=["agents", "everything"])
        with self.assertRaisesRegex(ValueError, "compose_profiles"):
            environment.load_target("staging", self.directory)

    def test_load_rejects_service_path_for_sut(self):
        for path in ("/meterengine/prod", "/meterengine/production", "/meterengine/staging"):
            write_target(self.directory, "test-storage", parameter_path=path)
            with self.assertRaisesRegex(ValueError, "parameter_path"):
                environment.load_target("test-storage", self.directory)

    def test_every_environment_file_loads(self):
        for name in ("staging", "production", "test-storage", "test-sjpark-m7g", "test-sjpark-t4g"):
            self.assertEqual(environment.load_target(name, ENVIRONMENTS)["parameter_path"], f"/meterengine/{name}")

    def test_database_identifier_rejects_full_address(self):
        with tempfile.TemporaryDirectory() as directory:
            write_target(Path(directory), "staging", database_identifier="meterengine-staging-db.abc.ap-northeast-2.rds.amazonaws.com")
            with self.assertRaisesRegex(ValueError, "database_identifier"):
                environment.load_target("staging", Path(directory))

    def test_observe_keeps_only_the_identifier(self):
        host = "meterengine-staging-db.abc.ap-northeast-2.rds.amazonaws.com"
        self.assertEqual(environment.database_identifier(host), "meterengine-staging-db")

    def test_each_environment_uses_its_image_repositories(self):
        prefixes = {name: environment.load_target(name, ENVIRONMENTS)["image_repository_prefix"]
                    for name in ("staging", "production", "test-storage", "test-sjpark-m7g", "test-sjpark-t4g")}
        self.assertEqual(prefixes, {"staging": "meterengine-staging", "production": "meterengine", "test-storage": "meterengine-test-storage",
                                    "test-sjpark-m7g": "meterengine-test-sjpark", "test-sjpark-t4g": "meterengine-test-sjpark"})

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
        self.observed = {"autoscaling_group_name": self.target["autoscaling_group_name"], "database_identifier": self.target["database_identifier"]}

    def test_validate_accepts_matching_server(self):
        environment.validate_target(self.target, self.observed)

    def test_validate_rejects_other_group(self):
        production = environment.load_target("production", ENVIRONMENTS)
        with self.assertRaisesRegex(ValueError, "autoscaling_group_name"):
            environment.validate_target(self.target, {**self.observed, "autoscaling_group_name": production["autoscaling_group_name"]})

    def test_validate_rejects_server_outside_any_group(self):
        with self.assertRaisesRegex(ValueError, "autoscaling_group_name.*없음"):
            environment.validate_target(self.target, {**self.observed, "autoscaling_group_name": ""})

    def test_validate_rejects_other_database(self):
        production = environment.load_target("production", ENVIRONMENTS)
        with self.assertRaisesRegex(ValueError, "database_identifier"):
            environment.validate_target(self.target, {**self.observed, "database_identifier": production["database_identifier"]})

    def test_validate_ignores_group_for_sut(self):
        target = environment.load_target("test-storage", ENVIRONMENTS)
        observed = {"database_identifier": target["database_identifier"]}
        environment.validate_target(target, observed)

    def test_validate_rejects_other_database_for_sut(self):
        target = environment.load_target("test-storage", ENVIRONMENTS)
        with self.assertRaisesRegex(ValueError, "database_identifier"):
            environment.validate_target(target, {"database_identifier": "meterengine-db"})

    def test_validate_rejects_missing_observation(self):
        with self.assertRaisesRegex(ValueError, "database_identifier"):
            environment.validate_target(self.target, {"autoscaling_group_name": self.target["autoscaling_group_name"]})


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
        self.fake(bin_dir / "curl", 'case "$*" in *api/token*) echo token ;; *aws:autoscaling:groupName*) [ -n "$FAKE_GROUP" ] || exit 22; echo "$FAKE_GROUP" ;; *tags/instance/ServerRole*) [ -n "$FAKE_ROLE" ] || exit 22; echo "$FAKE_ROLE" ;; *) exit 22 ;; esac\n')
        self.fake(bin_dir / "aws", 'echo "$FAKE_DB_HOST"\n')
        self.env = dict(os.environ, PATH=f"{bin_dir}{os.pathsep}{os.environ['PATH']}",
                        FAKE_GROUP=self.target["autoscaling_group_name"], FAKE_DB_HOST=f"{self.target['database_identifier']}.example.invalid")

    @staticmethod
    def fake(path, body):
        path.write_text("#!/bin/sh\n" + body)
        path.chmod(path.stat().st_mode | stat.S_IEXEC)

    def preflight(self, shell_tail="", **env):
        script = f'set -e; output=$(python3 environment.py preflight --environment staging); eval "$output"; {shell_tail}'
        return subprocess.run(["bash", "-c", script], cwd=self.root, env={**self.env, **env}, text=True, capture_output=True)

    def test_preflight_prints_shell_quoted_values(self):
        result = self.preflight('printf "%s|%s|%s|%s|%s" "$PARAM_PATH" "$SITE_ALIASES" "$COMPOSE_PROFILES" "$BACKEND_MEM_LIMIT" "$EXPECTED_DB_IDENTIFIER"')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout, "|".join(["/meterengine/staging", "www.one.example www.two.example", "agents", "768m", self.target["database_identifier"]]))

    def test_preflight_prints_image_repository_prefix(self):
        result = self.preflight('printf "%s" "$IMAGE_REPOSITORY_PREFIX"')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout, "meterengine-staging")

    def test_preflight_prints_all_role_without_the_tag(self):
        result = self.preflight('printf "%s" "$METERENGINE_ROLE"')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout, "all")

    def test_preflight_prints_the_tagged_role(self):
        result = self.preflight('printf "%s" "$METERENGINE_ROLE"', FAKE_ROLE="consumer")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout, "consumer")

    def test_unknown_role_tag_stops_before_printing(self):
        result = self.preflight(FAKE_ROLE="consumr")
        self.assertEqual(result.returncode, 2)
        self.assertEqual(result.stdout, "")
        self.assertIn("ServerRole", result.stderr)

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
                                cwd=self.root, env={**self.env, "FAKE_GROUP": ""},
                                text=True, capture_output=True)
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_preflight_failure_exits_2(self):
        result = subprocess.run([sys.executable, "environment.py", "preflight", "--environment", "staging"],
                                cwd=self.root, env={**self.env, "FAKE_GROUP": "meterengine-production-app"},
                                text=True, capture_output=True)
        self.assertEqual(result.returncode, 2)
        self.assertEqual(result.stdout, "")
        self.assertIn("autoscaling_group_name", result.stderr)

    def test_server_outside_any_group_stops_before_printing(self):
        result = self.preflight(FAKE_GROUP="")
        self.assertEqual(result.returncode, 2)
        self.assertIn("없음", result.stderr)


if __name__ == "__main__":
    unittest.main()
