# 이름 규칙

이름 규칙의 정본이다. 대상별로 절을 두고, 절마다 규칙(표), 근거, 검토한 대안 순이다.

## 표 양식과 근거의 종류

### 규칙

- 표의 열은 식별자, 케이스, 형식, 예, 근거다.
- 형식 셀은 한두 문장이다. 그보다 긴 규칙은 표 바로 아래 불릿으로 빼고, 불릿은 굵은 제목으로 시작하며, 셀은 그 제목을 따옴표로 가리킨다 (`낱말 순서는 아래 "오류 코드 상수의 낱말 순서"`).
- 예 열은 규칙을 적용한 이름이라 지금 코드와 다를 수 있다.
- 근거 열은 칸마다 종류를 앞에 붙인다.
  - 명세: 규범 문서가 정한 규칙. 강제인지는 조항마다 다르다.
  - 도구 동작: 명세가 아니라 구현이 그렇게 도는 것. 어기면 깨지고, 버전이나 설정으로 바뀔 수 있다.
  - 스타일 가이드: 한 조직이 정해 공개한 규칙집. 널리 쓰이는지는 별개다.
  - 관례: 실물이 그렇게 하고 있는 것. 문서로 정한 적은 없다.
- 외부 근거 없이 팀이 정한 행은 종류 대신 "이 파일"이나 그 결정이 나온 커밋을 적는다.

### 근거

- 이름 규칙은 행마다 같은 다섯 속성을 갖고, 읽는 사람은 식별자로 찾아 읽는다. 조회표라 표가 맞다. 불릿으로 풀면 행마다 같은 라벨을 되풀이한다.
- 셀에 산문을 넣으면 GitHub에서 가로 스크롤이 생기고 줄 댓글이 행 전체에 붙어 어느 문장을 지적했는지 보이지 않는다. 그래서 긴 규칙은 표 밖으로 뺀다.
- 근거에 종류를 붙이는 것은 어기면 깨지는 것과 권고를 같은 무게로 읽지 않기 위해서다.

### 검토한 대안

- 식별자마다 소제목을 두고 불릿으로 (Google Java Style Guide 꼴): 규칙마다 설명이 길면 낫지만, 행 대부분이 케이스 하나와 예 하나라 소제목이 과하다.

## 오류 처리

### 규칙

