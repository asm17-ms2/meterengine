import contextlib
import io
import json
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from bridge.const import LOGS_DIR as BRIDGE_LOGS_DIR
from core.api_client import ApiResult, Organization, TransportError
from core.model import Event
from csvdemo import send_cmd
from csvdemo.render import Console, format_gate

KEY = "me_live_QouOFuCiJyAbC0VGK1ltUCkG4IZM2LtA"
ORGANIZATION = Organization(id="d7cee55d-8c82-4afc-b996-6749d8b26a4e", name="데모 도입사")


class LogsDirTest(unittest.TestCase):

    def test_로그는_demo_logs에_쌓인다(self):
        demo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
        self.assertEqual(send_cmd.LOGS_DIR, os.path.join(demo, "logs"))

    def test_브리지와_같은_곳을_본다(self):
        self.assertEqual(send_cmd.LOGS_DIR, BRIDGE_LOGS_DIR)


def _result(status, body):
    return ApiResult(status=status, body=body, body_text=json.dumps(body, ensure_ascii=False), elapsed_ms=1)


class FakeClient:
    base_url = "http://localhost:8080"

    def __init__(self, replies):
        self.api_key = KEY
        self.replies = list(replies)
        self.posted = []

    def get_events_page(self, month):
        return _result(200, {"total": 0})

    def get_usage(self, month):
        return _result(200, {"billable_metric_usages": []})

    def post_event(self, body_text):
        self.posted.append(body_text)
        return _result(*self.replies.pop(0))


class Args:
    csv = "events.csv"
    dry_run = False
    yes = True
    interval = 0.0
    jitter = 0.0
    verbose = False


def _events(count):
    return [
        Event(
            transaction_id="evt-%d" % n,
            customer_id="35bc8d12-9d38-57ab-bc9b-bbd35d779a26",
            event_type="llm_request",
            timestamp_text="2026-08-24T10:00:00+09:00",
            properties_text='{"input_tokens": 5}',
        )
        for n in range(1, count + 1)
    ]


class SendLoopTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        original_dir = send_cmd.LOGS_DIR
        send_cmd.LOGS_DIR = self.tmp.name
        self.addCleanup(setattr, send_cmd, "LOGS_DIR", original_dir)
        original_argv = sys.argv
        sys.argv = ["meterdemo.py", "send", "--csv", "events.csv", "--api-key", KEY]
        self.addCleanup(setattr, sys, "argv", original_argv)

    def run_send(self, client, count=3):
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            code = send_cmd.send_events(_events(count), client, ORGANIZATION, Console(False), Args())
        return code, out.getvalue(), err.getvalue()

    def log_text(self):
        names = os.listdir(self.tmp.name)
        self.assertEqual(len(names), 1)
        with open(os.path.join(self.tmp.name, names[0]), encoding="utf-8") as f:
            return f.read()

    def test_401이면_그_자리에서_멈추고_code를_보인다(self):
        client = FakeClient([(401, {"code": "invalid_credentials", "message": "인증 정보가 올바르지 않습니다"})] * 3)
        code, out, err = self.run_send(client)
        self.assertEqual(len(client.posted), 1)
        self.assertEqual(code, 2)
        self.assertIn("invalid_credentials", err)
        self.assertIn("중단", err)

    def test_403이어도_멈춘다(self):
        client = FakeClient([(403, {"code": "organization_not_active", "message": "m"})] * 3)
        code, out, err = self.run_send(client)
        self.assertEqual(len(client.posted), 1)
        self.assertEqual(code, 2)
        self.assertIn("organization_not_active", err)

    def test_다른_4xx는_계속_보낸다(self):
        client = FakeClient([(400, {"code": "validation_error"})] * 3)
        code, out, err = self.run_send(client)
        self.assertEqual(len(client.posted), 3)
        self.assertEqual(code, 0)

    def test_기록_머리말에_도입사_id가_남고_키는_어디에도_없다(self):
        client = FakeClient([(200, {"duplicate": False})] * 3)
        code, out, err = self.run_send(client)
        text = self.log_text()
        header = json.loads(text.splitlines()[0])
        self.assertEqual(header["organization_id"], ORGANIZATION.id)
        self.assertNotIn(KEY, text)
        self.assertNotIn(KEY, out + err)

    def test_확인_게이트에_가린_키와_도입사가_나온다(self):
        client = FakeClient([(200, {"duplicate": False})] * 3)
        code, out, err = self.run_send(client)
        self.assertIn("me_live_****2LtA", out)
        self.assertIn("데모 도입사", out)
        self.assertIn(ORGANIZATION.id, out)


class FormatGateTest(unittest.TestCase):
    def test_도입사_줄은_가린_키와_이름과_id다(self):
        lines = format_gate("http://localhost:8080", KEY, ORGANIZATION, 3, {"2026-08": 3}, {"2026-08": 0})
        text = "\n".join(lines)
        self.assertNotIn(KEY, text)
        self.assertIn("me_live_****2LtA", text)
        self.assertIn("데모 도입사 (%s)" % ORGANIZATION.id, text)


