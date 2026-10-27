import unittest
from decimal import Decimal
from fractions import Fraction

from csvdemo.expected import (
    BillableMetricMeta,
    aggregate_quantities,
    build_expected,
    dimension_values_key,
    line_amount,
    predict_send,
    stored_events_from_log,
    sum_quantities_by_dimension_values,
    transform_quantity,
)
from core.jsonl_log import SendRecord
from core.model import Event, StoredEvent, parse_rfc3339

ACME = "a728e7b6-d82b-4f3c-a960-a66a02794c1d"
BETA = "252339bc-d5f8-472d-b5d6-ed8554049450"
KNOWN = {ACME, BETA}

TOKEN_USAGE = BillableMetricMeta(
    code="token-usage",
    event_type="chat_completion",
    aggregation="sum",
    target_property="token",
    unit_prices={dimension_values_key({}): Decimal("0.5")},
)

INPUT_TOKENS = BillableMetricMeta(
    code="input-tokens",
    event_type="llm_request",
    aggregation="sum",
    target_property="input_tokens",
    dimension_properties=["model"],
    unit_prices={
        dimension_values_key({"model": "claude-haiku-4-5"}): Decimal("0.00159"),
        dimension_values_key({"model": "claude-sonnet-5"}): Decimal("0.00318"),
    },
)


def _event(tx, customer=ACME, event_type="chat_completion", ts="2026-08-10T12:00:00+09:00", props='{"token": 500}'):
    return Event(
        transaction_id=tx,
        customer_id=customer,
        event_type=event_type,
        timestamp_text=ts,
        properties_text=props,
    )


def _sum_quantities(stored, month, event_type, target_property):
    billable_metric = BillableMetricMeta(
        code="token-usage", event_type=event_type, aggregation="sum", target_property=target_property
    )
    return {
        customer_id: by_dimension_values[dimension_values_key({})].quantity
        for customer_id, by_dimension_values in sum_quantities_by_dimension_values(
            stored, month, billable_metric
        ).items()
    }


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
        self.assertEqual(prediction.outcomes[0].code, "customer_not_found")

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
                "type": "chat_completion",
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
        sums = _sum_quantities(stored, "2026-08", "chat_completion", "token")
        self.assertEqual(sums[ACME], Decimal("40000"))

    def test_kst_월_경계로_귀속한다(self):
        stored = [
            _stored("evt-1", ts="2026-08-31T23:59:59+09:00"),
            _stored("evt-2", ts="2026-08-31T14:59:59Z"),
            _stored("evt-3", ts="2026-08-31T15:00:00Z"),
        ]
        aug = _sum_quantities(stored, "2026-08", "chat_completion", "token")
        sep = _sum_quantities(stored, "2026-09", "chat_completion", "token")
        self.assertEqual(aug[ACME], Decimal("1000"))
        self.assertEqual(sep[ACME], Decimal("500"))

    def test_다른_event_type은_합산하지_않는다(self):
        stored = [_stored("evt-1"), _stored("evt-2", event_type="embedding")]
        sums = _sum_quantities(stored, "2026-08", "chat_completion", "token")
        self.assertEqual(sums[ACME], Decimal("500"))

    def test_비숫자와_누락과_불리언은_제외한다(self):
        stored = [
            _stored("evt-1", props={"token": Decimal("500")}),
            _stored("evt-2", props={"token": "500"}),
            _stored("evt-3", props={"model": "claude"}),
            _stored("evt-4", props={"token": True}),
        ]
        sums = _sum_quantities(stored, "2026-08", "chat_completion", "token")
        self.assertEqual(sums[ACME], Decimal("500"))

    def test_고정밀_소수_자릿수를_유지한다(self):
        stored = [_stored("evt-1", props={"token": Decimal("1234567890.123456789012345")})]
        sums = _sum_quantities(stored, "2026-08", "chat_completion", "token")
        self.assertEqual(sums[ACME], Decimal("1234567890.123456789012345"))

    def test_유효숫자_28자리를_넘는_합도_정확하다(self):
        value = Decimal("12345678901234567890.123456789")
        stored = [_stored("evt-1", props={"token": value}), _stored("evt-2", props={"token": value})]
        sums = _sum_quantities(stored, "2026-08", "chat_completion", "token")
        self.assertEqual(sums[ACME], Decimal("24691357802469135780.246913578"))

    def test_대문자_uuid도_같은_고객으로_집계한다(self):
        stored = [_stored("evt-1", customer=ACME.upper())]
        sums = _sum_quantities(stored, "2026-08", "chat_completion", "token")
        self.assertEqual(sums[ACME], Decimal("500"))


