import json
import os
from pathlib import Path
import re
import shutil
import stat
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
MODULE = ROOT / "deploy/terraform/modules/app_fleet"
PROVISION = MODULE / "image/provision.sh"
BOOT_TEMPLATE = MODULE / "templates/app-user-data.sh.tftpl"
MONITORING_BOOTSTRAP = ROOT / "deploy/terraform/monitoring/bootstrap.sh"
COMPOSE_INSTALL_WORDS = ("docker-compose-linux-aarch64", "sha256sum --check", "install -D")
COMPOSE_PLUGIN = "/usr/libexec/docker/cli-plugins/docker-compose"
FLEET_IMAGE_SETTINGS = ((ROOT / "deploy/terraform/staging/main.tf", "module", "app_fleet", "ami_id"),
                        (ROOT / "deploy/terraform/production/main.tf", "module", "app_fleet", "ami_id"),
                        (ROOT / "measurements/test-storage/sut/terraform/variables.tf", "variable", "ami_id", "default"),
                        (ROOT / "measurements/test-sjpark/sut/module/variables.tf", "variable", "ami_id", "default"))
BUILD = MODULE / "image/build-app-image.sh"
BASE_IMAGE = "ami-0f4109eb491b0476f"
SOURCE_COMMIT = "c" * 40

# aws와 git이 이 파일 하나를 쓴다. 부른 명령을 남기고 시나리오 값으로 답한다. 상태 목록은 부를 때마다 앞에서 하나씩 꺼내고 마지막 값은 남긴다.
FAKE = r'''#!/usr/bin/env python3
import json, os, sys
program = os.path.basename(sys.argv[0])
args = sys.argv[1:]
path = os.environ["FAKE_STATE"]
state = json.load(open(path))
with open(os.environ["TRACE"], "a") as trace:
    trace.write(" ".join([program, *args]) + "\n")


def pop(values):
    return values.pop(0) if len(values) > 1 else values[0]


def finish(output="", code=0):
    json.dump(state, open(path, "w"))
    if output:
        print(output)
    sys.exit(code)


if program == "git":
    command = args[2]
    if command == "status":
        finish(state["dirty"])
    if command == "merge-base":
        finish(code=0 if state["in_develop"] else 1)
    if command == "rev-parse":
        finish(state["commit"])
    finish()
command = " ".join(args[:2])
answers = {"sts get-caller-identity": state["account"], "ec2 create-security-group": "sg-builder",
           "ec2 run-instances": "i-builder", "ec2 get-console-output": "console log", "ec2 create-image": "ami-result"}
if command in answers:
    finish(answers[command])
if command == "ec2 describe-instances":
    finish(pop(state["builder_states"]))
if command == "ec2 describe-images":
    finish(pop(state["image_states"]) if "State" in " ".join(args) else state["image"])
if command == "ec2 delete-security-group":
    finish(code=pop(state["security_group_deletes"]))
if command in ("ec2 revoke-security-group-egress", "ec2 authorize-security-group-egress", "ec2 enable-image-deregistration-protection",
               "ec2 terminate-instances", "ec2 wait"):
    finish()
finish(code=99)
'''


def commands(path):
    return [line.strip() for line in path.read_text().splitlines() if line.strip() and not line.lstrip().startswith("#")]


def lines_with(path, word):
    return [line for line in commands(path) if word in line]


def block_value(path, kind, name, key):
    block = re.search(rf'^{kind} "{name}" {{\n(.*?)^}}', path.read_text(), re.M | re.S)
    if block is None:
        raise AssertionError(f'{path}: {kind} "{name}" 블록이 없다')
    value = re.search(rf'^\s+{key}\s+=\s+"([^"]+)"$', block.group(1), re.M)
    if value is None:
        raise AssertionError(f'{path}: {kind} "{name}"의 {key}가 따옴표 문자열이 아니다')
    return value.group(1)


