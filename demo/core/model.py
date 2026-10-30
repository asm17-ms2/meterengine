from __future__ import annotations

import json
import re
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from decimal import Decimal
from typing import Mapping, Optional

KST = timezone(timedelta(hours=9))

DEFAULT_API_KEY = "me_live_QouOFuCiJyAbC0VGK1ltUCkG4IZM2LtA"
DEFAULT_BASE_URL = "http://localhost:8080"
API_KEY_ENV = "METERENGINE_API_KEY"

_API_KEY = re.compile(r"me_(?:live|test)_[A-Za-z0-9]{32}")

MAX_TRANSACTION_ID = 255

_RFC3339 = re.compile(
    r"^(\d{4})-(\d{2})-(\d{2})[Tt](\d{2}):(\d{2})"
    r"(?::(\d{2})(?:\.(\d{1,9}))?)?"
    r"(?:[Zz]|([+-])(\d{2})(?::(\d{2})(?::(\d{2}))?)?)$"
)

_MAX_OFFSET = timedelta(hours=18)

_UUID = re.compile(r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")


@dataclass
class Event:
    transaction_id: str
    customer_id: str
    event_type: str
    timestamp_text: str
    properties_text: str
    note: str = ""


@dataclass
class StoredEvent:
    transaction_id: str
    customer_id: str
    event_type: str
    occurred_at: datetime
    properties: dict
    received_at: Optional[datetime] = None


def parse_rfc3339(text: str) -> datetime:
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


def is_api_key(value: Optional[str]) -> bool:
    return isinstance(value, str) and bool(_API_KEY.fullmatch(value))


def mask_api_key(key: Optional[str]) -> str:
    if not is_api_key(key):
        return "****"
    return key[: len("me_live_")] + "****" + key[-4:]


def redact_api_keys(text: str) -> str:
    return _API_KEY.sub(lambda match: mask_api_key(match.group(0)), text)


def resolve_api_key(
    option: Optional[str],
    environ: Mapping[str, str],
    fallback: Optional[str] = DEFAULT_API_KEY,
    fallback_label: str = "기본 로컬 키",
) -> Optional[str]:
    candidates = (("--api-key", option), (API_KEY_ENV, environ.get(API_KEY_ENV)), (fallback_label, fallback))
    for label, value in candidates:
        value = (value or "").strip()
        if not value:
            continue
        if not is_api_key(value):
            raise ValueError(
                "%s 값이 API 키 형식이 아닙니다 (me_live_ 또는 me_test_ 뒤에 영문과 숫자 32자)" % label
            )
        return value
    return None


def build_body_text(event: Event) -> str:
    return (
        "{"
        + '"transaction_id": ' + json.dumps(event.transaction_id, ensure_ascii=False)
        + ', "customer_id": ' + json.dumps(event.customer_id)
        + ', "type": ' + json.dumps(event.event_type, ensure_ascii=False)
        + ', "properties": ' + event.properties_text.strip()
        + ', "timestamp": ' + json.dumps(event.timestamp_text)
        + "}"
    )


def _reject_constant(name: str):
    raise ValueError("JSON에 허용되지 않는 상수입니다: " + name)


def loads_decimal(text: str):
    return json.loads(text, parse_float=Decimal, parse_constant=_reject_constant)


def parse_properties(text: str) -> dict:
    value = loads_decimal(text)
    if not isinstance(value, dict):
        raise ValueError("properties는 JSON 객체여야 합니다: " + text)
    return value
