"""브리지 서버의 판정과 파일 갈아타기 (MS2-169).

여기 있는 것은 전부 코드 리뷰에서 나온 결함의 회귀 방지다. 네트워크는 쓰지 않는다.
"""

import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from bridge import server
from bridge.state import BridgeConfig, BridgeState


class FakeResult:
    def __init__(self, status, body):
        self.status = status
        self.body = body


class OutcomeTest(unittest.TestCase):
    """send_cmd._classify와 같은 어휘여야 한다. verify가 둘을 같은 뜻으로 읽는다."""

    def test_200은_duplicate_여부로_갈린다(self):
        self.assertEqual(server._outcome(FakeResult(200, {"duplicate": False})), "new")
        self.assertEqual(server._outcome(FakeResult(200, {"duplicate": True})), "duplicate")

    def test_400만_rejected다(self):
        self.assertEqual(server._outcome(FakeResult(400, {"title": "잘못된 요청"})), "rejected")

    def test_5xx는_error다(self):
        # rejected로 접으면 verify가 "거절됐으니 저장 안 됨"으로 확정한다. 실제로는
        # 저장되고 응답만 실패했을 수 있어, 저장 여부를 알 수 없다고 알려야 한다.
        self.assertEqual(server._outcome(FakeResult(500, None)), "error")
        self.assertEqual(server._outcome(FakeResult(503, {})), "error")

    def test_200이지만_duplicate가_없으면_error다(self):
        self.assertEqual(server._outcome(FakeResult(200, {})), "error")
        self.assertEqual(server._outcome(FakeResult(200, None)), "error")


class LogRollTest(unittest.TestCase):
    """상주 프로세스라 자정을 넘긴다. 파일명을 기동 때 한 번만 정하면 안 된다."""

    def _sender(self, directory):
        state = BridgeState(os.path.join(directory, "state.json"), "scope")
        return server.Sender(BridgeConfig(), state, directory)

    def test_날짜가_바뀌면_그날_파일로_갈아탄다(self):
        original = server._today
        with tempfile.TemporaryDirectory() as directory:
            server._today = lambda: "20260824"
            sender = self._sender(directory)
            try:
                first = sender.writer.path
                server._today = lambda: "20260825"
                sender._roll()
                second = sender.writer.path
            finally:
                sender.close()
                server._today = original
            self.assertTrue(first.endswith("bridge-20260824.jsonl"), first)
            self.assertTrue(second.endswith("bridge-20260825.jsonl"), second)

    def test_같은_날에는_파일을_유지한다(self):
        original = server._today
        with tempfile.TemporaryDirectory() as directory:
            server._today = lambda: "20260824"
            sender = self._sender(directory)
            try:
                first = sender.writer
                sender._roll()
                self.assertIs(sender.writer, first)
            finally:
                sender.close()
                server._today = original


if __name__ == "__main__":
    unittest.main()
