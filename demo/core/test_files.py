import json
import os
import stat
import tempfile
import unittest

from core.files import write_json_atomic


class WriteJsonAtomicModeTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.path = os.path.join(self.tmp.name, "bridge.json")
        previous = os.umask(0o022)
        self.addCleanup(os.umask, previous)

    def mode(self):
        return stat.S_IMODE(os.stat(self.path).st_mode)

    def test_권한을_주면_그_권한으로_쓴다(self):
        write_json_atomic(self.path, {"a": 1}, mode=0o600)
        self.assertEqual(self.mode(), 0o600)
        with open(self.path, encoding="utf-8") as f:
            self.assertEqual(json.load(f), {"a": 1})

    def test_넓게_열린_기존_파일도_좁힌다(self):
        with open(self.path, "w", encoding="utf-8") as f:
            f.write("{}")
        os.chmod(self.path, 0o644)
        with open(self.path + ".tmp", "w", encoding="utf-8") as f:
            f.write("")
        os.chmod(self.path + ".tmp", 0o644)
        write_json_atomic(self.path, {"a": 2}, mode=0o600)
        self.assertEqual(self.mode(), 0o600)

    def test_권한을_주지_않으면_umask를_따른다(self):
        write_json_atomic(self.path, {"a": 1})
        self.assertEqual(self.mode(), 0o644)


if __name__ == "__main__":
    unittest.main()
