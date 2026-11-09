from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]
ROOTS = ROOT / "measurements/test-sjpark/sut"
SPECS = {"m7g": ("test-sjpark-m7g", "m7g.large", "db.m7g.large"),
         "t4g": ("test-sjpark-t4g", "t4g.medium", "db.t4g.small")}


def files(root):
    """루트의 파일 이름. .gitignore가 무시하는 로컬 산출물은 뺀다."""
    return {path.name for path in (ROOTS / root).iterdir()
            if path.is_file() and ".tfstate" not in path.name and ".tfvars" not in path.name
            and not path.name.endswith(".tfplan")}


def difference_table_rows():
    """README 차이 표(첫 열이 구분인 표)의 첫 열 값."""
    lines = (ROOT / "measurements/test-sjpark/README.md").read_text().splitlines()
    start = next((index for index, line in enumerate(lines) if line.startswith("| 구분 |")), len(lines))
    rows = []
    for line in lines[start + 2:]:
        if not line.startswith("|"):
            break
        rows.append(line.split("|")[1].strip())
    return rows


# 환경 이름을 먼저 바꿔야 test-sjpark-m7g 안의 m7g가 남지 않는다.
def normalized(root, name):
    environment, instance, db = SPECS[root]
    text = (ROOTS / root / name).read_text()
    return text.replace(environment, "<ENV>").replace(db, "<DB>").replace(instance, "<INSTANCE>")


class MeasurementRootsTest(unittest.TestCase):
    def test_roots_have_the_same_files(self):
        self.assertEqual(files("m7g"), files("t4g"))

    def test_roots_differ_only_in_spec_values(self):
        for name in files("m7g"):
            self.assertEqual(normalized("m7g", name), normalized("t4g", name), name)

    def test_each_root_states_its_spec(self):
        for root, (environment, instance, db) in SPECS.items():
            main = (ROOTS / root / "main.tf").read_text()
            self.assertIn(f'environment       = "{environment}"', main)
            self.assertIn(f'instance_type     = "{instance}"', main)
            self.assertIn(f'db_instance_class = "{db}"', main)
            versions = (ROOTS / root / "versions.tf").read_text()
            self.assertIn(f'key          = "measurements/{environment}/sut/terraform.tfstate"', versions)

    def test_t4g_sets_no_cpu_credit(self):
        modules = "".join(path.read_text() for path in (ROOTS / "module").glob("*.tf"))
        self.assertNotIn("cpu_credits", modules)

    def test_readme_lists_every_production_difference(self):
        rows = difference_table_rows()
        kinds = [(path.name, kind) for path in sorted((ROOTS / "module").glob("*.tf"))
                 for kind in re.findall(r"# 운영과 다름\((.+?)\):", path.read_text())]
        # 주석 형식이 바뀌어 하나도 뽑히지 않으면 아무것도 보지 않고 통과한다.
        self.assertTrue(kinds, "모듈에서 '# 운영과 다름(<구분>):' 주석을 찾지 못했다")
        for name, kind in kinds:
            self.assertIn(kind, rows, f"{name}: README 차이 표에 '{kind}' 행이 없다")

    def test_k6_requires_base_url(self):
        config = (ROOT / "measurements/test-sjpark/k6/lib/config.js").read_text()
        self.assertNotIn("DEFAULT_BASE_URL", config)
        self.assertNotIn("meterengine.com", config)


if __name__ == "__main__":
    unittest.main()
