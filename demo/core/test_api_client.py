"""오류 응답 파싱 검증.

code와 message는 4xx와 5xx에 모두 실린다. 그래도 프록시가 끼어들면 이 형식이 아닌 본문이 오므로
둘 다 없는 경우를 폴백으로 다룬다.
"""

import unittest

from core.api_client import parse_problem


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
        # 와이어 이름이다. 자바 필드 이름(eventType)이 아니다.
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
