# backend

미터링 엔진 API 서버. 이벤트 수집, 집계, rating, 인보이스 생성을 담당한다.

## 기술 스택

- Java 25 + Spring Boot 4.1 + Gradle Kotlin DSL. 버전은 `gradle/libs.versions.toml`에서 관리한다
- PostgreSQL 단일 저장소, DB 접근은 Spring Data JPA. 집계는 사전 집계 없이 SQL로 계산한다
- 스키마 마이그레이션: Flyway. 마이그레이션은 `src/main/resources/db/migration/`에 있고 기동 때 자동 적용된다. 현재 다섯 개다
  - `V1__create_initial_tables.sql` - organization, billable_metric, customer, usage_event 네 테이블
  - `V2__split_price_policy_from_billable_metric.sql` - 미터의 unit_price를 price_policy(가격 정책)와 price_rate(단가)로 분리 (MS2-158). 다차원 가격 대비 형태지만 이번 슬라이스는 전부 무차원('{}')이다
  - `V3__add_customer_created_at.sql` - customer에 등록 시각 `created_at` 추가 (MS2-171). 새 행은 DB가 `clock_timestamp()`로 채운다. **이미 있던 행은 마이그레이션 시각 하나를 나눠 받았고 그 값은 실제 등록 시각이 아니다** (등록 시각을 기록하기 전에 만들어진 행이라 그 사실이 남아 있지 않다. 값이 전부 같다는 것이 백필 표식이다). API로는 이 값을 보낼 통로가 없고, raw SQL이 값을 실어 보내면 그대로 저장된다 - `usage_event.received_at`과 달리 덮어쓰는 트리거를 두지 않았다 (사유는 파일 주석에 있다)
  - `V4__collate_names_for_korean.sql` - 고객, 도입사, 미터의 이름 컬럼에 ICU 한국어(ko-KR) collation을 지정 (MS2-143). 정렬을 DB가 하는데 DB 기본 collation이 en_US.utf8이라 고객 목록이 한국어 사전순이 아니었다. 컬럼 레벨이라 볼륨을 지우지 않아도 적용된다
  - `R__seed.sql` - 시드 데이터. 반복 마이그레이션이라 파일 내용이 곧 상태다 (체크섬이 바뀌면 다시 적용된다). 미터 등록 API가 없어서(MS2-159 예정) 지금은 고객 API(MS2-155)와 가격 정책 API(MS2-157)를 빼면 데이터가 들어오는 통로가 이 파일뿐이다. 미터는 여섯 개이고 그중 `llm_request` 이벤트 하나를 입력/출력/캐시 읽기/캐시 생성 토큰 네 미터가 함께 잰다. 캐시 두 미터는 MS2-169에서 추가했는데, Claude Code 실측에서 토큰의 대부분이 캐시라 그것을 빼면 청구 예정액이 몇십 원에 그쳐 화면에서 확인할 것이 없었다 (단가 근거는 파일 주석에 있다)
- 엔티티가 스키마를 만들지 않는다. `spring.jpa.hibernate.ddl-auto=validate`라 기동 때 엔티티와 실제 테이블이 어긋났는지 확인만 한다
- API 명세: `openapi.yaml`(구현에서 자동 생성, 아래 "API 문서" 참조). 손으로 쓰는 명세는 없고, 이 파일이 계약의 정본이다 (`docs/document-rules.md`)
- 오류 응답: RFC 9457 problem+json 하나로 통일하고 `code` 확장 멤버로 종류를 고른다. 도입사가 읽는 문구는 한국어 고정이다 (아래 "오류 응답" 참조)
- API 문서 UI: Scalar. 앱을 띄우면 `/scalar`에 뜬다. 원본 문서는 `/v3/api-docs`(JSON)와 `/v3/api-docs.yaml`이다. Swagger UI는 쓰지 않는다. 두 UI가 같은 문서를 보여줄 이유가 없어 `springdoc-openapi-starter-webmvc-ui` 대신 `-scalar`를 쓴다. 렌더링 JS가 jar에 번들되어 앱이 직접 서빙하므로 CDN을 타지 않고 버전이 의존성에 고정된다
- 테스트: JUnit 5 + AssertJ + Testcontainers. DB가 필요한 테스트는 실제 PostgreSQL 컨테이너로 돌린다
- 코드 포맷: Spotless + google-java-format. CI에서 검사한다

