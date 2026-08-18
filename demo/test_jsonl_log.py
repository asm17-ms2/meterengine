"""JSONL 로그 포맷의 쓰기/읽기 왕복과 손상 내성 검증."""

import os
import tempfile
import unittest
from decimal import Decimal

from jsonl_log import JsonlLogWriter, read_log


def _write_sample(path):
    with JsonlLogWriter(path) as writer:
        writer.write_run_header(
            started_at_text="2026-08-14T10:00:00+09:00",
            base_url="http://localhost:8080",
            org_id="d7cee55d-8c82-4afc-b996-6749d8b26a4e",
            csv_path="events.csv",
            argv=["send", "--csv", "events.csv"],
        )
        writer.write_send(
            seq=1,
            sent_at_text="2026-08-14T10:00:01+09:00",
            request_body_text='{"transaction_id": "evt-1", "customer_id": "c", '
            '"event_type": "chat_completion", "properties": {"token": 500.00}, '
            '"timestamp": "2026-08-01T00:00:00+09:00"}',
            status=200,
            response_text='{"transaction_id": "evt-1", "duplicate": false}',
            outcome="new",
            error=None,
            elapsed_ms=12,
        )
        writer.write_send(
            seq=2,
            sent_at_text="2026-08-14T10:00:02+09:00",
            request_body_text='{"transaction_id": "evt-2", "customer_id": "x", '
            '"event_type": "chat_completion", "properties": {"token": 1}, '
            '"timestamp": "2026-08-01T00:00:00+09:00"}',
            status=400,
            response_text='{"status": 400, "code": "unknown_customer_reference"}',
            outcome="rejected",
            error=None,
            elapsed_ms=8,
        )
        writer.write_send(
            seq=3,
            sent_at_text="2026-08-14T10:00:03+09:00",
            request_body_text='{"transaction_id": "evt-3", "customer_id": "c", '
            '"event_type": "chat_completion", "properties": {"token": 2}, '
            '"timestamp": "2026-08-01T00:00:00+09:00"}',
            status=None,
            response_text=None,
            outcome="error",
            error="연결이 거부되었습니다",
            elapsed_ms=None,
        )


class JsonlLogRoundTripTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.path = os.path.join(self.tmp.name, "send-test.jsonl")

    def test_헤더와_레코드가_왕복한다(self):
        _write_sample(self.path)
        result = read_log(self.path)
        self.assertEqual(result.header.base_url, "http://localhost:8080")
        self.assertEqual(result.header.org_id, "d7cee55d-8c82-4afc-b996-6749d8b26a4e")
        self.assertEqual(len(result.records), 3)
        self.assertEqual(result.warnings, [])
        outcomes = [r.outcome for r in result.records]
        self.assertEqual(outcomes, ["new", "rejected", "error"])

    def test_소수_자릿수가_보존된다(self):
        _write_sample(self.path)
        record = read_log(self.path).records[0]
        self.assertEqual(record.request["properties"]["token"], Decimal("500.00"))
        self.assertEqual(str(record.request["properties"]["token"]), "500.00")

    def test_거절_응답의_problem_json이_보존된다(self):
        _write_sample(self.path)
        record = read_log(self.path).records[1]
        self.assertEqual(record.status, 400)
        self.assertEqual(record.response["code"], "unknown_customer_reference")

    def test_error_레코드는_status와_response가_없다(self):
        _write_sample(self.path)
        record = read_log(self.path).records[2]
        self.assertIsNone(record.status)
        self.assertIsNone(record.response)
        self.assertEqual(record.error, "연결이 거부되었습니다")

    def test_잘린_마지막_라인은_경고와_함께_건너뛴다(self):
        _write_sample(self.path)
        with open(self.path, "a", encoding="utf-8") as f:
            f.write('{"v": 1, "type": "send", "seq": 4, "requ')
        result = read_log(self.path)
        self.assertEqual(len(result.records), 3)
        self.assertEqual(len(result.warnings), 1)

    def test_모르는_type은_건너뛴다(self):
        _write_sample(self.path)
        with open(self.path, "a", encoding="utf-8") as f:
            f.write('{"v": 1, "type": "future_thing", "x": 1}\n')
        result = read_log(self.path)
        self.assertEqual(len(result.records), 3)

    def test_깨진_properties도_로그를_깨뜨리지_않는다(self):
        # csvio는 값을 검증하지 않으므로 와이어 바디가 깨진 JSON일 수 있다 (400 시연 목적).
        # 그래도 로그 파일은 항상 읽을 수 있어야 한다.
        _write_sample(self.path)
        with JsonlLogWriter(os.path.join(self.tmp.name, "broken.jsonl")) as writer:
            writer.write_send(
                seq=1,
                sent_at_text="2026-08-14T10:00:01+09:00",
                request_body_text='{"transaction_id": "evt-x", "customer_id": "c", '
                '"event_type": "e", "properties": {token: 5}, "timestamp": "t"}',
                status=400,
                response_text='{"status": 400, "code": "validation_error"}',
                outcome="rejected",
                error=None,
                elapsed_ms=3,
            )
        result = read_log(os.path.join(self.tmp.name, "broken.jsonl"))
        self.assertEqual(len(result.records), 1)
        self.assertEqual(result.records[0].outcome, "rejected")
        self.assertIn("{token: 5}", result.records[0].request_raw)
        self.assertEqual(result.records[0].response["code"], "validation_error")

    def test_개행이_든_properties도_한_라인으로_남는다(self):
        # 따옴표 친 CSV 셀에는 개행이 합법이고, JSON 토큰 사이 개행은 유효한 JSON이다.
        path = os.path.join(self.tmp.name, "newline.jsonl")
        with JsonlLogWriter(path) as writer:
            writer.write_send(
                seq=1,
                sent_at_text="2026-08-14T10:00:01+09:00",
                request_body_text='{"transaction_id": "evt-n", "customer_id": "c", '
                '"event_type": "e", "properties": {"token": 7,\n "model": "m"}, '
                '"timestamp": "2026-08-01T00:00:00+09:00"}',
                status=200,
                response_text='{"transaction_id": "evt-n", "duplicate": false}',
                outcome="new",
                error=None,
                elapsed_ms=3,
            )
        with open(path, encoding="utf-8") as f:
            self.assertEqual(len(f.read().rstrip("\n").split("\n")), 1)
        record = read_log(path).records[0]
        self.assertEqual(record.request["properties"]["token"], 7)

    def test_중간_라인_손상은_오류다(self):
        _write_sample(self.path)
        lines = open(self.path, encoding="utf-8").read().splitlines()
        lines[1] = lines[1][:20]
        with open(self.path, "w", encoding="utf-8") as f:
            f.write("\n".join(lines) + "\n")
        with self.assertRaises(ValueError):
            read_log(self.path)


if __name__ == "__main__":
    unittest.main()
