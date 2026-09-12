# backend

MeterEngine 미터링 엔진 API 서버. 이벤트 수집, 집계, rating, 인보이스 생성을 담당한다.

- Java 25 + Spring Boot 4 + Gradle Kotlin DSL. 버전의 정본은 `gradle/libs.versions.toml`이다.
- PostgreSQL 단일 저장소. Spring Data JPA로 접근하고 집계는 사전 집계 없이 SQL로 계산한다.
- 스키마 마이그레이션은 Flyway. 파일은 `src/main/resources/db/migration/`에 있고 기동 때 자동 적용된다.
- 엔티티는 스키마를 만들지 않는다 (`spring.jpa.hibernate.ddl-auto=validate`).
- API 계약의 정본은 `openapi.yaml`이고 빌드가 다시 만든다.
- 테스트는 JUnit 5 + AssertJ + Testcontainers. DB가 필요한 테스트는 PostgreSQL 컨테이너로 돈다.
- 포맷은 Spotless + google-java-format. CI가 검사한다.
- 코드와 문서 작성 규칙은 `docs/contributing/`, 도메인 정책은 `docs/policies/`에 있다.

## 사전 준비

- JDK 25.
- Docker Desktop (Compose 포함). 실행, 빌드, 테스트가 모두 컨테이너를 쓴다.

## 실행

```bash
cd backend
./gradlew bootRun
```

- 루트 `docker-compose.yml`의 PostgreSQL을 먼저 띄우고 서버를 시작한다 (spring-boot-docker-compose).
  - `backend/compose.yaml`이 루트 정의를 가리키는 include라 작업 디렉터리가 `backend/`든 레포 루트든 같게 동작한다.
  - IDE에서 `MeterEngineApplication`을 직접 실행해도 같다.
- http://localhost:8080 에서 열린다. API 문서 UI는 http://localhost:8080/scalar
- 뜬 것을 확인한다.

```bash
curl -s http://localhost:8080/actuator/health
curl -s http://localhost:8080/v1/customers \
  -H 'X-Organization-Id: d7cee55d-8c82-4afc-b996-6749d8b26a4e'
```

- 헤더의 값은 `src/main/resources/db/migration/R__seed.sql`이 넣는 데모 도입사 id다.
- DB만 필요하면 레포 루트에서 띄운다.

```bash
docker compose up -d
```

## 빌드와 테스트

```bash
cd backend
./gradlew build          # 컴파일, 포맷 검사, 테스트, openapi.yaml 생성
./gradlew test
./gradlew spotlessApply  # 포맷 자동 적용
```

- `build`와 `test`는 Docker 데몬이 떠 있어야 한다 (Testcontainers).
- `build`가 `openapi.yaml`을 다시 만든다. 컨트롤러나 DTO를 건드렸으면 빌드 뒤 `git status`에 뜬 이 파일을 같은 커밋에 넣는다.
  - CI는 이 파일이 낡았는지 검사하지 않는다. 빠뜨려도 아무것도 실패하지 않는다.
- `--tests` 필터를 건 실행은 생성 테스트를 건너뛰므로 `openapi.yaml`이 갱신되지 않는다. 커밋된 파일은 그대로 남는다.

## 컨테이너 이미지

배포용 실행 이미지는 `Dockerfile`이 만든다. 로컬 개발은 이 이미지를 쓰지 않는다.

```bash
cd backend
docker build -t meterengine-backend .
```

- 멀티 스테이지라 실행 이미지에는 JRE와 jar만 들어간다. 이미지 빌드 중에는 테스트를 돌리지 않는다.
- DB 접속 정보는 이미지에 굽지 않는다. 아래 "설정"의 환경변수로 받는다.

## 설정

`application.properties`에 datasource 설정이 없다. 컨테이너로 띄우면 아래 환경변수가 DB에 붙는 유일한 경로다.

| 환경변수 | 용도 | 기본값 |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL` | DB 접속 URL (`jdbc:postgresql://<host>:5432/meterengine`) | 없음 |
| `SPRING_DATASOURCE_USERNAME` | DB 사용자 | 없음 |
| `SPRING_DATASOURCE_PASSWORD` | DB 비밀번호 | 없음 |
| `TOSSPAYMENTS_SECRET_KEY` | 토스페이먼츠 시크릿 키 | `test_sk_LOCAL_PLACEHOLDER` (자리표시자) |

