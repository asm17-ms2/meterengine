import unittest
from datetime import datetime, timedelta, timezone
from decimal import Decimal

from core.model import (
    API_KEY_ENV,
    DEFAULT_API_KEY,
    KST,
    Event,
    build_body_text,
    is_api_key,
    kst_month,
    mask_api_key,
    parse_properties,
    parse_rfc3339,
    redact_api_keys,
    resolve_api_key,
)

LIVE_KEY = "me_live_QouOFuCiJyAbC0VGK1ltUCkG4IZM2LtA"
TEST_KEY = "me_test_wiCp766QYNqhyYm4Z7Xq9wYosNvQCeVC"
OTHER_KEY = "me_live_" + "A1" * 16


class ApiKeyFormatTest(unittest.TestCase):
    def test_live와_test_키를_알아본다(self):
        self.assertTrue(is_api_key(LIVE_KEY))
        self.assertTrue(is_api_key(TEST_KEY))

    def test_형식이_다르면_키가_아니다(self):
        for value in (
            "",
            None,
            "d7cee55d-8c82-4afc-b996-6749d8b26a4e",
            "me_session_" + "a" * 32,
            "me_prod_" + "a" * 32,
            "me_live_" + "a" * 31,
            "me_live_" + "a" * 33,
            "me_live_" + "a" * 31 + "-",
            " " + LIVE_KEY,
            LIVE_KEY + "\n",
        ):
            self.assertFalse(is_api_key(value), value)

    def test_기본_키는_로컬_live_키다(self):
        self.assertEqual(DEFAULT_API_KEY, LIVE_KEY)
        self.assertTrue(is_api_key(DEFAULT_API_KEY))

    def test_환경변수_이름(self):
        self.assertEqual(API_KEY_ENV, "METERENGINE_API_KEY")


class MaskApiKeyTest(unittest.TestCase):
    def test_모드_접두사와_끝_네_글자만_남긴다(self):
        self.assertEqual(mask_api_key(LIVE_KEY), "me_live_****2LtA")
        self.assertEqual(mask_api_key(TEST_KEY), "me_test_****CeVC")

    def test_키가_아니면_아무것도_드러내지_않는다(self):
        self.assertEqual(mask_api_key("비밀-값-1234"), "****")
        self.assertEqual(mask_api_key(""), "****")
        self.assertEqual(mask_api_key(None), "****")

    def test_글_안의_키를_가린다(self):
        text = "send --api-key %s --api-key=%s" % (LIVE_KEY, TEST_KEY)
        redacted = redact_api_keys(text)
        self.assertNotIn(LIVE_KEY, redacted)
        self.assertNotIn(TEST_KEY, redacted)
        self.assertIn("me_live_****2LtA", redacted)
        self.assertIn("--api-key=me_test_****CeVC", redacted)


class ResolveApiKeyTest(unittest.TestCase):
    def test_옵션이_가장_먼저다(self):
        self.assertEqual(resolve_api_key(TEST_KEY, {API_KEY_ENV: OTHER_KEY}), TEST_KEY)

    def test_옵션이_없으면_환경변수다(self):
        self.assertEqual(resolve_api_key(None, {API_KEY_ENV: OTHER_KEY}), OTHER_KEY)

    def test_둘_다_없으면_로컬_키다(self):
        self.assertEqual(resolve_api_key(None, {}), LIVE_KEY)
        self.assertEqual(resolve_api_key("", {API_KEY_ENV: ""}), LIVE_KEY)

    def test_대체값을_바꿀_수_있다(self):
        self.assertEqual(resolve_api_key(None, {}, fallback=TEST_KEY), TEST_KEY)
        self.assertIsNone(resolve_api_key(None, {}, fallback=None))

    def test_앞뒤_공백은_버린다(self):
        self.assertEqual(resolve_api_key(None, {API_KEY_ENV: OTHER_KEY + "\n"}), OTHER_KEY)

    def test_형식이_틀리면_원문_없이_멈춘다(self):
        wrong = "me_live_" + "x" * 10
        for option, environ in ((wrong, {}), (None, {API_KEY_ENV: wrong})):
            with self.assertRaises(ValueError) as caught:
                resolve_api_key(option, environ)
            self.assertNotIn(wrong, str(caught.exception))

    def test_오류는_어디서_온_값인지_알린다(self):
        with self.assertRaises(ValueError) as caught:
            resolve_api_key(None, {API_KEY_ENV: "틀림"})
        self.assertIn(API_KEY_ENV, str(caught.exception))
        with self.assertRaises(ValueError) as caught:
            resolve_api_key("틀림", {})
        self.assertIn("--api-key", str(caught.exception))


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
        self.assertIn('"timestamp": "2026-08-14T09:30:00+09:00"', body)


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
