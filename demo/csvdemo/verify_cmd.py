from __future__ import annotations

import sys
from dataclasses import dataclass
from decimal import Decimal, localcontext
from fractions import Fraction
from typing import Dict, List, Optional

from core.api_client import (
    ApiClient,
    ApiResult,
    Organization,
    parse_problem,
    require_organization,
    roster_from_usage,
)
from csvdemo.csvio import read_csv_events
from csvdemo.expected import (
    BillableMetricMeta,
    CALC_PRECISION,
    SUPPORTED_AGGREGATIONS,
    DimensionValuesKey,
    ExpectedLine,
    ExpectedMonth,
    build_expected,
    dimension_values_key,
    predict_send,
    stored_events_from_log,
)
from core.jsonl_log import read_log
from core.model import DEFAULT_BASE_URL, StoredEvent, kst_month, parse_rfc3339
from csvdemo.render import MISMATCH_GUIDANCE, Console, render_table

EXIT_MATCH = 0
EXIT_MISMATCH = 1
EXIT_ERROR = 2


@dataclass
class VerifySource:
    stored: List[StoredEvent]
    rejected_count: int
    error_count: int
    client: ApiClient
    label: str
    organization_id: Optional[str] = None
    organization: Optional[Organization] = None


def run_verify(args, console: Console) -> int:
    if not args.log and not args.csv:
        print("기대값 소스가 필요합니다: --log <send가 남긴 JSONL> 또는 --csv <원본 데이터>", file=sys.stderr)
        return EXIT_ERROR
    if args.csv and args.log:
        print("로그와 CSV가 모두 주어져 로그를 사용합니다 (로그가 실제 전송분입니다).")

    source = _load_log_source(args, console) if args.log else _load_csv_source(args, console)
    if source is None:
        return EXIT_ERROR
    client = source.client
    organization = source.organization or require_organization(client)
    if source.organization_id and source.organization_id != organization.id:
        print(
            console.warn(
                "주의: 기록은 도입사 %s에서 보냈는데 이 키는 다른 도입사 %s (%s)의 키입니다. 결과가 어긋날 수 있습니다."
                % (source.organization_id, organization.name, organization.id)
            )
        )

    months = [args.month] if args.month else sorted({kst_month(e.occurred_at) for e in source.stored})
    if not months:
        print("소스에 저장될 이벤트가 없습니다. --month로 검증할 월을 지정할 수 있습니다.", file=sys.stderr)
        return EXIT_ERROR

    print(
        "소스: %s (신규 %d건이 기대값에 기여, 거절 %d건은 제외)"
        % (source.label, len(source.stored), source.rejected_count)
    )
    if source.error_count:
        print(console.warn("주의: 전송 오류 %d건은 저장 여부를 알 수 없어 결과가 어긋날 수 있습니다." % source.error_count))

    stored = source.stored
    all_match = True
    for month in months:
        prices = client.get_billable_metric_prices(month)
        if prices.status != 200 or prices.body is None:
            print("단가를 얻지 못했습니다: %s" % parse_problem(prices.status, prices.body).summary(), file=sys.stderr)
            return EXIT_ERROR
        month_match = _verify_month(client, console, stored, month, prices)
        if month_match is None:
            return EXIT_ERROR
        all_match = all_match and month_match
    return EXIT_MATCH if all_match else EXIT_MISMATCH


def _load_log_source(args, console: Console) -> Optional[VerifySource]:
    try:
        log = read_log(args.log)
    except OSError as error:
        print("로그 파일을 열 수 없습니다: %s (%s)" % (args.log, error.strerror or error), file=sys.stderr)
        return None
    except ValueError as error:
        print("로그 파일을 읽지 못했습니다: %s" % error, file=sys.stderr)
        return None
    for warning in log.warnings:
        print(console.warn("경고: " + warning))
    if log.damaged and log.header_count <= 1:
        print(
            "로그 중간의 %d개 라인이 손상돼 기대값을 신뢰할 수 없습니다: %s\n"
            "send는 실행 하나가 파일 하나라 중간이 깨지면 몇 건이 빠졌는지 알 수 없습니다."
            % (len(log.damaged), args.log),
            file=sys.stderr,
        )
        return None
    try:
        stored = stored_events_from_log(log.records)
    except ValueError as error:
        print("로그의 이벤트를 해석하지 못했습니다: %s" % error, file=sys.stderr)
        return None
    base_url = args.base_url or (log.header.base_url if log.header else None) or DEFAULT_BASE_URL
    return VerifySource(
        stored=stored,
        rejected_count=sum(1 for r in log.records if r.outcome == "rejected"),
        error_count=sum(1 for r in log.records if r.outcome == "error"),
        client=ApiClient(base_url, args.api_key, timeout_seconds=args.timeout),
        label=args.log + " (send 로그, 서버 판정 기록)",
        organization_id=log.header.organization_id if log.header else None,
    )


