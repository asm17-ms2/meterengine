import http.client
import json
import os
import sys
import tempfile
import threading
import time
import unittest
from http.server import ThreadingHTTPServer

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from bridge import server
from bridge.state import BridgeConfig, BridgeState

DEMO_CUSTOMER = "35bc8d12-9d38-57ab-bc9b-bbd35d779a26"

API_REQUEST = {
    "attributes": [
        {"key": "event.name", "value": {"stringValue": "api_request"}},
        {"key": "request_id", "value": {"stringValue": "req_01"}},
    ],
    "timeUnixNano": "1787000000000000000",
}


class FakeResult:
    def __init__(self, status, body):
        self.status = status
        self.body = body
        self.body_text = json.dumps(body, ensure_ascii=False) if body is not None else ""
        self.elapsed_ms = 1


class FakeResolver:
    def resolve(self, name):
        return DEMO_CUSTOMER


class SenderTestCase(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.path = self.directory.name

    def freeze_today(self, day):
        original = server._today
        self.addCleanup(setattr, server, "_today", original)
        server._today = lambda: day

    def sender(self, config=None, resolver=None, client=None):
        state = BridgeState(os.path.join(self.path, "state.json"), "scope")
        made = server.Sender(config or BridgeConfig(), state, self.path)
        self.addCleanup(made.close)
        made.resolver = resolver
        if client is not None:
            made.client = client
        return made


class OutcomeCountsTest(SenderTestCase):
    def test_집계_키는_JSONL_어휘_그대로다(self):
        counts = self.sender().counts
        self.assertEqual(
            sorted(counts), sorted(["new", "duplicate", "rejected", "error", "skipped"])
        )


class LogRollTest(SenderTestCase):
    def test_날짜가_바뀌면_그날_파일로_갈아탄다(self):
        self.freeze_today("20260824")
        sender = self.sender()
        first = sender.writer.path
        self.freeze_today("20260825")
        sender._roll()
        self.assertTrue(first.endswith("bridge-20260824.jsonl"), first)
        self.assertTrue(sender.writer.path.endswith("bridge-20260825.jsonl"), sender.writer.path)

    def test_같은_날에는_파일을_유지한다(self):
        self.freeze_today("20260824")
        sender = self.sender()
        first = sender.writer
        sender._roll()
        self.assertIs(sender.writer, first)

    def test_갈아타다_실패해도_다음_이벤트를_받는다(self):
        self.freeze_today("20260824")
        sender = self.sender()
        yesterday = sender.writer

        original = server.JsonlLogWriter
        self.addCleanup(setattr, server, "JsonlLogWriter", original)

        def broken(*args, **kwargs):
            raise OSError("디스크가 찼습니다")

        server.JsonlLogWriter = broken
        self.freeze_today("20260825")
        with self.assertRaises(OSError):
            sender._roll()

        self.assertIs(sender.writer, yesterday)
        self.assertEqual(sender._day, "20260824")
        sender.writer.write_send(1, "2026-08-25T00:00:01+09:00", None, None, None, "error", "x", None)

        server.JsonlLogWriter = original
        sender._roll()
        self.assertTrue(sender.writer.path.endswith("bridge-20260825.jsonl"))


class LastSeqTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.path = os.path.join(self.directory.name, "bridge-20260824.jsonl")

    def test_없는_파일은_0이다(self):
        self.assertEqual(server._last_seq(self.path), 0)

    def test_마지막_seq를_찾는다(self):
        with open(self.path, "w", encoding="utf-8") as f:
            f.write('{"v": 1, "type": "run"}\n')
            f.write('{"v": 1, "type": "send", "seq": 1}\n')
            f.write('{"v": 1, "type": "send", "seq": 7}\n')
        self.assertEqual(server._last_seq(self.path), 7)

    def test_바이트_중간에서_잘려도_읽는다(self):
        with open(self.path, "wb") as f:
            f.write(b'{"v": 1, "type": "send", "seq": 3}\n')
            f.write('{"v": 1, "type": "send", "seq": 4, "error": "연결'.encode("utf-8")[:-1])
        self.assertEqual(server._last_seq(self.path), 3)

    def test_객체가_아닌_줄은_건너뛴다(self):
        with open(self.path, "w", encoding="utf-8") as f:
            f.write('{"v": 1, "type": "send", "seq": 2}\n')
            f.write("123\n")
        self.assertEqual(server._last_seq(self.path), 2)


class SendOneTest(SenderTestCase):
    def log_lines(self, sender):
        with open(sender.writer.path, encoding="utf-8") as f:
            return [json.loads(line) for line in f if line.strip()]

    def test_고객_해석에_실패해도_기록을_남긴다(self):
        class Broken:
            def resolve(self, name):
                raise RuntimeError("고객 목록 조회 실패: HTTP 500")

        sender = self.sender(resolver=Broken())
        sender.state.remember_session("sess-1", "meterengine")
        sender._send_one(API_REQUEST, "api_request", "sess-1")

        self.assertEqual(sender.counts["error"], 1)
        sends = [line for line in self.log_lines(sender) if line["type"] == "send"]
        self.assertEqual(len(sends), 1)
        self.assertEqual(sends[0]["outcome"], "error")
        self.assertIn("고객 해석 실패", sends[0]["error"])

    def test_서버가_모르는_고객이면_캐시를_버린다(self):
        class Rejecting:
            def post_event(self, body_text):
                return FakeResult(404, {"code": "customer_not_found"})

        sender = self.sender(resolver=FakeResolver(), client=Rejecting())
        sender.state.remember_session("sess-1", "meterengine")
        sender.state.remember_customer("meterengine", DEMO_CUSTOMER)
        sender._send_one(API_REQUEST, "api_request", "sess-1")

        self.assertEqual(sender.counts["rejected"], 1)
        self.assertIsNone(sender.state.cached_customer("meterengine"))

    def test_다른_이유의_400은_캐시를_남긴다(self):
        class Rejecting:
            def post_event(self, body_text):
                return FakeResult(400, {"code": "validation_error"})

        sender = self.sender(resolver=FakeResolver(), client=Rejecting())
        sender.state.remember_session("sess-1", "meterengine")
        sender.state.remember_customer("meterengine", DEMO_CUSTOMER)
        sender._send_one(API_REQUEST, "api_request", "sess-1")

        self.assertEqual(sender.state.cached_customer("meterengine"), DEMO_CUSTOMER)


class DenyTest(SenderTestCase):
    def test_묶인_뒤에_deny로_바뀐_프로젝트는_보내지_않는다(self):
        sender = self.sender(BridgeConfig(deny=["meterengine"]))
        sender.state.remember_session("sess-1", "meterengine")
        self.assertFalse(sender.state.is_denied("sess-1"))
        sender._send_one({}, "api_request", "sess-1")
        self.assertEqual(sender.counts["skipped"], 1)
        self.assertEqual(sender.counts["error"], 0)

    def test_deny_세션은_그대로_건너뛴다(self):
        sender = self.sender(BridgeConfig(deny=["비밀레포"]))
        sender.state.deny_session("sess-2")
        sender._send_one({}, "api_request", "sess-2")
        self.assertEqual(sender.counts["skipped"], 1)


class AllowedHostTest(unittest.TestCase):
    def test_이_기계를_가리키는_값은_통과한다(self):
        for host in ("127.0.0.1", "127.0.0.1:4318", "localhost:4318", "[::1]:4318", None, ""):
            self.assertTrue(server._allowed_host(host), host)

    def test_남의_도메인은_막는다(self):
        for host in ("evil.example:4318", "attacker.com", "127.0.0.1.evil.example"):
            self.assertFalse(server._allowed_host(host), host)

    def test_다른_주소에_붙였으면_그것도_통과한다(self):
        self.assertTrue(server._allowed_host("192.168.0.5:4318", "192.168.0.5"))
        self.assertFalse(server._allowed_host("evil.example", "192.168.0.5"))


class RequestOriginTest(unittest.TestCase):
    class RecordingSender:
        def __init__(self):
            self.submitted = []
            self.counts = {}

        def submit(self, record, name, session_id):
            self.submitted.append((record, name, session_id))

    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.sender = self.RecordingSender()
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), server.BridgeHandler)
        self.server.config = BridgeConfig()
        self.server.state = BridgeState(os.path.join(self.directory.name, "state.json"), "scope")
        self.server.sender = self.sender
        self.server.daemon_threads = True
        threading.Thread(target=self.server.serve_forever, daemon=True).start()
        self.addCleanup(self.server.server_close)
        self.addCleanup(self.server.shutdown)
        self.port = self.server.server_address[1]

    def eventually(self, condition, seconds=2.0):
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            if condition():
                return True
            time.sleep(0.01)
        return False

    def post(self, path, body, headers=None):
        connection = http.client.HTTPConnection("127.0.0.1", self.port, timeout=5)
        try:
            connection.request("POST", path, body, headers or {})
            return connection.getresponse().status
        finally:
            connection.close()

    def get(self, path, headers=None):
        connection = http.client.HTTPConnection("127.0.0.1", self.port, timeout=5)
        try:
            connection.request("GET", path, headers=headers or {})
            return connection.getresponse().status
        finally:
            connection.close()

    def test_도구가_보낸_hook은_받는다(self):
        body = json.dumps({"session_id": "sess-1", "cwd": self.directory.name})
        self.assertEqual(self.post("/meterengine/session", body), 200)
        self.assertTrue(self.eventually(lambda: self.server.state.project_of("sess-1")))

    def test_Origin이_붙은_요청은_거절한다(self):
        body = json.dumps({"session_id": "sess-2", "cwd": self.directory.name})
        status = self.post(
            "/meterengine/session",
            body,
            {"Origin": "https://evil.example", "Content-Type": "text/plain"},
        )
        self.assertEqual(status, 403)
        self.assertIsNone(self.server.state.project_of("sess-2"))

    def test_다른_사이트에서_온_GET은_거절한다(self):
        self.assertEqual(
            self.get("/meterengine/health", {"Sec-Fetch-Site": "cross-site"}), 403
        )

    def test_남의_도메인_Host는_거절한다(self):
        self.assertEqual(self.get("/meterengine/health", {"Host": "evil.example"}), 403)

    def test_OTLP도_같은_검사를_받는다(self):
        status = self.post("/v1/logs", "{}", {"Origin": "https://evil.example"})
        self.assertEqual(status, 403)
        self.assertEqual(self.sender.submitted, [])


if __name__ == "__main__":
    unittest.main()
