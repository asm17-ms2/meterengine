from pathlib import Path
import re
import subprocess
import unittest


ROOT = Path(__file__).resolve().parents[2]
MODULE = ROOT / "deploy/terraform/modules/app_fleet"
TEMPLATE = MODULE / "templates/app-user-data.sh.tftpl"
ASG = MODULE / "asg.tf"
TEST_STORAGE = ROOT / "measurements/test-storage/sut/terraform"

# 부팅 계약. 측정 환경처럼 이 템플릿을 부르는 곳도 같은 키를 넘긴다.
BOOT_TEMPLATE_VARS = {"environment", "parameter_path", "app_version_parameter_name", "aws_region", "repository_url"}
SAMPLE_VALUES = {
    "environment": "staging",
    "parameter_path": "/meterengine/staging",
    "app_version_parameter_name": "/meterengine/staging/app-version",
    "aws_region": "ap-northeast-2",
    "repository_url": "https://github.com/asm17-ms2/meterengine.git",
}
TEMPLATE_REFERENCE = re.compile(r"(?<!\$)\$\{([^}]*)\}")
IMAGE_TOOL_LOOP = "for tool in "


def template_references():
    return set(TEMPLATE_REFERENCE.findall(TEMPLATE.read_text()))


def boot_template_keys(path):
    block = re.search(r"^  boot_template_vars = \{\n(.*?)^  \}", path.read_text(), re.M | re.S).group(1)
    return set(re.findall(r"^\s+(\w+)\s+=", block, re.M))


def render():
    rendered = TEMPLATE_REFERENCE.sub(lambda match: SAMPLE_VALUES[match.group(1)], TEMPLATE.read_text())
    return rendered.replace("$${", "${").replace("%%{", "%{")


def image_tool_checks():
    script = render()
    return script[script.index(IMAGE_TOOL_LOOP):script.index("git clone")]


class BootTemplateTest(unittest.TestCase):
    def test_template_uses_exactly_the_contract_variables(self):
        self.assertEqual(template_references(), BOOT_TEMPLATE_VARS)

    def test_module_passes_exactly_the_contract_variables(self):
        self.assertEqual(boot_template_keys(ASG), BOOT_TEMPLATE_VARS)

    def test_test_storage_passes_exactly_the_contract_variables(self):
        self.assertEqual(boot_template_keys(TEST_STORAGE / "locals.tf"), BOOT_TEMPLATE_VARS)

    def test_test_storage_reads_this_template(self):
        relative = re.search(r'^  boot_template_path = "\$\{path\.root\}/([^"]+)"$', (TEST_STORAGE / "locals.tf").read_text(), re.M).group(1)
        self.assertEqual((TEST_STORAGE / relative).resolve(), TEMPLATE)

    def test_template_has_no_directives(self):
        self.assertNotRegex(TEMPLATE.read_text(), r"(?<!%)%\{")

    def test_rendered_script_parses(self):
        result = subprocess.run(["bash", "-n"], input=render(), text=True, capture_output=True)
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_unset_version_stops_before_cloning(self):
        script = render()
        self.assertLess(script.index("exit 1"), script.index("git clone"))
        self.assertIn('"$version" =~ ^[0-9a-f]{40}$', script)

    def test_boot_installs_nothing(self):
        script = render()
        for word in ("dnf", "curl", "install -D"):
            self.assertNotIn(word, script)

    def test_boot_checks_image_tools_before_cloning(self):
        script = render()
        self.assertLess(script.index('"$version" =~'), script.index(IMAGE_TOOL_LOOP))
        self.assertIn('command -v "$tool"', image_tool_checks())
        self.assertIn("docker compose version", image_tool_checks())

    def test_missing_image_tool_points_at_the_launch_template_image(self):
        messages = [line for line in image_tool_checks().splitlines() if line.lstrip().startswith("echo")]
        self.assertTrue(messages)
        for message in messages:
            self.assertIn("image_id", message)


if __name__ == "__main__":
    unittest.main()
