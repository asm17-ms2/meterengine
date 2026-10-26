import contextlib
import io
import os
import sys
import tempfile
import unittest
from decimal import Decimal

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from core.jsonl_log import JsonlLogWriter
from csvdemo.render import Console
from core.api_client import ApiRequestError, ApiResult
from csvdemo import verify_cmd
from csvdemo.expected import dimension_values_key
from csvdemo.verify_cmd import _derive_billable_metrics, _load_log_source


KEY = "me_live_QouOFuCiJyAbC0VGK1ltUCkG4IZM2LtA"
ORGANIZATION_ID = "d7cee55d-8c82-4afc-b996-6749d8b26a4e"


class Args:
    def __init__(self, log):
        self.log = log
        self.csv = None
        self.base_url = None
        self.api_key = KEY
        self.month = None
        self.timeout = 5.0


def _header(writer, base_url="http://localhost:8080", organization_id=ORGANIZATION_ID):
    writer.write_run_header(
        started_at_text="2026-08-24T10:00:00+09:00",
        base_url=base_url,
        organization_id=organization_id,
        csv_path=None,
        argv=["send"],
    )


def _send(writer, seq):
    writer.write_send(
        seq=seq,
        sent_at_text="2026-08-24T10:00:0%d+09:00" % seq,
        request_body_text='{"transaction_id": "evt-%d", "customer_id": "c", '
        '"type": "llm_request", "properties": {"input_tokens": 5}, '
        '"timestamp": "2026-08-24T10:00:00+09:00"}' % seq,
        status=200,
        response_text='{"transaction_id": "evt-%d", "duplicate": false}' % seq,
        outcome="new",
        error=None,
        elapsed_ms=3,
    )


class DamagedLogTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.console = Console(color=False)

    def path(self, name):
        return os.path.join(self.directory.name, name)

    def test_중간이_깨진_send_로그는_판정하지_않는다(self):
        path = self.path("send-20260824.jsonl")
        with JsonlLogWriter(path) as writer:
            _header(writer)
            _send(writer, 1)
            _send(writer, 2)
        with open(path, encoding="utf-8") as f:
            lines = f.read().splitlines()
        lines[1] = lines[1][:30]
        with open(path, "w", encoding="utf-8") as f:
            f.write("\n".join(lines) + "\n")

        self.assertIsNone(_load_log_source(Args(path), self.console))

    def test_브리지_로그는_경고까지만_한다(self):
        path = self.path("bridge-20260824.jsonl")
        with JsonlLogWriter(path) as writer:
            _header(writer)
            _send(writer, 1)
        with open(path, "a", encoding="utf-8") as f:
            f.write('{"v": 1, "type": "send", "seq": 2, "requ')
        with JsonlLogWriter(path, append=True) as writer:
            _header(writer)
            _send(writer, 3)

        source = _load_log_source(Args(path), self.console)
        self.assertIsNotNone(source)
        self.assertEqual(len(source.stored), 2)

    def test_깨끗한_로그는_그대로_읽는다(self):
        path = self.path("send-20260824.jsonl")
        with JsonlLogWriter(path) as writer:
            _header(writer, "https://meterengine.com")
            _send(writer, 1)
        source = _load_log_source(Args(path), self.console)
        self.assertEqual(len(source.stored), 1)
        self.assertEqual(source.client.base_url, "https://meterengine.com")


class ApiKeySourceTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.console = Console(color=False)
        self.path = os.path.join(self.directory.name, "send-20260824.jsonl")

    def test_옛_머리말의_org_id는_무시하고_받은_키를_쓴다(self):
        with open(self.path, "w", encoding="utf-8") as f:
            f.write('{"v": 1, "type": "run", "started_at": "2026-08-24T10:00:00+09:00",'
                    ' "base_url": "http://localhost:8080",'
                    ' "org_id": "69cb6c64-14ed-49a0-a397-5f90b468f510", "csv": null, "argv": ["send"]}\n')
        with JsonlLogWriter(self.path, append=True) as writer:
            _send(writer, 1)
        source = _load_log_source(Args(self.path), self.console)
        self.assertEqual(source.client.api_key, KEY)
        self.assertIsNone(source.organization_id)
        self.assertEqual(len(source.stored), 1)

    def test_새_머리말의_도입사_id를_들고_온다(self):
        with JsonlLogWriter(self.path) as writer:
            _header(writer)
            _send(writer, 1)
        source = _load_log_source(Args(self.path), self.console)
        self.assertEqual(source.organization_id, ORGANIZATION_ID)


