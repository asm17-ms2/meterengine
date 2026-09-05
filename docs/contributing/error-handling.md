# 오류 처리 규칙

오류 응답의 모양은 RFC-005가 정했다. 이 파일은 그 모양을 채울 때 팀원이 따르는 규칙이다. 서버 안 구조(예외 계층, 핸들러 배치)는 코드가 정본이고 이 파일은 자리만 정한다. 이름은 `naming.md` "오류 처리" 절이다.

## code

### 규칙

- 받는 쪽이 이 값으로 무엇을 다르게 하는가로 가른다. 다르게 할 것이 문구뿐이면 가르지 않고 `errors[]`로 어디가 틀렸는지만 준다.
- 상태가 같아도 뜻이 다르면 code가 따로다 (400의 `validation_error`와 `invalid_event`).
- code 하나는 (HTTP 상태, 의미) 하나만 가리킨다.
- 값의 정본은 `ErrorCode` enum이고 목록은 `backend/openapi.yaml`이 낸다. 다른 문서는 목록을 베끼지 않고 그 파일을 가리킨다.
- 새 오류는 `ErrorCode` 상수 하나로 더한다. 상태, code 문자열, 문구를 그 한 줄이 든다.

### 근거

- 문구만 다른 code가 늘면 클라이언트 분기가 늘고 표가 길어진다.
- 목록의 정본이 둘이면 어긋난다. openapi.yaml은 빌드가 다시 만들고 프론트엔드가 읽는다.

### 검토한 대안

- 원인마다 code (`invalid_billable_metric_aggregation`, `..._target_property`): 받는 쪽이 다르게 할 것이 문구뿐이라 `errors[]`로 충분하다.

## 문구

### 규칙

- `message`는 한국어 한 줄이고 code마다 하나다. 던지는 자리는 code만 고른다. 계약은 `code`이고 문구는 편의라 바꿔도 된다.
- 도입사가 보낸 값을 문구에 되비추지 않는다. `message`는 code마다 하나라 구조가 막지만, `errors[].message`는 던지는 자리가 쓰는 문장이라 쓰는 사람이 지킨다. 기대 타입이나 규칙은 우리 것이라 적어도 된다.
- `errors[].message`만 필드별 구체 사유다. 프레임워크 검증은 Bean Validation의 ko 번들 문구, 도메인 검증은 던지는 자리가 넘긴 문구다.
- `messages.properties`에는 `errors[].message`용 `problem.field.*` 키만 둔다. Bean Validation 제약의 문구는 덮어쓰지 않는다.

### 근거

- code마다 문구가 하나여야 문서에 code와 문구가 표로 실리고, 바꿀 때 한 곳만 고친다.
- 보낸 값을 되비추면 로그와 지원 문의에 도입사 데이터가 새고, 문구가 요청마다 달라진다.
- 제약마다 번역을 떠안으면 제약이 늘 때 번역 누락이 조용히 영어로 샌다.

### 검토한 대안

- 던지는 자리마다 문장 (`"SUM aggregation requires target_property"`): 필드 이름이 문장 속에 묻혀 클라이언트가 파싱해야 하고, 도입사가 보낸 값이 문장에 새기 쉽다.
- 문구를 `messages.properties` 키로: 다국어가 필요해지면 그때 한다. 지금은 `spring.web.locale`이 ko 고정이라 enum 한 줄이 더 짧다.

## 예외와 핸들러의 자리

### 규칙

- 오류 처리의 공용 코드는 `com.meterengine.global.error`에 둔다.
- 서비스가 던지는 것은 HTTP 상태별 종류 클래스(`NotFoundException`, `ConflictException`, `InvalidRequestException`)이고 `ErrorCode`를 인자로 넘긴다. `BusinessException`은 부모라 직접 던지지 않는다.
- 필드 오류 목록은 `InvalidRequestException`만 든다. 없음(404)과 충돌(409)은 code만 든다.
- 종류 클래스는 HTTP 상태 하나에 하나다. 인증(401, 403), 속도 제한(429) 같은 새 상태가 생기면 그때 하나 더 둔다.
- 대상별 전용 예외(`CustomerNotFoundException`)는 서비스가 타입으로 잡아야 할 때만 종류 클래스 아래 둔다. 도메인 패키지에 `exception` 패키지를 두지 않는다.
- `@RestControllerAdvice`는 `GlobalExceptionHandler` 하나다. `assignableTypes`와 `@Order`를 쓰지 않는다.
- 프레임워크 예외는 정확한 타입으로 하나씩 나열하고, 부모 타입(`TypeMismatchException` 등)을 적지 않는다. 나열하지 않은 것은 `Exception` 핸들러가 500으로 받는다.
- `DataIntegrityViolationException`은 핸들러에서 잡지 않는다. 뜻을 아는 서비스가 저장 전 조회로 거르거나 잡아서 종류 클래스로 바꿔 던진다.
- 테스트의 예외 단언은 종류 클래스를 잡은 뒤 code를 한 번 더 비교한다.

