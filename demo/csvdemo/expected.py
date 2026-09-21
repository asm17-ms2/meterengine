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

CALC_PRECISION = 50


@dataclass
class MetricMeta:
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
    code: Optional[str]
    detail: str


@dataclass
class Prediction:
    stored: List[StoredEvent]
    outcomes: List[PredictedOutcome]


def stored_events_from_log(records: List[SendRecord]) -> List[StoredEvent]:
    stored = []
    for record in records:
        if record.outcome != "new":
            continue
        request = record.request
        try:
            stored.append(
                StoredEvent(
                    transaction_id=request["transaction_id"],
                    customer_id=str(request["customer_id"]).lower(),
                    event_type=request["type"],
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
            outcomes.append(PredictedOutcome(index, "rejected", "customer_not_found", "미등록 고객"))
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
    if isinstance(value, bool):
        return False
    return isinstance(value, (int, Decimal))


def sum_quantities(
    stored: List[StoredEvent], month: str, event_type: str, target_property: str
) -> Dict[str, Decimal]:
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
    with localcontext() as context:
        context.prec = CALC_PRECISION
        return int((quantity * unit_price).to_integral_value(rounding=ROUND_DOWN))


def build_expected(
    stored: List[StoredEvent],
    metrics: List[MetricMeta],
    month: str,
    customer_ids: List[str],
) -> ExpectedMonth:
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