- `./gradlew bootRun`은 spring-boot-docker-compose가 커넥션을 만들어 주므로 datasource 변수를 주지 않아도 된다.
- `TOSSPAYMENTS_SECRET_KEY`의 기본값은 실제 키가 아니다. 이 값으로 토스페이먼츠 API를 부르면 거절당한다.
  - 빈 값을 주면 `TossPaymentsProperties`의 `@NotBlank`가 기동을 실패시킨다.
  - 로컬에서 결제를 시험하려면 같은 상점의 클라이언트 키도 함께 필요하다. 빌링키 발급이 브라우저에서 카드를 등록하는 단계부터 시작한다.
- 운영에서는 `deploy/compose.prod.yml`이 위 변수를 필수로 걸어 주입하고 값은 SSM Parameter Store에서 온다. 등록 절차는 `deploy/README.md`.
- actuator는 health와 prometheus만 노출한다 (`management.endpoints.web.exposure.include`).
- 오류 문구의 언어는 `spring.web.locale=ko`, `spring.web.locale-resolver=fixed`로 고정한다. `Accept-Language`가 무엇이든 한국어가 나간다.

## API 문서

- 계약의 정본은 `openapi.yaml`이다. 컨트롤러와 DTO에서 생성하므로 손으로 고치지 않는다.
- 앱을 띄우면 같은 문서를 `/scalar`(UI), `/v3/api-docs`(JSON), `/v3/api-docs.yaml`에서 볼 수 있다.
- 파라미터, 응답 스키마, 오류 코드는 `openapi.yaml`을 본다.
- 모든 오퍼레이션이 도입사를 `X-Organization-Id` 헤더로 받는다. 인증이 붙기 전까지 쓰는 임시 방식이다.

| 오퍼레이션 | 내용 |
| --- | --- |
| `GET /v1/customers` | 고객 목록. 이름 오름차순, 페이지 나누지 않음 |
| `POST /v1/customers` | 고객 등록. 서버가 id와 등록 시각을 만든다 |
| `PUT /v1/customers/{id}` | 고객 이름 수정 |
| `DELETE /v1/customers/{id}` | 고객 삭제. 이벤트가 있으면 409 |
| `POST /v1/events` | 사용량 이벤트 수집. transaction_id 기준 멱등(first-write-wins) |
| `GET /v1/events` | 이벤트 조회. 월, 고객, type 필터와 페이지 나누기 |
| `GET /v1/usage` | 고객별 월 사용량 집계 |
| `GET /v1/invoices/draft` | 고객별 청구 예정액 |
| `POST /v1/billable-metrics` | 집계 미터 등록. 집계 함수는 SUM만 받고 target_property가 필수다. 코드는 도입사 안에서 유일하다 |
| `GET /v1/billable-metrics` | 미터 목록. code 오름차순, 페이지 나누지 않음 |
| `POST /v1/billable-metrics/{code}/price-policy` | 가격 정책 등록. 축 선언만 받고 미터당 하나다 |
| `GET /v1/billable-metric-prices` | 미터별 가격 목록. 미터마다 정책과 무차원 기본 단가를 싣는다. code 오름차순, 페이지 나누지 않음 |

- 단가 등록 API가 아직 없다. 단가가 없는 미터는 청구 예정액 라인에서 빠진다.

### 오류 응답

- 오류는 5xx를 포함해 형식 하나로 나간다. 스키마는 `openapi.yaml`의 `ErrorResponse`다.

```json
{
  "code": "validation_error",
  "message": "요청 값이 올바르지 않습니다",
  "errors": [{ "field": "transaction_id", "message": "공백일 수 없습니다" }]
}
```

