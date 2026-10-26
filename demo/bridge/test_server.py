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
from core.api_client import Organization

DEMO_CUSTOMER = "35bc8d12-9d38-57ab-bc9b-bbd35d779a26"
LIVE_KEY = "me_live_QouOFuCiJyAbC0VGK1ltUCkG4IZM2LtA"
ORGANIZATION = Organization(id="d7cee55d-8c82-4afc-b996-6749d8b26a4e", name="데모 도입사")

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

    def sender(self, config=None, resolver=None, client=None, organization=ORGANIZATION, scope="scope"):
        state = BridgeState(os.path.join(self.path, "state.json"), scope)
        made = server.Sender(config or BridgeConfig(), state, self.path, organization)
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


class RunHeaderTest(SenderTestCase):
    def test_머리말에는_도입사_id만_남고_키는_없다(self):
        sender = self.sender(BridgeConfig(api_key=LIVE_KEY), organization=ORGANIZATION)
        with open(sender.writer.path, encoding="utf-8") as f:
            text = f.read()
        header = json.loads(text.splitlines()[0])
        self.assertEqual(header["type"], "run")
        self.assertEqual(header["organization_id"], ORGANIZATION.id)
        self.assertNotIn(LIVE_KEY, text)

    def test_보내는_요청에_설정의_키를_쓴다(self):
        sender = self.sender(BridgeConfig(api_key=LIVE_KEY), organization=ORGANIZATION)
        self.assertEqual(sender.client.api_key, LIVE_KEY)


class StartupLinesTest(unittest.TestCase):
    def test_시작_로그에는_가린_키만_나오고_도입사는_나중에_확인한다(self):
        config = BridgeConfig(owner="박성종", api_key=LIVE_KEY)
        text = "\n".join(server.startup_lines(config, "127.0.0.1", 4318))
        self.assertNotIn(LIVE_KEY, text)
        self.assertIn("me_live_****2LtA", text)
        self.assertIn("도입사: 첫 전송 때 확인", text)


class OrganizationClient:
    def __init__(self, replies, post_replies=None):
        self.api_key = LIVE_KEY
        self.replies = list(replies)
        self.post_replies = list(post_replies or [])
        self.organization_calls = 0
        self.posted = []

    def get_organization(self):
        self.organization_calls += 1
        status, body = self.replies.pop(0) if len(self.replies) > 1 else self.replies[0]
        return FakeResult(status, body)

    def post_event(self, body_text):
        self.posted.append(body_text)
        if self.post_replies:
            return FakeResult(*self.post_replies.pop(0))
        return FakeResult(200, {"duplicate": False})


ORGANIZATION_REPLY = (200, {"id": ORGANIZATION.id, "name": ORGANIZATION.name, "status": "active"})
REJECTED_REPLY = (401, {"code": "invalid_credentials", "message": "m"})


