from __future__ import annotations

import json
import math
from datetime import datetime, timezone
from decimal import Decimal
from typing import Dict, Iterator, List, Optional, Tuple

from core.model import KST, MAX_TRANSACTION_ID, Event

EVENT_TYPES = {
    "api_request": "llm_request",
    "api_error": "llm_error",
    "api_refusal": "llm_refusal",
    "tool_result": "tool_call",
}

BLOCKED_ATTRIBUTES = frozenset(
    {
        "user.id",
        "user.email",
        "user.account_uuid",
        "user.account_id",
        "organization.id",
    }
)

CONSUMED_ATTRIBUTES = frozenset({"event.name", "event.timestamp"})

NUMERIC_KEYS = frozenset(
    {
        "input_tokens",
        "output_tokens",
        "cache_read_tokens",
        "cache_creation_tokens",
        "cost_usd",
        "cost_usd_micros",
        "duration_ms",
        "tool_input_size_bytes",
        "tool_result_size_bytes",
        "attempt",
        "status_code",
        "event.sequence",
    }
)


class UnmappableRecord(Exception):
    pass


def iter_log_records(payload: dict) -> Iterator[dict]:
    for resource_logs in _as_list(payload.get("resourceLogs")):
        for scope_logs in _as_list(resource_logs.get("scopeLogs")):
            for record in _as_list(scope_logs.get("logRecords")):
                if isinstance(record, dict):
                    yield record


def event_name(record: dict) -> Optional[str]:
    for attribute in _as_list(record.get("attributes")):
        if isinstance(attribute, dict) and attribute.get("key") == "event.name":
            value = _any_value(attribute.get("value"))
            return value if isinstance(value, str) else None
    return None


def to_event(record: dict, customer_id: str, extra: Dict[str, object]) -> Event:
    name = event_name(record)
    if name is None:
        raise UnmappableRecord("event.name이 없습니다")
    event_type = EVENT_TYPES.get(name)
    if event_type is None:
        raise UnmappableRecord("보내지 않는 이벤트입니다: " + name)

    attributes = _flatten_attributes(record)
    properties = dict(extra)
    properties.update(attributes)
    properties["otel_event"] = name

    return Event(
        transaction_id=_transaction_id(name, attributes),
        customer_id=customer_id,
        event_type=event_type,
        timestamp_text=_timestamp_text(record),
        properties_text=properties_text(properties),
    )


def properties_text(properties: Dict[str, object]) -> str:
    parts = []
    for key in sorted(properties):
        parts.append(json.dumps(key, ensure_ascii=False) + ": " + _json_value(properties[key]))
    return "{" + ", ".join(parts) + "}"


def _json_value(value: object) -> str:
    if isinstance(value, Decimal):
        return str(value)
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, (int, float)):
        return json.dumps(value)
    return json.dumps(value, ensure_ascii=False)


def _flatten_attributes(record: dict) -> Dict[str, object]:
    flat: Dict[str, object] = {}
    for attribute in _as_list(record.get("attributes")):
        if not isinstance(attribute, dict):
            continue
        key = attribute.get("key")
        if not isinstance(key, str):
            continue
        if key in BLOCKED_ATTRIBUTES or key in CONSUMED_ATTRIBUTES:
            continue
        value = _any_value(attribute.get("value"))
        if value is None:
            continue
        if key in NUMERIC_KEYS:
            value = _as_number(value)
            if value is None:
                continue
        flat[key.replace(".", "_")] = value
    return flat


def _any_value(value: object) -> Optional[object]:
    if not isinstance(value, dict):
        return None
    for field in ("stringValue", "boolValue", "intValue", "doubleValue"):
        if field in value:
            return value[field]
    return None


def _as_number(value: object) -> Optional[object]:
    """수치로 써야 하는 값을 JSON number가 될 형태로 바꾼다."""
    if isinstance(value, bool):
        return None
    if isinstance(value, (int, float, Decimal)):
        return _finite(value)
    if isinstance(value, str):
        text = value.strip()
        try:
            return int(text)
        except ValueError:
            pass
        try:
            return _finite(Decimal(text))
        except Exception:
            return None
    return None


def _finite(number: object) -> Optional[object]:
    if isinstance(number, Decimal):
        return number if number.is_finite() else None
    if isinstance(number, float) and not math.isfinite(number):
        return None
    return number


def _transaction_id(name: str, attributes: Dict[str, object]) -> str:
    for key in ("request_id", "client_request_id", "tool_use_id"):
        value = attributes.get(key)
        if isinstance(value, str) and value.strip():
            return ("%s:%s" % (name, value.strip()))[:MAX_TRANSACTION_ID]
    session = attributes.get("session_id")
    sequence = attributes.get("event_sequence")
    if isinstance(session, str) and sequence is not None:
        return ("%s:%s:%s" % (name, session, sequence))[:MAX_TRANSACTION_ID]
    raise UnmappableRecord("멱등키로 쓸 값이 없습니다: " + name)


def _timestamp_text(record: dict) -> str:
    nano = record.get("timeUnixNano") or record.get("observedTimeUnixNano")
    if nano is not None:
        try:
            moment = datetime.fromtimestamp(int(nano) / 1_000_000_000, tz=timezone.utc)
            return moment.astimezone(KST).isoformat()
        except (TypeError, ValueError):
            pass
    for attribute in _as_list(record.get("attributes")):
        if isinstance(attribute, dict) and attribute.get("key") == "event.timestamp":
            text = _any_value(attribute.get("value"))
            if isinstance(text, str) and text:
                return text
    raise UnmappableRecord("시각을 알 수 없습니다")


def _as_list(value: object) -> List[dict]:
    return value if isinstance(value, list) else []


def session_ids(payload: dict) -> List[str]:
    found: List[str] = []
    for record in iter_log_records(payload):
        for attribute in _as_list(record.get("attributes")):
            if isinstance(attribute, dict) and attribute.get("key") == "session.id":
                value = _any_value(attribute.get("value"))
                if isinstance(value, str) and value not in found:
                    found.append(value)
    return found


def record_session_id(record: dict) -> Optional[str]:
    """레코드 하나의 session.id."""
    for attribute in _as_list(record.get("attributes")):
        if isinstance(attribute, dict) and attribute.get("key") == "session.id":
            value = _any_value(attribute.get("value"))
            return value if isinstance(value, str) else None
    return None


def split_records(payload: dict) -> List[Tuple[dict, str, Optional[str]]]:
    """보낼 레코드만 (레코드, event.name, session.id)로 추려 돌려준다."""
    picked = []
    for record in iter_log_records(payload):
        name = event_name(record)
        if name in EVENT_TYPES:
            picked.append((record, name, record_session_id(record)))
    return picked
