import json
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from core.api_client import (
    ApiClient,
    ApiRequestError,
    ApiResult,
    Organization,
    parse_problem,
    require_organization,
)

LIVE_KEY = "me_live_QouOFuCiJyAbC0VGK1ltUCkG4IZM2LtA"
ORGANIZATION_ID = "d7cee55d-8c82-4afc-b996-6749d8b26a4e"


class RecordingBackend:
    def __init__(self, status=200, body=None):
        self.requests = []
        self.status = status
        self.body = body if body is not None else {}
        backend = self

        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):  # noqa: N802
                backend.requests.append(("GET", self.path, dict(self.headers.items())))
                self._reply()

            def do_POST(self):  # noqa: N802
                length = int(self.headers.get("Content-Length") or 0)
                self.rfile.read(length)
                backend.requests.append(("POST", self.path, dict(self.headers.items())))
                self._reply()

            def _reply(self):
                raw = json.dumps(backend.body).encode("utf-8")
                self.send_response(backend.status)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(raw)))
                self.end_headers()
                self.wfile.write(raw)

            def log_message(self, *args):
                pass

        self.server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.server.daemon_threads = True
        threading.Thread(target=self.server.serve_forever, daemon=True).start()

    @property
    def base_url(self):
        return "http://127.0.0.1:%d" % self.server.server_address[1]

    def close(self):
        self.server.shutdown()
        self.server.server_close()


class PriceQueryTest(unittest.TestCase):
    CUSTOMER_ID = "4f9d8c2e-1b7a-4c3d-9e5f-2a6b8c0d1e3f"

    def backend(self):
        made = RecordingBackend(body={})
        self.addCleanup(made.close)
        return made

    def test_요금제_이력을_달과_고객으로_좁혀_조회한다(self):
        backend = self.backend()
        ApiClient(backend.base_url, LIVE_KEY).get_price_plans("2026-08", self.CUSTOMER_ID)
        self.assertEqual(backend.requests[0][1], "/v1/price-plans?month=2026-08&customer_id=" + self.CUSTOMER_ID)

    def test_미터별_가격을_달과_고객으로_좁혀_조회한다(self):
        backend = self.backend()
        ApiClient(backend.base_url, LIVE_KEY).get_billable_metric_prices("2026-08", self.CUSTOMER_ID)
        self.assertEqual(backend.requests[0][1], "/v1/billable-metric-prices?month=2026-08&customer_id=" + self.CUSTOMER_ID)


class AuthorizationHeaderTest(unittest.TestCase):
    def backend(self, status=200, body=None):
        made = RecordingBackend(status, body)
        self.addCleanup(made.close)
        return made

    def test_요청마다_Bearer로_키를_보낸다(self):
        backend = self.backend(body={"duplicate": False})
        client = ApiClient(backend.base_url, LIVE_KEY)
        client.post_event("{}")
        client.get_usage("2026-08")
        for _, _, headers in backend.requests:
            self.assertEqual(headers.get("Authorization"), "Bearer " + LIVE_KEY)

    def test_사용자_정의_헤더는_보내지_않는다(self):
        backend = self.backend()
        ApiClient(backend.base_url, LIVE_KEY).get_customers()
        names = [name.lower() for name in backend.requests[0][2]]
        self.assertEqual([name for name in names if name.startswith("x-")], [])

    def test_키의_도입사를_조회한다(self):
        body = {"id": ORGANIZATION_ID, "name": "데모 도입사", "status": "active", "created_at": "2026-08-01T00:00:00Z"}
        backend = self.backend(body=body)
        result = ApiClient(backend.base_url, LIVE_KEY).get_organization()
        self.assertEqual(backend.requests[0][:2], ("GET", "/v1/organization"))
        self.assertEqual(result.status, 200)
        self.assertEqual(result.body["id"], ORGANIZATION_ID)


class FakeClient:
    def __init__(self, status, body):
        self.status = status
        self.body = body
        self.api_key = LIVE_KEY

    def get_organization(self):
        return ApiResult(status=self.status, body=self.body, body_text=json.dumps(self.body), elapsed_ms=1)


class RequireOrganizationTest(unittest.TestCase):
    def test_200이면_도입사를_돌려준다(self):
        organization = require_organization(
            FakeClient(200, {"id": ORGANIZATION_ID, "name": "데모 도입사", "status": "active"})
        )
        self.assertEqual(organization, Organization(id=ORGANIZATION_ID, name="데모 도입사"))

    def test_401이면_code를_보이고_멈춘다(self):
        with self.assertRaises(ApiRequestError) as caught:
            require_organization(
                FakeClient(401, {"code": "invalid_credentials", "message": "인증 정보가 올바르지 않습니다"})
            )
        self.assertTrue(caught.exception.is_auth_failure)
        self.assertEqual(caught.exception.problem.code, "invalid_credentials")
        self.assertIn("invalid_credentials", str(caught.exception))

    def test_403도_인증_실패로_멈춘다(self):
        with self.assertRaises(ApiRequestError) as caught:
            require_organization(FakeClient(403, {"code": "organization_not_active", "message": "m"}))
        self.assertTrue(caught.exception.is_auth_failure)
        self.assertIn("organization_not_active", str(caught.exception))

    def test_오류_문구에는_가린_키만_나온다(self):
        with self.assertRaises(ApiRequestError) as caught:
            require_organization(FakeClient(401, {"code": "invalid_credentials"}))
        self.assertNotIn(LIVE_KEY, str(caught.exception))
        self.assertIn("me_live_****2LtA", str(caught.exception))

    def test_그_밖의_실패는_인증_실패가_아니다(self):
        with self.assertRaises(ApiRequestError) as caught:
            require_organization(FakeClient(500, {"code": "internal_server_error"}))
        self.assertFalse(caught.exception.is_auth_failure)

    def test_id가_없는_응답은_받지_않는다(self):
        with self.assertRaises(ApiRequestError):
            require_organization(FakeClient(200, {"name": "이름만"}))


class ParseProblemTest(unittest.TestCase):
    def test_events의_400은_code와_errors가_있다(self):
        problem = parse_problem(
            400,
            {
                "code": "validation_error",
                "message": "요청 값이 올바르지 않습니다",
                "errors": [{"field": "type", "message": "공백일 수 없습니다"}],
            },
        )
        self.assertEqual(problem.code, "validation_error")
        self.assertEqual(problem.message, "요청 값이 올바르지 않습니다")
        self.assertEqual(problem.errors[0]["field"], "type")

    def test_code가_없는_본문도_다룬다(self):
        problem = parse_problem(400, {"message": "요청 값이 올바르지 않습니다"})
        self.assertIsNone(problem.code)
        self.assertEqual(problem.message, "요청 값이 올바르지 않습니다")

    def test_json이_아닌_바디도_다룬다(self):
        problem = parse_problem(502, None)
        self.assertEqual(problem.status, 502)
        self.assertIsNone(problem.code)
        self.assertIsNone(problem.message)
        self.assertEqual(problem.errors, [])

    def test_요약문은_code와_message를_담는다(self):
        problem = parse_problem(
            404, {"code": "customer_not_found", "message": "고객을 찾을 수 없습니다"}
        )
        self.assertIn("customer_not_found", problem.summary())
        self.assertIn("고객을 찾을 수 없습니다", problem.summary())

    def test_5xx도_같은_형식이다(self):
        problem = parse_problem(
            500, {"code": "internal_server_error", "message": "서버 내부 오류입니다"}
        )
        self.assertEqual(problem.code, "internal_server_error")
        self.assertIn("서버 내부 오류입니다", problem.summary())


if __name__ == "__main__":
    unittest.main()
