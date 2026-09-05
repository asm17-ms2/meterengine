"""model 모듈의 시간 파싱, 월 귀속, 와이어 바디 조립 검증."""

import unittest
from datetime import datetime, timedelta, timezone
from decimal import Decimal

from core.model import (
    KST,
    Event,
    build_body_text,
    kst_month,
    parse_properties,
    parse_rfc3339,
)


class ParseRfc3339Test(unittest.TestCase):
    def test_offset_형식을_파싱한다(self):
        dt = parse_rfc3339("2026-08-14T09:30:00+09:00")
        self.assertEqual(dt, datetime(2026, 8, 14, 9, 30, 0, tzinfo=KST))

    def test_z_표기는_utc로_파싱한다(self):
        dt = parse_rfc3339("2026-08-14T00:30:00Z")
        self.assertEqual(dt, datetime(2026, 8, 14, 0, 30, 0, tzinfo=timezone.utc))

    def test_소수초는_마이크로초로_보존한다(self):
        dt = parse_rfc3339("2026-08-14T09:30:00.5+09:00")
        self.assertEqual(dt.microsecond, 500000)

    def test_나노초는_마이크로초로_절단한다(self):
        dt = parse_rfc3339("2026-08-14T09:30:00.123456789Z")
        self.assertEqual(dt.microsecond, 123456)

    def test_음수_오프셋을_파싱한다(self):
        dt = parse_rfc3339("2026-08-14T09:30:00-05:00")
        self.assertEqual(dt.utcoffset(), timedelta(hours=-5))

    def test_초_생략을_허용한다(self):
        # 서버의 Jackson OffsetDateTime(ISO_OFFSET_DATE_TIME)이 수용하므로 툴도 수용한다
        dt = parse_rfc3339("2026-08-14T09:30+09:00")
        self.assertEqual(dt, datetime(2026, 8, 14, 9, 30, 0, tzinfo=KST))

    def test_시만_있는_오프셋을_허용한다(self):
        dt = parse_rfc3339("2026-08-14T09:30:00+09")
        self.assertEqual(dt.utcoffset(), timedelta(hours=9))

    def test_30분_단위_오프셋을_허용한다(self):
        dt = parse_rfc3339("2026-08-14T09:30:00+05:30")
        self.assertEqual(dt.utcoffset(), timedelta(hours=5, minutes=30))

    def test_서버_수용_범위를_넘는_오프셋은_거부한다(self):
        with self.assertRaises(ValueError):
            parse_rfc3339("2026-08-14T09:30:00+19:00")

    def test_오프셋이_없으면_거부한다(self):
        with self.assertRaises(ValueError):
            parse_rfc3339("2026-08-14T09:30:00")

    def test_형식이_아니면_거부한다(self):
        with self.assertRaises(ValueError):
            parse_rfc3339("2026-08-14 09:30:00+09:00")


class KstMonthTest(unittest.TestCase):
    def test_kst_자정_직전은_그_달이다(self):
        self.assertEqual(kst_month(parse_rfc3339("2026-08-31T23:59:59+09:00")), "2026-08")

    def test_같은_순간의_utc_표기도_같은_달이다(self):
        self.assertEqual(kst_month(parse_rfc3339("2026-08-31T14:59:59Z")), "2026-08")

    def test_kst_다음달_자정은_다음달이다(self):
        self.assertEqual(kst_month(parse_rfc3339("2026-08-31T15:00:00Z")), "2026-09")


class BuildBodyTextTest(unittest.TestCase):
    def test_properties_원문을_그대로_끼워넣는다(self):
        event = Event(
            transaction_id="evt-001",
            customer_id="a728e7b6-d82b-4f3c-a960-a66a02794c1d",
            event_type="chat_completion",
            timestamp_text="2026-08-14T09:30:00+09:00",
            properties_text='{"token": 500.00, "model": "claude"}',
        )
        body = build_body_text(event)
        self.assertIn('"properties": {"token": 500.00, "model": "claude"}', body)
        self.assertIn('"transaction_id": "evt-001"', body)
        self.assertIn('"occurred_at": "2026-08-14T09:30:00+09:00"', body)


class ParsePropertiesTest(unittest.TestCase):
    def test_소수는_decimal로_읽는다(self):
        props = parse_properties('{"token": 500.25}')
        self.assertEqual(props["token"], Decimal("500.25"))

    def test_정수와_불리언은_타입을_유지한다(self):
        props = parse_properties('{"token": 500, "cached": true}')
        self.assertIsInstance(props["token"], int)
        self.assertIs(props["cached"], True)

    def test_nan은_거부한다(self):
        with self.assertRaises(ValueError):
            parse_properties('{"token": NaN}')

    def test_객체가_아니면_거부한다(self):
        with self.assertRaises(ValueError):
            parse_properties("[1, 2]")


if __name__ == "__main__":
    unittest.main()
