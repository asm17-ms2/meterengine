"""send 서브커맨드: 이벤트를 순차 전송하며 요청:응답을 1:1 대조 출력한다.

DB가 append-only라 오전송은 초기화 말고는 되돌릴 수 없다.
그래서 전송 전 확인 게이트가 있고, 모든 요청:응답 쌍은 항상 JSONL로 남긴다.
"""

from __future__ import annotations

import os
import random
import sys
import time
from datetime import datetime
from typing import Dict, List, Optional

from core.api_client import ApiClient, TransportError, parse_problem, roster_from_usage
from csvdemo.csvio import read_csv_events
from csvdemo.expected import Prediction, predict_send
from core.jsonl_log import JsonlLogWriter
from core.model import DEFAULT_BASE_URL, DEFAULT_ORG_ID, KST, Event, build_body_text, kst_month, parse_rfc3339
from csvdemo.render import Console, format_gate, format_send_pair, format_summary

LOGS_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "logs")
PREVIEW_LIMIT = 5


def run_send(args, console: Console) -> int:
    try:
        events = read_csv_events(args.csv)
    except OSError as error:
        print("CSV 파일을 열 수 없습니다: %s (%s)" % (args.csv, error.strerror or error), file=sys.stderr)
        return 2
    except ValueError as error:
        print(str(error), file=sys.stderr)
        return 2
    if not events:
        print("CSV에 보낼 이벤트가 없습니다: " + args.csv, file=sys.stderr)
        return 2
    base_url = args.base_url or DEFAULT_BASE_URL
    org_id = args.org_id or DEFAULT_ORG_ID
    client = ApiClient(base_url, org_id, timeout_seconds=args.timeout)
    return send_events(events, client, console, args)


def send_events(events: List[Event], client: ApiClient, console: Console, args) -> int:
    month_counts = _month_counts(events)
    server_totals = _server_totals(client, month_counts)
    prediction = _predict(client, events)
    predicted_counts: Dict[str, int] = {}
    predicted_outcomes: Dict[int, str] = {}
    for predicted in prediction.outcomes:
        predicted_counts[predicted.outcome] = predicted_counts.get(predicted.outcome, 0) + 1
        predicted_outcomes[predicted.index] = predicted.outcome
    # 행 설명은 CSV 작성자가 note 컬럼에 적은 것만 보여준다. 비어 있으면 아무것도 안 붙는다
    notes: Dict[int, str] = {index: event.note for index, event in enumerate(events) if event.note}
    for line in format_gate(
        client.base_url, client.org_id, len(events), month_counts, server_totals, predicted_counts
    ):
        print(line)
    print()
    _preview(events, console, predicted_outcomes, notes)

    if args.dry_run:
        print("dry-run이므로 전송하지 않고 로그도 남기지 않습니다.")
        return 0
    if not args.yes and not _confirmed():
        print("전송을 중단했습니다.")
        return 0

    log_path = os.path.join(LOGS_DIR, datetime.now(KST).strftime("send-%Y%m%d-%H%M%S.jsonl"))
    counts = {"new": 0, "duplicate": 0, "rejected": 0, "error": 0}
    sent = 0
    with JsonlLogWriter(log_path) as writer:
        writer.write_run_header(
            started_at_text=datetime.now(KST).isoformat(),
            base_url=client.base_url,
            org_id=client.org_id,
            csv_path=args.csv,
            argv=sys.argv[1:],
        )
        try:
            for index, event in enumerate(events):
                if index > 0:
                    _pause(args.interval, args.jitter)
                outcome, summary = _send_one(client, writer, index + 1, event, args.verbose)
                sent += 1
                counts[outcome] += 1
                for line in format_send_pair(
                    console, index + 1, len(events), event, outcome, summary, notes.get(index, "")
                ):
                    print(line)
                if outcome == "error":
                    print("전송 오류가 나서 중단합니다. 이후 이벤트는 보내지 않았습니다.", file=sys.stderr)
                    break
        except KeyboardInterrupt:
            print()
            print("중단했습니다. 여기까지의 기록은 로그에 남아 있습니다.")
    print()
    for line in format_summary(sent, counts["new"], counts["duplicate"], counts["rejected"], counts["error"], log_path):
        print(line)
    return 2 if counts["error"] else 0


