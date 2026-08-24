"""브리지의 수집 서버 (MS2-169).

Claude Code의 OTLP exporter와 hook을 받아, 사용량 이벤트로 바꿔 큐에 넣는다.
전송은 워커 스레드가 한다. 진입점은 demo/otel_bridge.py다.
"""

from __future__ import annotations

import json
import os
import queue
import signal
import threading
from datetime import datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Optional, Tuple

from bridge import otel_map
from bridge.const import HEALTH_PATH, LOGS_DIR, LOGS_PATH, SESSION_PATH
from bridge.state import BridgeConfig, BridgeState, CustomerResolver, project_for_cwd
from core.api_client import ApiClient, TransportError
from core.jsonl_log import OUTCOMES, JsonlLogWriter, classify_outcome
from core.model import KST, build_body_text, loads_decimal


class Sender:
    """이벤트를 순차 전송하는 워커.

    OTLP 요청을 처리하는 자리에서 바로 전송하지 않는 이유는 Claude Code가 export
    응답을 기다리기 때문이다. 배치 하나에 이벤트가 여럿이면 그만큼 붙잡게 된다.
    큐에 넣고 바로 200을 돌려준 뒤 뒤에서 보낸다.
    """

    def __init__(self, config: BridgeConfig, state: BridgeState, logs_dir: str):
        self.config = config
        self.state = state
        self.client = ApiClient(config.base_url, config.org_id, timeout_seconds=config.timeout_seconds)
        self.resolver = CustomerResolver(self.client, state)
        self.queue: "queue.Queue[Optional[Tuple[dict, str, Optional[str]]]]" = queue.Queue()
        self.logs_dir = logs_dir
        self._day = ""
        self._seq = 0
        self.writer: Optional[JsonlLogWriter] = None
        self._roll()
        # 키는 JSONL의 outcome 어휘 그대로다. 손으로 적으면 이름이 어긋나
        # 성공을 거절로 세게 된다. skipped만 우리 것이다 (전송 자체를 하지 않은 건).
        self._counts = dict.fromkeys(OUTCOMES + ("skipped",), 0)
        self._thread = threading.Thread(target=self._run, name="sender", daemon=True)
        self._thread.start()

    def _roll(self) -> None:
        """그날 파일로 갈아탄다. 이미 그 파일이면 아무것도 하지 않는다.

        하루치를 한 파일에 이어쓴다. 재시작마다 새 파일을 만들면 기록이 쪼개져
        verify가 하루 전체를 대조하지 못한다. 반대로 파일명을 기동 때 한 번만
        정하면, 상주 프로세스가 자정을 넘겼을 때 어제 파일에 오늘 것이 쌓인다.
        그래서 쓰기 직전마다 날짜를 본다.

        sender 스레드 하나만 이 메서드를 부른다 (close는 큐에 신호를 넣고 기다린다).

        새 파일이 열린 뒤에야 갈아탄다. 순서를 뒤집으면(날짜부터 올리고 옛 파일을
        닫으면) 도중에 실패했을 때 self._day는 오늘인데 writer는 닫힌 상태가 된다.
        그러면 다음 이벤트부터 이 메서드가 곧바로 반환하고 닫힌 파일에 쓰다 죽기를
        영영 반복한다. 아래 순서면 실패해도 어제 파일에 계속 쌓이고, 다음 이벤트가
        다시 시도한다.
        """
        day = _today()
        if day == self._day:
            return
        path = os.path.join(self.logs_dir, "bridge-%s.jsonl" % day)
        seq = _last_seq(path)
        writer = JsonlLogWriter(path, append=True)
        if self.writer is not None:
            self.writer.close()
        self._day = day
        self._seq = seq
        self.writer = writer
        self.writer.write_run_header(
            _now_text(), self.config.base_url, self.config.org_id, None,
            ["otel_bridge.py", "serve"],
        )

    def submit(self, record: dict, name: str, session_id: Optional[str]) -> None:
        self.queue.put((record, name, session_id))

    def close(self) -> None:
        self.queue.put(None)
        self._thread.join(timeout=30)
        self.writer.close()

    @property
    def counts(self) -> dict:
        return dict(self._counts)

    def _run(self) -> None:
        while True:
            item = self.queue.get()
            if item is None:
                return
            try:
                self._send_one(*item)
            except Exception as error:  # 워커가 죽으면 이후 이벤트가 전부 사라진다
                self._counts["error"] += 1
                _log("전송 처리 중 오류: %s" % error)

    def _send_one(self, record: dict, name: str, session_id: Optional[str]) -> None:
        if self.state.is_denied(session_id):
            self._counts["skipped"] += 1
            return
        # 매핑이 없으면 hook을 놓친 세션이다. 버리지 않고 폴백으로 보낸다.
        # deny와 구별되는 자리라, 위에서 먼저 걸러야 한다.
        project = self.state.project_of(session_id) or self.config.fallback_project
        # 프로젝트 이름으로 deny를 한 번 더 본다. 세션이 묶인 뒤에 그 레포를 deny로
        # 바꾸면, 다음 hook이 와서 세션을 옮기기 전까지 is_denied가 거짓이다. 이
        # 줄이 없으면 그 사이의 이벤트가 실명 그대로 나간다. README가 "deny에 적은
        # 레포는 아예 보내지 않는다"고 약속하므로 전송 시점에 다시 대조한다.
        if project in self.config.deny:
            self._counts["skipped"] += 1
            return
        customer_name = self.config.customer_name(project)
        self._roll()
        try:
            customer_id = self.resolver.resolve(customer_name)
        except (TransportError, RuntimeError) as error:
            # 여기서도 기록을 남긴다. 전송 실패(아래)만 남기고 이쪽은 화면에만
            # 찍으면, 로그에 아무 흔적이 없어 "원래 없던 이벤트"와 "잃어버린
            # 이벤트"를 나중에 구별할 수 없다.
            self._counts["error"] += 1
            self._seq += 1
            self.writer.write_send(
                self._seq, _now_text(), None, None, None, "error",
                "고객 해석 실패(%s): %s" % (customer_name, error), None,
            )
            _log("고객 해석 실패(%s): %s" % (customer_name, error))
            return

        extra = {"project": project}
        if self.config.owner:
            extra["owner"] = self.config.owner
        try:
            event = otel_map.to_event(record, customer_id, extra)
        except otel_map.UnmappableRecord as error:
            self._counts["skipped"] += 1
            _log("건너뜀(%s): %s" % (name, error))
            return

        body_text = build_body_text(event)
        self._seq += 1
        try:
            result = self.client.post_event(body_text)
        except TransportError as error:
            self._counts["error"] += 1
            self.writer.write_send(
                self._seq, _now_text(), body_text, None, None, "error", str(error), None
            )
            _log("전송 실패: %s" % error)
            return

        outcome = classify_outcome(result.status, result.body)
        self._counts[outcome] += 1
        self.writer.write_send(
            self._seq,
            _now_text(),
            body_text,
            result.status,
            result.body_text,
            outcome,
            None,
            result.elapsed_ms,
        )
        if outcome == "rejected" and _unknown_customer(result):
            # 서버가 그 고객을 모른다. 캐시한 id가 죽은 값이라는 뜻이라 버린다.
            # 그대로 두면 캐시가 디스크에 있어 재시작해도 같은 id를 계속 보내고,
            # 그 프로젝트의 이벤트가 영영 거절된다.
            self.state.forget_customer(customer_name)
            _log("고객 %s의 캐시를 버렸습니다. 다음 이벤트에서 다시 찾습니다." % customer_name)


