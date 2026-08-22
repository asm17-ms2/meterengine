"""CSV 소스 읽기.

[잠정] 이 스키마는 MS2-142(Mock 데이터)와 맞출 인터페이스로 아직 확정 전이다.
확정되면 이 모듈과 README의 스키마 절을 확정 내용으로 교체한다.

값 검증은 하지 않는다. 이 툴은 400 거절을 시연하는 것도 목적이라
잘못된 행도 그대로 서버에 보내야 하고, 판정은 서버 경계의 몫이다.
"""

from __future__ import annotations

import csv
from typing import List

from core.model import Event

REQUIRED_COLUMNS = ["transaction_id", "customer_id", "event_type", "timestamp", "properties"]

# 선택 컬럼. 행마다 사람이 적는 설명으로, 콘솔 표시에만 쓰고 서버로 보내지 않는다
NOTE_COLUMN = "note"


def read_csv_events(path: str) -> List[Event]:
    # utf-8-sig: Excel 내보내기가 붙이는 BOM을 허용한다
    with open(path, newline="", encoding="utf-8-sig") as f:
        reader = csv.DictReader(f)
        if reader.fieldnames is None:
            raise ValueError("CSV가 비어 있습니다: " + path)
        missing = [column for column in REQUIRED_COLUMNS if column not in reader.fieldnames]
        if missing:
            raise ValueError(
                "CSV에 필수 컬럼이 없습니다: %s (필요: %s)" % (", ".join(missing), ", ".join(REQUIRED_COLUMNS))
            )
        events = []
        for row in reader:
            events.append(
                Event(
                    transaction_id=row["transaction_id"] or "",
                    customer_id=row["customer_id"] or "",
                    event_type=row["event_type"] or "",
                    timestamp_text=row["timestamp"] or "",
                    properties_text=row["properties"] or "",
                    note=(row.get(NOTE_COLUMN) or "").strip(),
                )
            )
        return events
