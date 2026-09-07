# backend

미터링 엔진 API 서버. 이벤트 수집, 집계, rating, 인보이스 생성을 담당한다.

## 기술 스택

- Java 25 + Spring Boot 4.1 + Gradle Kotlin DSL. 버전은 `gradle/libs.versions.toml`에서 관리한다
- PostgreSQL 단일 저장소, DB 접근은 Spring Data JPA. 집계는 사전 집계 없이 SQL로 계산한다
- 스키마 마이그레이션: Flyway. 마이그레이션은 `src/main/resources/db/migration/`에 있고 기동 때 자동 적용된다
  - `V1__create_initial_tables.sql` - organization, billable_metric, customer, usage_event 테이블
  - `V2__split_price_policy_from_billable_metric.sql` - 미터의 unit_price를 price_policy(가격 정책)와 price_rate(단가)로 분리 (MS2-158). 다차원 가격 대비 형태지만 이번 슬라이스는 전부 무차원('{}')이다
  - `V3__add_customer_created_at.sql` - customer에 등록 시각 `created_at` 추가 (MS2-171). 새 행은 DB가 `clock_timestamp()`로 채운다. **이미 있던 행은 마이그레이션 시각 하나를 나눠 받았고 그 값은 실제 등록 시각이 아니다** (등록 시각을 기록하기 전에 만들어진 행이라 그 사실이 남아 있지 않다. 값이 전부 같다는 것이 백필 표식이다). API로는 이 값을 보낼 통로가 없고, raw SQL이 값을 실어 보내면 그대로 저장된다 - `event.received_at`과 달리 덮어쓰는 트리거를 두지 않았다 (사유는 파일 주석에 있다)
  - `V4__collate_names_for_korean.sql` - 고객, 도입사, 미터의 이름 컬럼에 ICU 한국어(ko-KR) collation을 지정 (MS2-143). 정렬을 DB가 하는데 DB 기본 collation이 en_US.utf8이라 고객 목록이 한국어 사전순이 아니었다. 컬럼 레벨이라 볼륨을 지우지 않아도 적용된다
  - `V5__create_invoice_tables.sql` - 확정 인보이스와 인보이스 라인 두 테이블. 고객 x 달로 한 장을 강제하고, 라인은 인보이스 안에서 미터와 단가 조합으로 유일하다. 확정본을 되돌리는 수단은 두지 않았고, 확정된 행을 지우거나 고치는 것을 DB가 막지는 않는다
  - `V6__rename_metric_code_to_billable_metric_code.sql` - price_policy, price_rate, invoice_line의 `metric_code`를 `billable_metric_code`로 개명. 다른 테이블을 가리키는 컬럼은 참조 테이블 이름을 붙인다는 이름 규칙(RFC-001)을 따른 것이다
  - `V7__rename_usage_event_to_event.sql` - `usage_event` 테이블을 `event`로, 그 테이블의 `event_type` 컬럼을 `type`으로 개명. PK, FK, NOT NULL 제약, 트리거, 함수 이름도 `usage_event_` 접두어를 `event_`로 맞췄다. 테이블 이름은 엔티티명과 같게 하고 같은 정보를 이름에 두 번 넣지 않는다는 이름 규칙(RFC-001)을 따른 것이고, 코드 쪽 이름이 이미 `Event`이고 와이어 키가 `type`이라 테이블을 그쪽에 맞췄다. `billable_metric.event_type`은 이 컬럼을 가리키는 참조 컬럼이라 그대로다
  - `R__seed.sql` - 시드 데이터. 반복 마이그레이션이라 파일 내용이 곧 상태다 (체크섬이 바뀌면 다시 적용된다). 고객, 미터, 가격 정책은 API로도 들어오지만 데모가 쓰는 도입사와 고객과 미터는 이 파일이 정한다. `llm_request` 이벤트 하나를 입력/출력/캐시 읽기/캐시 생성 토큰 미터가 함께 잰다. 캐시 미터는 MS2-169에서 추가했는데, Claude Code 실측에서 토큰의 대부분이 캐시라 그것을 빼면 청구 예정액이 몇십 원에 그쳐 화면에서 확인할 것이 없었다 (단가 근거는 파일 주석에 있다)