class FakeVerifyClient:
    def __init__(self, base_url, api_key, timeout_seconds=10.0):
        self.base_url = base_url
        self.api_key = api_key
        self.asked = []

    def get_organization(self):
        self.asked.append("organization")
        return FakeVerifyClient.organization_reply

    def get_billable_metric_prices(self):
        return _result({"billable_metric_prices": []})

    def get_usage(self, month):
        return _result({"billable_metric_usages": []})

    def get_invoice(self, month):
        return _result({"customers": [], "total_amount": 0})

    def get_events_page(self, month):
        return _result({"total": 1})


class RunVerifyAuthTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.path = os.path.join(self.directory.name, "send-20260824.jsonl")
        original = verify_cmd.ApiClient
        verify_cmd.ApiClient = FakeVerifyClient
        self.addCleanup(setattr, verify_cmd, "ApiClient", original)

    def run_verify(self, organization_id=ORGANIZATION_ID):
        with JsonlLogWriter(self.path) as writer:
            _header(writer, organization_id=organization_id)
            _send(writer, 1)
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            code = verify_cmd.run_verify(Args(self.path), Console(color=False))
        return code, out.getvalue(), err.getvalue()

    def test_키가_거부되면_code를_보이고_멈춘다(self):
        FakeVerifyClient.organization_reply = ApiResult(
            status=401, body={"code": "invalid_credentials"}, body_text="", elapsed_ms=0
        )
        with self.assertRaises(ApiRequestError) as caught:
            self.run_verify()
        self.assertIn("invalid_credentials", str(caught.exception))

    def test_기록과_키의_도입사가_다르면_알린다(self):
        FakeVerifyClient.organization_reply = _result(
            {"id": "69cb6c64-14ed-49a0-a397-5f90b468f510", "name": "로컬 두 번째 도입사"}
        )
        code, out, err = self.run_verify()
        self.assertIn("69cb6c64-14ed-49a0-a397-5f90b468f510", out + err)
        self.assertIn(ORGANIZATION_ID, out + err)
        self.assertNotIn(KEY, out + err)

    def test_같은_도입사면_알리지_않는다(self):
        FakeVerifyClient.organization_reply = _result({"id": ORGANIZATION_ID, "name": "데모 도입사"})
        code, out, err = self.run_verify()
        self.assertNotIn("다른 도입사", out + err)



class DeriveBillableMetricsTest(unittest.TestCase):
    def test_가격_목록에서_차원_키와_조합별_단가를_가져온다(self):
        usage = _result({
            "billable_metric_usages": [
                {"code": "input-tokens", "event_type": "llm_request", "aggregation": "sum", "target_property": "input_tokens"},
                {"code": "api-calls", "event_type": "api_call", "aggregation": "sum", "target_property": "count"},
            ]
        })
        prices = _result({
            "billable_metric_prices": [
                {
                    "billable_metric_code": "input-tokens",
                    "dimension_properties": ["model"],
                    "price_rates": [
                        {"dimension_values": {"model": "claude-opus-5-5"}, "unit_price": Decimal("0.00636")},
                        {"dimension_values": {"model": "claude-haiku-5-5"}, "unit_price": Decimal("0.000159")},
                    ],
                }
            ]
        })

        input_tokens, api_calls = _derive_billable_metrics(usage, prices)

        self.assertEqual(input_tokens.dimension_properties, ["model"])
        self.assertEqual(
            input_tokens.unit_prices,
            {
                dimension_values_key({"model": "claude-opus-5-5"}): Decimal("0.00636"),
                dimension_values_key({"model": "claude-haiku-5-5"}): Decimal("0.000159"),
            },
        )
        self.assertEqual(api_calls.dimension_properties, [])
        self.assertEqual(api_calls.unit_prices, {})


def _result(body):
    return ApiResult(status=200, body=body, body_text="", elapsed_ms=0)


if __name__ == "__main__":
    unittest.main()
