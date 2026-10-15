from __future__ import annotations

import json
from dataclasses import dataclass, field
from decimal import ROUND_DOWN, Decimal, localcontext
from typing import Dict, List, Optional, Set, Tuple

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


DimensionValuesKey = Tuple[Tuple[str, tuple], ...]


@dataclass
class BillableMetricMeta:
    code: str
    event_type: str
    aggregation: str
    target_property: str
    dimension_properties: List[str] = field(default_factory=list)
    unit_prices: Dict[DimensionValuesKey, Decimal] = field(default_factory=dict)


@dataclass
class DimensionUsage:
    dimension_values: dict
    quantity: Decimal


@dataclass
class ExpectedLine:
    billable_metric_code: str
    quantity: Decimal
    amount: Optional[int]
    priced_quantity: Decimal = Decimal("0")
    unpriced: List[DimensionUsage] = field(default_factory=list)


@dataclass
class ExpectedCustomer:
    customer_id: str
    lines: List[ExpectedLine]
    amount: int


@dataclass
class ExpectedMonth:
    month: str
    customers: Dict[str, ExpectedCustomer]
    total_amount: int


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


def dimension_values_key(dimension_values: dict) -> DimensionValuesKey:
    return tuple(sorted((key, _typed_value(value)) for key, value in dimension_values.items()))


def _typed_value(value) -> tuple:
    if value is None:
        return ("null",)
    if isinstance(value, bool):
        return ("bool", value)
    if isinstance(value, (int, float, Decimal)):
        return ("number", Decimal(str(value)).normalize())
    if isinstance(value, str):
        return ("string", value)
    return ("json", json.dumps(value, sort_keys=True, default=str))


def sum_quantities_by_dimension_values(
    stored: List[StoredEvent], month: str, billable_metric: BillableMetricMeta
) -> Dict[str, Dict[DimensionValuesKey, DimensionUsage]]:
    sums: Dict[str, Dict[DimensionValuesKey, DimensionUsage]] = {}
    with localcontext() as context:
        context.prec = CALC_PRECISION
        for event in stored:
            if event.event_type != billable_metric.event_type:
                continue
            if kst_month(event.occurred_at) != month:
                continue
            value = event.properties.get(billable_metric.target_property)
            if not _is_number(value):
                continue
            dimension_values = {
                dimension_property: event.properties.get(dimension_property)
                for dimension_property in billable_metric.dimension_properties
            }
            by_dimension_values = sums.setdefault(event.customer_id.lower(), {})
            key = dimension_values_key(dimension_values)
            current = by_dimension_values.get(key)
            if current is None:
                by_dimension_values[key] = DimensionUsage(dimension_values, Decimal(value))
            else:
                current.quantity = current.quantity + Decimal(value)
    return sums


def line_amount(quantity: Decimal, unit_price: Decimal) -> int:
    with localcontext() as context:
        context.prec = CALC_PRECISION
        return int((quantity * unit_price).quantize(Decimal("1E1"), rounding=ROUND_DOWN))


def build_expected(
    stored: List[StoredEvent],
    billable_metrics: List[BillableMetricMeta],
    month: str,
    customer_ids: List[str],
) -> ExpectedMonth:
    sums_by_metric = {
        billable_metric.code: sum_quantities_by_dimension_values(stored, month, billable_metric)
        for billable_metric in billable_metrics
    }
    customers: Dict[str, ExpectedCustomer] = {}
    total_amount = 0
    for customer_id in customer_ids:
        lines = []
        customer_amount = 0
        for billable_metric in billable_metrics:
            line = _expected_line(billable_metric, sums_by_metric[billable_metric.code].get(customer_id, {}))
            customer_amount += line.amount or 0
            lines.append(line)
        customers[customer_id] = ExpectedCustomer(
            customer_id=customer_id, lines=lines, amount=customer_amount
        )
        total_amount += customer_amount
    return ExpectedMonth(month=month, customers=customers, total_amount=total_amount)


def _expected_line(
    billable_metric: BillableMetricMeta, by_dimension_values: Dict[DimensionValuesKey, DimensionUsage]
) -> ExpectedLine:
    quantity = Decimal("0")
    priced_quantity = Decimal("0")
    amount: Optional[int] = 0 if billable_metric.unit_prices else None
    unpriced = []
    with localcontext() as context:
        context.prec = CALC_PRECISION
        for key, usage in by_dimension_values.items():
            quantity += usage.quantity
            unit_price = billable_metric.unit_prices.get(key)
            if unit_price is None:
                if usage.quantity != 0:
                    unpriced.append(usage)
                continue
            priced_quantity += usage.quantity
            amount += line_amount(usage.quantity, unit_price)
    return ExpectedLine(
        billable_metric_code=billable_metric.code,
        quantity=quantity,
        amount=amount,
        priced_quantity=priced_quantity,
        unpriced=unpriced,
    )
