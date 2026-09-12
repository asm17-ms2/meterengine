# 오류 처리

- 오류 응답의 모양은 RFC-004가 정했다. 이 파일은 그 모양을 채우는 규칙이다.
  - 예외 계층과 핸들러 배치는 코드가 정본이다. 이름은 `naming.md` "오류 처리".

## code

- 받는 쪽이 이 값으로 무엇을 다르게 하는가로 가른다. 문구만 다르면 가르지 않고 `errors[]`로 어디가 틀렸는지만 준다.
- 상태가 같아도 뜻이 다르면 code가 따로다. 예: 400의 `validation_error`와 `invalid_event`.
- code 하나는 (HTTP 상태, 의미) 하나만 가리킨다.
- 값의 정본은 `ErrorCode` enum이고 목록은 `backend/openapi.yaml`이 낸다. 다른 문서는 베끼지 않고 가리킨다.
- 새 오류는 `ErrorCode` 상수 하나로 더한다. 상태, code 문자열, 문구를 그 한 줄이 든다.

## 문구

- `message`는 한국어 한 줄이고 code마다 하나다. 던지는 자리는 code만 고른다.
  - 계약은 `code`다. 문구는 바꿔도 된다.
- 도입사가 보낸 값을 문구에 되비추지 않는다. 기대 타입이나 규칙은 적어도 된다.
- 필드별 사유는 `errors[].message`에만 둔다.
  - 프레임워크 검증은 Bean Validation의 ko 번들 문구, 도메인 검증은 던지는 자리가 넘긴 문구다.
  - 우리가 만드는 문구는 `GlobalExceptionHandler`의 상수다. Bean Validation이 문구를 만들지 않는 자리(헤더 누락, 타입 불일치)만 둔다.

## 예외와 핸들러의 자리

- 공용 코드는 `com.meterengine.global.error`에 둔다.
- 서비스는 HTTP 상태별 종류 클래스(`NotFoundException`, `ConflictException`, `InvalidRequestException`)에 `ErrorCode`를 넘겨 던진다.
  - 부모 `BusinessException`은 직접 던지지 않는다.
  - 필드 오류 목록은 `InvalidRequestException`만 든다.
  - 종류 클래스는 HTTP 상태 하나에 하나다. 새 상태(401, 403, 429)가 생기면 그때 더한다.
  - 대상별 전용 예외(`CustomerNotFoundException`)는 서비스가 타입으로 잡아야 할 때만 둔다. 도메인 패키지에 `exception` 패키지를 두지 않는다.
- `@RestControllerAdvice`는 `GlobalExceptionHandler` 하나다. `assignableTypes`와 `@Order`를 쓰지 않는다.
  - 프레임워크 예외는 정확한 타입으로 하나씩 나열한다. 나열하지 않은 것은 `Exception` 핸들러가 500으로 받는다.
  - `DataIntegrityViolationException`은 핸들러에서 잡지 않는다. 서비스가 저장 전 조회로 거르거나 잡아서 종류 클래스로 바꿔 던진다.
- 테스트는 종류 클래스를 잡은 뒤 code를 비교한다.
- 오류 응답의 모양은 통합 테스트가 실제 응답 본문으로 검증한다. 생성된 OpenAPI 문서는 실제 응답과 다를 수 있다.

