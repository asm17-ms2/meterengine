"""verify 서브커맨드: 소스 기반 기대값과 서버 응답을 나란히 비교한다.

미터 정의(event_type, target_property, 단가)는 서버 응답에서 유도한다.
독립 계산의 핵심은 이벤트에 대한 중복 제거/월 귀속/합산/절사이고,
유도한 메타데이터는 화면에 그대로 출력해 사람이 시드와 대조할 수 있게 한다.
"""

from __future__ import annotations

import sys
from dataclasses import dataclass
from decimal import Decimal
from typing import Dict, List, Optional

from core.api_client import ApiClient, ApiResult, parse_problem, roster_from_usage
from csvdemo.csvio import read_csv_events
from csvdemo.expected import ExpectedMonth, MetricMeta, build_expected, predict_send, stored_events_from_log
from core.jsonl_log import read_log
from core.model import DEFAULT_BASE_URL, DEFAULT_ORG_ID, StoredEvent, kst_month
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

    months = [args.month] if args.month else sorted({kst_month(e.occurred_at) for e in source.stored})
    if not months:
        print("소스에 저장될 이벤트가 없습니다. --month로 검증할 월을 지정할 수 있습니다.", file=sys.stderr)
        return EXIT_ERROR

    print(
        "소스: %s (신규 %d건이 기대값에 기여, 400 거절 %d건은 제외)"
        % (source.label, len(source.stored), source.rejected_count)
    )
    if source.error_count:
        print(console.warn("주의: 전송 오류 %d건은 저장 여부를 알 수 없어 결과가 어긋날 수 있습니다." % source.error_count))

    stored = source.stored
    all_match = True
    for month in months:
        month_match = _verify_month(client, console, stored, month)
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
    # 중간이 깨진 send 로그로는 판정하지 않는다. 레코드가 빠진 채로 남은 합계가
    # 우연히 서버와 맞으면 "일치"로 0을 내주는데, 그 0을 보고 다음 단계로 넘어가면
    # 손상을 통과시킨 것이 된다. 브리지 로그(헤더가 여럿)는 하루치를 이어쓰다
    # 재시작으로 잘리는 것이 정상 범위라 경고까지만 한다.
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
    org_id = args.org_id or (log.header.org_id if log.header else None) or DEFAULT_ORG_ID
    return VerifySource(
        stored=stored,
        rejected_count=sum(1 for r in log.records if r.outcome == "rejected"),
        error_count=sum(1 for r in log.records if r.outcome == "error"),
        client=ApiClient(base_url, org_id, timeout_seconds=args.timeout),
        label=args.log + " (send 로그, 서버 판정 기록)",
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
    org_id = args.org_id or DEFAULT_ORG_ID
    client = ApiClient(base_url, org_id, timeout_seconds=args.timeout)

    # 등록 고객 명단을 서버에서 받아 unknown_customer_reference까지 예측한다.
    usage = client.get_usage(args.month)
    if usage.status != 200:
        print("/v1/usage 응답이 200이 아니라 고객 명단을 얻지 못했습니다: %s" % parse_problem(usage.status, usage.body).summary(), file=sys.stderr)
        return None
    if usage.body is None:
        print("/v1/usage 응답이 JSON 객체가 아닙니다. --base-url이 백엔드를 가리키는지 확인하세요.", file=sys.stderr)
        return None
    known_customer_ids = set(roster_from_usage(usage.body))

    prediction = predict_send(events, known_customer_ids)
    print("CSV 소스는 서버 판정 시뮬레이션입니다 (invalid_event 같은 희귀 거절은 예측하지 못합니다. 실제 판정은 send 로그가 정본).")
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
    )


