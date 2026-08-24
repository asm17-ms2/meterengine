"""Claude Code가 보내는 OTLP 로그를 팀의 사용량 이벤트로 바꾼다 (MS2-169).

Claude Code hook에는 토큰과 비용이 없다. 공식 문서가 그렇게 밝히고 OpenTelemetry를
쓰라고 안내한다. 그래서 토큰은 OTel 이벤트 로그에서 받고, hook은 세션이 어느 폴더에서
도는지를 알리는 데만 쓴다 (bridge/state.py 참조).

이 모듈은 부수효과가 없다. 네트워크도 파일도 건드리지 않고 페이로드만 변환한다.
"""

from __future__ import annotations

import json
import math
from datetime import datetime, timezone
from decimal import Decimal
from typing import Dict, Iterator, List, Optional, Tuple

from core.model import KST, MAX_TRANSACTION_ID, Event

# OTel 이벤트 이름 -> 우리 event_type.
#
# 담는 기준은 "과금이나 구분에 쓰이는 메타"다. user_prompt와 assistant_response는
# 프롬프트/응답 본문 쪽이라 담지 않는다. hook_*, plugin_*, mcp_server_connection처럼
# 실행 환경만 알리는 이벤트도 뺀다.
#
# llm_request라는 이름은 우리가 고른 것이 아니라 시드(R__seed.sql)의 billable_metric이
# 쓰는 event_type이다. 이 이름이어야 input-tokens/output-tokens 미터에 걸린다.
# tool_call은 아직 미터가 없어 저장만 되고 집계되지 않는다. 나중에 미터를 만들면
# 그때부터 이미 쌓인 이벤트가 함께 집계된다 (raw event를 먼저 모으는 설계 그대로다).
EVENT_TYPES = {
    "api_request": "llm_request",
    "api_error": "llm_error",
    "api_refusal": "llm_refusal",
    "tool_result": "tool_call",
}

# properties에 담지 않는 속성. Anthropic 계정을 가리키는 값들이라, 공개된 화면에
# 뜨는 데이터에 들어가면 안 된다. organization.id도 우리 도입사가 아니라 Anthropic
# 조직 ID다.
BLOCKED_ATTRIBUTES = frozenset(
    {
        "user.id",
        "user.email",
        "user.account_uuid",
        "user.account_id",
        "organization.id",
    }
)

# event_type과 timestamp로 따로 나가므로 properties에서는 뺀다.
CONSUMED_ATTRIBUTES = frozenset({"event.name", "event.timestamp"})

# 값을 반드시 JSON number로 넣을 키.
#
# 같은 이름의 속성이라도 이벤트에 따라 OTLP 타입이 다르다. 실측에서 duration_ms는
# api_request에서 intValue였고 tool_result에서는 stringValue("145")였다. 미터의
# 집계는 target_property 값이 JSON number일 때만 도므로(demo/README.md), 문자열로
# 들어가면 저장은 되는데 사용량에서 조용히 빠진다. 그래서 수치로 쓸 키는 여기서
# 못박아 강제한다.
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
    """이 로그 레코드로는 이벤트를 만들 수 없다. 건너뛴다."""


def iter_log_records(payload: dict) -> Iterator[dict]:
    """OTLP 페이로드에서 logRecord를 순서대로 꺼낸다.

    구조가 어긋난 부분은 조용히 건너뛴다. 브리지는 Claude Code를 막지 않는 것이
    우선이라, 한 레코드가 이상하다고 배치 전체를 버리지 않는다.
    """
    for resource_logs in _as_list(payload.get("resourceLogs")):
        for scope_logs in _as_list(resource_logs.get("scopeLogs")):
            for record in _as_list(scope_logs.get("logRecords")):
                if isinstance(record, dict):
                    yield record


def event_name(record: dict) -> Optional[str]:
    """레코드의 event.name을 돌려준다. 없으면 None."""
    for attribute in _as_list(record.get("attributes")):
        if isinstance(attribute, dict) and attribute.get("key") == "event.name":
            value = _any_value(attribute.get("value"))
            return value if isinstance(value, str) else None
    return None