- 엔티티가 스키마를 만들지 않는다. `spring.jpa.hibernate.ddl-auto=validate`라 기동 때 엔티티와 실제 테이블이 어긋났는지 확인만 한다
- API 명세: `openapi.yaml`(구현에서 자동 생성, 아래 "API 문서" 참조). 손으로 쓰는 명세는 없고, 이 파일이 계약의 정본이다 (CONTRIBUTING.md "문서의 정본")
- 오류 응답: `code`, `message`, `errors[]`를 든 자체 스키마 하나로 통일한다. 도입사가 읽는 문구는 한국어 고정이다 (아래 "오류 응답" 참조)
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

## 외부 서비스 키

| 프로퍼티 | 환경변수 | 운영 값의 출처 |
| --- | --- | --- |
| `tosspayments.secret-key` | `TOSSPAYMENTS_SECRET_KEY` | SSM Parameter Store `/meterengine/prod/tosspayments-secret-key` |

운영에서 값이 흐르는 경로는 이렇다. Parameter Store에 넣은 이름이 그대로 환경변수가 되고
(`tosspayments-secret-key` -> `TOSSPAYMENTS_SECRET_KEY`), 그것을 Spring이 완화 바인딩으로
`tosspayments.secret-key`에 꽂는다. 중간에 이름을 갈아끼우는 곳이 없다. 등록 방법은
`deploy/README.md`의 "Parameter Store"와 "토스페이먼츠"에 있다.

`TossPaymentsProperties`가 `@NotBlank`라 값이 비면 기동 자체가 실패한다. 배포 스크립트가
`/actuator/health`를 기다리므로 **CD가 초록불이면 키가 실제로 전달됐다는 뜻**이다. 노출된 actuator
엔드포인트가 health와 prometheus뿐이라 밖에서 설정값을 들여다볼 방법이 없는데, 이 방식은 확인 절차를
따로 두지 않고도 같은 것을 보증한다.

`application.properties`의 기본값은 **자리표시자다.** 실제 키가 아니라서 이 값으로 토스페이먼츠
API를 부르면 거절당한다. 하는 일은 로컬과 CI가 아무 설정 없이 기동되게 하는 것뿐이고, 운영에서는
compose가 `:?`로 주입을 강제하므로 쓰일 일이 없다.

**실제 키를 기본값으로 두지 않는다.** 토스페이먼츠는 클라이언트 키와 시크릿 키가 같은 상점의 짝이어야
하는데 이 레포에는 클라이언트 키가 없다. 시크릿 키만 박아 두면 나중에 그 값이 낡았을 때 개발자가 받는
신호가 `UNAUTHORIZED_KEY` 하나뿐이라, 자기가 복사해 온 클라이언트 키를 의심하며 헤매게 된다.

로컬에서 실제로 결제를 시험하려면 `TOSSPAYMENTS_SECRET_KEY`를 환경변수로 주고, 클라이언트 키도 같은
상점의 것으로 함께 갖춘다. 빌링키 발급은 브라우저에서 클라이언트 키로 카드를 등록하는 단계부터
시작하므로 시크릿 키만으로는 시작할 수 없다. 어느 상점을 쓰는지는 `deploy/README.md`의 "토스페이먼츠"에
있다.

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

현재 오퍼레이션은 아래와 같다. 파라미터, 응답 스키마, 오류 코드는 `openapi.yaml`을 본다.

