"""verify가 로그를 소스로 받을 때의 판정 (MS2-169).

서버에 붙지 않는 부분만 본다. 손상된 로그를 어떻게 다루는지가 그 자리다.
"""

import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from core.jsonl_log import JsonlLogWriter
from csvdemo.render import Console
from csvdemo.verify_cmd import _load_log_source


class Args:
    def __init__(self, log):
        self.log = log
        self.csv = None
        self.base_url = None
        self.org_id = None
        self.month = None
        self.timeout = 5.0


def _header(writer, base_url="http://localhost:8080"):
    writer.write_run_header(
        started_at_text="2026-08-24T10:00:00+09:00",
        base_url=base_url,
        org_id="d7cee55d-8c82-4afc-b996-6749d8b26a4e",
        csv_path=None,
        argv=["send"],
    )


def _send(writer, seq):
    writer.write_send(
        seq=seq,
        sent_at_text="2026-08-24T10:00:0%d+09:00" % seq,
        request_body_text='{"transaction_id": "evt-%d", "customer_id": "c", '
        '"event_type": "llm_request", "properties": {"input_tokens": 5}, '
        '"timestamp": "2026-08-24T10:00:00+09:00"}' % seq,
        status=200,
        response_text='{"transaction_id": "evt-%d", "duplicate": false}' % seq,
        outcome="new",
        error=None,
        elapsed_ms=3,
    )


class DamagedLogTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.console = Console(color=False)

    def path(self, name):
        return os.path.join(self.directory.name, name)

    def test_중간이_깨진_send_로그는_판정하지_않는다(self):
        """레코드가 빠진 채 남은 합계가 우연히 맞으면 "일치"로 0을 내준다.

        그 0을 보고 다음 단계로 넘어가면 손상을 통과시킨 것이 된다.
        """
        path = self.path("send-20260824.jsonl")
        with JsonlLogWriter(path) as writer:
            _header(writer)
            _send(writer, 1)
            _send(writer, 2)
        with open(path, encoding="utf-8") as f:
            lines = f.read().splitlines()
        lines[1] = lines[1][:30]  # 중간 한 줄이 잘렸다
        with open(path, "w", encoding="utf-8") as f:
            f.write("\n".join(lines) + "\n")

        self.assertIsNone(_load_log_source(Args(path), self.console))

    def test_브리지_로그는_경고까지만_한다(self):
        """하루치를 이어쓰다 재시작으로 잘리는 것은 정상 범위다.

        여기서 거부하면 그날 기록 전체가 검증 불가가 된다. 헤더가 여럿인 것으로
        브리지 로그를 가른다.
        """
        path = self.path("bridge-20260824.jsonl")
        with JsonlLogWriter(path) as writer:
            _header(writer)
            _send(writer, 1)
        with open(path, "a", encoding="utf-8") as f:
            f.write('{"v": 1, "type": "send", "seq": 2, "requ')
        with JsonlLogWriter(path, append=True) as writer:  # 재시작
            _header(writer)
            _send(writer, 3)

        source = _load_log_source(Args(path), self.console)
        self.assertIsNotNone(source)
        self.assertEqual(len(source.stored), 2)

    def test_깨끗한_로그는_그대로_읽는다(self):
        path = self.path("send-20260824.jsonl")
        with JsonlLogWriter(path) as writer:
            _header(writer, "https://meterengine.com")
            _send(writer, 1)
        source = _load_log_source(Args(path), self.console)
        self.assertEqual(len(source.stored), 1)
        # 헤더의 전송 대상을 써야 한다. 놓치면 엉뚱한 서버를 검사한다.
        self.assertEqual(source.client.base_url, "https://meterengine.com")


if __name__ == "__main__":
    unittest.main()