def _load_csv_source(args, console: Console) -> Optional[VerifySource]:
    try:
        events = read_csv_events(args.csv)
    except OSError as error:
        print("CSV 파일을 열 수 없습니다: %s (%s)" % (args.csv, error.strerror or error), file=sys.stderr)
        return None
    except ValueError as error:
        print(str(error), file=sys.stderr)
        return None
    base_url = args.base_url or DEFAULT_BASE_URL
    client = ApiClient(base_url, args.api_key, timeout_seconds=args.timeout)
    organization = require_organization(client)

    usage = client.get_usage(args.month)
    if usage.status != 200:
        print("/v1/usage 응답이 200이 아니라 고객 명단을 얻지 못했습니다: %s" % parse_problem(usage.status, usage.body).summary(), file=sys.stderr)
        return None
    if usage.body is None:
        print("/v1/usage 응답이 JSON 객체가 아닙니다. --base-url이 백엔드를 가리키는지 확인하세요.", file=sys.stderr)
        return None
    known_customer_ids = set(roster_from_usage(usage.body))

    prediction = predict_send(events, known_customer_ids)
    print("CSV 소스는 서버 판정 시뮬레이션입니다 (저장할 수 없는 문자(NUL, 짝이 없는 UTF-16 서로게이트) 같은 드문 거절은 예측하지 못합니다. 실제 판정은 send 로그가 정본).")
    for outcome in prediction.outcomes:
        if outcome.outcome == "rejected":
            print("  거절 예측 (%d행): %s (%s)" % (outcome.index + 1, outcome.detail, outcome.code))
    rejected_count = sum(1 for o in prediction.outcomes if o.outcome == "rejected")
    return VerifySource(
        stored=prediction.stored,
        rejected_count=rejected_count,
        error_count=0,
        client=client,
        label=args.csv + " (CSV, 판정 시뮬레이션)",
        organization=organization,
    )