class LazyOrganizationTest(SenderTestCase):
    def setUp(self):
        super().setUp()
        self.logged = []
        original_log = server._log
        server._log = self.logged.append
        self.addCleanup(setattr, server, "_log", original_log)
        self.now = 1000.0
        self.freeze_today("20260824")

    def lazy_sender(self, replies, post_replies=None):
        client = OrganizationClient(replies, post_replies)
        sender = self.sender(
            BridgeConfig(api_key=LIVE_KEY), resolver=FakeResolver(), client=client, organization=None, scope=None
        )
        sender._clock = lambda: self.now
        sender.state.remember_session("sess-1", "meterengine")
        return sender, client

    def header_lines(self):
        path = os.path.join(self.path, "bridge-20260824.jsonl")
        if not os.path.exists(path):
            return []
        with open(path, encoding="utf-8") as f:
            return [json.loads(line) for line in f if line.strip() and json.loads(line)["type"] == "run"]

    def test_도입사를_알기_전에는_기록_파일을_열지_않는다(self):
        sender, client = self.lazy_sender([ORGANIZATION_REPLY])
        self.assertIsNone(sender.writer)
        self.assertEqual(self.header_lines(), [])
        self.assertEqual(client.organization_calls, 0)

    def test_첫_이벤트에서_한_번만_확인하고_머리말에_도입사_id를_남긴다(self):
        sender, client = self.lazy_sender([ORGANIZATION_REPLY])
        sender._send_one(API_REQUEST, "api_request", "sess-1")
        sender._send_one(API_REQUEST, "api_request", "sess-1")
        self.assertEqual(client.organization_calls, 1)
        self.assertEqual(len(client.posted), 2)
        headers = self.header_lines()
        self.assertEqual(len(headers), 1)
        self.assertEqual(headers[0]["organization_id"], ORGANIZATION.id)
        self.assertEqual(sender.organization, ORGANIZATION)

    def test_확인하면_고객_캐시_scope를_정한다(self):
        sender, client = self.lazy_sender([ORGANIZATION_REPLY])
        sender._send_one(API_REQUEST, "api_request", "sess-1")
        self.assertEqual(sender.state.scope, "http://localhost:8080|" + ORGANIZATION.id)

    def test_거부되면_보내지_않고_같은_오류는_한_번만_남긴다(self):
        sender, client = self.lazy_sender([REJECTED_REPLY])
        for _ in range(3):
            sender._send_one(API_REQUEST, "api_request", "sess-1")
        self.assertEqual(client.posted, [])
        self.assertEqual(sender.counts["error"], 3)
        self.assertEqual(len([line for line in self.logged if "invalid_credentials" in line]), 1)
        self.assertEqual(sender.organization_error, "invalid_credentials")
        self.assertIsNone(sender.state.scope)

    def test_60초가_지나기_전에는_다시_묻지_않는다(self):
        sender, client = self.lazy_sender([REJECTED_REPLY])
        sender._send_one(API_REQUEST, "api_request", "sess-1")
        self.now += 59
        sender._send_one(API_REQUEST, "api_request", "sess-1")
        self.assertEqual(client.organization_calls, 1)
        self.now += 2
        sender._send_one(API_REQUEST, "api_request", "sess-1")
        self.assertEqual(client.organization_calls, 2)
        self.assertEqual(len([line for line in self.logged if "invalid_credentials" in line]), 1)

    def test_승인되면_다음_확인에서_보내기_시작한다(self):
        sender, client = self.lazy_sender(
            [(403, {"code": "organization_not_active", "message": "m"}), ORGANIZATION_REPLY]
        )
        sender._send_one(API_REQUEST, "api_request", "sess-1")
        self.now += 61
        sender._send_one(API_REQUEST, "api_request", "sess-1")
        self.assertEqual(len(client.posted), 1)
        self.assertIsNone(sender.organization_error)

    def test_확인한_뒤에_키가_거부되면_도입사를_내려놓고_한_번만_남긴다(self):
        sender, client = self.lazy_sender([ORGANIZATION_REPLY], post_replies=[REJECTED_REPLY])
        sender._send_one(API_REQUEST, "api_request", "sess-1")
        self.assertEqual(sender.counts["rejected"], 1)
        self.assertIsNone(sender.organization)
        self.assertEqual(sender.organization_error, "invalid_credentials")
        for _ in range(2):
            sender._send_one(API_REQUEST, "api_request", "sess-1")
        self.assertEqual(len(client.posted), 1)
        self.assertEqual(client.organization_calls, 1)
        self.assertEqual(sender.counts["error"], 2)
        self.assertEqual(len([line for line in self.logged if "invalid_credentials" in line]), 1)
        self.assertNotIn(LIVE_KEY, "\n".join(self.logged))

    def test_키가_거부된_뒤_60초가_지나면_다시_확인하고_보낸다(self):
        sender, client = self.lazy_sender(
            [ORGANIZATION_REPLY], post_replies=[(403, {"code": "organization_not_active", "message": "m"})]
        )
        sender._send_one(API_REQUEST, "api_request", "sess-1")
        self.assertEqual(sender.organization_error, "organization_not_active")
        self.now += 61
        sender._send_one(API_REQUEST, "api_request", "sess-1")
        self.assertEqual(client.organization_calls, 2)
        self.assertEqual(len(client.posted), 2)
        self.assertEqual(sender.organization, ORGANIZATION)
        self.assertIsNone(sender.organization_error)

    def test_본문이_없는_401은_상태_번호로_남긴다(self):
        sender, client = self.lazy_sender([ORGANIZATION_REPLY], post_replies=[(401, None)])
        sender._send_one(API_REQUEST, "api_request", "sess-1")
        self.assertEqual(sender.organization_error, "http_401")

    def test_연결이_안_돼도_멈추지_않는다(self):
        class Unreachable(OrganizationClient):
            def get_organization(self):
                self.organization_calls += 1
                raise server.TransportError("http://localhost:8080에 연결할 수 없습니다")

        sender = self.sender(
            BridgeConfig(api_key=LIVE_KEY), resolver=FakeResolver(), client=Unreachable([]),
            organization=None, scope=None,
        )
        sender._clock = lambda: self.now
        sender.state.remember_session("sess-1", "meterengine")
        sender._send_one(API_REQUEST, "api_request", "sess-1")
        sender._send_one(API_REQUEST, "api_request", "sess-1")
        self.assertEqual(sender.counts["error"], 2)
        self.assertEqual(sender.organization_error, "connection_failed")
        self.assertEqual(len([line for line in self.logged if "연결할 수 없습니다" in line]), 1)


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
            self.organization = None
            self.organization_error = None

        def submit(self, record, name, session_id):
            self.submitted.append((record, name, session_id))

    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.sender = self.RecordingSender()
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), server.BridgeHandler)
        self.server.config = BridgeConfig(api_key=LIVE_KEY)
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

    def health_body(self):
        connection = http.client.HTTPConnection("127.0.0.1", self.port, timeout=5)
        try:
            connection.request("GET", "/meterengine/health")
            response = connection.getresponse()
            return response.status, response.read().decode("utf-8")
        finally:
            connection.close()

    def test_health에는_확인한_도입사와_가린_키만_나온다(self):
        self.sender.organization = ORGANIZATION
        status, text = self.health_body()
        self.assertEqual(status, 200)
        body = json.loads(text)
        self.assertEqual(body["organization"], {"id": ORGANIZATION.id, "name": ORGANIZATION.name})
        self.assertIsNone(body["organization_error"])
        self.assertEqual(body["api_key"], "me_live_****2LtA")
        self.assertNotIn(LIVE_KEY, text)
        self.assertNotIn("org_id", body)

    def test_확인_전이나_실패하면_health에_오류_code가_나온다(self):
        self.sender.organization_error = "invalid_credentials"
        body = json.loads(self.health_body()[1])
        self.assertIsNone(body["organization"])
        self.assertEqual(body["organization_error"], "invalid_credentials")

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
