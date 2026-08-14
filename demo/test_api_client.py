"""problem+json 파싱 검증. code 확장 멤버는 /v1/events에만 있으므로 폴백이 필요하다."""

import unittest

from api_client import parse_problem


class ParseProblemTest(unittest.TestCase):
    def test_events의_400은_code가_있다(self):
        problem = parse_problem(
            400,
            {
                "type": "about:blank",
                "title": "Bad Request",
                "status": 400,
                "detail": "요청 본문 검증에 실패했습니다",
                "code": "validation_error",
                "errors": [{"field": "eventType", "message": "must not be blank"}],
            },
        )
        self.assertEqual(problem.code, "validation_error")
        self.assertEqual(problem.errors[0]["field"], "eventType")

    def test_usage_invoice의_400은_code가_없다(self):
        problem = parse_problem(
            400,
            {"type": "about:blank", "title": "Bad Request", "status": 400, "detail": "Invalid request content."},
        )
        self.assertIsNone(problem.code)
        self.assertEqual(problem.detail, "Invalid request content.")

    def test_json이_아닌_바디도_다룬다(self):
        problem = parse_problem(502, None)
        self.assertEqual(problem.status, 502)
        self.assertIsNone(problem.code)
        self.assertEqual(problem.errors, [])

    def test_요약문은_code와_detail을_담는다(self):
        problem = parse_problem(400, {"code": "customer_not_found", "detail": "customer x"})
        self.assertIn("customer_not_found", problem.summary())
        self.assertIn("customer x", problem.summary())


if __name__ == "__main__":
    unittest.main()
