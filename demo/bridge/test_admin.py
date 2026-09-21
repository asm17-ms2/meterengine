import json
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from bridge import admin


class ApplyClaudeSettingsTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.path = os.path.join(self.directory.name, "settings.json")

    def write(self, data):
        with open(self.path, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False)

    def read(self, path=None):
        with open(path or self.path, encoding="utf-8") as f:
            return json.load(f)

    def apply(self):
        plan = admin.plan_claude_settings(self.path, "127.0.0.1", 4318)
        return admin.apply_claude_settings(plan)

    def test_기존_설정을_지우지_않는다(self):
        self.write({"model": "opus", "env": {"MY_VAR": "1"}})
        self.apply()
        settings = self.read()
        self.assertEqual(settings["model"], "opus")
        self.assertEqual(settings["env"]["MY_VAR"], "1")
        self.assertEqual(settings["env"]["CLAUDE_CODE_ENABLE_TELEMETRY"], "1")

    def test_백업은_처음_것을_지킨다(self):
        self.write({"model": "opus"})
        backup = self.apply()
        self.assertEqual(self.read(backup), {"model": "opus"})

        settings = self.read()
        del settings["env"]["OTEL_LOGS_EXPORTER"]
        self.write(settings)
        again = self.apply()

        self.assertEqual(again, backup)
        self.assertEqual(self.read(backup), {"model": "opus"})

    def test_파일이_없으면_백업도_없다(self):
        self.assertIsNone(self.apply())
        self.assertTrue(os.path.exists(self.path))

    def test_키_순서를_뒤집지_않는다(self):
        self.write({"zzz": 1, "aaa": 2})
        self.apply()
        with open(self.path, encoding="utf-8") as f:
            body = f.read()
        self.assertLess(body.index('"zzz"'), body.index('"aaa"'))

    def test_임시_파일을_남기지_않는다(self):
        self.write({"model": "opus"})
        self.apply()
        self.assertFalse(os.path.exists(self.path + ".tmp"))

    def test_이미_돼_있으면_바꿀_것이_없다(self):
        self.write({})
        self.apply()
        plan = admin.plan_claude_settings(self.path, "127.0.0.1", 4318)
        self.assertFalse(plan.needed)


if __name__ == "__main__":
    unittest.main()
