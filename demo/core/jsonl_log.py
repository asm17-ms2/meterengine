from __future__ import annotations

import json
import os
from dataclasses import dataclass, field
from typing import List, Optional

from core.model import loads_decimal

FORMAT_VERSION = 1

OUTCOMES = ("new", "duplicate", "rejected", "error")


def classify_outcome(status: Optional[int], body) -> str:
    if status == 200 and isinstance(body, dict) and "duplicate" in body:
        return "duplicate" if body["duplicate"] else "new"
    if status is not None and 400 <= status < 500:
        return "rejected"
    return "error"


@dataclass
class RunHeader:
    started_at: str
    base_url: str
    org_id: str
    csv_path: Optional[str]
    argv: List[str]


@dataclass
class SendRecord:
    seq: int
    sent_at: str
    request: dict
    status: Optional[int]
    response: Optional[dict]
    outcome: str
    error: Optional[str]
    elapsed_ms: Optional[int]
    request_raw: Optional[str] = None
    response_raw: Optional[str] = None


@dataclass
class LogReadResult:
    header: Optional[RunHeader]
    records: List[SendRecord]
    warnings: List[str]
    damaged: List[int] = field(default_factory=list)
    header_count: int = 0


class JsonlLogWriter:
    def __init__(self, path: str, append: bool = False):
        parent = os.path.dirname(path)
        if parent:
            os.makedirs(parent, exist_ok=True)
        unfinished = append and _lacks_final_newline(path)
        self._file = open(path, "a" if append else "w", encoding="utf-8")
        self.path = path
        if unfinished:
            self._file.write("\n")
            self._file.flush()

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, tb):
        self.close()

    def close(self):
        self._file.close()

    def write_run_header(self, started_at_text, base_url, org_id, csv_path, argv):
        self._write_line(
            json.dumps(
                {
                    "v": FORMAT_VERSION,
                    "type": "run",
                    "started_at": started_at_text,
                    "base_url": base_url,
                    "org_id": org_id,
                    "csv": csv_path,
                    "argv": list(argv),
                },
                ensure_ascii=False,
            )
        )

    def write_send(
        self,
        seq,
        sent_at_text,
        request_body_text,
        status,
        response_text,
        outcome,
        error,
        elapsed_ms,
    ):
        request_part = _spliceable(request_body_text)
        response_part = _spliceable(response_text)
        parts = [
            '"v": ' + str(FORMAT_VERSION),
            '"type": "send"',
            '"seq": ' + str(seq),
            '"sent_at": ' + json.dumps(sent_at_text),
            '"request": ' + (request_part or "null"),
        ]
        if request_part is None and request_body_text is not None:
            parts.append('"request_raw": ' + json.dumps(request_body_text, ensure_ascii=False))
        parts.append('"status": ' + (str(status) if status is not None else "null"))
        parts.append('"response": ' + (response_part or "null"))
        if response_part is None and response_text is not None:
            parts.append('"response_raw": ' + json.dumps(response_text, ensure_ascii=False))
        parts.append('"outcome": ' + json.dumps(outcome))
        parts.append('"error": ' + json.dumps(error, ensure_ascii=False))
        parts.append('"elapsed_ms": ' + (str(elapsed_ms) if elapsed_ms is not None else "null"))
        self._write_line("{" + ", ".join(parts) + "}")

    def _write_line(self, line: str):
        self._file.write(line + "\n")
        self._file.flush()


def _lacks_final_newline(path: str) -> bool:
    """파일이 있고, 비어 있지 않고, 개행으로 끝나지 않는가."""
    try:
        with open(path, "rb") as f:
            if f.seek(0, os.SEEK_END) == 0:
                return False
            f.seek(-1, os.SEEK_END)
            return f.read(1) != b"\n"
    except OSError:
        return False


def _spliceable(text: Optional[str]) -> Optional[str]:
    if text is None:
        return None
    single_line = text.replace("\r", " ").replace("\n", " ")
    try:
        loads_decimal(single_line)
    except ValueError:
        return None
    return single_line


def read_log(path: str) -> LogReadResult:
    header = None
    header_count = 0
    records = []
    warnings = []
    broken: List[int] = []
    with open(path, encoding="utf-8") as f:
        lines = [line.rstrip("\n").rstrip("\r") for line in f]
    non_empty = [(i + 1, line) for i, line in enumerate(lines) if line.strip()]
    for position, (line_no, line) in enumerate(non_empty):
        try:
            data = loads_decimal(line)
        except ValueError:
            if position == len(non_empty) - 1:
                warnings.append("%s의 마지막 라인(%d행)이 잘려 있어 건너뜁니다" % (path, line_no))
            else:
                broken.append(line_no)
            continue
        if not isinstance(data, dict):
            broken.append(line_no)
            continue
        record_type = data.get("type")
        if record_type == "run":
            header_count += 1
            header = RunHeader(
                started_at=data.get("started_at"),
                base_url=data.get("base_url"),
                org_id=data.get("org_id"),
                csv_path=data.get("csv"),
                argv=data.get("argv") or [],
            )
        elif record_type == "send":
            records.append(
                SendRecord(
                    seq=data.get("seq"),
                    sent_at=data.get("sent_at"),
                    request=data.get("request") or {},
                    status=data.get("status"),
                    response=data.get("response"),
                    outcome=data.get("outcome"),
                    error=data.get("error"),
                    elapsed_ms=data.get("elapsed_ms"),
                    request_raw=data.get("request_raw"),
                    response_raw=data.get("response_raw"),
                )
            )
    if broken:
        listed = ", ".join(str(n) for n in broken[:5])
        if len(broken) > 5:
            listed += " 외 %d개" % (len(broken) - 5)
        warnings.append(
            "%s의 %d개 라인(%s행)이 JSONL이 아니라 건너뜁니다. 기록 일부가 손상됐습니다"
            % (path, len(broken), listed)
        )
    return LogReadResult(
        header=header,
        records=records,
        warnings=warnings,
        damaged=broken,
        header_count=header_count,
    )