class ProvisionTest(unittest.TestCase):
    def test_provision_parses(self):
        result = subprocess.run(["bash", "-n", str(PROVISION)], text=True, capture_output=True)
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_provision_stops_at_the_first_failing_command(self):
        self.assertEqual(commands(PROVISION)[0], "set -euo pipefail")

    def test_provision_installs_docker_git_python_and_openssl(self):
        [install] = lines_with(PROVISION, "dnf install")
        for package in ("docker", "git", "python3", "openssl"):
            self.assertIn(package, install.split())

    def test_provision_pins_the_same_compose_as_the_monitoring_server(self):
        for word in COMPOSE_INSTALL_WORDS:
            self.assertEqual(lines_with(PROVISION, word), lines_with(MONITORING_BOOTSTRAP, word), word)

    def test_provision_checks_compose_digest_before_installing_it(self):
        source = PROVISION.read_text()
        self.assertLess(source.index("sha256sum --check"), source.index("install -D"))

    def test_provision_enables_docker_without_starting_it(self):
        source = PROVISION.read_text()
        self.assertIn("systemctl enable docker\n", source)
        self.assertNotIn("--now", source)
        self.assertNotIn("systemctl start", source)

    def test_provision_clears_machine_id_then_powers_off_last(self):
        lines = commands(PROVISION)
        self.assertEqual(lines[-1], "systemctl poweroff")
        self.assertIn("truncate -s 0 /etc/machine-id", lines)

    # README가 이 줄로 굽던 때 기록과 서버 부팅 기록을 가르라고 안내한다.
    def test_provision_marks_where_the_bake_ends_just_before_power_off(self):
        self.assertTrue(commands(PROVISION)[-2].startswith("echo '[app-image] 굽기 끝"))

    def test_provision_carries_no_checkout_unit_or_config(self):
        source = PROVISION.read_text()
        for word in ("git clone", "/opt/meterengine", "meterengine-cpu-metric", "/etc/meterengine"):
            self.assertNotIn(word, source)


class BootFromImageTest(unittest.TestCase):
    def test_boot_needs_only_what_the_image_provides(self):
        [install] = lines_with(PROVISION, "dnf install")
        [checked] = re.findall(r"^for tool in (.+); do$", BOOT_TEMPLATE.read_text(), re.M)
        self.assertLessEqual(set(checked.split()), set(install.split()))
        self.assertIn("docker compose version", BOOT_TEMPLATE.read_text())
        self.assertTrue(lines_with(PROVISION, COMPOSE_PLUGIN))

    def test_fleets_boot_the_same_image(self):
        images = {str(setting[0].relative_to(ROOT)): block_value(*setting) for setting in FLEET_IMAGE_SETTINGS}
        self.assertEqual(len(set(images.values())), 1, images)


