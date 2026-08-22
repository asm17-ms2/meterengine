"""콘솔 출력 형식. 요청:응답 1:1 대조 2행, 비교표, 확인 게이트 문구.

한글은 터미널에서 2칸을 차지하므로 표 정렬은 east_asian_width 기준으로 계산한다.
"""

from __future__ import annotations

import os
import re
import shutil
import sys
import unicodedata
from typing import Dict, List, Optional

from core.model import Event, parse_properties, parse_rfc3339

_ANSI = re.compile(r"\x1b\[[0-9;]*m")

_TAG_STYLES = {
    "new": ("[NEW]", "32"),
    "duplicate": ("[DUP]", "33"),
    "rejected": ("[400]", "31"),
    "error": ("[ERR]", "35"),
}


def color_enabled(no_color_flag: bool) -> bool:
    if no_color_flag or os.environ.get("NO_COLOR"):
        return False
    if os.environ.get("TERM") == "dumb":
        return False
    return sys.stdout.isatty()


class Console:
    def __init__(self, color: bool):
        self.color = color

    def paint(self, text: str, code: str) -> str:
        if not self.color:
            return text
        return "\x1b[%sm%s\x1b[0m" % (code, text)

    def tag(self, outcome: str) -> str:
        label, code = _TAG_STYLES.get(outcome, ("[???]", "0"))
        return self.paint(label, code)

    def ok(self, text: str) -> str:
        return self.paint(text, "32")

    def fail(self, text: str) -> str:
        return self.paint(text, "31")

    def warn(self, text: str) -> str:
        return self.paint(text, "33")


def display_width(text: str) -> int:
    plain = _ANSI.sub("", text)
    width = 0
    for char in plain:
        width += 2 if unicodedata.east_asian_width(char) in ("W", "F") else 1
    return width


def pad_display(text: str, width: int) -> str:
    return text + " " * max(0, width - display_width(text))


def rpad_display(text: str, width: int) -> str:
    return " " * max(0, width - display_width(text)) + text


def truncate_display(text: str, max_width: int) -> str:
    if display_width(text) <= max_width:
        return text
    out = []
    used = 0
    for char in text:
        char_width = 2 if unicodedata.east_asian_width(char) in ("W", "F") else 1
        if used + char_width > max_width - 2:
            break
        out.append(char)
        used += char_width
    return "".join(out) + ".."


def terminal_width() -> Optional[int]:
    # 파이프 출력이면 자르지 않는다. 전문은 어차피 JSONL에 있다.
    if not sys.stdout.isatty():
        return None
    return shutil.get_terminal_size((100, 24)).columns


def _short_timestamp(timestamp_text: str) -> str:
    try:
        dt = parse_rfc3339(timestamp_text)
    except ValueError:
        return timestamp_text
    offset = dt.utcoffset()
    total_minutes = int(offset.total_seconds()) // 60
    sign = "+" if total_minutes >= 0 else "-"
    hours, minutes = divmod(abs(total_minutes), 60)
    suffix = sign + "%02d" % hours + (":%02d" % minutes if minutes else "")
    return dt.strftime("%m-%d %H:%M") + suffix


def _props_digest(properties_text: str) -> str:
    try:
        props = parse_properties(properties_text)
    except ValueError:
        return properties_text
    return " ".join("%s=%s" % (key, value) for key, value in props.items())


def format_send_pair(
    console: Console,
    seq: int,
    total: int,
    event: Event,
    outcome: str,
    response_summary: str,
    note: str = "",
) -> List[str]:
    counter_width = len(str(total))
    prefix = "[%s/%d] " % (str(seq).rjust(counter_width), total)
    head = (
        prefix
        + console.tag(outcome)
        + " "
        # 문제 설명은 transaction_id보다 먼저 보여 사람이 즉시 원인을 읽게 한다
        + ((note + "  ") if note else "")
        + truncate_display(event.transaction_id, 28)
        + "  "
        + event.customer_id[:8]
        + "  "
        + truncate_display(event.event_type, 20)
        + "  "
        + _short_timestamp(event.timestamp_text)
    )
    width = terminal_width()
    digest = _props_digest(event.properties_text)
    if digest:
        if width is not None:
            room = width - display_width(head) - 2
            if room >= 6:
                head += "  " + truncate_display(digest, room)
        else:
            head += "  " + digest
    reply = " " * display_width(prefix) + "=> " + response_summary
    if width is not None:
        reply = truncate_display(reply, width)
    return [head, reply]


def render_table(headers: List[str], rows: List[List[str]], aligns: Optional[List[str]] = None) -> List[str]:
    aligns = aligns or ["left"] * len(headers)
    widths = [display_width(header) for header in headers]
    for row in rows:
        for index, cell in enumerate(row):
            widths[index] = max(widths[index], display_width(cell))

    def render_row(cells):
        parts = []
        for index, cell in enumerate(cells):
            if aligns[index] == "right":
                parts.append(rpad_display(cell, widths[index]))
            else:
                parts.append(pad_display(cell, widths[index]))
        return "  ".join(parts).rstrip()

    lines = [render_row(headers)]
    lines.append("  ".join("-" * width for width in widths))
    lines.extend(render_row(row) for row in rows)
    return lines


def format_gate(
    base_url: str,
    org_id: str,
    event_count: int,
    month_counts: Dict[str, int],
    server_totals: Dict[str, Optional[int]],
    predicted_counts: Optional[Dict[str, int]] = None,
) -> List[str]:
    lines = [
        "전송 전 확인",
        "  대상 서버: " + base_url,
        "  도입사(X-Organization-Id): " + org_id,
        "  전송 건수: %d건" % event_count,
    ]
    for month in sorted(month_counts):
        total = server_totals.get(month)
        server_note = "확인 실패" if total is None else "%d건" % total
        lines.append(
            "  %s: %d건 전송 예정 (서버에 이미 %s 있음)" % (month, month_counts[month], server_note)
        )
    if predicted_counts is not None:
        lines.append(
            "  예상 결과: 신규 %d, 중복 %d, 400 거절 %d (CSV 기준 예측. 서버에 이미 있는 건은 신규 대신 중복으로 나온다)"
            % (
                predicted_counts.get("new", 0),
                predicted_counts.get("duplicate", 0),
                predicted_counts.get("rejected", 0),
            )
        )
    lines.append("")
    lines.append(
        "  주의: usage_event는 append-only라 DELETE가 불가능합니다. 잘못 보낸 데이터는"
    )
    lines.append(
        "  docker compose down -v로 DB를 초기화해야만 지울 수 있습니다."
    )
    return lines


def format_summary(sent: int, new: int, duplicate: int, rejected: int, error: int, log_path: Optional[str]) -> List[str]:
    line = "전송 %d건: 신규 %d, 중복 %d, 400 거절 %d" % (sent, new, duplicate, rejected)
    if error:
        line += ", 오류 %d" % error
    lines = [line]
    if log_path:
        lines.append("로그: " + log_path)
    return lines


MISMATCH_GUIDANCE = (
    "불일치가 있습니다. 소스에 없는 이벤트가 서버에 이미 있으면(이전 실행분, 수동 전송분 등)\n"
    "어긋날 수 있습니다. DB 볼륨은 재시작해도 유지되므로 이 상황은 실제로 자주 생깁니다.\n"
    "깨끗한 상태에서 다시 확인하려면: docker compose down -v 후 백엔드를 재기동하세요."
)