def _verify_month(
    client: ApiClient, console: Console, stored: List[StoredEvent], month: str, prices: ApiResult
) -> Optional[bool]:
    usage = client.get_usage(month)
    invoice = client.get_invoice(month)
    for name, result in (("/v1/usage", usage), ("/v1/invoices/draft", invoice)):
        if result.status != 200:
            print("%s %s 응답이 200이 아닙니다: %s" % (name, month, parse_problem(result.status, result.body).summary()), file=sys.stderr)
            return None
        if result.body is None:
            print("%s %s 응답이 JSON 객체가 아닙니다. --base-url이 백엔드를 가리키는지 확인하세요." % (name, month), file=sys.stderr)
            return None

    billable_metrics = _derive_billable_metrics(usage, prices)
    unsupported = [m.code for m in billable_metrics if m.aggregation not in SUPPORTED_AGGREGATIONS]
    if unsupported:
        print("지원하지 않는 집계 함수의 미터입니다: %s" % ", ".join(unsupported), file=sys.stderr)
        return None
    roster = roster_from_usage(usage.body)
    customer_ids = sorted(roster, key=lambda cid: roster[cid])
    common_plans = client.get_price_plans(month)
    if common_plans.status != 200 or common_plans.body is None:
        print("공통 요금제를 얻지 못했습니다", file=sys.stderr)
        return None
    common_plan = next(iter(common_plans.body.get("price_plans") or []), None)
    price_plans = {}
    for customer_id in customer_ids:
        customer_prices = client.get_billable_metric_prices(month, customer_id)
        customer_plans = client.get_price_plans(month, customer_id)
        if customer_prices.status != 200 or customer_prices.body is None or customer_plans.status != 200 or customer_plans.body is None:
            print("고객 단가와 요금제를 얻지 못했습니다", file=sys.stderr)
            return None
        for metric in _derive_billable_metrics(usage, customer_prices):
            for common_metric in billable_metrics:
                if common_metric.code == metric.code:
                    common_metric.customer_price_rates[customer_id] = metric.price_rates
        plan = next(iter(customer_plans.body.get("price_plans") or []), common_plan)
        if plan:
            price_plans[customer_id] = plan
    cutoff = parse_rfc3339(invoice.body["calculated_at"]) if invoice.body.get("calculated_at") else None
    expected = build_expected(stored, billable_metrics, month, customer_ids, price_plans, cutoff)

    print()
    print("== %s 검증 (서버: %s) ==" % (month, client.base_url))
    print("사용한 미터 정의 (서버 설정에서 유도, 시드와 눈으로 대조 가능):")
    for billable_metric in billable_metrics:
        print("  %s: event_type=%s, target_property=%s, %s, unit_size=%s, rounding=%s(%s), %s" % (billable_metric.code, billable_metric.event_type, billable_metric.target_property, billable_metric.aggregation, billable_metric.unit_size, billable_metric.rounding_mode, billable_metric.rounding_precision, _price_summary(billable_metric)))
        if billable_metric.percentile is not None:
            print("    percentile=%s" % billable_metric.percentile)
        if billable_metric.dimension_properties:
            for key, unit_price in sorted(billable_metric.unit_prices.items()):
                print("    %s: %s" % (_format_dimension_values_key(key), unit_price))

    month_match = True
    for billable_metric in billable_metrics:
        matched = _print_quantity_table(console, billable_metric, expected, usage, invoice, roster, customer_ids)
        month_match = month_match and matched
    matched = _print_amount_table(console, expected, invoice, roster, customer_ids)
    month_match = month_match and matched

    if not month_match:
        print()
        print(console.warn(MISMATCH_GUIDANCE))
        _print_diagnosis(client, stored, month)
    return month_match


def _derive_billable_metrics(usage: ApiResult, prices: ApiResult) -> List[BillableMetricMeta]:
    prices_by_code = {
        billable_metric_price["billable_metric_code"]: billable_metric_price
        for billable_metric_price in prices.body.get("billable_metric_prices") or []
    }
    billable_metrics = []
    for billable_metric in usage.body.get("billable_metric_usages") or []:
        billable_metric_price = prices_by_code.get(billable_metric["code"]) or {}
        billable_metrics.append(
            BillableMetricMeta(
                code=billable_metric["code"],
                event_type=billable_metric["event_type"],
                aggregation=billable_metric["aggregation"].lower(),
                target_property=billable_metric.get("target_property"),
                unit_size=Decimal(str(billable_metric.get("unit_size", 1))),
                rounding_mode=billable_metric.get("rounding_mode") or "none",
                rounding_precision=billable_metric.get("rounding_precision") or 0,
                percentile=Decimal(str(billable_metric["percentile"])) if billable_metric.get("percentile") is not None else None,
                dimension_properties=list(billable_metric_price.get("dimension_properties") or []),
                unit_prices={
                    dimension_values_key(price_rate["dimension_values"]): Decimal(price_rate["unit_price"])
                    for price_rate in billable_metric_price.get("price_rates") or []
                    if price_rate.get("unit_price") is not None
                },
                price_rates={
                    dimension_values_key(rate["dimension_values"]): rate
                    for rate in billable_metric_price.get("price_rates") or []
                },
            )
        )
    return billable_metrics


def _price_summary(billable_metric: BillableMetricMeta) -> str:
    if billable_metric.price_rates and any((rate.get("model") or "per_unit") != "per_unit" for rate in billable_metric.price_rates.values()):
        return "구간 요금, 조합별 계산"
    if not billable_metric.unit_prices:
        return "단가 없음(인보이스에 라인 없음, 금액 0)"
    if not billable_metric.dimension_properties and len(billable_metric.unit_prices) == 1:
        return "단가 %s" % next(iter(billable_metric.unit_prices.values()))
    return "차원 키 %s, 조합별 단가" % ", ".join(billable_metric.dimension_properties)


