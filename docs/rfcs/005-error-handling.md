---
status: "draft"
date: "2026-09-03"
author: "박성종"
domain: "global"
---

# RFC-005: 오류 응답을 problem+json 대신 code와 message를 든 자체 스키마로 낸다

## 배경 및 문제 정의

지금 오류 응답은 RFC 9457 problem+json에 `code` 확장 멤버를 얹은 형식이다. 표준 형식 위에 실무 구조의 `code`를 얹은 절충이라 어느 쪽 관례도 아니고, 근거가 "원래 그랬다"뿐이다.

도입사가 보는 모양에 세 가지 문제가 있다.

- 400 안에서 모양이 둘이다. 프레임워크 검증은 어느 필드가 틀렸는지 `errors[]`로 주는데, 도메인 검증은 `detail` 문장 한 줄뿐이라 필드 이름이 문장 안에 묻힌다
- 문구가 영어(`title`, `detail`)와 한국어(`errors[].message`)로 섞여 있고, 화면 문구는 프론트엔드가 `code`로 고른다. 도입사가 API를 직접 부르는 제품이라 문구를 보는 쪽이 우리 프론트엔드만이 아니다
- 5xx는 본문 형식을 약속하지 않아 일부는 problem+json, 일부는 Boot 기본 형식으로 나간다. 클라이언트는 상태 코드만 본다

이 RFC는 도입사와 프론트엔드가 의존하는 오류 응답의 모양만 정한다. 서버 안에서 그 모양을 만드는 구조(예외 계층, 핸들러 배치)와 이름, code를 가르는 기준은 범위 밖이다. 구조는 코드가, 규칙은 팀 규칙 파일이 정본이다.

## 검토한 선택지

### 본문 형식

**A. 자체 스키마 `{code, message, errors[]}` (채택)**

- `code`가 계약이고 `message`는 code마다 하나인 한국어 문구, `errors[]`는 어디가 틀렸는지를 `{field, message}`로 든다
- Stripe, GitHub, [토스페이먼츠][toss-errors] 같은 공개 API와 국내 서비스 대부분이 이 계열이다. [토스페이먼츠 오류 코드][toss-errors]는 영어 `code`(`ALREADY_PROCESSED_PAYMENT`)에 한국어 `message`("이미 처리된 결제 입니다.")를 짝지어 문서에 둔다
- 잃는 것: problem+json을 버린다. `title`, `detail`, `instance`, `type`이 없어지고 Content-Type이 `application/json`이 된다

**B. problem+json을 Spring 문서대로 쓰기 (`type` URI, `ErrorResponseException`)**

- [Spring 레퍼런스 "Error Responses"][sf-error-responses]가 정한 길이다. problem+json 자체는 쓰는 곳이 뚜렷하다. [ASP.NET Core][aspnet-errors]는 기본 오류 형식이고, [Zalando API 가이드라인][zalando-176]은 MUST로 강제하며, [벨기에 공공 API 가이드][belgif-errors]가 요구한다
- 버린 이유: `type`이 URI라 클라이언트 분기 문자열이 길고, 국내 결제 API와 참고한 실무 예제에서 이 형식을 찾지 못했다. 표준 쪽을 따르고 싶으면 새 RFC로 다시 올린다

**C. 유지 (problem+json + `code` 확장)**

- 버린 이유: 어느 쪽 관례도 아니다. 400의 모양이 둘인 문제가 그대로 남는다

### 문구를 누가 드는가

**1. 백엔드가 code마다 문구 하나를 싣는다. code가 계약이고 문구는 편의 (채택)**

- 계약은 `code`뿐이다. 문구는 바꿔도 클라이언트가 깨지지 않고, 클라이언트는 `message`로 분기하지 않는다
- 도입사가 우리 문구를 쓸지 자기 문구를 만들지 지금은 알 수 없고, 백엔드가 문구를 싣고 `code`를 계약으로 두면 두 선택지가 남는다. 문서에는 code와 문구가 표로 같이 실린다
- 잃는 것: 원인이 여럿인 code에서 문구만으로는 무엇이 틀렸는지 모른다. 그래서 어디가 틀렸는지는 `errors[]`가 따로 든다. Stripe의 `param`, [Google 오류 상세][google-error-details]의 `BadRequest.FieldViolation`, [GitHub][github-errors]의 `errors[]`가 같은 자리다

**2. 던지는 자리마다 문구를 만들고 프론트엔드가 code로 문구를 고른다 (지금)**

- 버린 이유: 필드 이름이 문장 속에 묻혀 클라이언트가 파싱해야 하고, 표에 실을 문구가 code마다 하나로 정해지지 않으며, 도입사가 보낸 값이 문장에 새기 쉽다

### 5xx 본문

**1. 약속하지 않기 (지금)**

- 버린 이유: 4xx와 5xx의 본문 모양이 다르고, 5xx에서 `code`를 쓸 수 없다

**2. 4xx와 같은 스키마로 답하기 (채택)**

- 모르는 예외는 500과 `internal_server_error`다. 원인은 로그에만 남긴다. 상태는 바꾸지 않는다. 4xx로 내리면 클라이언트가 자기 입력을 의심하고 재시도를 멈춘다

## 결정

선택한 것: 본문 형식 A, 문구는 백엔드가 code마다 하나씩 든다, 5xx도 같은 스키마. 이유: 국내외 공개 API가 같은 모양이라 도입사가 설명 없이 읽고, 400이 프레임워크 검증이든 도메인 검증이든 같은 모양으로 어디가 틀렸는지 주며, 4xx와 5xx가 한 스키마로 나간다.

