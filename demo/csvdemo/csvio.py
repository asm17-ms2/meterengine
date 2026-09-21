from __future__ import annotations

import csv
from typing import List

from core.model import Event

REQUIRED_COLUMNS = ["transaction_id", "customer_id", "event_type", "timestamp", "properties"]

NOTE_COLUMN = "note"


def read_csv_events(path: str) -> List[Event]:
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