| 식별자 | 케이스 | 형식 | 예 | 근거 |
| --- | --- | --- | --- | --- |
| 오류 코드 enum | UpperCamelCase 단수 | `ErrorCode` | `ErrorCode` | 관례: [Kill Bill][killbill] `ErrorCode`, [spring-guide][spring-guide] `ErrorCode` |
| 오류 코드 상수 | UPPER_SNAKE_CASE | 영어 어순. 낱말 순서는 아래 "오류 코드 상수의 낱말 순서" | `CUSTOMER_NOT_FOUND`, `INVALID_EVENT`, `INVOICE_EXPIRED` | 케이스는 [Google Java 5.2.4][gj-5.2.4]. 낱말 순서의 근거는 아래 "오류 코드 상수의 낱말 순서" |
| 오류 코드 문자열 | lowercase snake_case | 상수명을 소문자로 내린 것. 따로 짓지 않는다 | `customer_not_found` | 명세: [RFC 6749 5.2][rfc6749-5.2]의 `error` 값. 관례: [Stripe][stripe], [Chargebee][chargebee], [Lago][lago]. 문자열 열거값과 같은 케이스 |
| 예외 부모 | UpperCamelCase | `BusinessException`. 직접 던지지 않는다 | `BusinessException` | 관례: [spring-guide][spring-guide] `BusinessException` |
| 예외 종류 클래스 | UpperCamelCase | `<종류>Exception`. 종류는 HTTP 상태의 뜻이고 상태 하나에 클래스 하나 | `NotFoundException`(404), `ConflictException`(409), `InvalidRequestException`(400) | 관례: [Jakarta REST][jakarta-rs] `NotFoundException` `BadRequestException`, [Camunda][camunda] `NotFoundException`, [spring-guide][spring-guide] `EntityNotFoundException` |
| 전용 예외 | UpperCamelCase | `<대상><종류>Exception`. 서비스가 타입으로 잡아야 할 때만 종류 클래스 아래 둔다 | `CustomerNotFoundException` | 관례: JDK `FileNotFoundException`, [Fineract][fineract] `ClientNotFoundException` |
| 전역 핸들러 | UpperCamelCase | `GlobalExceptionHandler` 하나. 컨트롤러별 짝 핸들러는 없다 | `GlobalExceptionHandler` | 관례: [spring-guide][spring-guide] `GlobalExceptionHandler`. 접미사는 Spring [`ResponseEntityExceptionHandler`][sf-error-responses]와 같은 형 |
| 핸들러 메서드 | lowerCamelCase | `handle<예외 클래스 단순명>`. `Exception`을 떼지 않는다 | `handleBusinessException`, `handleMethodArgumentNotValidException`, `handleException` | 관례: [spring-guide][spring-guide] `handleMethodArgumentNotValidException` `handleBusinessException` `handleException`, Spring [`ResponseEntityExceptionHandler`][sf-error-responses]가 최근에 더한 `handleNoResourceFoundException` `handleHandlerMethodValidationException`. 아래 "핸들러 메서드에서 Exception을 떼지 않는 이유" |
| 오류 응답 | UpperCamelCase | `ErrorResponse`. Spring에도 같은 이름이 있다. 아래 "오류 응답의 이름 충돌" | `ErrorResponse` | 관례: [spring-guide][spring-guide] `ErrorResponse` |
| 오류 응답 안 요소 | UpperCamelCase | `ErrorResponse.FieldError`. 응답 안 요소 규칙의 예외. 아래 "오류 응답 안 요소의 예외" | `ErrorResponse.FieldError` | 이 파일 |
| 패키지 역할 | lowercase 한 단어 | `global.error` | `com.meterengine.global.error` | 관례: [spring-guide][spring-guide] `global/error`, [2023-zipgo][zipgo] `common/error`. `exception`이 아닌 이유는 아래 "패키지 역할이 error인 이유" |

- **오류 코드 상수의 낱말 순서.** 대상을 주어로 한 문장이 되는 상태(is not found, already exists, has events)는 대상 뒤에 붙고(`<대상>_<상태>`), 대상을 꾸미는 한 낱말(invalid, unknown, missing)은 대상 앞에 온다(`<형용사>_<대상>`). expired, canceled처럼 둘 다 되는 낱말은 문장형으로 뒤에 둔다(`INVOICE_EXPIRED`, `PAYMENT_ALREADY_CANCELED`). 같은 오류를 예외 클래스로 지었을 때의 낱말 순서와 같다. 대상은 엔티티 이름 그대로이고 약칭하지 않으며(`METRIC` 아니고 `BILLABLE_METRIC`), `_REFERENCE` 같은 덧말을 붙이지 않는다. 근거는 관례: JDK `FileNotFoundException` `UnknownHostException`, [AWS DynamoDB 오류][aws-ddb-errors] `ResourceNotFoundException` `MissingAuthenticationTokenException`, [Slack Web API][slack-errors] `channel_not_found` `invalid_auth` `missing_scope`, [Google API 오류][google-errors] `PERMISSION_DENIED` `RESOURCE_EXHAUSTED`. 꾸미는 낱말이 앞에 오는 꼴은 [토스페이먼츠][toss-errors] `INVALID_CARD_NUMBER` `REQUIRED_AMOUNT`도 같다.
- **오류 응답의 이름 충돌.** Spring에도 `org.springframework.web.ErrorResponse`가 있어 단순명이 같다. 우리 것은 핸들러, 테스트, 그리고 `ErrorResponse.FieldError`를 만드는 서비스가 쓰지만, 한 파일에서 둘을 같이 import하는 자리는 없다. 생기면 Spring 쪽을 패키지까지 적어 부른다.
- **오류 응답 안 요소의 예외.** 응답 안 요소 규칙(`<응답 리소스><JSON 필드명 단수>`)대로면 `ErrorResponseError`인데 뜻이 겹쳐 읽기 어려워 오류 응답만 예외로 둔다. 이름이 Spring `org.springframework.validation.FieldError`와 같은 낱말이라 핸들러가 둘을 한 파일에서 쓴다. 우리 것은 `ErrorResponse.FieldError`로 한정해 부른다.
- **핸들러 메서드에서 Exception을 떼지 않는 이유.** Spring `ResponseEntityExceptionHandler`는 옛 메서드(`handleMethodArgumentNotValid`)는 떼고 최근 메서드(`handleNoResourceFoundException`)는 붙여 섞여 있다. 떼면 `Exception` 자체를 받는 메서드 이름이 비어 예외 조항이 필요하다. 붙이면 클래스 이름이 그대로 들어가 검색되고 예외 조항이 없다.
- **패키지 역할이 error인 이유.** `exception`으로 짓는 곳([prolog][prolog] `common/exception`)도 있으나 이 패키지는 예외 말고 `ErrorCode`와 `ErrorResponse`도 들므로 `error`다.

