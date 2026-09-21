import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from bridge.const import LOGS_DIR as BRIDGE_LOGS_DIR
from csvdemo import send_cmd


class LogsDirTest(unittest.TestCase):

    def test_로그는_demo_logs에_쌓인다(self):
        demo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
        self.assertEqual(send_cmd.LOGS_DIR, os.path.join(demo, "logs"))

    def test_브리지와_같은_곳을_본다(self):
        self.assertEqual(send_cmd.LOGS_DIR, BRIDGE_LOGS_DIR)


if __name__ == "__main__":
    unittest.main()
