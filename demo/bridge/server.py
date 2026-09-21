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
        self._counts = dict.fromkeys(OUTCOMES + ("skipped",), 0)
        self._thread = threading.Thread(target=self._run, name="sender", daemon=True)
        self._thread.start()

    def _roll(self) -> None:
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
            except Exception as error:
                self._counts["error"] += 1
                _log("전송 처리 중 오류: %s" % error)

    def _send_one(self, record: dict, name: str, session_id: Optional[str]) -> None:
        if self.state.is_denied(session_id):
            self._counts["skipped"] += 1
            return
        project = self.state.project_of(session_id) or self.config.fallback_project
        if project in self.config.deny:
            self._counts["skipped"] += 1
            return
        customer_name = self.config.customer_name(project)
        self._roll()
        try:
            customer_id = self.resolver.resolve(customer_name)
        except (TransportError, RuntimeError) as error:
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
            self.state.forget_customer(customer_name)
            _log("고객 %s의 캐시를 버렸습니다. 다음 이벤트에서 다시 찾습니다." % customer_name)


def _unknown_customer(result) -> bool:
    return isinstance(result.body, dict) and result.body.get("code") == "customer_not_found"


class BridgeHandler(BaseHTTPRequestHandler):
    server_version = "MeterEngineBridge/1.0"

    def do_POST(self):  # noqa: N802
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
            sessions, customers, denied = self.server.state.snapshot()
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
            self.server.state.deny_session(session_id)
            _log("보내지 않음: %s (deny)" % cwd)
            return
        previous = self.server.state.project_of(session_id)
        if previous != project:
            _log("세션 %s: %s -> %s" % (session_id[:8], cwd, project))
        self.server.state.remember_session(session_id, project)

    def _from_local_tool(self) -> bool:
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
        pass


def serve(config: BridgeConfig, state: BridgeState, host: str, port: int) -> int:
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
    if not host:
        return True
    name = host.strip()
    if name.startswith("["):
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
    last = 0
    try:
        with open(path, encoding="utf-8", errors="replace") as f:
            for line in f:
                if not line.strip():
                    continue
                try:
                    record = json.loads(line)
                except ValueError:
                    continue
                if not isinstance(record, dict):
                    continue
                if record.get("type") == "send" and isinstance(record.get("seq"), int):
                    last = max(last, record["seq"])
    except OSError:
        return 0
    return last


def _log(message: str) -> None:
    print("[%s] %s" % (datetime.now(KST).strftime("%H:%M:%S"), message), flush=True)