## 실행

Docker Desktop(Compose 포함)과 JDK 25가 필요하다.

```
./gradlew bootRun
```

레포 루트의 `docker-compose.yml`에 정의된 PostgreSQL을 자동으로 띄우고 서버를 시작한다 (spring-boot-docker-compose). IDE에서 main 클래스를 직접 실행해도 되고, working directory가 backend/든 레포 루트든 동작한다 (`backend/compose.yaml`은 루트 정의를 가리키는 include 심이다).

## 빌드와 테스트

```
./gradlew build          # 컴파일 + 포맷 검사 + 테스트 + OpenAPI 생성물 (Docker 필요)
./gradlew spotlessApply  # 포맷 자동 적용
```

## 컨테이너 이미지

배포용 실행 이미지는 `Dockerfile`이 만든다 (MS2-161). 멀티 스테이지라 실행 이미지에는
JRE와 jar만 들어간다. 테스트는 CI가 돌리므로 이미지 빌드에서는 실행하지 않는다
(Testcontainers가 Docker 데몬을 요구하는데 빌드 안에는 데몬이 없다).

```
docker build -t meterengine-backend .
```

**DB 접속은 이미지에 굽지 않고 런타임 환경변수로 받는다.** `application.properties`에
`spring.datasource.*`가 한 줄도 없는 이유다. 로컬은 spring-boot-docker-compose가 커넥션을
만들어 주지만 그건 developmentOnly라 jar에 들어가지 않는다. 즉 운영에서 DB에 붙는 유일한
경로가 아래 세 변수다.

| 환경변수 | 예 |
| --- | --- |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://<host>:5432/meterengine` |
| `SPRING_DATASOURCE_USERNAME` | `meterengine` |
| `SPRING_DATASOURCE_PASSWORD` | (SSM Parameter Store SecureString) |

운영에서 이 값을 주입하는 것은 `deploy/compose.prod.yml`이고, 값은 Parameter Store에서
온다. 절차는 `deploy/README.md`에 있다.

## API 문서

`openapi.yaml`이 API 계약의 정본이다. 컨트롤러와 DTO에서 자동 생성되므로 손으로 고치지 않는다.

현재 오퍼레이션은 아홉이다. 파라미터, 응답 스키마, 오류 코드는 `openapi.yaml`을 본다.

| 오퍼레이션 | 내용 |
| --- | --- |
| `GET /v1/customers` | 고객 목록. 이름 오름차순, 페이지 나누지 않음 |
| `POST /v1/customers` | 고객 등록. 서버가 customer_id와 등록 시각을 만든다 |
| `PUT /v1/customers/{id}` | 고객 이름 수정 |
| `DELETE /v1/customers/{id}` | 고객 삭제. 이벤트가 있으면 409로 거절 |
| `POST /v1/events` | 사용량 이벤트 수집. transaction_id 기준 멱등(first-write-wins) |
| `GET /v1/events` | 이벤트 조회. 월/고객/event_type 필터, 페이지 나누기 |
| `GET /v1/usage` | 고객별 월 사용량 집계 |
| `GET /v1/invoice` | 고객별 청구 예정액 (draft) |
| `POST /v1/metrics/{metricCode}/price-policy` | 가격 정책 등록. 축 선언만 받고 미터당 1개(중복 409). 단가는 MS2-177의 단가 API 몫이고, 단가 없는 미터는 청구 예정액 라인에서 빠진다 |

전부 도입사를 `X-Organization-Id` 헤더로 받는다. 인증이 아직 없어서 쓰는 임시 방식이다.

고객 삭제는 행을 실제로 지운다 (MS2-155). 지워도 되는 고객이 곧 이벤트가 하나도 없는 고객이라 남길 것이 없어서다. 이벤트가 있는 고객을 지우려 하면 409이고, 그 규칙은 앱이 아니라 `usage_event`의 복합 FK가 강제한다.

**컨트롤러나 DTO를 건드렸으면 `openapi.yaml`을 같은 커밋에 넣는다.** `./gradlew build`가 다시 만들어 주니, 빌드 후 `git status`에 이 파일이 떴으면 계약이 바뀐 것이다. 프론트엔드는 백엔드를 띄우지 않고 이 파일로 계약을 읽는다.

