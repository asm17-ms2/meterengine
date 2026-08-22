"""기대값 계산 규칙 검증. MS2-128 인수조건의 계산 규칙에 1:1 대응한다."""

import unittest
from decimal import Decimal

from csvdemo.expected import (
    MetricMeta,
    build_expected,
    line_amount,
    predict_send,
    stored_events_from_log,
    sum_quantities,
)
from core.jsonl_log import SendRecord
from core.model import Event, StoredEvent, parse_rfc3339

ACME = "a728e7b6-d82b-4f3c-a960-a66a02794c1d"
BETA = "252339bc-d5f8-472d-b5d6-ed8554049450"
KNOWN = {ACME, BETA}

TOKEN_USAGE = MetricMeta(
    code="token-usage",
    event_type="chat_completion",
    aggregation="SUM",
    target_property="token",
    unit_price=Decimal("0.5"),
)


def _event(tx, customer=ACME, event_type="chat_completion", ts="2026-08-10T12:00:00+09:00", props='{"token": 500}'):
    return Event(
        transaction_id=tx,
        customer_id=customer,
        event_type=event_type,
        timestamp_text=ts,
        properties_text=props,
    )


def _stored(tx, customer=ACME, event_type="chat_completion", ts="2026-08-10T12:00:00+09:00", props=None):
    return StoredEvent(
        transaction_id=tx,
        customer_id=customer,
        event_type=event_type,
        occurred_at=parse_rfc3339(ts),
        properties={"token": 500} if props is None else props,
    )


class PredictSendTest(unittest.TestCase):
    def test_인수조건_100건_중복20건이면_80건만_저장된다(self):
        events = [_event("evt-%03d" % i) for i in range(80)]
        events += [_event("evt-%03d" % i) for i in range(20)]
        prediction = predict_send(events, KNOWN)
        self.assertEqual(len(prediction.stored), 80)
        outcomes = [o.outcome for o in prediction.outcomes]
        self.assertEqual(outcomes.count("new"), 80)
        self.assertEqual(outcomes.count("duplicate"), 20)

    def test_미등록_고객은_거절로_예측한다(self):
        prediction = predict_send([_event("evt-1", customer="9f31c2aa-0000-0000-0000-000000000000")], KNOWN)
        self.assertEqual(prediction.stored, [])
        self.assertEqual(prediction.outcomes[0].outcome, "rejected")
        self.assertEqual(prediction.outcomes[0].code, "unknown_customer_reference")

    def test_필수값_누락은_거절로_예측한다(self):
        prediction = predict_send([_event("evt-1", event_type="  ")], KNOWN)
        self.assertEqual(prediction.outcomes[0].outcome, "rejected")
        self.assertEqual(prediction.outcomes[0].code, "validation_error")
        self.assertIn("event_type", prediction.outcomes[0].detail)

    def test_고객_명단이_없으면_미등록_판정을_건너뛴다(self):
        prediction = predict_send([_event("evt-1", customer="9f31c2aa-0000-0000-0000-000000000000")], None)
        self.assertEqual(prediction.outcomes[0].outcome, "new")

    def test_대문자_uuid는_등록_고객으로_인정한다(self):
        prediction = predict_send([_event("evt-1", customer=ACME.upper())], KNOWN)
        self.assertEqual(prediction.outcomes[0].outcome, "new")
        self.assertEqual(prediction.stored[0].customer_id, ACME)

    def test_거절된_전송은_중복_판정에_들어가지_않는다(self):
        # first-write-wins의 "최초"는 최초 저장분이다. 거절은 저장이 없으므로
        # 같은 transaction_id의 나중 유효 전송이 신규가 된다.
        rejected_then_valid = [
            _event("evt-1", customer="9f31c2aa-0000-0000-0000-000000000000"),
            _event("evt-1"),
        ]
        prediction = predict_send(rejected_then_valid, KNOWN)
        self.assertEqual([o.outcome for o in prediction.outcomes], ["rejected", "new"])
        self.assertEqual(len(prediction.stored), 1)


class StoredEventsFromLogTest(unittest.TestCase):
    def _record(self, outcome, tx="evt-1"):
        return SendRecord(
            seq=1,
            sent_at="2026-08-14T10:00:00+09:00",
            request={
                "transaction_id": tx,
                "customer_id": ACME,
                "event_type": "chat_completion",
                "properties": {"token": Decimal("500")},
                "timestamp": "2026-08-10T12:00:00+09:00",
            },
            status=200,
            response=None,
            outcome=outcome,
            error=None,
            elapsed_ms=1,
        )

    def test_new만_서버_상태에_기여한다(self):
        records = [
            self._record("new", "evt-1"),
            self._record("duplicate", "evt-1"),
            self._record("rejected", "evt-2"),
            self._record("error", "evt-3"),
        ]
        stored = stored_events_from_log(records)
        self.assertEqual([e.transaction_id for e in stored], ["evt-1"])