def to_event(record: dict, customer_id: str, extra: Dict[str, object]) -> Event:
    """logRecord 하나를 전송할 Event로 바꾼다.

    extra는 브리지가 덧붙이는 값이다(프로젝트, 소유자 등). OTel 속성과 이름이
    겹치면 OTel 쪽이 이긴다. 원본이 정본이고 extra는 보강이다.
    """
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
    """properties를 JSON 객체 텍스트로 조립한다.

    json.dumps를 쓰지 않는 이유는 Decimal 때문이다. cost_usd 같은 소수를 float으로
    되돌리면 자릿수가 변한다. 팀 정책(MS2-121)이 금액과 수량에 float을 쓰지 않는
    것이라, 소수는 Decimal로 읽어 문자열로 그대로 끼운다. model.build_body_text가
    properties 원문을 그대로 끼우는 것과 같은 이유다.
    """
    parts = []
    for key in sorted(properties):
        parts.append(json.dumps(key, ensure_ascii=False) + ": " + _json_value(properties[key]))
    return "{" + ", ".join(parts) + "}"


def _json_value(value: object) -> str:
    if isinstance(value, Decimal):
        # Decimal은 json.dumps가 직렬화하지 못한다. 읽은 그대로의 표기를 쓴다.
        return str(value)
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, (int, float)):
        return json.dumps(value)
    return json.dumps(value, ensure_ascii=False)


def _flatten_attributes(record: dict) -> Dict[str, object]:
    """OTLP 속성 배열을 평평한 딕셔너리로 만든다.

    키의 점은 언더스코어로 바꾼다(agent.name -> agent_name). 시드 미터의
    target_property가 전부 언더스코어라 표기를 맞추고, 다차원 가격 정책이
    dimension_properties에 키를 선언할 때 점 있는 키를 다루지 않아도 된다.
    """
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
    """OTLP AnyValue에서 파이썬 값을 꺼낸다.

    intValue는 JSON 숫자로도 문자열로도 온다. protobuf의 JSON 매핑은 int64를
    문자열로 쓰도록 정하고 있는데 Claude Code는 숫자로 보낸다(실측). 어느 쪽이든
    받도록 둔다. 값을 여기서 정수로 바꾸지는 않는다. 문자열 상태의 판정은
    NUMERIC_KEYS를 아는 호출자가 한다.
    """
    if not isinstance(value, dict):
        return None
    for field in ("stringValue", "boolValue", "intValue", "doubleValue"):
        if field in value:
            return value[field]
    # arrayValue, kvlistValue, bytesValue는 평평하게 만들 수 없어 담지 않는다.
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
    """NaN과 Infinity를 걸러낸다. 담을 수 없는 값이라 없는 셈 친다.

    JSON에는 이 값들의 표기가 없다. 그대로 실으면 본문이 JSON으로 성립하지 않아
    서버가 거절하고, 로그에도 request_raw만 남아(jsonl_log._spliceable) verify가
    그 줄을 재구성하지 못한다. loads_decimal이 JSON 리터럴 NaN을 거부하는 것과
    같은 이유이고, 남은 유입 경로는 수치 키가 문자열 "NaN"으로 오는 경우다.
    """
    if isinstance(number, Decimal):
        return number if number.is_finite() else None
    if isinstance(number, float) and not math.isfinite(number):
        return None
    return number


def _transaction_id(name: str, attributes: Dict[str, object]) -> str:
    """멱등키를 고른다.

    같은 요청이 두 번 전송돼도 서버가 first-write-wins로 걸러 주므로, 요청을
    고유하게 가리키는 값이면 된다. request_id가 그 값이고(req_011Ce...),
    도구 이벤트에는 tool_use_id가 있다. 둘 다 없으면 세션과 순번으로 만든다.

    이벤트 이름을 앞에 붙이는 이유는 id 하나가 이벤트 여럿에 걸리기 때문이다.
    api_request와 api_refusal이 같은 request_id를 실어 보내면 멱등키가 같아져,
    뒤엣것이 서버에서 duplicate로 조용히 사라진다. 토큰이 실린 llm_request가
    그렇게 버려질 수 있다. 세션/순번 폴백은 원래 이름을 붙이고 있었다.
    """
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
    """이벤트 발생 시각을 KST 오프셋 RFC 3339로 만든다.

    timeUnixNano가 정본이다. 없으면 event.timestamp(ISO 8601 Z 표기)를 쓴다.
    KST로 바꾸는 이유는 월 귀속이 KST 자정 경계이기 때문이다. 값 자체는 같은
    순간이라 어느 표기든 결과가 같지만, 로그를 사람이 읽을 때 헷갈리지 않는다.

    폴백 값을 flatten된 속성이 아니라 원본 레코드에서 찾는 이유는, event.timestamp가
    CONSUMED_ATTRIBUTES라 flatten 단계에서 이미 빠지기 때문이다.
    """
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
    """페이로드에 실린 session.id를 중복 없이 모은다. 진단 출력용."""
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
