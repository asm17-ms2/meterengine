"""소스 이벤트로부터 기대 사용량과 기대 예정액을 독립 계산한다.

서버 계산 규칙의 정본:
- 합산 필터: UsageEventRepository.sumByCustomer (jsonb_typeof = 'number', KST 반열린 월 구간)
- 라인 금액: DraftInvoiceService (quantity x unit_price를 RoundingMode.DOWN 절사)
이 모듈은 그 규칙을 Decimal 연산으로 복제한다. float를 쓰지 않는다.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import ROUND_DOWN, Decimal, localcontext
from typing import Dict, List, Optional, Set

from core.jsonl_log import SendRecord
from core.model import (
    MAX_TRANSACTION_ID,
    Event,
    StoredEvent,
    is_uuid,
    kst_month,
    parse_properties,
    parse_rfc3339,
)

# 서버는 PostgreSQL numeric으로 정확 계산하므로 기본 컨텍스트(유효숫자 28)의
# 반올림이 끼지 않게 합산과 금액 계산 모두 넉넉한 정밀도로 돌린다.
CALC_PRECISION = 50


@dataclass
class MetricMeta:
    """서버 응답에서 유도한 미터 정의. unit_price는 인보이스 라인이 없으면 미상."""

    code: str
    event_type: str
    aggregation: str
    target_property: str
    unit_price: Optional[Decimal]


@dataclass
class ExpectedLine:
    metric_code: str
    quantity: Decimal
    amount: Optional[int]


@dataclass
class ExpectedCustomer:
    customer_id: str
    lines: List[ExpectedLine]
    amount: Optional[int]


@dataclass
class ExpectedMonth:
    month: str
    customers: Dict[str, ExpectedCustomer]
    total_amount: Optional[int]


@dataclass
class PredictedOutcome:
    index: int
    outcome: str
    # 거절 예측일 때만 채워진다. code는 서버 problem+json의 code에 대응한다
    code: Optional[str]
    detail: str


@dataclass
class Prediction:
    stored: List[StoredEvent]
    outcomes: List[PredictedOutcome]


def stored_events_from_log(records: List[SendRecord]) -> List[StoredEvent]:
    """로그에서 서버 상태에 기여한 이벤트만 뽑는다.

    서버 판정이 기록돼 있으므로 outcome=new만 저장분이다. duplicate는 이미
    저장된 것의 재전송이고, rejected는 저장이 없고, error는 저장 여부 불명이다.
    """
    stored = []
    for record in records:
        if record.outcome != "new":
            continue
        request = record.request
        try:
            stored.append(
                StoredEvent(
                    transaction_id=request["transaction_id"],
                    # 서버는 UUID를 대소문자 무관으로 파싱하고 응답은 소문자로 내려주므로
                    # 기대값 계산도 소문자로 정규화해야 서버 응답과 키가 맞는다
                    customer_id=str(request["customer_id"]).lower(),
                    event_type=request["event_type"],
                    occurred_at=parse_rfc3339(request["timestamp"]),
                    properties=request.get("properties") or {},
                )
            )
        except (KeyError, TypeError) as error:
            raise ValueError(
                "로그 seq %s의 신규(new) 레코드에 요청 본문이 온전하지 않습니다: %s" % (record.seq, error)
            )
    return stored


def predict_send(events: List[Event], known_customer_ids: Optional[Set[str]]) -> Prediction:
    """전송 전에 서버 판정을 시뮬레이션한다 (send 미리보기와 verify --csv 공용).

    거절된 전송은 저장이 없으므로 중복 판정(first-write-wins)은
    검증을 통과한 이벤트끼리만 돌린다. known_customer_ids가 None이면
    (고객 명단을 얻지 못한 경우) 미등록 고객 판정은 건너뛴다.
    invalid_event 같은 희귀 케이스는 예측하지 못한다.
    """
    stored = []
    outcomes = []
    seen_transaction_ids = set()
    for index, event in enumerate(events):
        problem = _validation_problem(event)
        if problem:
            outcomes.append(PredictedOutcome(index, "rejected", "validation_error", problem))
            continue
        customer_id = event.customer_id.lower()
        if known_customer_ids is not None and customer_id not in known_customer_ids:
            outcomes.append(PredictedOutcome(index, "rejected", "unknown_customer_reference", "미등록 고객"))
            continue
        if event.transaction_id in seen_transaction_ids:
            outcomes.append(PredictedOutcome(index, "duplicate", None, "같은 transaction_id가 이미 저장됨"))
            continue
        seen_transaction_ids.add(event.transaction_id)
        outcomes.append(PredictedOutcome(index, "new", None, ""))
        stored.append(
            StoredEvent(
                transaction_id=event.transaction_id,
                customer_id=customer_id,
                event_type=event.event_type,
                occurred_at=parse_rfc3339(event.timestamp_text),
                properties=parse_properties(event.properties_text),
            )
        )
    return Prediction(stored=stored, outcomes=outcomes)


def _validation_problem(event: Event) -> Optional[str]:
    if not event.transaction_id.strip():
        return "transaction_id 누락"
    if len(event.transaction_id) > MAX_TRANSACTION_ID:
        return "transaction_id가 %d자를 넘음" % MAX_TRANSACTION_ID
    if not is_uuid(event.customer_id):
        return "customer_id가 UUID가 아님"
    if not event.event_type.strip():
        return "event_type 누락"
    try:
        parse_rfc3339(event.timestamp_text)
    except ValueError:
        return "timestamp가 RFC 3339가 아님"
    try:
        parse_properties(event.properties_text)
    except ValueError:
        return "properties가 JSON 객체가 아님"
    return None


def _is_number(value) -> bool:
    # 서버의 jsonb_typeof = 'number'에 대응한다.
    # Python에서 bool은 int의 하위 타입이라 먼저 배제해야 한다.
    if isinstance(value, bool):
        return False
    return isinstance(value, (int, Decimal))


def sum_quantities(
    stored: List[StoredEvent], month: str, event_type: str, target_property: str
) -> Dict[str, Decimal]:
    """미터 하나에 대한 고객별 사용량 합. 대상 월 밖, 다른 event_type, 비숫자 값은 제외."""
    sums: Dict[str, Decimal] = {}
    with localcontext() as context:
        context.prec = CALC_PRECISION
        for event in stored:
            if event.event_type != event_type:
                continue
            if kst_month(event.occurred_at) != month:
                continue
            value = event.properties.get(target_property)
            if not _is_number(value):
                continue
            customer_id = event.customer_id.lower()
            current = sums.get(customer_id, Decimal("0"))
            sums[customer_id] = current + Decimal(value)
    return sums


def line_amount(quantity: Decimal, unit_price: Decimal) -> int:
    """서버와 동일한 라인 금액: 곱한 뒤 라인마다 절사해 정수로.

    기본 컨텍스트(유효숫자 28)의 곱셈 반올림이 끼어들지 않게 정밀도를 넉넉히 잡는다.
    """
    with localcontext() as context:
        context.prec = CALC_PRECISION
        return int((quantity * unit_price).to_integral_value(rounding=ROUND_DOWN))


def build_expected(
    stored: List[StoredEvent],
    metrics: List[MetricMeta],
    month: str,
    customer_ids: List[str],
) -> ExpectedMonth:
    """고객별 라인/소계와 총계를 계산한다. 단가 미상인 미터가 있으면 금액은 미상으로 둔다."""
    sums_by_metric = {
        metric.code: sum_quantities(stored, month, metric.event_type, metric.target_property)
        for metric in metrics
    }
    customers: Dict[str, ExpectedCustomer] = {}
    total_amount: Optional[int] = 0
    for customer_id in customer_ids:
        lines = []
        customer_amount: Optional[int] = 0
        for metric in metrics:
            quantity = sums_by_metric[metric.code].get(customer_id, Decimal("0"))
            if metric.unit_price is None:
                amount = None
                customer_amount = None
            else:
                amount = line_amount(quantity, metric.unit_price)
                if customer_amount is not None:
                    customer_amount += amount
            lines.append(ExpectedLine(metric_code=metric.code, quantity=quantity, amount=amount))
        customers[customer_id] = ExpectedCustomer(
            customer_id=customer_id, lines=lines, amount=customer_amount
        )
        if customer_amount is None:
            total_amount = None
        elif total_amount is not None:
            total_amount += customer_amount
    return ExpectedMonth(month=month, customers=customers, total_amount=total_amount)
