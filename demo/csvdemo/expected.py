from __future__ import annotations

import json
from datetime import datetime, timezone
from dataclasses import dataclass, field
from decimal import ROUND_HALF_UP, Decimal, localcontext
from fractions import Fraction
from typing import Dict, List, Optional, Set, Tuple, Union

from core.jsonl_log import SendRecord
from core.model import (
    KST,
    MAX_TRANSACTION_ID,
    Event,
    StoredEvent,
    is_uuid,
    kst_month,
    parse_properties,
    parse_rfc3339,
)

CALC_PRECISION = 50
CUTOFF_HOUR = 13
SUPPORTED_AGGREGATIONS = frozenset({"sum", "count", "count_distinct", "min", "max", "avg", "first", "last", "median", "percentile"})


DimensionValuesKey = Tuple[Tuple[str, tuple], ...]


@dataclass
class BillableMetricMeta:
    code: str
    event_type: str
    aggregation: str
    target_property: Optional[str]
    dimension_properties: List[str] = field(default_factory=list)
    unit_prices: Dict[DimensionValuesKey, Decimal] = field(default_factory=dict)
    price_rates: Dict[DimensionValuesKey, dict] = field(default_factory=dict)
    customer_price_rates: Dict[str, Dict[DimensionValuesKey, dict]] = field(default_factory=dict)
    unit_size: Decimal = Decimal("1")
    rounding_mode: str = "none"
    rounding_precision: int = 0
    percentile: Optional[Decimal] = None


@dataclass
class DimensionUsage:
    dimension_values: dict
    quantity: Decimal
    raw_quantity: Optional[Decimal] = None
    exact_quantity: Optional[Fraction] = None
    raw_fraction: Optional[Fraction] = None


@dataclass
class ExpectedLine:
    billable_metric_code: str
    quantity: Decimal
    amount: Optional[int]
    priced_quantity: Decimal = Decimal("0")
    unpriced: List[DimensionUsage] = field(default_factory=list)
    raw_quantity: Decimal = Decimal("0")
    dimension_usages: List[DimensionUsage] = field(default_factory=list)
    raw_fraction: Optional[Fraction] = None
    priced_fraction: Fraction = Fraction(0)


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
                    received_at=datetime.fromisoformat(record.sent_at) if record.sent_at else None,
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
    return isinstance(value, (int, float, Decimal)) and Decimal(str(value)).is_finite()


def dimension_values_key(dimension_values: dict) -> DimensionValuesKey:
    return tuple(sorted((key, _typed_value(value)) for key, value in dimension_values.items()))


def _typed_value(value) -> tuple:
    if value is None:
        return ("null",)
    if isinstance(value, bool):
        return ("bool", value)
    if isinstance(value, (int, float, Decimal)):
        return ("number", Decimal(str(value)))
    if isinstance(value, str):
        return ("string", value)
    return ("json", json.dumps(value, sort_keys=True, default=str))


def cutoff_at(month: str) -> datetime:
    """서버가 그 달 사용량을 집계하는 마감 시각. 다음 달 1일 13:00 KST다."""
    year, month_number = (int(part) for part in month.split("-"))
    if month_number == 12:
        year, month_number = year + 1, 1
    else:
        month_number += 1
    return datetime(year, month_number, 1, CUTOFF_HOUR, tzinfo=KST)


def sum_quantities_by_dimension_values(
    stored: List[StoredEvent], month: str, billable_metric: BillableMetricMeta, cutoff=None
) -> Dict[str, Dict[DimensionValuesKey, DimensionUsage]]:
    return aggregate_quantities(stored, month, billable_metric, cutoff)[1]