def _unknown_customer(result) -> bool:
    """400의 사유가 "그런 고객이 없다"인가 (backend의 problem+json code)."""
    return isinstance(result.body, dict) and result.body.get("code") == "unknown_customer_reference"


class BridgeHandler(BaseHTTPRequestHandler):
    """설정과 상태는 self.server에 붙어 있다 (run_serve가 붙인다)."""

    server_version = "MeterEngineBridge/1.0"

    def do_POST(self):  # noqa: N802 (BaseHTTPRequestHandler 규약)
        raw = self._read_body()
        if not self._from_local_tool():
            return
        if self.path.startswith(LOGS_PATH):
            self._handle_logs(raw)
        elif self.path.startswith(SESSION_PATH):
            self._handle_session(raw)
        else:
            self._respond(404, b"{}")

    def do_GET(self):  # noqa: N802
        if not self._from_local_tool():
            return
        if self.path.startswith(HEALTH_PATH):
            # 잠금 아래에서 한 번에 복사한다. 그대로 순회하면 hook이 매핑을 넣는
            # 순간 dict가 바뀌어 터진다.
            sessions, customers, denied = self.server.state.snapshot()
            # 세션 수가 아니라 프로젝트별로 센다. 어느 폴더가 어느 고객으로 갔는지가
            # 실제로 알고 싶은 값이라, 개수만 보여 주면 확인하러 DB를 뒤지게 된다.
            projects: dict = {}
            for project in sessions.values():
                projects[project] = projects.get(project, 0) + 1
            body = json.dumps(
                {
                    "status": "ok",
                    "base_url": self.server.config.base_url,
                    "org_id": self.server.config.org_id,
                    "owner": self.server.config.owner,
                    "projects": projects,
                    "customers": sorted(customers),
                    "denied_sessions": denied,
                    "counts": self.server.sender.counts,
                },
                ensure_ascii=False,
            ).encode("utf-8")
            self._respond(200, body)
        else:
            self._respond(404, b"{}")

    def _handle_logs(self, raw: bytes) -> None:
        """OTLP 수신.

        무슨 일이 있어도 200을 돌려준다. 브리지 사정으로 Claude Code의 export가
        재시도를 반복하거나 느려지면 안 된다.
        """
        self._respond(200, b"{}")
        try:
            payload = loads_decimal(raw.decode("utf-8"))
        except (ValueError, UnicodeDecodeError) as error:
            _log("OTLP 페이로드를 읽지 못했습니다: %s" % error)
            return
        if not isinstance(payload, dict):
            return
        for record, name, session_id in otel_map.split_records(payload):
            self.server.sender.submit(record, name, session_id)

    def _handle_session(self, raw: bytes) -> None:
        """hook 입력 수신. session_id를 프로젝트에 묶는다.

        응답은 빈 본문 200이다. 문서상 "2xx 빈 본문"은 출력 없는 성공과 같아서
        Claude의 프롬프트나 판단에 아무 영향을 주지 않는다.
        """
        self._respond(200, b"")
        try:
            data = json.loads(raw.decode("utf-8"))
        except (ValueError, UnicodeDecodeError):
            return
        if not isinstance(data, dict):
            return
        session_id = data.get("session_id")
        cwd = data.get("cwd") or ""
        if not isinstance(session_id, str) or not session_id:
            return
        project = project_for_cwd(str(cwd), self.server.config)
        if project is None:
            # deny에 걸린 폴더다. "매핑 없음"과 구별해서 남겨야 워커가 폴백으로
            # 보내지 않는다.
            self.server.state.deny_session(session_id)
            _log("보내지 않음: %s (deny)" % cwd)
            return
        # 판정 결과를 남긴다. 이 줄이 없으면 폴더를 왜 그 고객으로 봤는지 알아내려고
        # DB를 뒤져야 한다. 폴더와 프로젝트가 다를 때가 특히 그렇다(git 레포가
        # 아니거나 허용 목록 밖이라 폴백으로 합쳐진 경우).
        previous = self.server.state.project_of(session_id)
        if previous != project:
            _log("세션 %s: %s -> %s" % (session_id[:8], cwd, project))
        self.server.state.remember_session(session_id, project)

    def _from_local_tool(self) -> bool:
        """웹 페이지가 보낸 요청이면 막는다. 아니면 True.

        이 서버는 인증이 없다. 127.0.0.1에만 붙지만 그것으로는 부족하다.
        사용자가 아무 사이트나 열어 두면 그 페이지의 스크립트가 이 포트로
        POST할 수 있다. Content-Type을 text/plain으로 두면 CORS 사전 요청 없이
        곧바로 나가고, 응답을 읽지 못해도 우리는 이미 처리한 뒤다. 그러면 남의
        페이지가 지어낸 토큰 수를 우리 usage_event에 넣을 수 있고(append-only라
        지울 수 없다) /meterengine/session으로 남의 세션 귀속까지 바꿀 수 있다.

        브라우저가 붙이고 도구는 붙이지 않는 표시로 가른다. Origin은 페이지에서
        나간 POST에 항상 붙고, Sec-Fetch-Site는 GET을 포함해 요즘 브라우저가 늘
        붙인다(주소창으로 직접 연 경우만 none). Host까지 보는 것은 DNS 리바인딩
        때문이다. 공격자 도메인이 127.0.0.1로 풀려도 Host에는 그 도메인이 남는다.
        """
        if self.headers.get("Origin"):
            self._respond(403, b'{"error": "browser origin"}')
            return False
        site = self.headers.get("Sec-Fetch-Site")
        if site and site != "none":
            self._respond(403, b'{"error": "browser origin"}')
            return False
        if not _allowed_host(self.headers.get("Host"), getattr(self.server, "listen_host", "")):
            self._respond(403, b'{"error": "host"}')
            return False
        return True

    def _read_body(self) -> bytes:
        try:
            length = int(self.headers.get("Content-Length") or 0)
        except ValueError:
            return b""
        return self.rfile.read(length) if length > 0 else b""

    def _respond(self, status: int, body: bytes) -> None:
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        if body:
            self.wfile.write(body)

    def log_message(self, *args):
        """접근 로그를 끈다. 이벤트마다 한 줄씩 나오면 브리지 로그가 묻힌다."""