class SumQuantitiesTest(unittest.TestCase):
    def test_80건분_token_합이_나온다(self):
        stored = [_stored("evt-%03d" % i) for i in range(80)]
        sums = sum_quantities(stored, "2026-08", "chat_completion", "token")
        self.assertEqual(sums[ACME], Decimal("40000"))

    def test_kst_월_경계로_귀속한다(self):
        stored = [
            _stored("evt-1", ts="2026-08-31T23:59:59+09:00"),
            _stored("evt-2", ts="2026-08-31T14:59:59Z"),
            _stored("evt-3", ts="2026-08-31T15:00:00Z"),
        ]
        aug = sum_quantities(stored, "2026-08", "chat_completion", "token")
        sep = sum_quantities(stored, "2026-09", "chat_completion", "token")
        self.assertEqual(aug[ACME], Decimal("1000"))
        self.assertEqual(sep[ACME], Decimal("500"))

    def test_다른_event_type은_합산하지_않는다(self):
        stored = [_stored("evt-1"), _stored("evt-2", event_type="embedding")]
        sums = sum_quantities(stored, "2026-08", "chat_completion", "token")
        self.assertEqual(sums[ACME], Decimal("500"))

    def test_비숫자와_누락과_불리언은_제외한다(self):
        # 서버는 jsonb_typeof = 'number'만 합산한다. 저장은 되지만 합계에서 빠진다.
        stored = [
            _stored("evt-1", props={"token": Decimal("500")}),
            _stored("evt-2", props={"token": "500"}),
            _stored("evt-3", props={"model": "claude"}),
            _stored("evt-4", props={"token": True}),
        ]
        sums = sum_quantities(stored, "2026-08", "chat_completion", "token")
        self.assertEqual(sums[ACME], Decimal("500"))

    def test_고정밀_소수_자릿수를_유지한다(self):
        stored = [_stored("evt-1", props={"token": Decimal("1234567890.123456789012345")})]
        sums = sum_quantities(stored, "2026-08", "chat_completion", "token")
        self.assertEqual(sums[ACME], Decimal("1234567890.123456789012345"))

    def test_유효숫자_28자리를_넘는_합도_정확하다(self):
        # 서버는 PostgreSQL numeric SUM으로 정확 계산하므로 기본 컨텍스트(prec=28) 반올림이 끼면 안 된다.
        value = Decimal("12345678901234567890.123456789")
        stored = [_stored("evt-1", props={"token": value}), _stored("evt-2", props={"token": value})]
        sums = sum_quantities(stored, "2026-08", "chat_completion", "token")
        self.assertEqual(sums[ACME], Decimal("24691357802469135780.246913578"))

    def test_대문자_uuid도_같은_고객으로_집계한다(self):
        # 서버는 UUID를 대소문자 무관으로 파싱하고 응답은 소문자로 내려준다.
        stored = [_stored("evt-1", customer=ACME.upper())]
        sums = sum_quantities(stored, "2026-08", "chat_completion", "token")
        self.assertEqual(sums[ACME], Decimal("500"))


class LineAmountTest(unittest.TestCase):
    def test_라인마다_절사한다(self):
        self.assertEqual(line_amount(Decimal("3"), Decimal("0.5")), 1)

    def test_정확히_떨어지면_그대로다(self):
        self.assertEqual(line_amount(Decimal("40000"), Decimal("0.5")), 20000)


class BuildExpectedTest(unittest.TestCase):
    def test_라인별_절사가_고객별로_적용된다(self):
        # 고객 둘이 각각 수량 3 x 단가 0.5 = 1.5원이면 라인 절사로 각 1원, 총 2원이다.
        # 합산 후 절사(3원)와 구분되는 케이스다.
        stored = [
            _stored("evt-1", customer=ACME, props={"token": 3}),
            _stored("evt-2", customer=BETA, props={"token": 3}),
        ]
        result = build_expected(stored, [TOKEN_USAGE], "2026-08", [ACME, BETA])
        self.assertEqual(result.customers[ACME].amount, 1)
        self.assertEqual(result.customers[BETA].amount, 1)
        self.assertEqual(result.total_amount, 2)

    def test_이벤트가_없는_고객은_0으로_채운다(self):
        stored = [_stored("evt-1", customer=ACME)]
        result = build_expected(stored, [TOKEN_USAGE], "2026-08", [ACME, BETA])
        self.assertEqual(result.customers[BETA].lines[0].quantity, Decimal("0"))
        self.assertEqual(result.customers[BETA].amount, 0)

    def test_단가를_모르는_미터는_금액이_없다(self):
        no_price = MetricMeta(
            code="api-calls",
            event_type="api_call",
            aggregation="SUM",
            target_property="count",
            unit_price=None,
        )
        stored = [_stored("evt-1")]
        result = build_expected(stored, [TOKEN_USAGE, no_price], "2026-08", [ACME])
        self.assertIsNone(result.customers[ACME].amount)
        self.assertIsNone(result.total_amount)


if __name__ == "__main__":
    unittest.main()
