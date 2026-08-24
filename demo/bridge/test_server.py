"""브리지 서버의 판정, 파일 갈아타기, 요청 출처 검사 (MS2-169).

여기 있는 것은 전부 코드 리뷰에서 나온 결함의 회귀 방지다. 실제 네트워크로
나가지 않는다 (요청 출처 검사만 루프백에 소켓을 연다).
"""

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

# to_event가 통과시키는 가장 작은 레코드. request_id가 멱등키가 된다.
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
    """이름을 늘 같은 고객으로 옮긴다. 네트워크로 나가지 않는다."""

    def resolve(self, name):
        return DEMO_CUSTOMER


class SenderTestCase(unittest.TestCase):
    """Sender를 만들되 네트워크로 나가지 않게 막는다."""

    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.path = self.directory.name

    def freeze_today(self, day):
        """server._today를 바꾸고 반드시 되돌린다.

        되돌리기를 try 밖에 두면 그 사이에서 실패했을 때 패치가 남아, 이후 모든
        테스트와 실제 롤 판정이 고정된 날짜를 보게 된다.
        """
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
        """손으로 적으면 이름이 어긋나 성공을 거절로 세게 된다."""
        counts = self.sender().counts
        self.assertEqual(
            sorted(counts), sorted(["new", "duplicate", "rejected", "error", "skipped"])
        )


class LogRollTest(SenderTestCase):
    """상주 프로세스라 자정을 넘긴다. 파일명을 기동 때 한 번만 정하면 안 된다."""

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
        """새 파일을 열지 못하면 어제 파일에 계속 쌓여야 한다.

        날짜부터 올리고 옛 파일을 닫으면, 여기서 실패한 뒤로는 이 메서드가 곧바로
        반환하고 닫힌 파일에 쓰다 죽기를 영영 반복한다. 그날 이후 기록이 통째로
        사라지고 재시작해야만 낫는다.
        """
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
        # 옛 파일이 살아 있어야 이 쓰기가 성공한다
        sender.writer.write_send(1, "2026-08-25T00:00:01+09:00", None, None, None, "error", "x", None)

        server.JsonlLogWriter = original
        sender._roll()
        self.assertTrue(sender.writer.path.endswith("bridge-20260825.jsonl"))


class LastSeqTest(unittest.TestCase):
    """기동 경로다. 여기서 던지면 launchd가 브리지를 10초마다 되살렸다 죽인다."""

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
        # 쓰는 도중 죽으면 한글 한 글자가 쪼개져 남는다. 기본 설정이면 그 줄에서
        # UnicodeDecodeError가 나고, 아무도 잡지 않아 기동이 실패한다.
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
        # 라인마다 flush하므로 그대로 읽으면 된다
        with open(sender.writer.path, encoding="utf-8") as f:
            return [json.loads(line) for line in f if line.strip()]

    def test_고객_해석에_실패해도_기록을_남긴다(self):
        """화면에만 찍으면 로그에 흔적이 없다.

        그러면 나중에 "원래 없던 이벤트"와 "잃어버린 이벤트"를 구별할 수 없다.
        전송 실패는 error 줄을 남기므로 이쪽만 안 남기면 두 실패가 비대칭이 된다.
        """

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
        """캐시는 디스크에 있어 재시작해도 남는다. 버리지 않으면 영영 거절된다."""

        class Rejecting:
            def post_event(self, body_text):
                return FakeResult(400, {"code": "unknown_customer_reference"})

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
    """deny에 적은 레포는 아예 보내지 않는다 (README의 약속)."""

    def test_묶인_뒤에_deny로_바뀐_프로젝트는_보내지_않는다(self):
        """세션이 이미 묶여 있으면 is_denied가 거짓이다.

        다음 hook이 와서 세션을 deny로 옮기기 전까지, 프로젝트 이름으로 다시
        대조하지 않으면 그 사이의 이벤트가 실명 그대로 나간다.
        """
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
        # DNS 리바인딩. 이름이 127.0.0.1로 풀려도 Host에는 그 도메인이 남는다.
        for host in ("evil.example:4318", "attacker.com", "127.0.0.1.evil.example"):
            self.assertFalse(server._allowed_host(host), host)

    def test_다른_주소에_붙였으면_그것도_통과한다(self):
        # --host로 바꿔 띄운 경우. 기본값(127.0.0.1)이면 영향이 없다.
        self.assertTrue(server._allowed_host("192.168.0.5:4318", "192.168.0.5"))
        self.assertFalse(server._allowed_host("evil.example", "192.168.0.5"))


class RequestOriginTest(unittest.TestCase):
    """이 서버는 인증이 없다. 브라우저가 보낸 요청은 받지 않는다.

    받으면 사용자가 열어 둔 아무 페이지나 지어낸 토큰 수를 usage_event에 넣을 수
    있다. append-only라 지울 수도 없다.
    """

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
        # 응답을 먼저 돌려주고 처리하므로(Claude를 붙잡지 않으려고) 잠깐 기다린다
        self.assertTrue(self.eventually(lambda: self.server.state.project_of("sess-1")))

    def test_Origin이_붙은_요청은_거절한다(self):
        """페이지에서 나간 POST에는 브라우저가 Origin을 붙인다.

        Content-Type을 text/plain으로 두면 사전 요청 없이 곧바로 오고, 응답을 읽지
        못해도 우리가 이미 처리한 뒤다.
        """
        body = json.dumps({"session_id": "sess-2", "cwd": self.directory.name})
        status = self.post(
            "/meterengine/session",
            body,
            {"Origin": "https://evil.example", "Content-Type": "text/plain"},
        )
        self.assertEqual(status, 403)
        self.assertIsNone(self.server.state.project_of("sess-2"))

    def test_다른_사이트에서_온_GET은_거절한다(self):
        # GET에는 Origin이 붙지 않는다. Sec-Fetch-Site가 그 자리를 메운다.
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