생성은 `OpenApiDocumentTest`가 한다. springdoc은 코드를 정적으로 분석하지 않아 앱이 떠 있어야 문서를 만들 수 있고, 그래서 생성 자리가 테스트다. 앱을 띄운 상태에서는 같은 문서를 `/scalar`(UI), `/v3/api-docs`(JSON), `/v3/api-docs.yaml`에서 볼 수 있다.

**CI는 이 파일을 검사하지 않는다.** 커밋된 생성물과 다시 만든 것을 비교해 실패시키는 스텝을 두지 않았다. 문서를 바꾸는 변경 중에 `@Parameter` 문구 수정처럼 알아채기 어려운 것이 많고, springdoc이나 Spring Boot 버전을 올려도 출력이 통째로 달라질 수 있어서, 검사를 넣으면 백엔드를 만지는 PR이 납득하기 어려운 이유로 빨개진다. 대신 생성을 빌드에 붙여 `git status`에 뜨게 했다.

**생성 자체는 CI에서도 돈다.** backend job이 `./gradlew build`를 돌리기 때문이다. 그래서 애노테이션이 잘못돼 문서 생성이 깨지면 CI가 잡는다. 잡지 않는 것은 "커밋된 파일이 낡았는지"뿐이다.

그래서 **갱신을 빠뜨리면 아무것도 실패하지 않는다.** 다음 둘 중 하나가 나오면 CI 검사를 다시 논의한다 (MS2-140).

- 프론트엔드가 이 파일과 실제 응답이 다르다고 보고한다
- PR 리뷰에서 생성물 누락을 지적한 일이 두 번 나온다

## 오류 응답

오류는 형식 하나로 나간다. RFC 9457 problem+json에 `code` 확장 멤버를 얹은 것이고, 스키마는 `openapi.yaml`의 `ProblemResponse`다 (MS2-150).

```json
{
  "title": "Bad Request",
  "status": 400,
  "detail": "the request could not be accepted as sent",
  "instance": "/v1/events",
  "code": "validation_error",
  "errors": [{ "field": "event_type", "message": "공백일 수 없습니다" }]
}
```

- `code`가 기계 판독용이다. 화면 문구는 이 값으로 고른다. `title`과 `detail`은 영어이고 로그와 개발자용이라 그대로 띄우지 않는다
- `errors`는 `code=validation_error`일 때만 실린다. `field`는 도입사가 보낸 이름이다 (자바 필드명이 아니다)
- `type`은 나가지 않는다. 값이 기본값 `about:blank`라 직렬화에서 빠지고, 우리가 `setType`을 부르는 곳이 없다
- **5xx는 본문 형식을 약속하지 않는다.** 일부 5xx가 problem+json으로, 일부가 Boot 기본 형식으로 나가는데 클라이언트가 둘을 구분할 방법이 없다. 5xx에는 `code`를 붙이지 않는다. 클라이언트는 상태 코드만 보고, `code`가 없을 때 쓸 기본 문구를 갖고 있어야 한다

### code 목록

값의 정본은 `ErrorCodes`다. **code를 늘리거나 없앨 때 고치는 곳은 그 파일 하나다.** 운영 코드, 문서 스키마의 `allowableValues`, 통합 테스트 단언이 전부 이 상수를 거친다. 예외가 하나 있다. 테스트 **메서드 이름**에 code가 박힌 자리(`...code가_unknown_customer_reference다` 같은)는 자바 식별자라 상수를 거치지 못하므로, code를 개명하면 컴파일도 테스트도 통과한 채 이름만 옛 값으로 남는다. 개명할 때 메서드 이름을 grep으로 찾아 손으로 같이 고친다.