class LineAmountTest(unittest.TestCase):
    def test_라인마다_10원_미만을_절사한다(self):
        self.assertEqual(line_amount(Decimal("39"), Decimal("0.5")), 10)

    def test_10원이_안_되면_0이다(self):
        self.assertEqual(line_amount(Decimal("19"), Decimal("0.5")), 0)

    def test_정확히_떨어지면_그대로다(self):
        self.assertEqual(line_amount(Decimal("40000"), Decimal("0.5")), 20000)


class BuildExpectedTest(unittest.TestCase):
    def test_라인별_절사가_고객별로_적용된다(self):
        stored = [
            _stored("evt-1", customer=ACME, props={"token": 39}),
            _stored("evt-2", customer=BETA, props={"token": 39}),
        ]
        result = build_expected(stored, [TOKEN_USAGE], "2026-08", [ACME, BETA])
        self.assertEqual(result.customers[ACME].amount, 10)
        self.assertEqual(result.customers[BETA].amount, 10)
        self.assertEqual(result.total_amount, 20)

    def test_이벤트가_없는_고객은_0으로_채운다(self):
        stored = [_stored("evt-1", customer=ACME)]
        result = build_expected(stored, [TOKEN_USAGE], "2026-08", [ACME, BETA])
        self.assertEqual(result.customers[BETA].lines[0].quantity, Decimal("0"))
        self.assertEqual(result.customers[BETA].amount, 0)

    def test_단가가_없는_미터는_금액에_더하지_않는다(self):
        no_price = BillableMetricMeta(
            code="api-calls",
            event_type="api_call",
            aggregation="sum",
            target_property="count",
        )
        stored = [_stored("evt-1"), _stored("evt-2", event_type="api_call", props={"count": 3})]
        result = build_expected(stored, [TOKEN_USAGE, no_price], "2026-08", [ACME])
        self.assertIsNone(result.customers[ACME].lines[1].amount)
        self.assertEqual(result.customers[ACME].amount, 250)
        self.assertEqual(result.total_amount, 250)

    def test_조합마다_그_조합의_단가로_계산한다(self):
        stored = [
            _stored("evt-1", customer=ACME, event_type="llm_request", props={"input_tokens": 5010, "model": "claude-haiku-4-5"}),
            _stored("evt-2", customer=BETA, event_type="llm_request", props={"input_tokens": 4820, "model": "claude-sonnet-5"}),
        ]
        result = build_expected(stored, [INPUT_TOKENS], "2026-08", [ACME, BETA])
        self.assertEqual(result.customers[ACME].amount, 0)
        self.assertEqual(result.customers[BETA].amount, 10)

    def test_한_고객의_여러_조합은_조합마다_절사한_뒤_더한다(self):
        stored = [
            _stored("evt-1", event_type="llm_request", props={"input_tokens": 9000, "model": "claude-haiku-4-5"}),
            _stored("evt-2", event_type="llm_request", props={"input_tokens": 9000, "model": "claude-sonnet-5"}),
        ]
        line = build_expected(stored, [INPUT_TOKENS], "2026-08", [ACME]).customers[ACME].lines[0]
        self.assertEqual(line.quantity, Decimal("18000"))
        self.assertEqual(line.amount, 10 + 20)

    def test_단가가_없는_조합은_금액과_청구_수량에서_빠지고_따로_남는다(self):
        stored = [
            _stored("evt-1", event_type="llm_request", props={"input_tokens": 4820, "model": "claude-sonnet-5"}),
            _stored("evt-2", event_type="llm_request", props={"input_tokens": 700, "model": "claude-unknown"}),
            _stored("evt-3", event_type="llm_request", props={"input_tokens": 300}),
        ]
        line = build_expected(stored, [INPUT_TOKENS], "2026-08", [ACME]).customers[ACME].lines[0]
        self.assertEqual(line.quantity, Decimal("5820"))
        self.assertEqual(line.priced_quantity, Decimal("4820"))
        self.assertEqual(line.amount, 10)
        self.assertEqual(
            [(usage.dimension_values, usage.quantity) for usage in line.unpriced],
            [({"model": "claude-unknown"}, Decimal("700")), ({"model": None}, Decimal("300"))],
        )


