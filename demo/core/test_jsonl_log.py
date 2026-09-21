import os
import tempfile
import unittest
from decimal import Decimal

from core.jsonl_log import JsonlLogWriter, classify_outcome, read_log


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
            '"type": "chat_completion", "properties": {"token": 500.00}, '
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
            '"type": "chat_completion", "properties": {"token": 1}, '
            '"timestamp": "2026-08-01T00:00:00+09:00"}',
            status=400,
            response_text='{"code": "customer_not_found", "message": "고객을 찾을 수 없습니다"}',
            outcome="rejected",
            error=None,
            elapsed_ms=8,
        )
        writer.write_send(
            seq=3,
            sent_at_text="2026-08-14T10:00:03+09:00",
            request_body_text='{"transaction_id": "evt-3", "customer_id": "c", '
            '"type": "chat_completion", "properties": {"token": 2}, '
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

    def test_거절_응답의_오류_본문이_보존된다(self):
        _write_sample(self.path)
        record = read_log(self.path).records[1]
        self.assertEqual(record.status, 400)
        self.assertEqual(record.response["code"], "customer_not_found")

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

    def test_중간이_깨진_라인도_건너뛰고_경고한다(self):
        _write_sample(self.path)
        with open(self.path, "a", encoding="utf-8") as f:
            f.write('{"v": 1, "type": "send", "seq": 4, "requ')
            f.write('{"v": 1, "type": "run", "started_at": "2026-08-24T10:00:00+09:00"}\n')
            f.write('{"v": 1, "type": "send", "seq": 5, "sent_at": "2026-08-24T10:00:01+09:00",'
                    ' "request": {}, "status": 200, "response": {}, "outcome": "new",'
                    ' "error": null, "elapsed_ms": 3}\n')
        result = read_log(self.path)
        self.assertEqual([r.seq for r in result.records], [1, 2, 3, 5])
        self.assertEqual(len(result.warnings), 1)
        self.assertIn("손상", result.warnings[0])

    def test_모르는_type은_건너뛴다(self):
        _write_sample(self.path)
        with open(self.path, "a", encoding="utf-8") as f:
            f.write('{"v": 1, "type": "future_thing", "x": 1}\n')
        result = read_log(self.path)
        self.assertEqual(len(result.records), 3)

    def test_깨진_properties도_로그를_깨뜨리지_않는다(self):
        _write_sample(self.path)
        with JsonlLogWriter(os.path.join(self.tmp.name, "broken.jsonl")) as writer:
            writer.write_send(
                seq=1,
                sent_at_text="2026-08-14T10:00:01+09:00",
                request_body_text='{"transaction_id": "evt-x", "customer_id": "c", '
                '"type": "e", "properties": {token: 5}, "timestamp": "t"}',
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
        path = os.path.join(self.tmp.name, "newline.jsonl")
        with JsonlLogWriter(path) as writer:
            writer.write_send(
                seq=1,
                sent_at_text="2026-08-14T10:00:01+09:00",
                request_body_text='{"transaction_id": "evt-n", "customer_id": "c", '
                '"type": "e", "properties": {"token": 7,\n "model": "m"}, '
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

    def test_중간_라인_손상은_경고로_올린다(self):
        _write_sample(self.path)
        with open(self.path, encoding="utf-8") as f:
            lines = f.read().splitlines()
        lines[1] = lines[1][:20]
        with open(self.path, "w", encoding="utf-8") as f:
            f.write("\n".join(lines) + "\n")
        result = read_log(self.path)
        self.assertEqual([r.seq for r in result.records], [2, 3])
        self.assertEqual(len(result.warnings), 1)
        self.assertIn("2행", result.warnings[0])


class ClassifyOutcomeTest(unittest.TestCase):
    def test_200은_duplicate_여부로_갈린다(self):
        self.assertEqual(classify_outcome(200, {"duplicate": False}), "new")
        self.assertEqual(classify_outcome(200, {"duplicate": True}), "duplicate")

    def test_4xx는_rejected다(self):
        self.assertEqual(classify_outcome(400, {"code": "validation_error"}), "rejected")
        self.assertEqual(classify_outcome(404, {"code": "customer_not_found"}), "rejected")
        self.assertEqual(classify_outcome(499, None), "rejected")

    def test_5xx는_error다(self):
        self.assertEqual(classify_outcome(500, None), "error")
        self.assertEqual(classify_outcome(503, {}), "error")

    def test_200이지만_duplicate가_없으면_error다(self):
        self.assertEqual(classify_outcome(200, {}), "error")
        self.assertEqual(classify_outcome(200, None), "error")

    def test_객체가_아닌_본문도_error다(self):
        self.assertEqual(classify_outcome(200, [1, 2]), "error")
        self.assertEqual(classify_outcome(200, "ok"), "error")


class AppendTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.path = os.path.join(self.tmp.name, "bridge-20260824.jsonl")

    def test_개행_없이_끊긴_파일에_이어써도_헤더가_살아난다(self):
        _write_sample(self.path)
        with open(self.path, "a", encoding="utf-8") as f:
            f.write('{"v": 1, "type": "send", "seq": 4, "requ')
        with JsonlLogWriter(self.path, append=True) as writer:
            writer.write_run_header(
                started_at_text="2026-08-24T11:00:00+09:00",
                base_url="https://meterengine.com",
                org_id="d7cee55d-8c82-4afc-b996-6749d8b26a4e",
                csv_path=None,
                argv=["otel_bridge.py", "serve"],
            )
        result = read_log(self.path)
        self.assertEqual(result.header.base_url, "https://meterengine.com")
        self.assertEqual(result.header_count, 2)

    def test_빈_파일에_이어써도_빈_줄이_생기지_않는다(self):
        with JsonlLogWriter(self.path, append=True) as writer:
            writer.write_run_header("2026-08-24T11:00:00+09:00", "http://localhost:8080", "o", None, [])
        with open(self.path, encoding="utf-8") as f:
            self.assertEqual(len(f.read().rstrip("\n").split("\n")), 1)


class DamagedTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.path = os.path.join(self.tmp.name, "send-test.jsonl")

    def test_JSON이지만_객체가_아닌_줄도_건너뛴다(self):
        _write_sample(self.path)
        with open(self.path, "a", encoding="utf-8") as f:
            f.write("123\n")
            f.write('{"v": 1, "type": "send", "seq": 9, "sent_at": "t", "request": {},'
                    ' "status": 200, "response": {}, "outcome": "new", "error": null,'
                    ' "elapsed_ms": 1}\n')
        result = read_log(self.path)
        self.assertEqual([r.seq for r in result.records], [1, 2, 3, 9])
        self.assertEqual(result.damaged, [5])

    def test_깨끗한_파일은_damaged가_비어_있다(self):
        _write_sample(self.path)
        result = read_log(self.path)
        self.assertEqual(result.damaged, [])
        self.assertEqual(result.header_count, 1)

    def test_잘린_마지막_라인은_damaged가_아니다(self):
        _write_sample(self.path)
        with open(self.path, "a", encoding="utf-8") as f:
            f.write('{"v": 1, "type": "send", "seq": 4, "requ')
        self.assertEqual(read_log(self.path).damaged, [])


if __name__ == "__main__":
    unittest.main()