| code | 상태 | 언제 |
| --- | --- | --- |
| `validation_error` | 400 | 헤더 누락, 쿼리 파라미터 타입 불일치, 본문 필드 제약 위반 |
| `unknown_customer_reference` | 400 | 실어 보낸 `customer_id`로 (도입사, 고객) 조합을 찾지 못했다 |
| `unknown_organization` | 400 | `X-Organization-Id`가 등록된 도입사가 아니다 (고객 등록에서만 난다) |
| `invalid_event` | 400 | DB가 저장을 거부했다. 같은 본문을 다시 보내도 성공하지 않는다 |
| `malformed_request_body` | 400 | 본문을 JSON으로 읽지 못했다 (깨진 JSON, 빈 본문, 오프셋 없는 timestamp) |
| `invalid_price_policy` | 400 | 본문이 가격 정책으로 성립하지 않는다 (선언의 중복 키, 빈 키) |
| `customer_not_found` | 404 | 경로가 가리킨 고객이 없거나 다른 도입사 소속이다 |
| `metric_not_found` | 404 | 경로가 가리킨 미터가 없거나 다른 도입사 소속이다 |
| `endpoint_not_found` | 404 | 그 경로에 대응하는 엔드포인트가 없다 |
| `customer_has_events` | 409 | 사용량 이벤트가 있어 고객을 지울 수 없다 |
| `price_policy_already_exists` | 409 | 그 미터에 가격 정책이 이미 있다 |
| `method_not_allowed` | 405 | 경로는 있고 HTTP 메서드가 틀렸다 |
| `response_type_not_acceptable` | 406 | `Accept`로 만족시킬 응답 표현이 없다 |
| `request_type_not_supported` | 415 | 보낸 `Content-Type`을 받을 수 없다 |

**code 하나는 (HTTP 상태, 의미) 하나만 가리킨다.** 그래서 옛 이름 `customer_not_found`를 `unknown_customer_reference`로 개명했다. 그 오류는 주소(`/v1/events`)가 아니라 실어 보낸 값이 잘못된 경우라 404가 아니라 400이다. 옛 이름은 MS2-155가 가져갔다. `PUT`/`DELETE /v1/customers/{id}`가 가리킨 고객이 없을 때의 404다. 단건 조회(`GET /v1/customers/{id}`)는 만들지 않았다. 고객이 가진 정보가 목록에 이미 다 들어 있어서다.

### 문구의 언어

도입사가 읽는 자리는 `errors[].message` 하나다. 여기만 한국어이고 `title`과 `detail`은 영어로 둔다. 로그와 지원 문의에서 검색 가능한 고정 문자열이 낫기 때문이다.

`spring.web.locale=ko`와 `spring.web.locale-resolver=fixed`로 못박아서 `Accept-Language`가 무엇이든 한국어가 나간다. 리졸버를 열어 두면 한 응답 안에 두 언어가 섞인다. Hibernate Validator는 en 번들을 갖고 있어 "must not be blank"로 답하는데 우리 문구는 ko 하나뿐이라 한국어가 그대로 나가기 때문이다. 다국어가 필요해지면 번들을 갖추고 그때 연다.

우리가 만드는 문구는 `src/main/resources/messages.properties`에 있고 넷뿐이다. 헤더 누락과 타입 불일치처럼 Bean Validation이 문구를 만들지 않는 자리만 여기 둔다. `@NotBlank` 같은 제약의 문구는 Hibernate Validator의 ko 번들에 맡긴다. 제약이 늘 때마다 번역을 떠안으면 누락이 조용히 영어로 새기 때문이다.

### 예외 핸들러 넷

| 클래스 | 걸리는 범위 | 잡는 것 |
| --- | --- | --- |
| `FrameworkExceptionHandler` (루트) | 전역 | 프레임워크가 내는 예외 20종. 본문이 나가는 19종이 `handleExceptionInternal` 한 자리를 지나므로 거기서 4xx에만 `code`를 얹는다. 상태 코드와 응답 헤더(405의 `Allow`, 415의 `Accept`) 결정은 프레임워크에 남긴다 |
| `EventExceptionHandler` (`event.controller`) | `EventController`만 | 도메인 오류 둘. `UnknownCustomerException` -> `unknown_customer_reference`, `DataIntegrityViolationException` -> `invalid_event` |
| `CustomerExceptionHandler` (`customer.controller`) | `CustomerController`만 | 도메인 오류 셋. `CustomerNotFoundException` -> `customer_not_found`, `CustomerHasEventsException` -> `customer_has_events`, `DataIntegrityViolationException` -> `unknown_organization` |
| `PricePolicyExceptionHandler` (`pricing.controller`) | `PricePolicyController`만 | 도메인 오류 셋. `MetricNotFoundException` -> `metric_not_found`, `PricePolicyAlreadyExistsException` -> `price_policy_already_exists`, `InvalidPricePolicyException` -> `invalid_price_policy` |