class BuildAppImageTest(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory(prefix="meterengine-app-image-")
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        image_dir = self.root / "image"
        image_dir.mkdir()
        source = BUILD.read_text()
        for name in ("STOP_WAIT_POLLS", "IMAGE_WAIT_POLLS"):
            source = re.sub(rf"^{name}=.*$", f"{name}=3", source, flags=re.M)
        self.script = image_dir / BUILD.name
        self.script.write_text(source)
        shutil.copy(PROVISION, image_dir / PROVISION.name)
        bin_dir = self.root / "bin"
        bin_dir.mkdir()
        for name, body in (("aws", FAKE), ("git", FAKE), ("sleep", "#!/bin/sh\nexit 0\n")):
            path = bin_dir / name
            path.write_text(body)
            path.chmod(path.stat().st_mode | stat.S_IEXEC)
        self.state_file = self.root / "state.json"
        self.trace = self.root / "trace"
        self.env = dict(os.environ, PATH=f"{bin_dir}{os.pathsep}{os.environ['PATH']}", FAKE_STATE=str(self.state_file), TRACE=str(self.trace))
        self.set_state()

    def set_state(self, **changes):
        state = {"account": "928618307647", "dirty": "", "in_develop": True, "commit": SOURCE_COMMIT,
                 "builder_states": ["pending", "running", "stopped"], "image_states": ["pending", "available"],
                 "image": "arm64\t/dev/xvda\t30", "security_group_deletes": [0]}
        state.update(changes)
        self.state_file.write_text(json.dumps(state))
        self.trace.write_text("")

    def build(self, arguments=(BASE_IMAGE,)):
        return subprocess.run(["bash", str(self.script), *arguments], env=self.env, text=True, capture_output=True, timeout=30)

    def build_with_closed_log(self, restore_signals=True):
        reader, writer = os.pipe()
        os.close(reader)
        try:
            return subprocess.run(["bash", str(self.script), BASE_IMAGE], env=self.env, stdout=subprocess.DEVNULL, stderr=writer,
                                  timeout=30, restore_signals=restore_signals)
        finally:
            os.close(writer)

    def calls(self, command):
        return [line for line in self.trace.read_text().splitlines() if line.startswith(command)]

    def assert_cleaned_up(self):
        self.assertEqual(self.calls("aws ec2 terminate-instances"), ["aws ec2 terminate-instances --instance-ids i-builder"])
        self.assertEqual(self.calls("aws ec2 delete-security-group"), ["aws ec2 delete-security-group --group-id sg-builder"])

    def test_script_is_executable(self):
        self.assertTrue(os.access(BUILD, os.X_OK))

    def test_rejects_bad_arguments_before_any_call(self):
        for arguments in ((), ("ami-xyz",), (BASE_IMAGE, "extra")):
            result = self.build(arguments)
            self.assertNotEqual(result.returncode, 0, arguments)
            self.assertIn("사용법", result.stderr)
        self.assertEqual(self.trace.read_text(), "")

    def test_successful_build_prints_only_the_image_id(self):
        result = self.build()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout, "image_id=ami-result\n")

    def test_image_records_where_it_came_from(self):
        self.build()
        [create] = self.calls("aws ec2 create-image")
        for tag in ("Key=Role,Value=app-image", f"Key=BaseImage,Value={BASE_IMAGE}", "Key=ComposeVersion,Value=v5.5.0",
                    f"Key=SourceCommit,Value={SOURCE_COMMIT}", "Key=Name,Value=meterengine-app-al2023-arm64-"):
            self.assertIn(tag, create)
        self.assertIn("ResourceType=snapshot,Tags=[", create)

    def test_image_is_protected_from_deregistration(self):
        self.build()
        self.assertEqual(self.calls("aws ec2 enable-image-deregistration-protection"),
                         ["aws ec2 enable-image-deregistration-protection --image-id ami-result"])

    def test_builder_launches_from_the_base_image_in_the_public_subnet(self):
        self.build()
        [launch] = self.calls("aws ec2 run-instances")
        for setting in (f"--image-id {BASE_IMAGE}", "--instance-type t4g.medium", "HttpTokens=required",
                        "--instance-initiated-shutdown-behavior stop", "/provision.sh",
                        '"SubnetId":"subnet-0348a18c1d399c193"', '"Groups":["sg-builder"]', '"AssociatePublicIpAddress":true'):
            self.assertIn(setting, launch)

    def test_builder_has_no_role_and_only_https_egress(self):
        self.build()
        [launch] = self.calls("aws ec2 run-instances")
        self.assertNotIn("--iam-instance-profile", launch)
        [revoke] = self.calls("aws ec2 revoke-security-group-egress")
        self.assertIn('"IpProtocol":"-1"', revoke)
        [authorize] = self.calls("aws ec2 authorize-security-group-egress")
        self.assertIn('"IpProtocol":"tcp","FromPort":443,"ToPort":443', authorize)
        self.assertEqual(self.calls("aws ec2 authorize-security-group-ingress"), [])
        self.assertLess(self.trace.read_text().index("revoke-security-group-egress"), self.trace.read_text().index("run-instances"))

    def test_builder_and_security_group_are_removed_after_a_build(self):
        self.build()
        self.assert_cleaned_up()
        trace = self.trace.read_text()
        self.assertLess(trace.index("terminate-instances"), trace.index("delete-security-group"))

    def test_security_group_delete_is_retried_while_the_builder_lets_go(self):
        self.set_state(security_group_deletes=[1, 0])
        result = self.build()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(len(self.calls("aws ec2 delete-security-group")), 2)
        self.assertNotIn("지우지 못했다", result.stderr)

    def test_security_group_left_behind_is_reported_with_the_command_to_remove_it(self):
        self.set_state(security_group_deletes=[1])
        result = self.build()
        self.assertEqual(result.stdout, "image_id=ami-result\n")
        self.assertIn("aws ec2 delete-security-group --group-id sg-builder", result.stderr)

    def test_builder_is_removed_when_the_log_reader_is_gone(self):
        self.build_with_closed_log()
        self.assert_cleaned_up()

    # 터미널이 끊긴 경우처럼 신호 없이 쓰기만 실패한다.
    def test_builder_is_removed_when_the_log_cannot_be_written(self):
        self.build_with_closed_log(restore_signals=False)
        self.assert_cleaned_up()

    def test_wrong_account_launches_nothing(self):
        self.set_state(account="111111111111")
        result = self.build()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("111111111111", result.stderr)
        self.assertEqual(self.calls("aws ec2"), [])

    def test_uncommitted_provision_launches_nothing(self):
        self.set_state(dirty=" M provision.sh")
        result = self.build()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("provision.sh", result.stderr)
        self.assertEqual(self.calls("aws ec2"), [])

    def test_commit_missing_from_develop_launches_nothing(self):
        self.set_state(in_develop=False)
        result = self.build()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("develop", result.stderr)
        self.assertEqual(self.calls("aws ec2"), [])

    def test_source_check_fetches_develop_before_comparing(self):
        self.build()
        trace = self.trace.read_text()
        self.assertLess(trace.index("fetch --quiet origin develop"), trace.index("merge-base --is-ancestor HEAD origin/develop"))

    def test_builder_that_never_stops_shows_its_console_and_leaves_no_image(self):
        self.set_state(builder_states=["running"])
        result = self.build()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("console log", result.stderr)
        self.assertEqual(self.calls("aws ec2 create-image"), [])
        self.assert_cleaned_up()

    def test_image_is_checked_and_protected_only_after_it_becomes_available(self):
        self.build()
        trace = self.trace.read_text()
        self.assertEqual(len(self.calls("aws ec2 describe-images --image-ids ami-result --query Images[0].State")), 2)
        self.assertLess(trace.rindex("Images[0].State"), trace.index("Images[0].[Architecture"))
        self.assertLess(trace.rindex("Images[0].State"), trace.index("enable-image-deregistration-protection"))

    def test_failed_image_stops_the_wait_at_once(self):
        self.set_state(image_states=["failed"])
        result = self.build()
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(result.stdout, "")
        self.assertIn("failed", result.stderr)
        self.assertEqual(len(self.calls("aws ec2 describe-images --image-ids ami-result --query Images[0].State")), 1)
        self.assertEqual(self.calls("aws ec2 enable-image-deregistration-protection"), [])
        self.assert_cleaned_up()

    def test_image_still_pending_at_the_limit_is_reported_as_unprotected(self):
        self.set_state(image_states=["pending"])
        result = self.build()
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(result.stdout, "")
        self.assertIn("ami-result", result.stderr)
        self.assertIn("삭제 보호 없이", result.stderr)
        self.assertEqual(self.calls("aws ec2 enable-image-deregistration-protection"), [])
        self.assert_cleaned_up()

    def test_image_that_does_not_fit_the_launch_template_fails(self):
        for shape in ("x86_64\t/dev/xvda\t8", "arm64\t/dev/sda1\tNone", "arm64\t/dev/xvda\t40"):
            with self.subTest(shape=shape):
                self.set_state(image=shape)
                result = self.build()
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(result.stdout, "")
                self.assertIn("ami-result", result.stderr)
                self.assertIn("delete-snapshot", result.stderr)
                self.assertEqual(self.calls("aws ec2 enable-image-deregistration-protection"), [])
                self.assert_cleaned_up()


if __name__ == "__main__":
    unittest.main()
