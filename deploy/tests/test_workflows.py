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
MEASUREMENT = WORKFLOWS / "deploy-test-sjpark.yml"
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

    def test_measurement_deploy_runs_only_by_hand_on_measurement_environments(self):
        source = MEASUREMENT.read_text()
        triggers = re.search(r"^on:\n((?:  .*\n|\n)*)", source, re.M).group(1)
        self.assertEqual(re.findall(r"^  (\w+):", triggers, re.M), ["workflow_dispatch"])
        self.assertEqual(re.findall(r"^          - (\S+)$", source, re.M), ["test-sjpark-m7g", "test-sjpark-t4g"])
        self.assertIn("uses: ./.github/workflows/deploy.yml\n", source)
        self.assertIn("aws_role: arn:aws:iam::928618307647:role/meterengine-${{ inputs.environment }}-deploy\n", source)
        self.assertIn("group: deploy-${{ inputs.environment }}\n", source)

    def test_no_hardcoded_instance_id(self):
        for path in (DEPLOY, PRODUCTION, STAGING, MEASUREMENT):
            self.assertIsNone(re.search(r"\bi-0[0-9a-f]+", path.read_text()), path.name)

    def test_jobs_run_in_the_github_environment(self):
        self.assertEqual(DEPLOY.read_text().count("environment: ${{ inputs.environment }}\n"), 2)

    def test_deploy_rolls_the_group_named_by_the_deployed_commit(self):
        source = DEPLOY.read_text()
        self.assertIn('target=$(git show "$SHA:deploy/environments/$DEPLOY_ENV.json")', source)
        self.assertIn('jq -er .autoscaling_group_name <<<"$target"', source)
        self.assertIn('echo "VERSION_PARAMETER=$(jq -er .parameter_path <<<"$target")/app-version"', source)
        self.assertIn('./deploy/rollout.sh "$AUTOSCALING_GROUP" "$VERSION_PARAMETER" "$SHA" | tee -a "$GITHUB_OUTPUT"', source)

    def test_deploy_no_longer_sends_commands_to_servers(self):
        source = DEPLOY.read_text()
        for word in ("send-command", "get-command-invocation", "deploy/deploy.sh"):
            self.assertNotIn(word, source)

    def test_deploy_rejects_commits_that_still_run_caddy(self):
        source = DEPLOY.read_text()
        self.assertIn('if git cat-file -e "$SHA:deploy/caddy/Caddyfile" 2>/dev/null; then', source)
        self.assertLess(source.index("deploy/caddy/Caddyfile"), source.index("./deploy/rollout.sh"))

    def test_deploy_checks_both_images_before_recording_the_version(self):
        source = DEPLOY.read_text()
        self.assertIn('for component in backend frontend; do', source)
        self.assertLess(source.index('--image-ids imageTag="$SHA"'), source.index("./deploy/rollout.sh"))

    def test_external_check_goes_through_the_alb_only_after_a_real_rollout(self):
        source = DEPLOY.read_text()
        self.assertIn("if: steps.rollout.outputs.recorded_only == 'false' && inputs.external_check\n", source)
        self.assertIn('--connect-to "$SITE_DOMAIN:443:$ALB_DNS_NAME:443"', source)

    def test_only_staging_skips_the_external_check(self):
        # 스테이징 ALB는 팀 IP만 받아 GitHub 러너가 닿지 못한다. 나머지 환경은 기본값으로 확인한다.
        self.assertRegex(DEPLOY.read_text(), r"      external_check:\n(?:        .*\n)*?        type: boolean\n        default: true\n")
        self.assertIn("      external_check: false\n", STAGING.read_text())
        for path in (PRODUCTION, MEASUREMENT):
            self.assertNotIn("external_check", path.read_text())

    def test_deploy_job_outlasts_rollout_and_recovery(self):
        self.assertIn("timeout-minutes: 50\n", DEPLOY.read_text())

    def test_cd_skips_merges_the_app_servers_do_not_read(self):
        excluded = ["deploy/terraform/**", "deploy/tests/**", "deploy/**/*.md", "deploy/monitoring/**",
                    "deploy/compose.monitoring.yml", "deploy/grafana/**", "deploy/cloudwatch/**"]
        for path in (PRODUCTION, STAGING):
            source = path.read_text()
            for pattern in excluded:
                self.assertIn(f"      - '!{pattern}'\n", source, path.name)
                self.assertLess(source.index("- 'deploy/**'"), source.index(f"- '!{pattern}'"), path.name)

    def test_concurrency_is_per_environment(self):
        self.assertIn("group: cd-production\n", PRODUCTION.read_text())
        self.assertIn("group: cd-staging\n", STAGING.read_text())

    def test_ci_runs_on_develop(self):
        self.assertEqual(set(push_branches(CI)), {"main", "develop"})

    def test_ci_runs_deploy_tests_for_every_file_they_read(self):
        source = CI.read_text()
        for pattern in (".github/workflows/deploy.yml", ".github/workflows/cd-staging.yml", ".github/workflows/cd-production.yml",
                        ".github/workflows/deploy-test-sjpark.yml", "measurements/*/lib/common.sh",
                        "measurements/*/sut/terraform/iam.tf", "measurements/*/sut/terraform/locals.tf",
                        "measurements/*/sut/terraform/variables.tf",
                        "measurements/*/sut/scripts/remote/upsert-exporter-user.sh", "measurements/*/sut/scripts/sut-deploy.sh",
                        "measurements/*/sut/scripts/push-images.sh", "measurements/*/sut/scripts/sut-stop.sh",
                        "measurements/*/README.md", "measurements/test-sjpark/sut/**",
                        "measurements/test-storage/sut/terraform/**",
                        "measurements/test-sjpark/k6/lib/config.js"):
            self.assertIn(f"- '{pattern}'", source)

    def test_readme_manual_rollout_matches_the_cd_call(self):
        section = README.read_text().split("### 수동 배포")[1].split("## 롤백")[0]
        self.assertIn("deploy/rollout.sh meterengine-<환경>-app /meterengine/<환경>/app-version <git-sha>", section)

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
        self.assertIn("RECOVERY_DB_IDENTIFIER=meterengine-db-restore-<YYYYMMDD> ./deploy/deploy.sh production <SHA>)", section)
        self.assertIn("aws autoscaling suspend-processes --auto-scaling-group-name meterengine-production-app", section)
        self.assertIn("aws autoscaling resume-processes --auto-scaling-group-name meterengine-production-app", section)
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