도메인 advice 셋을 각자 한 컨트롤러에만 건 이유는 event와 customer 쪽이 둘 다 잡는 `DataIntegrityViolationException`이 제약 위반 전반을 덮는 넓은 타입이라서다. 전역에 걸면 관계없는 제약 위반까지 "보낸 이벤트가 잘못됐다"거나 "도입사가 등록되지 않았다"로 둔갑한다. 같은 예외가 두 곳에서 다른 뜻인 것이 범위를 좁혀야 하는 이유다. pricing advice는 그 예외를 잡지 않지만(잡을 도달 가능한 경우가 없다, `PricePolicyExceptionHandler` 주석 참조) 같은 원칙으로 범위를 좁혀 둔다.

고객 삭제에서 나는 FK 위반은 advice까지 가지 않는다. `CustomerService.delete`가 `CustomerHasEventsException`으로 바꿔 던져 409가 된다. 그 자리가 뜻을 아는 유일한 곳이라서다.

**[주의] `ResponseEntityExceptionHandler`를 상속하는 클래스를 또 만들지 않는다.** Boot 자동 설정이 `@ConditionalOnMissingBean(ResponseEntityExceptionHandler.class)`라, 그 타입의 빈이 둘이면 하나만 등록되고 프레임워크 예외 처리의 절반이 조용히 사라진다. 그 자리는 `FrameworkExceptionHandler`가 의도적으로 차지하고 있다. 새 오류를 붙이려면 그 클래스를 고치거나, 도메인 예외를 잡는 별도 advice(상속 없는 `@RestControllerAdvice`)를 쓴다.

프레임워크 4xx까지 같은 형식으로 끌어오는 스위치가 `spring.mvc.problemdetails.enabled=true`다. 끄면 한 엔드포인트가 400을 두 스키마로 낸다.

## 구조

단일 Gradle 모듈이다. `com.meterengine` 아래 도메인 패키지 다섯을 두고, 도메인 안은 종류별 하위 패키지(controller, service, repository, dto, 필요하면 entity, exception)로 나눈다 (MS2-149).

- `event`: 사용량 이벤트 수집과 조회 (`/v1/events`). 클래스 이름은 Event 접두어로 통일한다
- `metric`: 과금 지표와 고객별 월 사용량 집계 (`/v1/usage`)
- `invoice`: 청구 예정액 조회 (`/v1/invoice`)
- `pricing`: 가격 정책과 단가 (`/v1/metrics/{metricCode}/price-policy`). MS2-158에서 미터의 unit_price를 분리했고 MS2-157이 정책 등록 API를 얹었다. 조회는 MS2-176, 단가 등록/수정/삭제는 MS2-177 예정이다
- `customer`: 고객 등록/수정/삭제와 조회 (`/v1/customers`). event, metric, invoice가 공통으로 쓰는 아래층이다
- 도메인 어디에도 속하지 않는 것은 루트(`com.meterengine`)에 둔다. 부트스트랩(`MeterEngineApplication`), 설정(`OpenApiConfig`), 오류 계약(`ErrorCodes`, `ProblemMembers`, `ProblemResponse`, `ProblemFieldError`, `FrameworkExceptionHandler`)이다. 오류 계약을 한 도메인에 두면 나머지 도메인이 그 도메인을 import하게 된다

경계는 코드 리뷰로 지킨다. 다른 패키지가 쓰는 것만 public으로 열고 나머지는 package-private을 유지한다. 도메인 사이 의존은 여덟이다.

- `event` -> `customer` (고객 판정), `event` -> `metric` (청구 월 경계 계산 공유)
- `invoice` -> `customer` (고객 조회), `invoice` -> `metric` (집계 호출), `invoice` -> `pricing` (단가 조회)
- `metric` -> `customer` (고객 조회)
- `customer` -> `event` (고객 삭제 전 이벤트 유무 확인, MS2-155)
- `pricing` -> `metric` (정책 등록 전 미터 존재 확인, MS2-157)

`customer`가 아래층이고 event, metric, invoice가 그것을 쓴다. 역방향은 `customer` -> `event` 하나뿐인데, 이 때문에 event와 customer는 서로를 참조한다. 수용한 이유와 방향을 되돌리는 방법은 `CustomerService`의 클래스 주석에 있다.