- 본문은 `{code, message, errors[]}`다. Content-Type은 `application/json`이다
- `code`가 계약이다. lowercase snake_case 문자열이고, code 하나는 (HTTP 상태, 의미) 하나만 가리킨다. 값의 목록은 `backend/openapi.yaml`이 정본이다
- `message`는 code마다 하나인 한국어 한 줄이다. 계약이 아니라 편의라 문구는 바꿔도 되고, 클라이언트는 `code`로만 분기한다. 도입사가 보낸 값을 문구에 되비추지 않는다. 화면은 이 문구를 그대로 띄워도 되고 `code`로 자기 문구를 골라도 된다
- `errors[]`는 `{field, message}`이고 400에만 실린다. `field`는 도입사가 보낸 이름(JSON 키, 쿼리 파라미터, 헤더명)이고 `message`만 필드별 구체 사유다
- 5xx도 같은 스키마다. 모르는 예외는 500 `internal_server_error`다
- 요청 본문이나 쿼리 파라미터가 가리키는 자원이 없는 것도 없음이다. 이벤트 수집과 이벤트 조회(`customer_id` 필터)의 미등록 고객은 400 `unknown_customer_reference` 대신 404 `customer_not_found`로 낸다. [Slack][slack-errors]은 본문이 가리킨 채널에도 `channel_not_found`, [Google][google-errors]은 `NOT_FOUND`를 404로 매핑한다

고객 삭제에서 대상이 없을 때는 404로 이렇게 나간다.

```json
{ "code": "customer_not_found", "message": "고객을 찾을 수 없습니다" }
```

미터 등록에서 SUM 집계에 `target_property`가 없을 때는 400으로 필드 오류가 실린다.

```json
{
  "code": "invalid_billable_metric",
  "message": "집계 미터로 성립하지 않습니다",
  "errors": [{ "field": "target_property", "message": "SUM 집계에는 필요합니다" }]
}
```

### 결과

- 좋은 점: 국내외 공개 API와 같은 모양이라 도입사와 새 팀원이 설명 없이 읽는다
- 좋은 점: 400이면 프레임워크 검증이든 도메인 검증이든 `errors[]`로 어느 필드인지 준다
- 좋은 점: 4xx와 5xx가 같은 스키마다. "5xx 본문은 약속하지 않는다"던 결정을 뒤집는다
- 좋은 점: 문구가 백엔드에 있어 code와 함께 문서에 실리고, 바꿀 때 한 곳만 고친다
- 나쁜 점: problem+json을 버린다. 표준 형식을 기대하는 외부 도구나 클라이언트 라이브러리와 맞지 않고, `instance`(요청 경로)가 본문에서 빠진다
- 나쁜 점: 문구가 로케일을 타지 않는다. `spring.web.locale`이 ko 고정이라 지금은 문제가 없고, 다국어가 필요해지면 새 RFC를 쓴다
- 나쁜 점: `DispatcherServlet` 밖에서 난 오류는 이 스키마로 나가지 않는다. 서블릿 필터(나중에 인증 필터가 생기면 그 401, 403)나 컨테이너 단계의 오류는 Boot 기본 `/error` 본문으로 나간다. 지금은 그런 필터가 없고, 생기면 그 필터가 같은 스키마를 직접 쓰게 한다
- 나쁜 점: 이미 나간 `code` 값 일부가 바뀐다(`unknown_customer_reference` 폐지, 이름 규칙에 따른 개명). 첫 도입사 연동 전이라 구현 PR에서 프론트엔드와 demo를 같이 고친다

## 참고 문서

- 명세: [Jakarta RESTful Web Services `NotFoundException`][jakarta-rs]
- 도구 동작: [Spring Framework 레퍼런스 "Error Responses"][sf-error-responses](RFC 9457, `ErrorResponseException`), [ASP.NET Core "Handle errors in web APIs"][aspnet-errors](기본 오류 형식이 `ProblemDetails`)
- 스타일 가이드: [Zalando RESTful API Guidelines 규칙 176 "MUST support problem JSON"][zalando-176], [Belgif REST Guide "Error handling"][belgif-errors]
- 관례: [토스페이먼츠 오류 코드][toss-errors](영어 `code`와 한국어 `message`의 짝), [Slack Web API][slack-errors]와 [Google API 오류][google-errors](본문이 가리킨 자원 없음도 not found), [Google 오류 상세 `error_details.proto`][google-error-details]와 [GitHub REST API 오류][github-errors](필드 오류 목록은 검증 실패에만)

[jakarta-rs]: https://jakarta.ee/specifications/restful-ws/4.0/apidocs/jakarta.ws.rs/jakarta/ws/rs/NotFoundException.html
[sf-error-responses]: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html
[aspnet-errors]: https://learn.microsoft.com/en-us/aspnet/core/web-api/handle-errors
[zalando-176]: https://opensource.zalando.com/restful-api-guidelines/#176
[belgif-errors]: https://www.belgif.be/specification/rest/api-guide/#error-handling
[toss-errors]: https://docs.tosspayments.com/reference/error-codes
[google-error-details]: https://github.com/googleapis/googleapis/blob/master/google/rpc/error_details.proto
[github-errors]: https://docs.github.com/en/rest/using-the-rest-api/troubleshooting-the-rest-api
[slack-errors]: https://api.slack.com/methods/conversations.info#errors
[google-errors]: https://cloud.google.com/apis/design/errors