def _format_dimension_values_key(key: DimensionValuesKey) -> str:
    return ", ".join("%s=%s" % (name, typed_value[-1] if len(typed_value) > 1 else "null") for name, typed_value in key)


def _print_quantity_table(console, billable_metric, expected: ExpectedMonth, usage, invoice, roster, customer_ids) -> bool:
    usage_customers = {}
    for usage_billable_metric in usage.body.get("billable_metric_usages") or []:
        if usage_billable_metric["code"] == billable_metric.code:
            usage_customers = {c["customer_id"]: c for c in usage_billable_metric.get("customers") or []}
    invoice_quantities: Dict[str, Decimal] = {}
    invoice_fractions: Dict[str, Fraction] = {}
    legacy_customers = set()
    invalid_customers = set()
    for customer in invoice.body.get("customers") or []:
        for line in customer.get("lines") or []:
            if line["billable_metric_code"] == billable_metric.code:
                customer_id = customer["customer_id"]
                with localcontext() as context:
                    context.prec = CALC_PRECISION
                    invoice_quantities[customer_id] = invoice_quantities.get(customer_id, Decimal("0")) + Decimal(str(line["quantity"]))
                numerator, denominator = line.get("quantity_numerator"), line.get("quantity_denominator")
                if numerator is None and denominator is None:
                    legacy_customers.add(customer_id)
                elif numerator is None or denominator is None or Decimal(str(denominator)) <= 0:
                    invalid_customers.add(customer_id)
                else:
                    invoice_fractions[customer_id] = invoice_fractions.get(customer_id, Fraction(0)) + Fraction(Decimal(str(numerator))) / Fraction(Decimal(str(denominator)))

    rows = []
    unpriced_rows = []
    all_match = True
    for customer_id in customer_ids:
        expected_line = _find_expected_line(expected, customer_id, billable_metric.code)
        usage_customer = usage_customers.get(customer_id) or {}
        usage_quantity = Decimal(str(usage_customer.get("quantity", 0)))
        invoice_quantity = invoice_quantities.get(customer_id, Decimal("0"))
        invoice_matched = _quantity_matches(expected_line.priced_quantity, invoice_quantity, billable_metric) if customer_id in legacy_customers else expected_line.priced_fraction == invoice_fractions.get(customer_id, Fraction(0))
        invoice_matched = invoice_matched and customer_id not in invalid_customers
        matched = _quantity_matches(expected_line.quantity, usage_quantity, billable_metric) and invoice_matched
        if "raw_quantity" in usage_customer:
            matched = matched and _quantity_matches(expected_line.raw_quantity, Decimal(str(usage_customer["raw_quantity"])), billable_metric, raw=True)
        matched = matched and _raw_fraction_matches(expected_line.raw_fraction, usage_customer)
        if "dimension_usages" in usage_customer:
            matched = matched and _dimensions_match(expected_line, usage_customer["dimension_usages"], billable_metric)
        all_match = all_match and matched
        rows.append([
            roster[customer_id],
            str(expected_line.quantity),
            str(usage_quantity),
            str(expected_line.priced_quantity),
            str(invoice_quantity),
            console.ok("일치") if matched else console.fail("불일치"),
        ])
        for unpriced in expected_line.unpriced:
            unpriced_rows.append([roster[customer_id], _format_dimension_values(unpriced.dimension_values), str(unpriced.quantity)])
    print()
    print("[%s] 고객별 사용량" % billable_metric.code)
    for line in render_table(
        ["고객", "전체 기대값", "/usage 전체", "청구 수량 기대값", "/draft 수량", "판정"],
        rows,
        ["left", "right", "right", "right", "right", "left"],
    ):
        print(line)
    if unpriced_rows:
        print("단가가 없어 청구 예정액에서 빠지는 조합:")
        for line in render_table(["고객", "조합", "수량"], unpriced_rows, ["left", "left", "right"]):
            print(line)
    return all_match