def serve(config: BridgeConfig, state: BridgeState, host: str, port: int) -> int:
    """브리지를 띄우고 SIGINT/SIGTERM까지 돈다.

    포트를 먼저 잡는다. Sender를 먼저 만들면 그 생성자가 그날 로그에 실행 헤더를
    쓰고 워커 스레드를 띄우는데, 브리지가 이미 떠 있어 바인드가 실패하면 아무것도
    보내지 않은 실행의 헤더만 남는다.
    """
    server = ThreadingHTTPServer((host, port), BridgeHandler)
    sender = Sender(config, state, LOGS_DIR)

    server.config = config
    server.state = state
    server.sender = sender
    server.listen_host = host
    server.daemon_threads = True

    _log("브리지 시작: http://%s:%d" % (host, port))
    _log("  전송 대상 %s (도입사 %s)" % (config.base_url, config.org_id))
    _log("  주인 %s, 허용 목록 %s" % (config.owner or "(없음)", config.allow or "(전부 실명)"))
    _log("  기록 %s" % os.path.join(LOGS_DIR, "bridge-<날짜>.jsonl"))

    stopping = threading.Event()

    def stop(signum, frame):
        if not stopping.is_set():
            stopping.set()
            threading.Thread(target=server.shutdown, daemon=True).start()

    signal.signal(signal.SIGINT, stop)
    signal.signal(signal.SIGTERM, stop)
    try:
        server.serve_forever()
    finally:
        server.server_close()
        sender.close()
        _log("브리지 종료: %s" % json.dumps(sender.counts, ensure_ascii=False))
    return 0