- `code`가 기계 판독용이다. 오류별 처리는 이 값으로 분기한다.
- `message`는 code마다 하나인 한국어 문구다. 예고 없이 바뀌므로 분기에 쓰지 않는다.
- `errors`는 400에만 실리고 비면 나가지 않는다. `field`는 도입사가 보낸 이름이다.
- code 값의 정본은 `ErrorCode` enum이고 목록은 `openapi.yaml`이 낸다.
- 오류를 더하거나 고칠 때 따르는 규칙은 `docs/contributing/error-handling.md`에 있다.

## 구조

단일 Gradle 모듈이다. `com.meterengine` 아래에 도메인 패키지를 두고, 도메인 안은 controller, service, repository, dto, entity로 나눈다.

| 패키지 | 내용 | 경로 |
| --- | --- | --- |
| `event` | 사용량 이벤트 수집과 조회 | `/v1/events` |
| `metric` | 미터 등록과 조회, 고객별 월 사용량 집계 | `/v1/billable-metrics`, `/v1/usage` |
| `pricing` | 가격 정책과 단가 | `/v1/billable-metrics/{code}/price-policy`, `/v1/billable-metric-prices` |
| `invoice` | 청구 예정액 조회 | `/v1/invoices/draft` |
| `customer` | 고객 등록, 수정, 삭제, 조회 | `/v1/customers` |
| `payment` | 토스페이먼츠 시크릿 키 설정 | 없음 |
| `global.error` | 오류 계약과 예외 핸들러 | 없음 |

- 도메인 어디에도 속하지 않는 것은 루트에 둔다. 부트스트랩(`MeterEngineApplication`)과 설정(`OpenApiConfig`)이다.
- 다른 패키지가 쓰는 것만 public으로 열고 나머지는 package-private을 유지한다. 경계는 코드 리뷰로 지킨다.
- `customer`가 아래층이고 `event`, `metric`, `invoice`가 그것을 쓴다. 역방향은 고객 삭제가 이벤트 유무를 묻는 `customer` -> `event` 하나다.
- 아직 없는 것.
  - 확정 인보이스를 저장하는 서비스와 API. 엔티티와 리포지토리만 있다.
  - 단가 등록, 수정, 삭제 API.
  - 빌링키와 결제 이력.

### 마이그레이션

- 파일은 `src/main/resources/db/migration/`에 있고 기동 때 자동 적용된다.
- `R__seed.sql`은 반복 마이그레이션이라 파일 내용이 바뀌면 다시 적용된다. 데모가 쓰는 도입사, 고객, 미터, 가격을 이 파일이 정한다.
- 이미 적용된 `V__` 파일은 주석만 고쳐도 체크섬이 바뀌어 그 DB의 기동이 실패한다. 아래 "문제 해결" 참조.

## 문제 해결

| 증상 | 원인 | 조치 |
| --- | --- | --- |
| 기동이 Flyway 체크섬 불일치로 실패한다 | 이미 적용된 `V__` 파일을 고쳤다 | 로컬은 볼륨을 지우고 다시 만든다 (아래 명령). 배포 DB는 `deploy/README.md` |
| 기동이 스키마 검증에서 실패한다 | 엔티티와 실제 테이블이 어긋났다 | 마이그레이션을 더하거나 엔티티를 맞춘다 |
| `./gradlew build`가 컨테이너를 띄우지 못하고 실패한다 | Docker 데몬이 꺼져 있다 | Docker Desktop을 켠다 |
| 기동이 `tosspayments.secret-key` 검증에서 실패한다 | `TOSSPAYMENTS_SECRET_KEY`를 빈 값으로 줬다 | 값을 주거나 변수를 빼서 기본값을 쓴다 |
| 빌드가 포맷 검사에서 실패한다 | google-java-format 결과와 다르다 | `./gradlew spotlessApply` |
| 요청이 `unknown_organization`으로 400이 난다 | `X-Organization-Id`가 등록된 도입사가 아니다 | 시드가 넣은 도입사 id를 쓴다 (위 "실행") |

```bash
# 로컬 DB를 비우고 다시 만든다 (레포 루트에서, 데이터가 사라진다)
docker compose down -v
docker compose up -d
```

- 서버 로그는 `./gradlew bootRun`을 띄운 터미널에 그대로 나온다.
- DB에 직접 붙을 때는 `docker exec -it meterengine-postgres psql -U meterengine -d meterengine`.