| 오퍼레이션 | 내용 |
| --- | --- |
| `GET /v1/customers` | 고객 목록. 이름 오름차순, 페이지 나누지 않음 |
| `POST /v1/customers` | 고객 등록. 서버가 id와 등록 시각을 만든다 |
| `PUT /v1/customers/{id}` | 고객 이름 수정 |
| `DELETE /v1/customers/{id}` | 고객 삭제. 이벤트가 있으면 409로 거절 |
| `POST /v1/events` | 사용량 이벤트 수집. transaction_id 기준 멱등(first-write-wins) |
| `GET /v1/events` | 이벤트 조회. 월/고객/type 필터, 페이지 나누기 |
| `GET /v1/usage` | 고객별 월 사용량 집계 |
| `GET /v1/invoices/draft` | 고객별 청구 예정액 (draft) |
| `POST /v1/billable-metrics` | 집계 미터 등록. 집계 함수는 SUM만 받고 target_property가 필수다. 코드는 도입사 안에서 유일(중복 409) |
| `GET /v1/billable-metrics` | 미터 목록. code 오름차순으로 전부, 페이지 나누지 않음 |
| `POST /v1/billable-metrics/{code}/price-policy` | 가격 정책 등록. 축 선언만 받고 미터당 1개(중복 409). 단가 등록 API는 아직 없고, 단가 없는 미터는 청구 예정액 라인에서 빠진다 |
| `GET /v1/price-policies` | 미터별 가격 정책 목록. 미터 code 오름차순, 페이지 나누지 않음. 정책 없는 미터는 dimension_properties가 null이고 무차원 정책은 빈 배열이다. unit_price는 무차원 조합의 기본 단가이며 단가 행이 없으면 null이다 |

전부 도입사를 `X-Organization-Id` 헤더로 받는다. 인증이 아직 없어서 쓰는 임시 방식이다.

고객 삭제는 행을 실제로 지운다 (MS2-155). 지워도 되는 고객이 곧 이벤트가 하나도 없는 고객이라 남길 것이 없어서다. 이벤트가 있는 고객을 지우려 하면 409이고, 그 규칙은 앱이 아니라 `event` 테이블의 복합 FK가 강제한다.

**컨트롤러나 DTO를 건드렸으면 `openapi.yaml`을 같은 커밋에 넣는다.** `./gradlew build`가 다시 만들어 주니, 빌드 후 `git status`에 이 파일이 떴으면 계약이 바뀐 것이다. 프론트엔드는 백엔드를 띄우지 않고 이 파일로 계약을 읽는다.

생성은 `OpenApiDocumentTest`가 한다. springdoc은 코드를 정적으로 분석하지 않아 앱이 떠 있어야 문서를 만들 수 있고, 그래서 생성 자리가 테스트다. 앱을 띄운 상태에서는 같은 문서를 `/scalar`(UI), `/v3/api-docs`(JSON), `/v3/api-docs.yaml`에서 볼 수 있다.

**CI는 이 파일을 검사하지 않는다.** 커밋된 생성물과 다시 만든 것을 비교해 실패시키는 스텝을 두지 않았다. 문서를 바꾸는 변경 중에 `@Parameter` 문구 수정처럼 알아채기 어려운 것이 많고, springdoc이나 Spring Boot 버전을 올려도 출력이 통째로 달라질 수 있어서, 검사를 넣으면 백엔드를 만지는 PR이 납득하기 어려운 이유로 빨개진다. 대신 생성을 빌드에 붙여 `git status`에 뜨게 했다.

**생성 자체는 CI에서도 돈다.** backend job이 `./gradlew build`를 돌리기 때문이다. 그래서 애노테이션이 잘못돼 문서 생성이 깨지면 CI가 잡는다. 잡지 않는 것은 "커밋된 파일이 낡았는지"뿐이다.

그래서 **갱신을 빠뜨리면 아무것도 실패하지 않는다.** 다음 둘 중 하나가 나오면 CI 검사를 다시 논의한다 (MS2-140).

- 프론트엔드가 이 파일과 실제 응답이 다르다고 보고한다
- PR 리뷰에서 생성물 누락을 지적한 일이 두 번 나온다

## 오류 응답

오류는 형식 하나로 나간다. 스키마는 `openapi.yaml`의 `ErrorResponse`이고, 왜 이 모양인지는 `docs/rfcs/004-error-handling.md`, 채울 때 따르는 규칙은 `docs/contributing/error-handling.md`에 있다.

```json
{
  "code": "validation_error",
  "message": "요청 값이 올바르지 않습니다",
  "errors": [{ "field": "transaction_id", "message": "공백일 수 없습니다" }]
}
```