class DimensionValuesKeyTest(unittest.TestCase):
    def test_숫자는_표기가_달라도_값이_같으면_같은_조합이다(self):
        self.assertEqual(dimension_values_key({"tier": 9}), dimension_values_key({"tier": Decimal("9.0")}))
        self.assertEqual(dimension_values_key({"tier": Decimal("9.00")}), dimension_values_key({"tier": 9}))
        self.assertEqual(dimension_values_key({"tier": Decimal("1E+2")}), dimension_values_key({"tier": 100}))

    def test_숫자와_문자열과_참거짓은_서로_다른_조합이다(self):
        self.assertNotEqual(dimension_values_key({"tier": 9}), dimension_values_key({"tier": "9"}))
        self.assertNotEqual(dimension_values_key({"tier": 1}), dimension_values_key({"tier": True}))
        self.assertNotEqual(dimension_values_key({"tier": None}), dimension_values_key({}))

    def test_키_순서와_상관없이_같다(self):
        self.assertEqual(dimension_values_key({"a": 1, "b": "x"}), dimension_values_key({"b": "x", "a": 1}))


class AggregationTest(unittest.TestCase):
    def test_열_집계가_속성_형식과_발생_순서를_구분한다(self):
        events = [
            _stored("evt-d", ts="2026-08-10T11:00:00+09:00", props={"token": 0}),
            _stored("evt-b", ts="2026-08-10T09:00:00+09:00", props={"token": 20}),
            _stored("evt-c", ts="2026-08-10T11:00:00+09:00", props={"token": 66}),
            _stored("evt-a", ts="2026-08-10T09:00:00+09:00", props={"token": 7}),
            *[_stored("extra-%s" % index, props=props) for index, props in enumerate([
                {"token": "7"}, {"token": True}, {"token": None}, {"token": []}, {},
            ])],
        ]
        expected = {"sum": "93", "count": "9", "count_distinct": "6", "min": "0", "max": "66", "avg": "23.25", "first": "7", "last": "0", "median": "13.5", "percentile": "59.1"}
        for aggregation, quantity in expected.items():
            with self.subTest(aggregation=aggregation):
                metric = BillableMetricMeta("metric", "chat_completion", aggregation, None if aggregation == "count" else "token", percentile=Decimal("95") if aggregation == "percentile" else None)
                totals, dimensions = aggregate_quantities(events, "2026-08", metric)
                self.assertEqual(totals[ACME].quantity, Decimal(quantity))
                self.assertEqual(next(iter(dimensions[ACME].values())).raw_quantity, Decimal(quantity))

    def test_고유값은_숫자표기만_정규화하고_다른_형식은_구분한다(self):
        values = [1, Decimal("1.0"), "1", True, False, None, [], {}, Decimal("123456789012345678901234567890.1"), Decimal("123456789012345678901234567890.2")]
        metric = BillableMetricMeta("distinct", "chat_completion", "count_distinct", "token")
        totals, _ = aggregate_quantities([_stored(str(index), props={"token": value}) for index, value in enumerate(values)], "2026-08", metric)
        self.assertEqual(totals[ACME].quantity, 6)

    def test_고객_평균은_차원별_평균을_더하지_않는다(self):
        metric = BillableMetricMeta("average", "chat_completion", "avg", "token", ["model"], unit_prices={dimension_values_key({"model": model}): Decimal("10") for model in ["a", "b"]})
        events = [_stored(str(index), props={"token": value, "model": model}) for index, (value, model) in enumerate([(10, "a"), (20, "a"), (100, "b")])]
        line = build_expected(events, [metric], "2026-08", [ACME]).customers[ACME].lines[0]
        self.assertAlmostEqual(line.quantity, Decimal("43.33333333333333333333333333"))
        self.assertEqual(line.priced_quantity, 115)
        self.assertEqual(line.amount, 1150)

    def test_고유값과_단위올림도_고객전체와_차원을_따로_계산한다(self):
        for aggregation, unit in [("count_distinct", "1"), ("sum", "3")]:
            with self.subTest(aggregation=aggregation):
                metric = BillableMetricMeta("metric", "chat_completion", aggregation, "token", ["model"], unit_prices={dimension_values_key({"model": model}): Decimal("10") for model in ["a", "b"]}, unit_size=Decimal(unit), rounding_mode="ceil")
                events = [_stored(model, props={"token": 1, "model": model}) for model in ["a", "b"]]
                line = build_expected(events, [metric], "2026-08", [ACME]).customers[ACME].lines[0]
                self.assertEqual(line.quantity, 1)
                self.assertEqual(line.priced_quantity, 2)
                self.assertEqual(line.amount, 20)

    def test_반올림은_단위를_나눈_후_양수와_음수에_적용한다(self):
        cases = [("ceil", "25", "2.5"), ("floor", "25", "2.5"), ("round", "25.5", "2.6"), ("round", "-25.5", "-2.6"), ("ceil", "-25.5", "-2.5"), ("floor", "-25.5", "-2.6")]
        for mode, value, expected in cases:
            with self.subTest(mode=mode, value=value):
                metric = BillableMetricMeta("metric", "chat_completion", "sum", "token", unit_size=Decimal("10"), rounding_mode=mode, rounding_precision=1)
                self.assertEqual(transform_quantity(Decimal(value), metric), Decimal(expected))

    def test_무반올림_반복소수는_18자리_이상으로_유한소수는_그대로_보존한다(self):
        metric = BillableMetricMeta("metric", "chat_completion", "sum", "token", unit_size=Decimal("3"))
        self.assertEqual(transform_quantity(Decimal("1"), metric), Decimal("0.333333333333333333"))
        self.assertEqual(transform_quantity(Decimal("1.00000000000000000000"), metric), Decimal("0.33333333333333333333"))
        metric.unit_size = Decimal("3E18")
        self.assertEqual(transform_quantity(Decimal("1"), metric), Decimal("0.000000000000000000333333333333333333"))
        metric.unit_size = Decimal("3")
        self.assertEqual(transform_quantity(Decimal("1E-30"), metric), Decimal("0.000000000000000000000000000000333333333333333333"))
        metric.unit_size = Decimal("2")
        self.assertEqual(transform_quantity(Decimal("0.12345678901234567890123"), metric), Decimal("0.061728394506172839450615"))

    def test_0으로_올린_음수_원본은_단가없는_양수사용량으로_바뀌지_않는다(self):
        metric = BillableMetricMeta("metric", "chat_completion", "sum", "token", rounding_mode="ceil")
        result = build_expected([_stored("negative", props={"token": Decimal("-0.4")})], [metric], "2026-08", [ACME])
        line = result.customers[ACME].lines[0]
        self.assertEqual(line.raw_quantity, Decimal("-0.4"))
        self.assertEqual(line.quantity, 0)
        self.assertEqual(line.priced_quantity, 0)
        self.assertEqual(line.unpriced, [])

    def test_count도_유형과_서울월과_계산시각_범위를_적용한다(self):
        metric = BillableMetricMeta("count", "chat_completion", "count", None)
        cutoff = parse_rfc3339("2026-08-10T12:00:00+09:00")
        events = [
            _stored("included", ts="2026-08-10T11:59:59+09:00", props={}),
            _stored("cutoff", ts="2026-08-10T12:00:00+09:00"),
            _stored("different-type", event_type="other"),
            _stored("previous-month", ts="2026-07-31T14:59:59Z"),
        ]
        totals, _ = aggregate_quantities(events, "2026-08", metric, cutoff)
        self.assertEqual(totals[ACME].quantity, 1)

    def test_반복소수_표시량을_다시_곱해서_과소청구하지_않는다(self):
        for aggregation, values, unit in [("sum", [1], "3"), ("avg", [1, 0, 0], "1")]:
            with self.subTest(aggregation=aggregation):
                metric = BillableMetricMeta("metric", "chat_completion", aggregation, "token", unit_size=Decimal(unit), unit_prices={dimension_values_key({}): Decimal("300")})
                stored = [_stored(str(index), props={"token": value}) for index, value in enumerate(values)]
                line = build_expected(stored, [metric], "2026-08", [ACME]).customers[ACME].lines[0]
                self.assertEqual(line.dimension_usages[0].exact_quantity, Fraction(1, 3))
                self.assertEqual(line.amount, 100)
                metric.rounding_mode = "floor"
                metric.rounding_precision = 2
                rounded = build_expected(stored, [metric], "2026-08", [ACME]).customers[ACME].lines[0]
                self.assertEqual(rounded.amount, 90)

    def test_평균의_미세한_경계도_정확분수에서_반올림한다(self):
        cases = [
            ("ceil", ["1", "1", "1.000000000000000000000000000001"], "2"),
            ("floor", ["1", "1", "0.999999999999999999999999999999"], "0"),
            ("round", ["1.5", "1.5", "1.499999999999999999999999999999"], "1"),
        ]
        for mode, values, quantity in cases:
            with self.subTest(mode=mode):
                metric = BillableMetricMeta("metric", "chat_completion", "avg", "token", rounding_mode=mode)
                totals, _ = aggregate_quantities([_stored(str(index), props={"token": Decimal(value)}) for index, value in enumerate(values)], "2026-08", metric)
                self.assertEqual(totals[ACME].quantity, Decimal(quantity))
        metric = BillableMetricMeta("metric", "chat_completion", "avg", "token", unit_size=Decimal("3"))
        totals, _ = aggregate_quantities([_stored(str(index), props={"token": Decimal("1.00000000000000000000")}) for index in range(3)], "2026-08", metric)
        self.assertEqual(totals[ACME].quantity, Decimal("0.33333333333333333333"))
        metric.aggregation = "percentile"
        metric.percentile = Decimal("50.00000000000000000000")
        totals, _ = aggregate_quantities([_stored(str(index), props={"token": value}) for index, value in enumerate([0, 2])], "2026-08", metric)
        self.assertEqual(totals[ACME].quantity, Decimal("0.3333333333333333333333"))

    def test_작은_양수에도_정확분수_금액을_보존한다(self):
        metric = BillableMetricMeta("metric", "chat_completion", "sum", "token", unit_size=Decimal("3E18"), unit_prices={dimension_values_key({}): Decimal("3E20")})
        line = build_expected([_stored("tx", props={"token": 1})], [metric], "2026-08", [ACME]).customers[ACME].lines[0]
        self.assertGreater(line.quantity, 0)
        self.assertEqual(line.amount, 100)


if __name__ == "__main__":
    unittest.main()