def _quantity_matches(expected: Decimal, actual: Decimal, metric: BillableMetricMeta, raw=False) -> bool:
    return expected == actual


def _dimensions_match(line: ExpectedLine, actual: list, metric: BillableMetricMeta) -> bool:
    expected_by_key = {dimension_values_key(item.dimension_values): item for item in line.dimension_usages}
    actual_by_key = {dimension_values_key(item["dimension_values"]): item for item in actual}
    if expected_by_key.keys() != actual_by_key.keys() or len(actual_by_key) != len(actual):
        return False
    for key, expected in expected_by_key.items():
        actual_usage = actual_by_key[key]
        if not _quantity_matches(expected.quantity, Decimal(str(actual_usage["quantity"])), metric):
            return False
        if "raw_quantity" in actual_usage and not _quantity_matches(expected.raw_quantity, Decimal(str(actual_usage["raw_quantity"])), metric, raw=True):
            return False
        if not _raw_fraction_matches(expected.raw_fraction, actual_usage):
            return False
    return True


def _raw_fraction_matches(expected: Optional[Fraction], actual: dict) -> bool:
    numerator = actual.get("raw_quantity_numerator")
    denominator = actual.get("raw_quantity_denominator")
    if numerator is None and denominator is None:
        return True
    if numerator is None or denominator is None or expected is None:
        return False
    try:
        denominator_value = Fraction(Decimal(str(denominator)))
        return denominator_value > 0 and expected == Fraction(Decimal(str(numerator))) / denominator_value
    except (ValueError, ArithmeticError):
        return False


def _find_expected_line(expected: ExpectedMonth, customer_id: str, billable_metric_code: str) -> ExpectedLine:
    for line in expected.customers[customer_id].lines:
        if line.billable_metric_code == billable_metric_code:
            return line
    return ExpectedLine(billable_metric_code=billable_metric_code, quantity=Decimal("0"), amount=None)


def _format_dimension_values(dimension_values: dict) -> str:
    return ", ".join("%s=%s" % (key, value) for key, value in dimension_values.items()) or "(조합 없음)"


def _print_amount_table(console, expected: ExpectedMonth, invoice, roster, customer_ids) -> bool:
    invoice_amounts = {c["customer_id"]: c["amount"] for c in invoice.body.get("customers") or []}
    rows = []
    all_match = True
    for customer_id in customer_ids:
        expected_amount = expected.customers[customer_id].amount
        invoice_amount = invoice_amounts.get(customer_id, 0)
        matched = expected_amount == invoice_amount
        all_match = all_match and matched
        rows.append([
            roster[customer_id],
            str(expected_amount),
            str(invoice_amount),
            console.ok("일치") if matched else console.fail("불일치"),
        ])
    invoice_total = invoice.body.get("total_amount")
    total_matched = expected.total_amount == invoice_total
    all_match = all_match and total_matched
    rows.append([
        "합계",
        str(expected.total_amount),
        str(invoice_total),
        console.ok("일치") if total_matched else console.fail("불일치"),
    ])
    print()
    print("고객별 청구 예정액")
    for line in render_table(
        ["고객", "기대값", "/v1/invoices/draft", "판정"],
        rows,
        ["left", "right", "right", "left"],
    ):
        print(line)
    return all_match


def _print_diagnosis(client: ApiClient, stored: List[StoredEvent], month: str):
    source_count = sum(1 for e in stored if kst_month(e.occurred_at) == month)
    result = client.get_events_page(month)
    server_total = result.body.get("total") if result.status == 200 and result.body else None
    if server_total is None:
        print("진단: 서버 이벤트 건수를 확인하지 못했습니다 (GET /v1/events).")
        return
    print("진단: 이 달에 소스가 저장한 이벤트 %d건, 서버에 있는 이벤트 %d건." % (source_count, server_total))
    if server_total > source_count:
        print("      서버에 소스 밖 이벤트가 %d건 있습니다. 위 안내대로 초기화 후 재실행하면 맞춰볼 수 있습니다." % (server_total - source_count))
