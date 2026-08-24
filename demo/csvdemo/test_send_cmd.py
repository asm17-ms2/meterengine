"""CSV 데모 전송의 경로 약속 (MS2-169).

send가 로그를 어디에 쌓는지는 README와 verify 안내가 같이 걸린 값이라, 파일을
옮길 때 조용히 어긋나기 쉽다. 실제로 core/csvdemo 분리에서 한 번 어긋났다.
"""

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
        # 두 층이 각자 계산하므로 갈라질 수 있다. verify가 두 로그를 같은 자리에서
        # 찾는다는 약속을 여기서 잡는다.
        self.assertEqual(send_cmd.LOGS_DIR, BRIDGE_LOGS_DIR)


if __name__ == "__main__":
    unittest.main()