- `code`가 기계 판독용이다. 오류별 처리는 이 값으로 분기한다
- `message`는 code마다 하나인 한국어 문구다. 예고 없이 바뀌므로 분기에 쓰지 않는다
- `errors`는 400에만 실리고 비면 나가지 않는다. `field`는 도입사가 보낸 이름이다 (자바 필드명이 아니다)
- **5xx도 같은 형식이다.** 나열되지 않은 예외는 `code`가 `internal_server_error`인 같은 본문으로 나간다

### code 목록

값의 정본은 `ErrorCode` enum이다. **code를 늘리거나 없앨 때 고치는 곳은 그 파일 하나다.** 운영 코드, `openapi.yaml`의 `enum`, 통합 테스트 단언이 전부 이 상수를 거친다. 예외가 하나 있다. 테스트 **메서드 이름**에 code가 박힌 자리(`...code가_customer_not_found다` 같은)는 자바 식별자라 상수를 거치지 못하므로, code를 개명하면 컴파일도 테스트도 통과한 채 이름만 옛 값으로 남는다. 개명할 때 메서드 이름을 grep으로 찾아 손으로 같이 고친다.

| code | 상태 | 언제 |
| --- | --- | --- |
| `validation_error` | 400 | 헤더 누락, 쿼리 파라미터 타입 불일치, 본문 필드 제약 위반 |
| `malformed_request_body` | 400 | 본문을 JSON으로 읽지 못했다 (깨진 JSON, 빈 본문, 오프셋 없는 timestamp) |
| `unknown_organization` | 400 | `X-Organization-Id`가 등록된 도입사가 아니다 (고객 등록과 미터 등록에서 난다) |
| `invalid_event` | 400 | DB가 저장을 거부했다. 같은 본문을 다시 보내도 성공하지 않는다 |
| `invalid_billable_metric` | 400 | 본문이 집계 미터로 성립하지 않는다 (SUM이 아닌 집계 함수, target_property 누락) |
| `invalid_price_policy` | 400 | 본문이 가격 정책으로 성립하지 않는다 (선언의 중복 키, 빈 키) |
| `customer_not_found` | 404 | 가리킨 고객이 없거나 다른 도입사 소속이다. 경로의 고객, 이벤트 수집 본문의 `customer_id`, 이벤트 조회 필터의 `customer_id`가 모두 같다 |
| `billable_metric_not_found` | 404 | 경로가 가리킨 미터가 없거나 다른 도입사 소속이다 |
| `endpoint_not_found` | 404 | 그 경로에 대응하는 엔드포인트가 없다 |
| `method_not_allowed` | 405 | 경로는 있고 HTTP 메서드가 틀렸다 |
| `response_type_not_acceptable` | 406 | `Accept`로 만족시킬 응답 표현이 없다 |
| `customer_has_events` | 409 | 사용량 이벤트가 있어 고객을 지울 수 없다 |
| `billable_metric_already_exists` | 409 | 같은 코드의 미터가 이미 있다 |
| `price_policy_already_exists` | 409 | 그 미터에 가격 정책이 이미 있다 |
| `request_type_not_supported` | 415 | 보낸 `Content-Type`을 받을 수 없다 |
| `internal_server_error` | 500 | 핸들러에 나열되지 않은 예외가 올라왔다 |

### 오류를 새로 붙일 때

- `ErrorCode`에 상수를 더한다. HTTP 상태, code 문자열, 문구를 그 한 줄이 든다
- 서비스에서 종류 클래스(`NotFoundException`, `ConflictException`, `InvalidRequestException`)에 그 상수를 넣어 던진다. DB 제약 위반은 뜻을 아는 서비스가 잡아서 바꿔 던진다
- 통합 테스트에서 상태와 `code`를 단언한다

### 핸들러 구조

`@RestControllerAdvice`는 `GlobalExceptionHandler` 하나다. 도메인별 advice가 없고, 도메인 예외(`BusinessException`)와 프레임워크 예외를 한 자리에서 받는다. 프레임워크 예외는 정확한 타입으로 하나씩 나열하고 나열하지 않은 것은 `Exception` 핸들러가 500으로 받는다. `ResponseEntityExceptionHandler`를 상속하지 않으므로 405의 `Allow`와 415의 `Accept` 헤더는 붙지 않는다. 왜 이렇게 했는지는 `docs/contributing/error-handling.md`에 있다.