def _allowed_host(host: Optional[str], listen_host: str = "") -> bool:
    """Host 헤더가 이 브리지 자신을 가리키는가.

    헤더가 없으면 통과시킨다. HTTP/1.0 클라이언트에는 없을 수 있고, 이 검사가
    막으려는 것은 브라우저인데 브라우저는 언제나 붙인다.

    listen_host는 --host로 다른 주소에 붙였을 때를 위한 것이다. 기본값
    127.0.0.1이면 아무 영향이 없다.
    """
    if not host:
        return True
    name = host.strip()
    if name.startswith("["):  # [::1]:4318
        name = name[1:].split("]", 1)[0]
    elif ":" in name:
        name = name.rsplit(":", 1)[0]
    if listen_host and name == listen_host:
        return True
    return name in ("127.0.0.1", "localhost", "::1")


def _now_text() -> str:
    return datetime.now(KST).isoformat()


def _today() -> str:
    return datetime.now(KST).strftime("%Y%m%d")


def _last_seq(path: str) -> int:
    """이어쓸 파일의 마지막 seq. 없으면 0.

    재시작해도 번호가 1로 돌아가지 않게 한다. seq는 오류 메시지가 줄을 가리키는
    데 쓰이므로(expected.py) 한 파일 안에서 겹치면 어느 줄인지 알 수 없다.

    errors="replace"인 이유는 이 함수가 기동 경로에 있기 때문이다. 쓰는 도중
    죽으면 한글 한 글자가 바이트 중간에서 잘려 남는데, 기본 설정이면 그 줄을
    읽다가 UnicodeDecodeError가 난다. 이 함수는 Sender 생성자가 부르고 그 예외는
    아무도 잡지 않아서, launchd가 브리지를 살릴 때마다 같은 자리에서 죽는다.
    (KeepAlive라 10초 간격 무한 재시작이 되고, 로그 파일을 손으로 지워야 낫는다.)
    깨진 글자는 대체 문자로 바뀌고 그 줄만 JSON 파싱에서 걸러진다.
    """
    last = 0
    try:
        with open(path, encoding="utf-8", errors="replace") as f:
            for line in f:
                if not line.strip():
                    continue
                try:
                    record = json.loads(line)
                except ValueError:
                    continue  # 읽히지 않는 줄은 건너뛴다 (read_log와 같은 판단)
                if not isinstance(record, dict):
                    continue  # JSON이긴 한데 레코드가 아니다 (잘린 뒤 남은 조각)
                if record.get("type") == "send" and isinstance(record.get("seq"), int):
                    last = max(last, record["seq"])
    except OSError:
        return 0
    return last


def _log(message: str) -> None:
    print("[%s] %s" % (datetime.now(KST).strftime("%H:%M:%S"), message), flush=True)