def _verify_month(client: ApiClient, console: Console, stored: List[StoredEvent], month: str) -> Optional[bool]:
    usage = client.get_usage(month)
    invoice = client.get_invoice(month)
    for name, result in (("/v1/usage", usage), ("/v1/invoice", invoice)):
        if result.status != 200:
            print("%s %s 응답이 200이 아닙니다: %s" % (name, month, parse_problem(result.status, result.body).summary()), file=sys.stderr)
            return None
        if result.body is None:
            print("%s %s 응답이 JSON 객체가 아닙니다. --base-url이 백엔드를 가리키는지 확인하세요." % (name, month), file=sys.stderr)
            return None

    metrics = _derive_metrics(usage, invoice)
    unsupported = [m.code for m in metrics if m.aggregation != "SUM"]
    if unsupported:
        print("SUM 외 집계는 아직 지원하지 않습니다: %s" % ", ".join(unsupported), file=sys.stderr)
        return None
    roster = roster_from_usage(usage.body)
    customer_ids = sorted(roster, key=lambda cid: roster[cid])
    expected = build_expected(stored, metrics, month, customer_ids)

    print()
    print("== %s 검증 (서버: %s) ==" % (month, client.base_url))
    print("사용한 미터 정의 (서버 응답에서 유도, 시드와 눈으로 대조 가능):")
    for metric in metrics:
        price = str(metric.unit_price) if metric.unit_price is not None else "미상(인보이스에 라인 없음)"
        print("  %s: event_type=%s, target_property=%s, %s, 단가 %s" % (metric.code, metric.event_type, metric.target_property, metric.aggregation, price))

    month_match = True
    for metric in metrics:
        matched = _print_quantity_table(console, metric, expected, usage, invoice, roster, customer_ids)
        month_match = month_match and matched
    matched = _print_amount_table(console, expected, invoice, roster, customer_ids)
    month_match = month_match and matched

    if not month_match:
        print()
        print(console.warn(MISMATCH_GUIDANCE))
        _print_diagnosis(client, stored, month)
    return month_match


def _derive_metrics(usage: ApiResult, invoice: ApiResult) -> List[MetricMeta]:
    unit_prices: Dict[str, Decimal] = {}
    for customer in invoice.body.get("customers") or []:
        for line in customer.get("lines") or []:
            unit_prices.setdefault(line["metric_code"], Decimal(line["unit_price"]))
    metrics = []
    for metric in usage.body.get("metrics") or []:
        metrics.append(
            MetricMeta(
                code=metric["code"],
                event_type=metric["event_type"],
                aggregation=metric["aggregation"],
                target_property=metric["target_property"],
                unit_price=unit_prices.get(metric["code"]),
            )
        )
    return metrics


def _print_quantity_table(console, metric, expected: ExpectedMonth, usage, invoice, roster, customer_ids) -> bool:
    usage_quantities = {}
    for usage_metric in usage.body.get("metrics") or []:
        if usage_metric["code"] == metric.code:
            usage_quantities = {c["customer_id"]: Decimal(c["quantity"]) for c in usage_metric.get("customers") or []}
    invoice_quantities = {}
    for customer in invoice.body.get("customers") or []:
        for line in customer.get("lines") or []:
            if line["metric_code"] == metric.code:
                invoice_quantities[customer["customer_id"]] = Decimal(line["quantity"])

    rows = []
    all_match = True
    for customer_id in customer_ids:
        expected_quantity = _expected_quantity(expected, customer_id, metric.code)
        usage_quantity = usage_quantities.get(customer_id, Decimal("0"))
        invoice_quantity = invoice_quantities.get(customer_id, Decimal("0"))
        matched = expected_quantity == usage_quantity == invoice_quantity
        all_match = all_match and matched
        rows.append([
            roster[customer_id],
            str(expected_quantity),
            str(usage_quantity),
            str(invoice_quantity),
            console.ok("일치") if matched else console.fail("불일치"),
        ])
    print()
    print("[%s] 고객별 사용량" % metric.code)
    for line in render_table(
        ["고객", "기대값", "/v1/usage", "/v1/invoice", "판정"],
        rows,
        ["left", "right", "right", "right", "left"],
    ):
        print(line)
    return all_match


def _expected_quantity(expected: ExpectedMonth, customer_id: str, metric_code: str) -> Decimal:
    for line in expected.customers[customer_id].lines:
        if line.metric_code == metric_code:
            return line.quantity
    return Decimal("0")


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
            "미상" if expected_amount is None else str(expected_amount),
            str(invoice_amount),
            console.ok("일치") if matched else console.fail("불일치"),
        ])
    invoice_total = invoice.body.get("total_amount")
    total_matched = expected.total_amount == invoice_total
    all_match = all_match and total_matched
    rows.append([
        "합계",
        "미상" if expected.total_amount is None else str(expected.total_amount),
        str(invoice_total),
        console.ok("일치") if total_matched else console.fail("불일치"),
    ])
    print()
    print("고객별 청구 예정액")
    for line in render_table(
        ["고객", "기대값", "/v1/invoice", "판정"],
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