```
com.meterengine.global.error
├── ErrorCode                  enum. 상태, code, 문구
├── BusinessException          부모. 직접 던지지 않는다
├── NotFoundException          404
├── ConflictException          409
├── InvalidRequestException    400. 필드 오류 목록을 든다
├── GlobalExceptionHandler     @RestControllerAdvice 하나
└── ErrorResponse              응답 record. code, message, errors[]
```

### 근거

- 종류마다 드는 값을 다르게 설계할 수 있다. 잘못된 요청만 필드 오류 목록을 들고, 응답의 `errors[]`가 400에만 붙는 것과 짝이 맞는다. 필드 오류 목록은 검증 실패(400)에만 싣는 것이 [Google 오류 상세][google-error-details], [GitHub][github-errors]의 모양이다.
- 없음(404)과 충돌(409)은 어느 자원인지를 요청이 이미 말한다. 삭제 충돌은 경로의 식별자, 중복 충돌은 본문의 그 값이다. [토스페이먼츠][toss-errors]의 `ALREADY_PROCESSED_PAYMENT`, [Slack][slack-errors]의 `channel_not_found`가 code와 문구만 낸다.
- advice가 하나면 순서와 범위를 생각할 일이 없다.
- 부모 타입을 적으면 그 자식인 `ConversionNotSupportedException`(서버 설정 오류, 500)까지 같은 핸들러로 들어와 500이 400으로 둔갑한다.
- `DataIntegrityViolationException`은 FK 없음, jsonb 거부, PK 중복이 전부 한 예외로 오고 어느 것인지는 던진 자리만 안다. 핸들러가 받으면 컨트롤러마다 advice와 순서가 는다.
- 종류 클래스 방식은 [Camunda][camunda], [Kill Bill][killbill], [JHipster][jhipster] 생성 코드, [Baeldung][baeldung] 예제의 모양이다.

### 검토한 대안

- code마다 예외 클래스 (`CustomerNotFoundException`, 지금까지의 코드): 클래스가 code 수만큼 늘고, 프레임워크와 부트캠프 코드의 모양이지 코드가 보이는 SaaS 제품에서 끝까지 지키는 곳이 없다. [ThingsBoard][thingsboard]와 [Keycloak][keycloak]은 예외 하나에 code를 인자로 들고, [Camunda][camunda]와 [Kill Bill][killbill]은 종류마다 클래스를 둔다.
- 클래스 하나에 `ErrorCode`만 인자 (`BusinessException(ErrorCode)`, Spring `ResponseStatusException` 꼴): 타입으로 전혀 못 잡고, 필드 오류 목록을 실으려면 모든 예외가 쓰지 않는 값을 든다.
- 컨트롤러별 advice + `ResponseEntityExceptionHandler` 상속 (지금까지의 코드): 프레임워크 예외 20종과 405의 `Allow`, 415의 `Accept` 헤더를 공짜로 받지만, 도메인 예외와 프레임워크 예외가 다른 길을 타서 두 구조를 같이 유지한다. 나열 방식은 Spring이 새 4xx 예외를 더하면 500으로 떨어지지만, 도달 가능한 4xx가 실측으로 열거돼 있어 지금은 빠지는 것이 없다.
- `DataIntegrityViolationException`을 컨트롤러 범위 advice에서 잡기: advice가 컨트롤러 수만큼 늘고 한 컨트롤러 안에서 뜻이 둘 이상이면 가를 수 없다.

## 이 파일의 근거

- RFC-005가 정한 오류 응답의 모양은 되돌리면 도입사와 프론트엔드 계약이 같이 움직이지만, 이 파일의 규칙은 행 하나를 고치면 되돌아간다. governance.md의 기준대로 둘을 가른다.

[baeldung]: https://www.baeldung.com/exception-handling-for-rest-with-spring
[camunda]: https://github.com/camunda/camunda-bpm-platform
[killbill]: https://github.com/killbill/killbill
[jhipster]: https://github.com/jhipster/generator-jhipster
[thingsboard]: https://github.com/thingsboard/thingsboard
[keycloak]: https://github.com/keycloak/keycloak
[toss-errors]: https://docs.tosspayments.com/reference/error-codes
[google-error-details]: https://github.com/googleapis/googleapis/blob/master/google/rpc/error_details.proto
[github-errors]: https://docs.github.com/en/rest/using-the-rest-api/troubleshooting-the-rest-api
[slack-errors]: https://api.slack.com/methods/conversations.info#errors
