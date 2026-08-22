"""send 실행 기록 JSONL의 쓰기와 읽기. 포맷 명세의 정본은 README다.

한 파일은 헤더 라인(type=run) 하나와 전송 라인(type=send) 여럿으로 구성된다.
request/response는 와이어에 실린 텍스트를 그대로 끼워 넣어(재직렬화 없음)
소수 자릿수까지 재현 가능하게 보존한다. 중단에 대비해 라인마다 flush한다.
"""

from __future__ import annotations

import json
import os
from dataclasses import dataclass
from typing import List, Optional

from core.model import loads_decimal

FORMAT_VERSION = 1


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
    # 와이어 텍스트가 JSON으로 성립하지 않을 때(깨진 properties 등) 원문 보존용
    request_raw: Optional[str] = None
    response_raw: Optional[str] = None


@dataclass
class LogReadResult:
    header: Optional[RunHeader]
    records: List[SendRecord]
    warnings: List[str]


class JsonlLogWriter:
    def __init__(self, path: str):
        parent = os.path.dirname(path)
        if parent:
            os.makedirs(parent, exist_ok=True)
        self._file = open(path, "w", encoding="utf-8")
        self.path = path

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
        # request/response는 와이어 텍스트를 그대로 끼운다. json.dumps를 거치면
        # Decimal 직렬화 실패나 자릿수 변형이 생기므로 문자열 조립으로 만든다.
        # 단 와이어 텍스트가 JSON으로 성립하지 않으면(깨진 properties를 일부러
        # 보내는 400 시연 등) 그대로 끼우면 로그 전체가 못 읽게 되므로,
        # 그 경우에만 JSON 문자열로 감싼 *_raw 필드로 원문을 보존한다.
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


def _spliceable(text: Optional[str]) -> Optional[str]:
    """라인에 그대로 끼워도 되는 형태로 만든다. JSON이 아니면 None.

    유효한 JSON에서 이스케이프 안 된 개행은 토큰 사이 공백뿐이라
    (문자열 안에는 올 수 없다) 공백 치환은 값을 바꾸지 않는다.
    """
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
    records = []
    warnings = []
    with open(path, encoding="utf-8") as f:
        # splitlines()는 U+2028 같은 유니코드 경계로도 쪼개므로 파일 개행 기준으로만 나눈다
        lines = [line.rstrip("\n").rstrip("\r") for line in f]
    non_empty = [(i + 1, line) for i, line in enumerate(lines) if line.strip()]
    for position, (line_no, line) in enumerate(non_empty):
        try:
            data = loads_decimal(line)
        except ValueError:
            if position == len(non_empty) - 1:
                # 전송 도중 중단되면 마지막 라인이 잘릴 수 있다. 그 앞까지는 유효하다.
                warnings.append("%s의 마지막 라인(%d행)이 잘려 있어 건너뜁니다" % (path, line_no))
                continue
            raise ValueError("%s의 %d행이 JSONL이 아닙니다" % (path, line_no))
        record_type = data.get("type")
        if record_type == "run":
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
        # 모르는 type은 전방 호환을 위해 조용히 건너뛴다
    return LogReadResult(header=header, records=records, warnings=warnings)
