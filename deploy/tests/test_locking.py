import os
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import sys
import tempfile
import time
import unittest


ROOT = Path(os.environ.get("DEPLOY_TEST_REPO", Path(__file__).resolve().parents[2]))


def function(source, name):
    match = re.search(rf"^{name}\(\) \{{.*?^\}}", source, re.M | re.S)
    if match is None:
        raise AssertionError(f"Missing function: {name}")
    return match.group()


@unittest.skipUnless(sys.platform == "linux" and shutil.which("flock"), "Linux flock required")
class DeploymentLockTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.repo = self.root / "repo"
        (self.repo / "deploy").mkdir(parents=True)
        source = (ROOT / "deploy/deploy.sh").read_text()
        lock_path = re.findall(r"^LOCK_FILE=(.+)$", source, re.M)[-1]
        self.lock = self.root / Path(lock_path).name
        self.legacy_lock = self.root / "deploy.lock"
        self.trace = self.root / "trace"
        self.trace.touch()
        self.release = self.root / "release"
        self.env = dict(os.environ, LOCK_FILE=str(self.lock), LEGACY_LOCK_FILE=str(self.legacy_lock), TRACE=str(self.trace),
                        RELEASE=str(self.release), SHA="a" * 40, REF="b" * 40)
        if "\nacquire_deploy_lock() {" in source:
            self.acquire = function(source, "acquire_deploy_lock")
        else:
            self.acquire = 'acquire_deploy_lock() {\n' + re.search(
                r'\texec 9>"\$LOCK_FILE"\n\tflock -n 9[^\n]*', source
            ).group() + '\n}'
        script = ('#!/usr/bin/env bash\nset -euo pipefail\n'
                  'die() { echo "$*" >&2; exit 1; }\n' + self.acquire + '\n'
                  'if [ "${LEGACY:-no}" = reopens ]; then\n'
                  '  LOCK_FILE=$LEGACY_LOCK_FILE\n'
                  '  exec 9>"$LOCK_FILE"\n'
                  '  flock -n 9 || die "legacy lock failed"\n'
                  'elif [ "${LEGACY:-no}" != yes ]; then acquire_deploy_lock; fi\n'
                  'echo "started $*" >> "$TRACE"\n'
                  'if [ "${HOLD:-no}" = yes ]; then\n'
                  '  while [ ! -f "$RELEASE" ]; do sleep 0.02; done\n'
                  'fi\nexit "${RESULT:-0}"\n')
        deploy = self.repo / "deploy/deploy.sh"
        deploy.write_text(script)
        deploy.chmod(0o755)

    def entrypoints(self):
        entries = {}
        for relative in (".github/workflows/cd.yml", "deploy/README.md",
                         "measurements/test-slo/k6/README.md"):
            source = (ROOT / relative).read_text()
            for index, command in enumerate(re.findall(r'--parameters commands="([^"\n]+)"', source)):
                entries[f"{relative}:{index}"] = command
            if relative == "deploy/README.md":
                entries["manual"] = re.search(r"^(?:\(exec [89]>|cd /opt/meterengine &&).*deploy/deploy.sh.*$",
                                               source, re.M).group()
        config = self.root / "deploy.conf"
        config.write_text("PARAM_PATH=/meterengine/test\nSITE_DOMAIN=test.example\n")
        for target in ("test-slo", "test-storage"):
            source = (ROOT / f"measurements/{target}/sut/scripts/sut-deploy.sh").read_text()
            generate = ('REMOTE_DEPLOY_CONFIG=' + shlex.quote(str(config)) + '\n'
                        'REMOTE_REPO_DIR=/opt/meterengine\n'
                        'FIRST_SHA_WITH_DEPLOY_CONF=' + 'c' * 40 + '\n'
                        + function(source, "deploy_command") + '\n'
                        'deploy_command "$SHA" /meterengine/test test.example\n')
            command = subprocess.run(["bash", "-c", generate], env=self.env,
                                     text=True, capture_output=True, check=True).stdout
            entries[target] = command
        return {key: value.replace("/etc/meterengine/deploy-command.lock", str(self.root / "deploy-command.lock"))
                .replace("/etc/meterengine/deploy.lock", str(self.legacy_lock))
                .replace("/opt/meterengine", str(self.repo))
                .replace("<git-sha>", "$SHA").replace("<SHA>", "$SHA")
                for key, value in entries.items()}

    def run_entry(self, command, shell="bash", invocation="ssm", **env):
        script = '''set -euo pipefail
git() {
    if [ -e /proc/self/fd/8 ] || [ -e /proc/self/fd/9 ]; then echo 'git inherited lock' >&2; return 97; fi
    printf 'git %s\n' "$*" >> "$TRACE"
}
''' + command
        command_file = self.root / "command.sh"
        command_file.write_text(script)
        command_file.chmod(0o755)
        if invocation == "command":
            args = [shell, "-c", script]
        elif invocation == "file":
            args = [shell, str(command_file)]
        else:
            args = [shell, "-c", shlex.quote(str(command_file))]
        return subprocess.run(args, env=dict(self.env, **env),
                              text=True, capture_output=True, timeout=5)

    def holder(self, legacy="no"):
        command = ('exec 8>"$LOCK_FILE"\nflock -n 8\ncd ' + shlex.quote(str(self.repo))
                   + '\n./deploy/deploy.sh "$SHA"\n')
        process = subprocess.Popen(["bash", "-ec", command], env=dict(self.env, HOLD="yes", LEGACY=legacy),
                                   stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)

        def finish():
            self.release.touch()
            try:
                process.communicate(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()
                process.communicate()
        self.addCleanup(finish)
        deadline = time.monotonic() + 5
        while time.monotonic() < deadline:
            if self.trace.exists() and "started" in self.trace.read_text():
                return process
            if process.poll() is not None:
                break
            time.sleep(0.02)
        self.fail("Deployment did not start: " + process.communicate(timeout=1)[1])

    def test_all_entrypoints_reject_before_git(self):
        self.holder(legacy="yes")
        for name, command in self.entrypoints().items():
            with self.subTest(entry=name):
                before = self.trace.read_text()
                result = self.run_entry(command, SHA="d" * 40, REF="e" * 40)
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(self.trace.read_text(), before)

    def test_entrypoints_reuse_lock_and_close_it_for_git(self):
        for name, command in self.entrypoints().items():
            with self.subTest(entry=name):
                before = self.trace.read_text()
                result = self.run_entry(command)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertIn("started", self.trace.read_text()[len(before):])

    def test_remote_entrypoints_work_in_posix_shell(self):
        for name, command in self.entrypoints().items():
            with self.subTest(entry=name):
                result = self.run_entry(command, shell="sh")
                self.assertEqual(result.returncode, 0, result.stderr)

    def test_direct_script_can_acquire_lock(self):
        result = subprocess.run([str(self.repo / "deploy/deploy.sh")], env=self.env,
                                text=True, capture_output=True, timeout=5)
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_outer_lock_covers_legacy_script_without_lock(self):
        self.holder(legacy="yes")
        probe = subprocess.run(["flock", "-n", str(self.lock), "true"])
        self.assertNotEqual(probe.returncode, 0)

    def test_older_script_can_reopen_its_legacy_lock(self):
        for invocation in ("ssm", "file", "command"):
            for name, command in self.entrypoints().items():
                with self.subTest(entry=name, invocation=invocation):
                    before = self.trace.read_text()
                    result = self.run_entry(command, invocation=invocation, LEGACY="reopens")
                    self.assertEqual(result.returncode, 0, result.stderr)
                    self.assertIn("started", self.trace.read_text()[len(before):])

    def test_old_script_reopening_fd9_does_not_release_outer_lock(self):
        self.holder(legacy="reopens")
        result = self.run_entry(next(iter(self.entrypoints().values())))
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.trace.read_text().count("started"), 1)
        self.assertNotEqual(subprocess.run(["flock", "-n", str(self.lock), "true"]).returncode, 0)

    def test_direct_script_cannot_enter_while_locked(self):
        self.holder()
        result = subprocess.run([str(self.repo / "deploy/deploy.sh")], env=self.env,
                                text=True, capture_output=True, timeout=5)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.trace.read_text().count("started"), 1)

    def test_unrelated_fd_cannot_bypass_lock(self):
        self.holder()
        result = self.run_entry('exec 8>' + shlex.quote(str(self.root / "other.lock"))
                                + '\n' + shlex.quote(str(self.repo / "deploy/deploy.sh")))
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.trace.read_text().count("started"), 1)

    def test_lock_is_released_after_failure(self):
        command = next(iter(self.entrypoints().values()))
        result = self.run_entry(command, RESULT="23")
        self.assertEqual(result.returncode, 23, result.stderr)
        self.assertEqual(subprocess.run(["flock", "-n", str(self.lock), "true"]).returncode, 0)


if __name__ == "__main__":
    unittest.main()