class BatchSendTest(unittest.TestCase):
    setUp = SendLoopTest.setUp
    log_text = SendLoopTest.log_text

    def test_배치_실패_뒤_모든_입력행을_UNKNOWN과_NOT_SENT로_구분해_남긴다(self):
        class BatchClient(FakeClient):
            def post_events(self, body_text):
                rows = json.loads(body_text)["events"]
                self.posted.append(body_text)
                if len(self.posted) > 1:
                    raise TransportError("connection lost")
                return _result(200, {"results": [{"index": i, "transaction_id": row["transaction_id"], "result": "stored"} for i, row in enumerate(rows)]})

        args = Args()
        args.batch_size = 2
        client = BatchClient([])
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            code = send_cmd.send_events(_events(5), client, ORGANIZATION, Console(False), args)
        self.assertEqual(code, 2)
        self.assertEqual(len(client.posted), 2)
        records = [json.loads(line) for line in self.log_text().splitlines()][1:]
        self.assertEqual([row["seq"] for row in records], [1, 2, 3, 4, 5])
        self.assertEqual([row["outcome"] for row in records[:2]], ["new", "new"])
        self.assertIn("UNKNOWN", records[2]["error"])
        self.assertIn("UNKNOWN", records[3]["error"])
        self.assertIn("NOT_SENT", records[4]["error"])

    def test_속성이_깨진_행만_단건으로_보내고_나머지는_배치로_저장한다(self):
        class BatchClient(FakeClient):
            def post_events(self, body_text):
                self.posted.append(body_text)
                try:
                    rows = json.loads(body_text)["events"]
                except ValueError:
                    return _result(400, {"code": "malformed_request_body", "message": "본문을 읽을 수 없습니다"})
                return _result(200, {"results": [{"index": i, "transaction_id": row["transaction_id"], "result": "stored"} for i, row in enumerate(rows)]})

        events = _events(3)
        events[1].properties_text = '{"quantity":'
        args = Args()
        args.batch_size = 3
        client = BatchClient([(400, {"code": "malformed_request_body", "message": "본문을 읽을 수 없습니다"})])
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            code = send_cmd.send_events(events, client, ORGANIZATION, Console(False), args)
        records = [json.loads(line) for line in self.log_text().splitlines()][1:]
        self.assertEqual([row["seq"] for row in records], [1, 2, 3])
        self.assertEqual([row["outcome"] for row in records], ["new", "rejected", "new"])
        self.assertEqual(code, 0)

    def test_단건으로_보낸_깨진_행이_인증에_실패하면_같은_묶음의_다음_깨진_행을_보내지_않는다(self):
        class BatchClient(FakeClient):
            def post_events(self, body_text):
                rows = json.loads(body_text)["events"]
                return _result(200, {"results": [{"index": i, "transaction_id": row["transaction_id"], "result": "stored"} for i, row in enumerate(rows)]})

        events = _events(3)
        events[0].properties_text = '{"quantity":'
        events[1].properties_text = '{"quantity":'
        args = Args()
        args.batch_size = 3
        client = BatchClient([(401, {"code": "invalid_credentials", "message": "인증 실패"})])
        with contextlib.redirect_stdout(io.StringIO()) as output, contextlib.redirect_stderr(io.StringIO()):
            code = send_cmd.send_events(events, client, ORGANIZATION, Console(False), args)
        records = [json.loads(line) for line in self.log_text().splitlines()][1:]
        self.assertEqual(len(client.posted), 1)
        self.assertEqual([row["seq"] for row in records], [1, 2, 3])
        self.assertIn("NOT_SENT", records[1]["error"])
        self.assertEqual(records[2]["outcome"], "new")
        self.assertIn("전송 2건", output.getvalue())
        self.assertEqual(code, 2)

    def test_봉투_전체를_거절한_4xx는_rejected가_아닌_error로_센다(self):
        class BatchClient(FakeClient):
            def post_events(self, body_text):
                self.posted.append(body_text)
                return _result(400, {"code": "validation_error", "message": "요청 값이 올바르지 않습니다"})

        args = Args()
        args.batch_size = 2
        client = BatchClient([])
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            code = send_cmd.send_events(_events(2), client, ORGANIZATION, Console(False), args)
        records = [json.loads(line) for line in self.log_text().splitlines()][1:]
        self.assertEqual([row["outcome"] for row in records], ["error", "error"])
        self.assertEqual(code, 2)

    def test_배치의_속성_숫자는_원문_자릿수를_그대로_보낸다(self):
        class BatchClient(FakeClient):
            def post_events(self, body_text):
                self.posted.append(body_text)
                return _result(200, {"results": [{"index": 0, "transaction_id": "evt-1", "result": "stored"}]})

        events = _events(1)
        number = "12345678901234567890.12345678901234567890"
        events[0].properties_text = '{"quantity":' + number + '}'
        args = Args()
        args.batch_size = 2
        client = BatchClient([])
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(send_cmd.send_events(events, client, ORGANIZATION, Console(False), args), 0)
        self.assertIn(number, client.posted[0])


if __name__ == "__main__":
    unittest.main()