def aggregate_quantities(
    stored: List[StoredEvent], month: str, billable_metric: BillableMetricMeta, cutoff=None
) -> Tuple[Dict[str, DimensionUsage], Dict[str, Dict[DimensionValuesKey, DimensionUsage]]]:
    if billable_metric.aggregation not in SUPPORTED_AGGREGATIONS:
        raise ValueError("지원하지 않는 집계 함수입니다: %s" % billable_metric.aggregation)
    totals: Dict[str, List[StoredEvent]] = {}
    groups: Dict[str, Dict[DimensionValuesKey, Tuple[dict, List[StoredEvent]]]] = {}
    cutoff = cutoff or datetime.now(timezone.utc)
    received_cutoff = cutoff_at(month)
    for event in stored:
        if event.event_type != billable_metric.event_type:
            continue
        if kst_month(event.occurred_at) != month or event.occurred_at >= cutoff:
            continue
        if event.received_at is not None and event.received_at >= received_cutoff:
            continue
        value = event.properties.get(billable_metric.target_property)
        aggregation = billable_metric.aggregation
        if aggregation == "count_distinct":
            if not isinstance(value, (str, bool)) and not _is_number(value):
                continue
        elif aggregation != "count" and not _is_number(value):
            continue
        customer_id = event.customer_id.lower()
        totals.setdefault(customer_id, []).append(event)
        dimension_values = {key: event.properties.get(key) for key in billable_metric.dimension_properties}
        key = dimension_values_key(dimension_values)
        customer_groups = groups.setdefault(customer_id, {})
        customer_groups.setdefault(key, (dimension_values, []))[1].append(event)
    total_usages = {customer: _aggregate_usage({}, events, billable_metric) for customer, events in totals.items()}
    dimension_usages = {
        customer: {key: _aggregate_usage(dimensions, events, billable_metric) for key, (dimensions, events) in customer_groups.items()}
        for customer, customer_groups in groups.items()
    }
    return total_usages, dimension_usages


def _aggregate_usage(dimensions: dict, events: List[StoredEvent], metric: BillableMetricMeta) -> DimensionUsage:
    with localcontext() as context:
        context.prec = CALC_PRECISION
        aggregation = metric.aggregation
        if aggregation == "count":
            raw = Decimal(len(events))
        elif aggregation == "count_distinct":
            raw = Decimal(len({_typed_value(event.properties[metric.target_property]) for event in events}))
        elif aggregation in ("first", "last"):
            ordered = sorted(events, key=lambda event: (event.occurred_at, event.transaction_id))
            raw = Decimal(str(ordered[0 if aggregation == "first" else -1].properties[metric.target_property]))
        else:
            values = [Decimal(str(event.properties[metric.target_property])) for event in events]
            if aggregation == "sum":
                raw = exact_decimal_sum(values)
            elif aggregation == "avg":
                total = exact_decimal_sum(values)
                raw = decimal_ratio(total, Decimal(len(values)))
            elif aggregation == "min":
                raw = min(values)
            elif aggregation == "max":
                raw = max(values)
            else:
                ordered = sorted(values)
                percentile = Decimal("50") if aggregation == "median" else metric.percentile
                if percentile is None or not 0 <= percentile <= 100:
                    raise ValueError("백분위는 0부터 100 사이여야 합니다")
                position = Decimal(len(ordered) - 1) * percentile * Decimal("0.01")
                lower = int(position)
                upper = min(lower + 1, len(ordered) - 1)
                raw = ordered[lower] + (ordered[upper] - ordered[lower]) * (position - lower)
        raw_fraction = Fraction(raw)
        if aggregation in ("sum", "avg"):
            raw_fraction = sum((Fraction(value) for value in values), Fraction(0))
            if aggregation == "avg":
                raw_fraction /= len(values)
        elif aggregation in ("median", "percentile"):
            exact_position = Fraction(len(ordered) - 1) * Fraction(percentile) / 100
            lower = exact_position.numerator // exact_position.denominator
            upper = min(lower + 1, len(ordered) - 1)
            raw_fraction = Fraction(ordered[lower]) + (Fraction(ordered[upper]) - Fraction(ordered[lower])) * (exact_position - lower)
            value_exponent = min(ordered[lower].as_tuple().exponent, ordered[upper].as_tuple().exponent)
            result_exponent = min(ordered[lower].as_tuple().exponent, value_exponent + min(0, percentile.as_tuple().exponent - 2))
            raw = fraction_decimal_at_exponent(raw_fraction, result_exponent)
        if aggregation != "avg" and Fraction(raw) != raw_fraction:
            raw = finite_fraction_decimal(raw_fraction)
        numerator = total if aggregation == "avg" else raw
        denominator = Decimal(len(values)) if aggregation == "avg" else Decimal("1")
        quantity = transform_quantity(raw, metric, raw_fraction, numerator, denominator)
        exact_quantity = raw_fraction / Fraction(metric.unit_size) if metric.rounding_mode == "none" else Fraction(quantity)
        return DimensionUsage(dimensions, quantity, raw, exact_quantity, raw_fraction)