def _send_one(client: ApiClient, writer: JsonlLogWriter, seq: int, event: Event, verbose: bool):
    body_text = build_body_text(event)
    sent_at = datetime.now(KST).isoformat()
    try:
        result = client.post_event(body_text)
    except TransportError as error:
        writer.write_send(
            seq=seq, sent_at_text=sent_at, request_body_text=body_text,
            status=None, response_text=None, outcome="error", error=str(error), elapsed_ms=None,
        )
        return "error", str(error)
    outcome = _classify(result.status, result.body)
    writer.write_send(
        seq=seq, sent_at_text=sent_at, request_body_text=body_text,
        status=result.status, response_text=result.body_text or None,
        outcome=outcome, error=None, elapsed_ms=result.elapsed_ms,
    )
    if verbose:
        print("  요청: " + body_text)
        print("  응답: " + result.body_text)
    if outcome in ("new", "duplicate"):
        summary = "200 duplicate=%s (%dms)" % (str(result.body.get("duplicate")).lower(), result.elapsed_ms)
    elif outcome == "rejected":
        summary = "400 " + parse_problem(result.status, result.body).summary()
    else:
        summary = "HTTP %d %s" % (result.status, parse_problem(result.status, result.body).summary())
    return outcome, summary


def _predict(client: ApiClient, events: List[Event]) -> Prediction:
    """전송 전에 각 행을 로컬 판정한다. 고객 명단을 못 얻으면 그 판정만 건너뛴다."""
    known_customer_ids = None
    try:
        usage = client.get_usage(None)
        if usage.status == 200 and usage.body is not None:
            roster = roster_from_usage(usage.body)
            if roster:
                known_customer_ids = set(roster)
    except TransportError:
        pass
    return predict_send(events, known_customer_ids)


def _classify(status: int, body: Optional[dict]) -> str:
    if status == 200 and body is not None and "duplicate" in body:
        return "duplicate" if body["duplicate"] else "new"
    if status == 400:
        return "rejected"
    return "error"


def _month_counts(events: List[Event]) -> Dict[str, int]:
    counts: Dict[str, int] = {}
    for event in events:
        try:
            month = kst_month(parse_rfc3339(event.timestamp_text))
        except ValueError:
            month = "(판정 불가)"
        counts[month] = counts.get(month, 0) + 1
    return counts


def _server_totals(client: ApiClient, month_counts: Dict[str, int]) -> Dict[str, Optional[int]]:
    totals: Dict[str, Optional[int]] = {}
    for month in month_counts:
        if month == "(판정 불가)":
            continue
        try:
            result = client.get_events_page(month)
        except TransportError as error:
            # 게이트 표시는 참고 정보라 여기서는 멈추지 않는다. 전송 시점에 다시 드러난다.
            print("서버 확인 실패: " + str(error), file=sys.stderr)
            totals[month] = None
            continue
        totals[month] = result.body.get("total") if result.status == 200 and result.body else None
    return totals


def _preview(events: List[Event], console: Console, predicted_outcomes: Dict[int, str], notes: Dict[int, str]):
    def print_row(index: int):
        head, _ = format_send_pair(
            console,
            index + 1,
            len(events),
            events[index],
            predicted_outcomes.get(index, "new"),
            "",
            notes.get(index, ""),
        )
        print("  " + head)

    for index in range(min(PREVIEW_LIMIT, len(events))):
        print_row(index)
    # 문제로 예측된 행은 미리보기 범위 밖이어도 반드시 보여준다.
    # 전송 전에 사람이 걸러낼 기회가 게이트의 존재 이유이기 때문이다.
    hidden_problems = [
        index
        for index in range(PREVIEW_LIMIT, len(events))
        if predicted_outcomes.get(index) == "rejected"
    ]
    remaining = len(events) - PREVIEW_LIMIT - len(hidden_problems)
    if hidden_problems:
        print("  --- 아래는 문제로 예측된 행 ---")
        for index in hidden_problems:
            print_row(index)
    if remaining > 0:
        print("  ... 외 %d건" % remaining)
    print()


def _confirmed() -> bool:
    if not sys.stdin.isatty():
        print("비대화식 실행에서는 --yes가 필요합니다.", file=sys.stderr)
        return False
    answer = input("전송하려면 y를 입력하세요: ")
    return answer.strip().lower() == "y"


def _pause(interval: float, jitter: float):
    delay = interval + (random.uniform(0, jitter) if jitter > 0 else 0)
    if delay > 0:
        time.sleep(delay)