### 문구의 언어

도입사가 읽는 자리는 `message`와 `errors[].message`이고 둘 다 한국어다.

`spring.web.locale=ko`와 `spring.web.locale-resolver=fixed`로 못박아서 `Accept-Language`가 무엇이든 한국어가 나간다. 리졸버를 열어 두면 한 응답 안에 두 언어가 섞인다. Hibernate Validator는 en 번들을 갖고 있어 "must not be blank"로 답하는데 우리 문구는 ko 하나뿐이라 한국어가 그대로 나가기 때문이다. 다국어가 필요해지면 번들을 갖추고 그때 연다.

`message`는 `ErrorCode` 상수가 든다. `errors[].message`는 던지는 자리가 넘긴 문구이거나 Hibernate Validator의 ko 번들 문구다. 우리가 만드는 것은 `GlobalExceptionHandler`의 상수뿐이고, 헤더 누락과 타입 불일치처럼 Bean Validation이 문구를 만들지 않는 자리만 거기 둔다. `@NotBlank` 같은 제약의 문구는 ko 번들에 맡긴다. 제약이 늘 때마다 번역을 떠안으면 누락이 조용히 영어로 새기 때문이다.

## 구조

단일 Gradle 모듈이다. `com.meterengine` 아래에 도메인 패키지를 두고, 도메인 안은 종류별 하위 패키지(controller, service, repository, dto, 필요하면 entity)로 나눈다.

- `event`: 사용량 이벤트 수집과 조회 (`/v1/events`)
- `metric`: 과금 지표의 등록과 조회, 고객별 월 사용량 집계 (`/v1/billable-metrics`, `/v1/usage`)
- `invoice`: 청구 예정액 조회 (`/v1/invoices/draft`). 확정 인보이스는 엔티티와 리포지토리만 있고, 저장하는 서비스와 API는 아직 없다
- `pricing`: 가격 정책과 단가 (`/v1/billable-metrics/{code}/price-policy`, `/v1/price-policies`). 미터의 unit_price를 분리한 뒤 정책 등록 API와 목록 조회를 얹었다. 단가 등록/수정/삭제는 아직 없다
- `customer`: 고객 등록/수정/삭제와 조회 (`/v1/customers`). event, metric, invoice가 공통으로 쓰는 아래층이다
- `payment`: 토스페이먼츠 연동. 지금은 시크릿 키를 담는 `TossPaymentsProperties`만 있고, 빌링키와 결제 이력은 아직 없다. 위 "외부 서비스 키" 참조
- 도메인 어디에도 속하지 않는 것은 루트(`com.meterengine`)에 둔다. 부트스트랩(`MeterEngineApplication`)과 설정(`OpenApiConfig`)이다
- 오류 계약은 `global.error`에 둔다. `ErrorCode`, `ErrorResponse`, `BusinessException`과 종류 클래스(`NotFoundException`, `ConflictException`, `InvalidRequestException`), `GlobalExceptionHandler`다. 한 도메인에 두면 나머지 도메인이 그 도메인을 import하게 된다

경계는 코드 리뷰로 지킨다. 다른 패키지가 쓰는 것만 public으로 열고 나머지는 package-private을 유지한다. 도메인 사이 의존은 여덟이다.

- `event` -> `customer` (고객 판정), `event` -> `metric` (청구 월 경계 계산 공유)
- `invoice` -> `customer` (고객 조회), `invoice` -> `metric` (집계 호출), `invoice` -> `pricing` (단가 조회)
- `metric` -> `customer` (고객 조회)
- `customer` -> `event` (고객 삭제 전 이벤트 유무 확인, MS2-155)
- `pricing` -> `metric` (정책 등록 전 미터 존재 확인, MS2-157)

`customer`가 아래층이고 event, metric, invoice가 그것을 쓴다. 역방향은 `customer` -> `event` 하나뿐인데, 이 때문에 event와 customer는 서로를 참조한다. 수용한 이유와 방향을 되돌리는 방법은 `CustomerService`의 클래스 주석에 있다.
