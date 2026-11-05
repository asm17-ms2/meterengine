from pathlib import Path
import re
import subprocess
import unittest


ROOT = Path(__file__).resolve().parents[2]
MODULE = ROOT / "deploy/terraform/modules/app_fleet"
TEMPLATE = MODULE / "templates/app-user-data.sh.tftpl"
ASG = MODULE / "asg.tf"

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


def template_references():
    return set(TEMPLATE_REFERENCE.findall(TEMPLATE.read_text()))


def boot_template_keys():
    block = re.search(r"^  boot_template_vars = \{\n(.*?)^  \}", ASG.read_text(), re.M | re.S).group(1)
    return set(re.findall(r"^\s+(\w+)\s+=", block, re.M))


def render():
    rendered = TEMPLATE_REFERENCE.sub(lambda match: SAMPLE_VALUES[match.group(1)], TEMPLATE.read_text())
    return rendered.replace("$${", "${").replace("%%{", "%{")


class BootTemplateTest(unittest.TestCase):
    def test_template_uses_exactly_the_contract_variables(self):
        self.assertEqual(template_references(), BOOT_TEMPLATE_VARS)

    def test_module_passes_exactly_the_contract_variables(self):
        self.assertEqual(boot_template_keys(), BOOT_TEMPLATE_VARS)

    def test_template_has_no_directives(self):
        self.assertNotRegex(TEMPLATE.read_text(), r"(?<!%)%\{")

    def test_rendered_script_parses(self):
        result = subprocess.run(["bash", "-n"], input=render(), text=True, capture_output=True)
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_unset_version_stops_before_installing(self):
        script = render()
        self.assertLess(script.index("exit 1"), script.index("dnf install"))
        self.assertIn('"$version" =~ ^[0-9a-f]{40}$', script)


if __name__ == "__main__":
    unittest.main()
