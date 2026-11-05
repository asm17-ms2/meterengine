import os
from pathlib import Path
import re
import shutil
import subprocess
import unittest


WORKFLOWS = Path(__file__).resolve().parents[2] / ".github/workflows"
DEPLOY = WORKFLOWS / "deploy.yml"
PRODUCTION = WORKFLOWS / "cd-production.yml"
STAGING = WORKFLOWS / "cd-staging.yml"
CI = WORKFLOWS / "ci.yml"
ROOT = WORKFLOWS.parents[1]
README = ROOT / "deploy/README.md"


def push_branches(path):
    return re.search(r"^  push:\n    branches: \[([^\]]*)\]", path.read_text(), re.M).group(1).replace(" ", "").split(",")


class WorkflowTest(unittest.TestCase):
    def test_cd_targets_main_only(self):
        self.assertEqual(push_branches(PRODUCTION), ["main"])
        self.assertIn("environment: production\n", PRODUCTION.read_text())
        self.assertIn("role/meterengine-github-actions-role", PRODUCTION.read_text())

    def test_cd_staging_targets_develop_only(self):
        self.assertEqual(push_branches(STAGING), ["develop"])
        self.assertIn("environment: staging\n", STAGING.read_text())
        self.assertIn("role/meterengine-staging-deploy", STAGING.read_text())

    def test_no_hardcoded_instance_id(self):
        for path in (DEPLOY, PRODUCTION, STAGING):
            self.assertIsNone(re.search(r"\bi-0[0-9a-f]+", path.read_text()), path.name)

    def test_jobs_run_in_the_github_environment(self):
        self.assertEqual(DEPLOY.read_text().count("environment: ${{ inputs.environment }}\n"), 2)

    def test_old_commit_uses_old_call(self):
        source = DEPLOY.read_text()
        self.assertIn("if test -f deploy/environments/$DEPLOY_ENV.json; then ./deploy/deploy.sh $DEPLOY_ENV $SHA", source)
        self.assertIn("./deploy/deploy.sh $SHA", source)

    def test_old_call_is_production_only(self):
        # 옛 deploy.sh의 기본값은 production이라 다른 서버에서 부르면 production 값으로 배포를 시작한다.
        self.assertIn("elif test $DEPLOY_ENV = production; then ./deploy/deploy.sh $SHA; else", DEPLOY.read_text())

    def test_concurrency_is_per_environment(self):
        self.assertIn("group: cd-production\n", PRODUCTION.read_text())
        self.assertIn("group: cd-staging\n", STAGING.read_text())

    def test_ci_runs_on_develop(self):
        self.assertEqual(set(push_branches(CI)), {"main", "develop"})

    def test_ci_runs_deploy_tests_for_every_file_they_read(self):
        source = CI.read_text()
        for pattern in (".github/workflows/deploy.yml", ".github/workflows/cd-staging.yml", ".github/workflows/cd-production.yml", "measurements/*/lib/common.sh",
                        "measurements/*/sut/terraform/iam.tf", "measurements/*/sut/terraform/locals.tf",
                        "measurements/*/sut/scripts/remote/upsert-exporter-user.sh", "measurements/*/README.md"):
            self.assertIn(f"- '{pattern}'", source)

    def test_readme_rollback_handles_commits_without_environment_file(self):
        section = README.read_text().split("### 서버에서 롤백")[1].split("## DB 백업과 복구")[0]
        self.assertIn("if test -f deploy/environments/production.json; then ./deploy/deploy.sh production <SHA>; else ./deploy/deploy.sh <SHA>; fi", section)

    def test_images_go_to_the_repository_of_the_environment(self):
        source = DEPLOY.read_text()
        self.assertIn("jq -er .image_repository_prefix", source)
        self.assertEqual(source.count("meterengine-${{ matrix.component }}"), 0)
        self.assertEqual(source.count("${{ steps.target.outputs.repository }}"), 2)

    def test_workflows_never_pass_recovery_value(self):
        for path in WORKFLOWS.glob("*.yml"):
            self.assertNotIn("RECOVERY_DB_IDENTIFIER", path.read_text(), path.name)

    def test_readme_restore_uses_recovery_value_then_updates_environment_file(self):
        section = README.read_text().split("### 복원")[1].split("### 암호화 키 맞추기")[0]
        self.assertIn("RECOVERY_DB_IDENTIFIER=$RESTORED_ID ./deploy/deploy.sh production <SHA>", section)
        self.assertIn("production.json", section)


class AwsParsingTest(unittest.TestCase):
    """AWS CLI는 commands= 값을 shorthand 문법으로 읽어, 대괄호나 쉼표가 있으면 요청을 보내기 전에 실패한다."""

    def commands(self):
        for path in (DEPLOY, README):
            for command in re.findall(r'--parameters commands="([^"\n]+)"', path.read_text()):
                yield path.name, (command.replace("$DEPLOY_ENV", "production").replace("$SHA", "a" * 40)
                                  .replace("<git-sha>", "a" * 40).replace("<SHA>", "a" * 40).replace("<환경>", "production"))

    def test_commands_avoid_shorthand_syntax(self):
        for name, command in self.commands():
            self.assertNotRegex(command, r"[\[\],{}]", name)

    @unittest.skipUnless(shutil.which("aws"), "AWS CLI required")
    def test_commands_pass_the_aws_cli_parser(self):
        # 닫힌 로컬 포트로 보내 AWS에 닿지 않고 파싱만 확인한다.
        environment = {name: value for name, value in os.environ.items() if not name.startswith("AWS_")}
        environment.update(AWS_ACCESS_KEY_ID="test", AWS_SECRET_ACCESS_KEY="test", AWS_REGION="ap-northeast-2", AWS_MAX_ATTEMPTS="1")
        for name, command in self.commands():
            result = subprocess.run(["aws", "ssm", "send-command", "--endpoint-url", "http://127.0.0.1:9", "--cli-connect-timeout", "1",
                                     "--instance-ids", "i-00000000000000000", "--document-name", "AWS-RunShellScript",
                                     "--parameters", f"commands={command}"],
                                    env=environment, text=True, capture_output=True, timeout=30)
            self.assertNotIn("Error parsing parameter", result.stderr, name)
            self.assertNotIn("Expected:", result.stderr, name)


if __name__ == "__main__":
    unittest.main()
