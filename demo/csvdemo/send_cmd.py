from __future__ import annotations

import os
import json
import random
import sys
import time
from datetime import datetime
from typing import Dict, List, Optional

from core.api_client import (
    ApiClient,
    Organization,
    Problem,
    TransportError,
    auth_failure_text,
    is_auth_failure,
    parse_problem,
    require_organization,
    roster_from_usage,
)
from csvdemo.csvio import read_csv_events
from csvdemo.expected import Prediction, predict_send
from core.jsonl_log import JsonlLogWriter, classify_outcome
from core.model import DEFAULT_BASE_URL, KST, Event, build_body_text, kst_month, parse_properties, parse_rfc3339
from csvdemo.render import Console, format_gate, format_send_pair, format_summary

LOGS_DIR = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "logs"
)
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
    client = ApiClient(base_url, args.api_key, timeout_seconds=args.timeout, environment=getattr(args, "environment", None))
    organization = require_organization(client)
    return send_events(events, client, organization, console, args)


def send_events(events: List[Event], client: ApiClient, organization: Organization, console: Console, args) -> int:
    month_counts = _month_counts(events)
    server_totals = _server_totals(client, month_counts)
    prediction = _predict(client, events)
    predicted_counts: Dict[str, int] = {}
    predicted_outcomes: Dict[int, str] = {}
    for predicted in prediction.outcomes:
        predicted_counts[predicted.outcome] = predicted_counts.get(predicted.outcome, 0) + 1
        predicted_outcomes[predicted.index] = predicted.outcome
    notes: Dict[int, str] = {index: event.note for index, event in enumerate(events) if event.note}
    for line in format_gate(
        client.base_url, client.api_key, organization, len(events), month_counts, server_totals, predicted_counts
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
    auth_problem: Optional[Problem] = None
    with JsonlLogWriter(log_path) as writer:
        writer.write_run_header(
            started_at_text=datetime.now(KST).isoformat(),
            base_url=client.base_url,
            organization_id=organization.id,
            csv_path=args.csv,
            environment=getattr(client, "environment", None),
            source_fingerprint=getattr(client, "source_fingerprint", None),
            argv=sys.argv[1:],
        )
        try:
            if getattr(args, "batch_size", 1) > 1:
                sent, auth_problem = _send_batches(events, client, writer, counts, notes, console, args)
            else:
                for index, event in enumerate(events):
                    if index > 0:
                        _pause(args.interval, args.jitter)
                    outcome, summary, auth_problem = _send_one(client, writer, index + 1, event, args.verbose)
                    sent += 1
                    counts[outcome] += 1
                    for line in format_send_pair(
                        console, index + 1, len(events), event, outcome, summary, notes.get(index, "")
                    ):
                        print(line)
                    if auth_problem is not None:
                        print(
                            "%s. 중단합니다. 이후 이벤트는 보내지 않았습니다." % auth_failure_text(auth_problem, client.api_key),
                            file=sys.stderr,
                        )
                        break
                    if outcome == "error":
                        print("전송 오류가 나서 중단합니다. 이후 이벤트는 보내지 않았습니다.", file=sys.stderr)
                        break
        except KeyboardInterrupt:
            print()
            print("중단했습니다. 여기까지의 기록은 로그에 남아 있습니다.")
        finally:
            sent = max(sent, counts.get("_sent", 0))
            for index in range(sent, len(events)):
                writer.write_send(seq=index + 1, sent_at_text=datetime.now(KST).isoformat(), request_body_text=build_body_text(events[index]), status=None, response_text=None, outcome="error", error="NOT_SENT: 앞선 요청 이후 전송을 중단했습니다", elapsed_ms=None)
                print("%d/%d NOT_SENT: 전송하지 않았습니다" % (index + 1, len(events)))
    print()
    for line in format_summary(sent, counts["new"], counts["duplicate"], counts["rejected"], counts["error"], log_path):
        print(line)
    return 2 if counts["error"] or auth_problem is not None or sent < len(events) else 0



def _send_batches(events, client, writer, counts, notes, console, args):
    sent = 0
    auth_problem = None
    batch_size = args.batch_size
    for start in range(0, len(events), batch_size):
        if start:
            _pause(args.interval, args.jitter)
        chunk = events[start:start + batch_size]
        # 속성이 깨진 행은 봉투 전체를 깨뜨리므로 단건으로 보내 서버가 그 행만 판정하게 한다.
        broken = {offset for offset, event in enumerate(chunk) if not _has_valid_properties(event)}
        batched = [event for offset, event in enumerate(chunk) if offset not in broken]
        sent_at = datetime.now(KST).isoformat()
        result, rows, failure, problem = _post_batch(client, batched) if batched else (None, [], None, None)
        auth_problem = auth_problem or problem
        stop = failure is not None or problem is not None
        batch_rows = iter(rows or [])
        for offset, event in enumerate(chunk):
            seq = start + offset + 1
            if offset in broken and failure is None:
                outcome, summary, single_problem = _send_one(client, writer, seq, event, args.verbose)
                auth_problem = auth_problem or single_problem
                stop = stop or single_problem is not None or outcome == "error"
            elif offset in broken:
                outcome, summary = "error", "NOT_SENT: 앞선 요청 이후 전송을 중단했습니다"
                writer.write_send(seq=seq, sent_at_text=sent_at, request_body_text=build_body_text(event), status=None, response_text=None, outcome=outcome, error=summary, elapsed_ms=None)
            elif failure is None:
                row = next(batch_rows)
                outcome = {"stored": "new", "duplicate": "duplicate", "rejected": "rejected"}[row["result"]]
                summary = row["result"]
                if row.get("error"):
                    summary += ": " + parse_problem(400, row["error"]).summary()
                writer.write_send(seq=seq, sent_at_text=sent_at, request_body_text=build_body_text(event), status=result.status, response_text=json.dumps(row, ensure_ascii=False), outcome=outcome, error=None, elapsed_ms=result.elapsed_ms)
            else:
                outcome, summary = "error", failure
                writer.write_send(seq=seq, sent_at_text=sent_at, request_body_text=build_body_text(event), status=result.status if result is not None else None, response_text=result.body_text if result is not None else None, outcome=outcome, error=failure, elapsed_ms=result.elapsed_ms if result is not None else None)
            counts[outcome] += 1
            sent += 1
            counts["_sent"] = sent
            for line in format_send_pair(console, seq, len(events), event, outcome, summary, notes.get(seq - 1, "")):
                print(line)
        if stop:
            break
    return sent, auth_problem


def _has_valid_properties(event: Event) -> bool:
    try:
        parse_properties(event.properties_text)
    except ValueError:
        return False
    return True


# 봉투 전체가 거절되면 각 행의 저장 여부를 알 수 없으므로 rejected가 아니라 error로 센다.
def _post_batch(client: ApiClient, batched: List[Event]):
    raw = '{"events":[' + ','.join(build_body_text(event) for event in batched) + ']}'
    try:
        result = client.post_events(raw)
    except (TransportError, KeyboardInterrupt) as error:
        return None, None, "UNKNOWN: " + (str(error) or "전송 중 중단되어 저장 여부를 확인할 수 없습니다"), None
    rows = (result.body or {}).get("results") if isinstance(result.body, dict) else None
    if result.status == 200 and isinstance(rows, list) and len(rows) == len(batched) and all(isinstance(row, dict) and row.get("index") == index and row.get("transaction_id") == batched[index].transaction_id and row.get("result") in ("stored", "duplicate", "rejected") for index, row in enumerate(rows)):
        return result, rows, None, None
    if 400 <= result.status < 500:
        problem = parse_problem(result.status, result.body)
        return result, None, problem.summary(), problem if is_auth_failure(result.status) else None
    return result, None, "UNKNOWN: 배치 응답으로 각 이벤트의 저장 여부를 확인할 수 없습니다", None


def _send_one(client: ApiClient, writer: JsonlLogWriter, seq: int, event: Event, verbose: bool):
    body_text = build_body_text(event)
    sent_at = datetime.now(KST).isoformat()
    try:
        result = client.post_event(body_text)
    except (TransportError, KeyboardInterrupt) as error:
        message = "UNKNOWN: " + (str(error) or "전송 중 중단되어 저장 여부를 확인할 수 없습니다")
        writer.write_send(
            seq=seq, sent_at_text=sent_at, request_body_text=body_text,
            status=None, response_text=None, outcome="error", error=message, elapsed_ms=None,
        )
        return "error", message, None
    outcome = classify_outcome(result.status, result.body)
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
        summary = "%d %s" % (result.status, parse_problem(result.status, result.body).summary())
    else:
        summary = "HTTP %d %s" % (result.status, parse_problem(result.status, result.body).summary())
    auth_problem = parse_problem(result.status, result.body) if is_auth_failure(result.status) else None
    return outcome, summary, auth_problem


def _predict(client: ApiClient, events: List[Event]) -> Prediction:
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
