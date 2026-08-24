"""send 실행 기록 JSONL의 쓰기와 읽기. 포맷 명세의 정본은 README다.

한 파일은 헤더 라인(type=run)과 전송 라인(type=send) 여럿으로 구성된다. send는
실행 하나가 파일 하나라 헤더가 하나뿐이고, 브리지(MS2-169)는 하루치를 한 파일에
이어써서 재시작할 때마다 헤더가 하나씩 더 붙는다. 읽는 쪽은 마지막 헤더를 쓴다.
request/response는 와이어에 실린 텍스트를 그대로 끼워 넣어(재직렬화 없음)
소수 자릿수까지 재현 가능하게 보존한다. 중단에 대비해 라인마다 flush한다.
"""

from __future__ import annotations

import json
import os
from dataclasses import dataclass, field
from typing import List, Optional

from core.model import loads_decimal

FORMAT_VERSION = 1

# 전송 한 건의 판정. 이 어휘를 verify가 읽는다(csvdemo/expected.py의
# stored_events_from_log는 new만 저장분으로 본다). 포맷이 이 파일의 소관이라
# 판정하는 함수도 여기 둔다. 층마다 따로 구현하면 5xx를 한쪽만 거절로 접는
# 식으로 조용히 갈라진다.
OUTCOMES = ("new", "duplicate", "rejected", "error")


def classify_outcome(status: Optional[int], body) -> str:
    """서버 응답을 outcome으로 옮긴다. 브리지와 CSV 데모가 함께 쓴다.

    5xx를 rejected로 접으면 안 된다. verify는 rejected를 "서버가 거절했으니 저장되지
    않았다"로 확정 처리하는데, 응답만 실패하고 저장은 됐을 수 있다. error로 남겨야
    verify가 "저장 여부를 알 수 없다"고 경고한다.
    """
    if status == 200 and isinstance(body, dict) and "duplicate" in body:
        return "duplicate" if body["duplicate"] else "new"
    if status == 400:
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
    # 와이어 텍스트가 JSON으로 성립하지 않을 때(깨진 properties 등) 원문 보존용
    request_raw: Optional[str] = None
    response_raw: Optional[str] = None


@dataclass
class LogReadResult:
    header: Optional[RunHeader]
    records: List[SendRecord]
    warnings: List[str]
    # 건너뛴 라인의 행 번호. 파일 끝이 잘린 것(마지막 라인)은 여기 들어가지 않는다.
    # 부르는 쪽이 이것으로 판정을 가른다 (verify는 send 로그의 중간 손상을 오류로 본다).
    damaged: List[int] = field(default_factory=list)
    # 이 파일에 든 실행 헤더 수. 브리지는 재시작마다 하나씩 더 붙이므로 2 이상이 정상이고,
    # send는 실행 하나가 파일 하나라 1이다.
    header_count: int = 0


class JsonlLogWriter:
    """전송 기록을 쓴다.

    append는 브리지(MS2-169)를 위한 것이다. 브리지는 상주 프로세스라 하루에도 여러 번
    재시작되는데, 그때마다 새 파일을 만들면 기록이 쪼개져 verify가 하루치를 한 번에
    대조하지 못한다. send는 실행 한 번이 파일 하나라 append가 필요 없다.
    """

    def __init__(self, path: str, append: bool = False):
        parent = os.path.dirname(path)
        if parent:
            os.makedirs(parent, exist_ok=True)
        # 이어쓰기 전에 앞 줄이 끝나 있는지 본다. 쓰다가 죽으면 개행 없이 끊긴
        # 라인이 남는데, 거기에 그냥 이어 붙이면 두 레코드가 한 줄로 엉겨 둘 다
        # 못 읽게 된다. 그 한 줄이 실행 헤더면 verify가 전송 대상을 모른 채
        # 기본값(localhost)으로 검증한다.
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
    """JSONL을 읽는다. 읽을 수 없는 라인은 건너뛰고 경고로 올린다.

    마지막 라인만 봐주면 안 되는 이유는 브리지가 하루치를 이어쓰기 때문이다.
    쓰다 만 라인이 남은 채 브리지가 다시 뜨면 그 뒤에 실행 헤더가 붙어, 잘린
    라인이 더 이상 마지막이 아니게 된다. 그때 파일 전체를 거부하면 그날 기록이
    통째로 검증 불가가 된다. 디스크가 차서 쓰기가 끊긴 경우도 같은 모양이다.

    그렇다고 조용히 넘기지는 않는다. send는 실행 하나가 파일 하나라 중간이
    깨졌다는 것 자체가 신호다. 건너뛴 줄을 경고로 올리고, 몇 행이 깨졌는지를
    damaged로 함께 돌려준다. 경고는 사람이 읽고 지나칠 수 있지만 damaged는
    부르는 쪽이 종료 코드로 바꿀 수 있다 (verify가 그렇게 한다).
    """
    header = None
    header_count = 0
    records = []
    warnings = []
    broken: List[int] = []
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
            else:
                broken.append(line_no)
            continue
        if not isinstance(data, dict):
            # JSON으로는 읽히는데 레코드가 아니다. 잘린 라인 뒤에 숫자나 배열 조각만
            # 남은 경우다. 확인 없이 .get을 부르면 여기서 AttributeError로 죽는다.
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
        # 모르는 type은 전방 호환을 위해 조용히 건너뛴다
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