### 근거

- 근거는 표의 근거 열과 위 불릿에 행마다 있다.

### 검토한 대안

- 상태를 앞에 두는 어순 (`<상태>_<대상>`, [토스페이먼츠][toss-errors] `NOT_FOUND_PAYMENT` `ALREADY_PROCESSED_PAYMENT`): JDK, AWS, Slack, Google이 대상을 앞에 두고, 같은 오류의 예외 클래스 이름(`CustomerNotFoundException`)과 어순이 어긋난다. 토스 안에서도 `PAYOUT_NOT_FOUND`처럼 반대 어순이 섞여 있다.
- 오류 코드 문자열을 UPPER_SNAKE_CASE로 ([Google AIP-193][aip-193], [토스페이먼츠][toss-errors], PortOne, PayPal, Square): 빌링 SaaS(Stripe, Chargebee, Lago, Recurly)와 RFC 6749가 소문자이고, 문자열 열거값이 이미 소문자로 나가 있어 한 API 안에 케이스가 둘이 된다. 국내 PG에 맞추려면 열거값 행까지 같이 뒤집어야 한다.
- 예외를 code마다 클래스로, 또는 클래스 하나로 두는 대안은 `error-handling.md` "예외와 핸들러의 자리"의 검토한 대안에 있다.

[jakarta-rs]: https://jakarta.ee/specifications/restful-ws/4.0/apidocs/jakarta.ws.rs/jakarta/ws/rs/NotFoundException.html
[sf-error-responses]: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html
[gj-5.2.4]: https://google.github.io/styleguide/javaguide.html#s5.2.4-constant-names
[spring-guide]: https://github.com/cheese10yun/spring-guide
[camunda]: https://github.com/camunda/camunda-bpm-platform
[killbill]: https://github.com/killbill/killbill
[fineract]: https://github.com/apache/fineract
[stripe]: https://docs.stripe.com/error-codes
[chargebee]: https://apidocs.chargebee.com/docs/api/error-handling
[lago]: https://getlago.com/docs/api-reference/errors
[rfc6749-5.2]: https://datatracker.ietf.org/doc/html/rfc6749#section-5.2
[aip-193]: https://google.aip.dev/193
[toss-errors]: https://docs.tosspayments.com/reference/error-codes
[slack-errors]: https://api.slack.com/methods/conversations.info#errors
[aws-ddb-errors]: https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Programming.Errors.html
[google-errors]: https://cloud.google.com/apis/design/errors
[zipgo]: https://github.com/woowacourse-teams/2023-zipgo
[prolog]: https://github.com/woowacourse/prolog