def transform_quantity(raw: Decimal, metric: BillableMetricMeta, raw_fraction: Optional[Fraction] = None,
                       numerator: Optional[Decimal] = None, denominator: Optional[Decimal] = None) -> Decimal:
    if metric.unit_size <= 0:
        raise ValueError("과금 단위 크기는 0보다 커야 합니다")
    with localcontext() as context:
        context.prec = CALC_PRECISION
        context.clear_flags()
        if metric.rounding_mode != "none":
            if metric.rounding_mode not in {"ceil", "floor", "round"} or not 0 <= metric.rounding_precision <= 12:
                raise ValueError("반올림 방식이나 소수 자릿수가 잘못됐습니다")
            scaled = (raw_fraction if raw_fraction is not None else Fraction(raw)) / Fraction(metric.unit_size) * 10 ** metric.rounding_precision
            numerator, denominator = scaled.numerator, scaled.denominator
            if metric.rounding_mode == "ceil":
                coefficient = -(-numerator // denominator)
            elif metric.rounding_mode == "floor":
                coefficient = numerator // denominator
            else:
                magnitude, remainder = divmod(abs(numerator), denominator)
                coefficient = (magnitude + (remainder * 2 >= denominator)) * (-1 if numerator < 0 else 1)
            return Decimal("%se-%s" % (coefficient, metric.rounding_precision))
        if metric.unit_size == 1:
            return raw
        return decimal_ratio(numerator if numerator is not None else raw,
                             finite_fraction_decimal(Fraction(denominator if denominator is not None else Decimal("1")) * Fraction(metric.unit_size)),
                             -raw.as_tuple().exponent)


def decimal_ratio(numerator: Decimal, denominator: Decimal, minimum_scale=0) -> Decimal:
    ratio = Fraction(numerator) / Fraction(denominator)
    exact = finite_fraction_decimal(ratio)
    if exact is not None:
        preferred_exponent = numerator.as_tuple().exponent - denominator.as_tuple().exponent
        return fraction_decimal_at_exponent(ratio, min(exact.as_tuple().exponent, preferred_exponent))
    numerator_digits = numerator.as_tuple()
    denominator_digits = denominator.as_tuple()
    numerator_magnitude = len(numerator_digits.digits) + numerator_digits.exponent
    denominator_magnitude = len(denominator_digits.digits) + denominator_digits.exponent
    scale = max(18, minimum_scale, -numerator_digits.exponent, 18 + denominator_magnitude - numerator_magnitude)
    with localcontext() as context:
        context.prec = max(CALC_PRECISION, scale + max(0, numerator_magnitude - denominator_magnitude) + 10)
        quantity = numerator / denominator
        return quantity.quantize(Decimal("1").scaleb(-scale), rounding=ROUND_HALF_UP)


def finite_fraction_decimal(value: Fraction) -> Optional[Decimal]:
    denominator = value.denominator
    powers = []
    for prime in (2, 5):
        power = 0
        while denominator % prime == 0:
            denominator //= prime
            power += 1
        powers.append(power)
    if denominator != 1:
        return None
    scale = max(powers)
    coefficient = value.numerator * 2 ** (scale - powers[0]) * 5 ** (scale - powers[1])
    return Decimal("%se-%s" % (coefficient, scale))


def fraction_decimal_at_exponent(value: Fraction, exponent: int) -> Decimal:
    coefficient = value / Fraction(10) ** exponent
    if coefficient.denominator != 1:
        raise ValueError("지정한 소수 자릿수로 정확한 값을 표현할 수 없습니다")
    return Decimal("%se%s" % (coefficient.numerator, exponent))


def exact_decimal_sum(values: List[Decimal]) -> Decimal:
    exponent = min([0] + [value.as_tuple().exponent for value in values])
    return fraction_decimal_at_exponent(sum((Fraction(value) for value in values), Fraction(0)), exponent)


def line_amount(quantity: Union[Decimal, Fraction], unit_price: Decimal) -> int:
    return int(Fraction(quantity) * Fraction(unit_price) / 10) * 10


def build_expected(
    stored: List[StoredEvent],
    billable_metrics: List[BillableMetricMeta],
    month: str,
    customer_ids: List[str],
    price_plans: Optional[Dict[str, dict]] = None,
    cutoff=None,
) -> ExpectedMonth:
    quantities_by_metric = {
        billable_metric.code: aggregate_quantities(stored, month, billable_metric, cutoff)
        for billable_metric in billable_metrics
    }
    customers: Dict[str, ExpectedCustomer] = {}
    total_amount = 0
    for customer_id in customer_ids:
        lines = []
        customer_amount = 0
        for billable_metric in billable_metrics:
            totals, dimensions = quantities_by_metric[billable_metric.code]
            line = _expected_line(billable_metric, dimensions.get(customer_id, {}), customer_id, totals.get(customer_id))
            customer_amount += line.amount or 0
            lines.append(line)
        used = any(
            (usage.exact_quantity if usage.exact_quantity is not None else usage.quantity) > 0
            for _, sums in quantities_by_metric.values()
            for usage in sums.get(customer_id, {}).values()
        )
        plan = (price_plans or {}).get(customer_id)
        if plan and used:
            minimum = int(plan["minimum_amount"])
            base_fee = int(plan["base_fee"])
            customer_amount = max(customer_amount, minimum - minimum % 10) + base_fee - base_fee % 10
        customers[customer_id] = ExpectedCustomer(
            customer_id=customer_id, lines=lines, amount=customer_amount
        )
        total_amount += customer_amount
    return ExpectedMonth(month=month, customers=customers, total_amount=total_amount)


def _expected_line(
    billable_metric: BillableMetricMeta, by_dimension_values: Dict[DimensionValuesKey, DimensionUsage],
    customer_id: Optional[str] = None,
    total: Optional[DimensionUsage] = None,
) -> ExpectedLine:
    priced_quantity = Decimal("0")
    priced_fraction = Fraction(0)
    rates = dict(billable_metric.price_rates)
    for key, price in billable_metric.unit_prices.items():
        rates.setdefault(key, {"model": "per_unit", "unit_price": price})
    rates.update(billable_metric.customer_price_rates.get(customer_id, {}))
    amount: Optional[int] = 0 if rates else None
    unpriced = []
    with localcontext() as context:
        context.prec = CALC_PRECISION
        for key, usage in by_dimension_values.items():
            exact_quantity = usage.exact_quantity if usage.exact_quantity is not None else Fraction(usage.quantity)
            if exact_quantity <= 0:
                continue
            rate = rates.get(key)
            if rate is None:
                unpriced.append(usage)
                continue
            priced_quantity += usage.quantity
            priced_fraction += exact_quantity
            amount += rate_amount(exact_quantity, rate)
    return ExpectedLine(
        billable_metric_code=billable_metric.code,
        quantity=total.quantity if total else Decimal("0"),
        amount=amount,
        priced_quantity=priced_quantity,
        unpriced=unpriced,
        raw_quantity=total.raw_quantity if total else Decimal("0"),
        dimension_usages=list(by_dimension_values.values()),
        raw_fraction=total.raw_fraction if total else Fraction(0),
        priced_fraction=priced_fraction,
    )


def rate_amount(quantity: Union[Decimal, Fraction], rate: dict) -> int:
    quantity = Fraction(quantity)
    if quantity <= 0:
        return 0
    model = rate.get("model") or "per_unit"
    if model == "per_unit":
        return line_amount(quantity, Decimal(str(rate["unit_price"])))
    tiers = rate.get("tiers") or []
    if model == "volume":
        for tier in tiers:
            upper = tier.get("up_to")
            if upper is None or quantity <= Fraction(Decimal(str(upper))):
                return line_amount(quantity, Decimal(str(tier["unit_price"])))
    elif model == "graduated":
        lower = Fraction(0)
        amount = 0
        for tier in tiers:
            limit = tier.get("up_to")
            upper = quantity if limit is None else min(quantity, Fraction(Decimal(str(limit))))
            amount += line_amount(max(Fraction(0), upper - lower), Decimal(str(tier["unit_price"])))
            if limit is None or quantity <= Fraction(Decimal(str(limit))):
                return amount
            lower = Fraction(Decimal(str(limit)))
    raise ValueError("지원하지 않거나 마지막 구간이 없는 요금 모델입니다")
