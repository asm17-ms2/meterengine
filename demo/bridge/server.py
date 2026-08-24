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
from core.jsonl_log import JsonlLogWriter
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
        # 키는 JSONL의 outcome 어휘와 같다. 이름이 어긋나면 성공을 거절로 세게 된다.
        self._counts = {"new": 0, "duplicate": 0, "rejected": 0, "error": 0, "skipped": 0}
        self._thread = threading.Thread(target=self._run, name="sender", daemon=True)
        self._thread.start()

    def _roll(self) -> None:
        """그날 파일로 갈아탄다. 이미 그 파일이면 아무것도 하지 않는다.

        하루치를 한 파일에 이어쓴다. 재시작마다 새 파일을 만들면 기록이 쪼개져
        verify가 하루 전체를 대조하지 못한다. 반대로 파일명을 기동 때 한 번만
        정하면, 상주 프로세스가 자정을 넘겼을 때 어제 파일에 오늘 것이 쌓인다.
        그래서 쓰기 직전마다 날짜를 본다.

        sender 스레드 하나만 이 메서드를 부른다 (close는 큐에 신호를 넣고 기다린다).
        """
        day = _today()
        if day == self._day:
            return
        if self.writer is not None:
            self.writer.close()
        path = os.path.join(self.logs_dir, "bridge-%s.jsonl" % day)
        self._day = day
        self._seq = _last_seq(path)
        self.writer = JsonlLogWriter(path, append=True)
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
        try:
            customer_id = self.resolver.resolve(customer_name)
        except (TransportError, RuntimeError) as error:
            self._counts["error"] += 1
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
        self._roll()
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

        outcome = _outcome(result)
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


def _outcome(result) -> str:
    """verify가 읽는 outcome 값. send_cmd._classify와 판정이 같아야 한다.

    5xx를 rejected로 접으면 안 된다. verify는 rejected를 "서버가 거절했으니 저장되지
    않았다"로 확정 처리하는데, 응답만 실패하고 저장은 됐을 수 있다. error로 남겨야
    verify가 "저장 여부를 알 수 없다"고 경고한다.
    """
    if result.status == 200 and isinstance(result.body, dict) and "duplicate" in result.body:
        return "duplicate" if result.body["duplicate"] else "new"
    if result.status == 400:
        return "rejected"
    return "error"


class BridgeHandler(BaseHTTPRequestHandler):
    """설정과 상태는 self.server에 붙어 있다 (run_serve가 붙인다)."""

    server_version = "MeterEngineBridge/1.0"

    def do_POST(self):  # noqa: N802 (BaseHTTPRequestHandler 규약)
        raw = self._read_body()
        if self.path.startswith(LOGS_PATH):
            self._handle_logs(raw)
        elif self.path.startswith(SESSION_PATH):
            self._handle_session(raw)
        else:
            self._respond(404, b"{}")

    def do_GET(self):  # noqa: N802
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


def _now_text() -> str:
    return datetime.now(KST).isoformat()


def _today() -> str:
    return datetime.now(KST).strftime("%Y%m%d")


def _last_seq(path: str) -> int:
    """이어쓸 파일의 마지막 seq. 없으면 0.

    재시작해도 번호가 1로 돌아가지 않게 한다. seq는 오류 메시지가 줄을 가리키는
    데 쓰이므로(expected.py) 한 파일 안에서 겹치면 어느 줄인지 알 수 없다.
    """
    last = 0
    try:
        with open(path, encoding="utf-8") as f:
            for line in f:
                if not line.strip():
                    continue
                try:
                    record = json.loads(line)
                except ValueError:
                    continue  # 읽히지 않는 줄은 건너뛴다 (read_log와 같은 판단)
                if record.get("type") == "send" and isinstance(record.get("seq"), int):
                    last = max(last, record["seq"])
    except (FileNotFoundError, OSError):
        return 0
    return last


def _log(message: str) -> None:
    print("[%s] %s" % (datetime.now(KST).strftime("%H:%M:%S"), message), flush=True)
