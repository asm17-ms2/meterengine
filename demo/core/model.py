"""공용 모델과 시간/JSON 파싱 유틸.

금액과 수량에 float를 쓰지 않는다는 팀 정책(MS2-121)에 따라
JSON의 소수는 항상 Decimal로 읽고, properties는 원문 텍스트를 정본으로 다룬다.
"""

from __future__ import annotations

import json
import re
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from decimal import Decimal

# 월 귀속 판정은 KST 자정 경계다 (backend UsageAggregationService.BILLING_ZONE과 동일).
# Asia/Seoul은 DST가 없으므로 고정 오프셋으로 충분하고 tzdata 의존이 없다.
KST = timezone(timedelta(hours=9))

# R__seed.sql의 데모 도입사. 시드가 바뀌면 여기도 맞춘다.
DEFAULT_ORG_ID = "d7cee55d-8c82-4afc-b996-6749d8b26a4e"
DEFAULT_BASE_URL = "http://localhost:8080"

# transaction_id 상한. 서버가 255자를 넘기면 400이다 (EventIngestRequest).
# 브리지는 여기에 맞춰 자르고(bridge/otel_map.py) CSV 데모는 넘는 행을 거절로
# 예측한다(csvdemo/expected.py). 같은 서버 제한이라 한 곳에 둔다.
MAX_TRANSACTION_ID = 255

# 서버(Jackson의 OffsetDateTime, ISO_OFFSET_DATE_TIME)가 수용하는 형태와 맞춘다:
# 초 생략, 소수초, 오프셋의 분/초 생략(+09, +09:00, +09:00:30)까지 허용하고
# 오프셋 범위도 서버와 같이 +-18시간으로 제한한다. 더 엄격하면 서버가 저장한
# 이벤트를 툴이 거부해 거짓 불일치가 난다.
_RFC3339 = re.compile(
    r"^(\d{4})-(\d{2})-(\d{2})[Tt](\d{2}):(\d{2})"
    r"(?::(\d{2})(?:\.(\d{1,9}))?)?"
    r"(?:[Zz]|([+-])(\d{2})(?::(\d{2})(?::(\d{2}))?)?)$"
)

_MAX_OFFSET = timedelta(hours=18)

_UUID = re.compile(r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")


@dataclass
class Event:
    """전송할 원본 이벤트. properties는 파싱하지 않은 JSON 텍스트를 정본으로 든다."""

    transaction_id: str
    customer_id: str
    event_type: str
    timestamp_text: str
    properties_text: str
    # CSV 작성자가 적는 표시 전용 설명. 서버로 전송하지 않는다
    note: str = ""


@dataclass
class StoredEvent:
    """서버에 저장된 것으로 판정된 이벤트. 기대값 계산의 입력."""

    transaction_id: str
    customer_id: str
    event_type: str
    occurred_at: datetime
    properties: dict


def parse_rfc3339(text: str) -> datetime:
    """RFC 3339 타임스탬프를 파싱한다. 오프셋이 없으면 거부한다.

    datetime.fromisoformat은 3.11 전에는 Z 표기와 자릿수 긴 소수초를 못 읽어
    버전에 따라 결과가 갈린다. 결정성을 위해 직접 파싱한다.
    """
    match = _RFC3339.match(text.strip())
    if not match:
        raise ValueError(
            "RFC 3339 타임스탬프가 아닙니다 (오프셋 필수, 예: 2026-08-14T09:30:00+09:00): " + text
        )
    year, month, day, hour, minute = (int(g) for g in match.group(1, 2, 3, 4, 5))
    second = int(match.group(6) or 0)
    fraction = match.group(7) or ""
    microsecond = int(fraction[:6].ljust(6, "0")) if fraction else 0
    sign = match.group(8)
    if sign is None:
        tz = timezone.utc
    else:
        offset = timedelta(
            hours=int(match.group(9)),
            minutes=int(match.group(10) or 0),
            seconds=int(match.group(11) or 0),
        )
        if offset > _MAX_OFFSET:
            raise ValueError("오프셋이 서버 수용 범위(+-18:00)를 넘습니다: " + text)
        tz = timezone(-offset if sign == "-" else offset)
    return datetime(year, month, day, hour, minute, second, microsecond, tzinfo=tz)


def kst_month(dt: datetime) -> str:
    """occurred_at이 귀속되는 KST 기준 월을 yyyy-MM으로 돌려준다."""
    return dt.astimezone(KST).strftime("%Y-%m")


def is_uuid(text: str) -> bool:
    return bool(_UUID.match(text))


def build_body_text(event: Event) -> str:
    """POST /v1/events 와이어 바디를 조립한다.

    properties는 원문 텍스트를 그대로 끼워 소수 자릿수를 보존한다.
    json.dumps는 Decimal을 직렬화하지 못하므로 재직렬화 경로를 두지 않는다.
    """
    return (
        "{"
        + '"transaction_id": ' + json.dumps(event.transaction_id, ensure_ascii=False)
        + ', "customer_id": ' + json.dumps(event.customer_id)
        + ', "event_type": ' + json.dumps(event.event_type, ensure_ascii=False)
        + ', "properties": ' + event.properties_text.strip()
        + ', "timestamp": ' + json.dumps(event.timestamp_text)
        + "}"
    )


def _reject_constant(name: str):
    raise ValueError("JSON에 허용되지 않는 상수입니다: " + name)


def loads_decimal(text: str):
    """소수를 Decimal로 읽는 json.loads. NaN/Infinity는 거부한다."""
    return json.loads(text, parse_float=Decimal, parse_constant=_reject_constant)


def parse_properties(text: str) -> dict:
    """properties JSON 텍스트를 검증하며 파싱한다. 최상위가 객체가 아니면 거부한다."""
    value = loads_decimal(text)
    if not isinstance(value, dict):
        raise ValueError("properties는 JSON 객체여야 합니다: " + text)
    return value
