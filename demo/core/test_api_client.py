"""problem+json 파싱 검증.

code 확장 멤버는 2026-08-17(MS2-150 A-1, B-1)부터 세 엔드포인트의 4xx에 모두 붙는다. 예전에는
/v1/events에만 있었고 이 파일의 폴백도 그 이유로 있었다. 폴백은 그대로 두는데 이유가 바뀌었다.
5xx는 본문 형식을 약속하지 않기로 했고(B-3), 프록시가 끼어들면 problem+json이 아닌 본문도 온다.
"""

import unittest

from core.api_client import parse_problem


class ParseProblemTest(unittest.TestCase):
    def test_events의_400은_code가_있다(self):
        problem = parse_problem(
            400,
            {
                # type은 넣지 않는다. 서버가 안 보낸다 (MS2-150 7단계 결정). 2026-08-17까지 이
                # 픽스처들이 "about:blank"를 담고 있어 없는 필드를 예시로 보여주고 있었다.
                "title": "Bad Request",
                "status": 400,
                # detail은 영어, errors[].message는 한국어다. 헷갈리기 쉬운데 읽는 사람이 다르다.
                # detail은 로그와 개발자용이고(B-2), 도입사가 읽는 자리는 errors[].message다
                # (MS2-150 6단계). 2026-08-17까지 이 픽스처는 둘을 정확히 반대로 담고 있었다.
                "detail": "the request could not be accepted as sent",
                "code": "validation_error",
                "errors": [{"field": "event_type", "message": "공백일 수 없습니다"}],
            },
        )
        self.assertEqual(problem.code, "validation_error")
        # 와이어 이름이다. 2026-08-17까지 이 픽스처는 자바 이름(eventType)을 담고 있었는데,
        # 서버가 A-2로 바뀐 뒤에도 그대로여서 데모가 없는 계약을 예시로 보여주고 있었다.
        self.assertEqual(problem.errors[0]["field"], "event_type")

    def test_code가_없는_본문도_다룬다(self):
        problem = parse_problem(
            400,
            {"title": "Bad Request", "status": 400, "detail": "the request could not be accepted as sent"},
        )
        self.assertIsNone(problem.code)
        self.assertEqual(problem.detail, "the request could not be accepted as sent")

    def test_json이_아닌_바디도_다룬다(self):
        problem = parse_problem(502, None)
        self.assertEqual(problem.status, 502)
        self.assertIsNone(problem.code)
        self.assertEqual(problem.errors, [])

    def test_요약문은_code와_detail을_담는다(self):
        problem = parse_problem(400, {"code": "unknown_customer_reference", "detail": "customer x"})
        self.assertIn("unknown_customer_reference", problem.summary())
        self.assertIn("customer x", problem.summary())


if __name__ == "__main__":
    unittest.main()
