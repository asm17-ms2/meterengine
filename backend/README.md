# backend

MeterEngine 미터링 엔진 API 서버. 이벤트 수집, 집계, rating, 인보이스 생성을 담당한다.

- Java 25 + Spring Boot 4 + Gradle Kotlin DSL. 버전의 정본은 `gradle/libs.versions.toml`이다.
- PostgreSQL 단일 저장소. Spring Data JPA로 접근하고 집계는 사전 집계 없이 SQL로 계산한다.
- 스키마 마이그레이션은 Flyway. 파일은 `src/main/resources/db/migration/`에 있고 기동 때 자동 적용된다.
- 엔티티는 스키마를 만들지 않는다 (`spring.jpa.hibernate.ddl-auto=validate`).
- 청구 메일 본문은 Thymeleaf 템플릿(`src/main/resources/templates/mail/`)으로 만든다.
- API 계약의 정본은 `openapi.yaml`이고 빌드가 다시 만든다.
- 테스트는 JUnit 5 + AssertJ + Testcontainers. DB가 필요한 테스트는 PostgreSQL 컨테이너로 돈다.
- 포맷은 Spotless + google-java-format, 이름 케이스는 Checkstyle. CI가 검사한다.
- 코드와 문서 작성 규칙은 `docs/contributing/`에 있다.

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
- `bootRun`은 `local` 프로파일로 뜬다. 로컬 계정과 키(아래 "로컬 계정과 키")는 이 프로파일에서만 DB에 들어간다.
  - IDE에서 `MeterEngineApplication`을 직접 실행할 때는 실행 설정에 `local` 프로파일을 지정한다.
- http://localhost:8080 에서 열린다. API 문서 UI는 http://localhost:8080/scalar
- 뜬 것을 확인한다.

```bash
curl -s http://localhost:8080/actuator/health
curl -s http://localhost:8080/v1/customers \
  -H 'Authorization: Bearer me_live_QouOFuCiJyAbC0VGK1ltUCkG4IZM2LtA'
```

- 키는 로컬 live 키이고 데모 도입사의 데이터를 본다.
- DB만 필요하면 레포 루트에서 띄운다.

```bash
docker compose up -d
```

## 빌드와 테스트

```bash
cd backend
./gradlew build          # 컴파일, 포맷 검사, 이름 검사, 테스트, openapi.yaml 생성
./gradlew test
./gradlew spotlessApply  # 포맷 자동 적용
```

- `build`와 `test`는 Docker 데몬이 떠 있어야 한다 (Testcontainers).
- `build`가 `openapi.yaml`을 다시 만든다. 컨트롤러나 DTO를 건드렸으면 빌드 뒤 `git status`에 뜬 이 파일을 같은 커밋에 넣는다.
  - 빠뜨리면 CI가 실패한다. CI는 빌드 뒤 이 파일이 커밋된 것과 다른지 본다.
- `--tests` 필터를 건 실행은 생성 테스트를 건너뛰므로 `openapi.yaml`이 갱신되지 않는다. 커밋된 파일은 그대로 남는다.
- 통합 테스트의 MockMvc는 보안 필터를 거치게 만든다.

```java
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

mvc = MockMvcTester.from(webApplicationContext, builder -> builder.apply(springSecurity()).build());
```

- 자격은 `com.meterengine.TestCredentials`로 넣고 돌려받은 값을 `Authorization` 헤더에 싣는다.
  - `issueApiKey(jdbcTemplate, organizationId, "test", "ingest", "read")`: 첫 값이 `live`나 `test`면 모드이고 나머지는 스코프다. 모드를 빼면 live, 스코프를 빼면 admin이다.
  - `issueMemberSession(jdbcTemplate, organizationId)`: 그 도입사 소속 member의 세션. `issueOperatorSession(jdbcTemplate)`: 소속 없는 operator의 세션.

## 컨테이너 이미지

배포용 실행 이미지는 `Dockerfile`이 만든다. 로컬 개발은 이 이미지를 쓰지 않는다.

```bash
cd backend
docker build -t meterengine-backend .
```

- 멀티 스테이지라 실행 이미지에는 JRE와 jar만 들어간다. 이미지 빌드 중에는 테스트를 돌리지 않는다.
- DB 접속 정보는 이미지에 굽지 않는다. 아래 "설정"의 환경변수로 받는다.

## 설정

`application.properties`에 DB 접속 정보(URL, 계정)가 없다. 컨테이너로 띄우면 아래 환경변수가 DB에 붙는 유일한 경로다.

| 환경변수 | 용도 | 기본값 |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL` | DB 접속 URL (`jdbc:postgresql://<host>:5432/meterengine`) | 없음 |
| `SPRING_DATASOURCE_USERNAME` | DB 사용자 | 없음 |
| `SPRING_DATASOURCE_PASSWORD` | DB 비밀번호 | 없음 |

- `./gradlew bootRun`은 spring-boot-docker-compose가 커넥션을 만들어 주므로 datasource 변수를 주지 않아도 된다.
- 배포할 때는 `deploy/compose.prod.yml`이 위 변수를 필수로 걸어 주입하고 값은 SSM Parameter Store에서 온다. 등록 절차는 `deploy/README.md`.
- DB 쿼리 응답은 30초까지만 기다린다(`spring.datasource.hikari.data-source-properties.socketTimeout`). DB가 답 없이 끊겨도 커넥션이 묶이지 않고 풀려 앱이 회복한다.
- actuator는 health와 prometheus만 노출한다 (`management.endpoints.web.exposure.include`).
- `CRYPTO_ENCRYPTION_KEY`는 운영 기본값이 없는 필수 값이다. 토스페이먼츠 시크릿 키, 빌링키, 웹훅 서명 비밀값을 DB에 AES-GCM으로 암호화하는 키로, 32바이트를 base64로 준다. `local` 프로파일만 개발용 값을 쓴다. 값을 바꾸면 이미 저장한 암호문을 풀지 못해 결제와 카드 등록이 실패하고, 웹훅 전송은 `secret_unavailable`로 실패한다. 배포 값은 `deploy/README.md`의 `crypto-encryption-key`다.
- `meterengine.webhook.enabled`(기본 true)를 false로 두면 웹훅 발송 작업과 그 작업 등록만 멈춘다. 엔드포인트 등록, 시험 통보, 통보 적재는 계속되고, 다시 켜면 쌓인 전송을 차례로 보낸다.
- `meterengine.webhook.allow-private-network`(기본 false)를 true로 두면 http와 사설망 주소로도 통보를 보낸다. `local` 프로파일과 시험만 켠다.
- 오류 문구의 언어는 `spring.web.locale=ko`, `spring.web.locale-resolver=fixed`로 고정한다. `Accept-Language`가 무엇이든 한국어가 나간다.

## 인증

- `/v1` 요청은 `Authorization: Bearer <값>`으로 인증한다. 값은 API 키(`me_live_`, `me_test_`)나 로그인 세션 토큰(`me_session_`)이고, 도입사는 키나 세션이 정한다.
- 인증 없이 부를 수 있는 것은 로그인(`POST /v1/sessions`), 가입 신청(`POST /v1/signups`), 초대 확인(`GET /v1/organization-invitations/{token}`)과 수락(`POST /v1/organization-invitations/{token}/accept`), `/actuator/health`, `/actuator/prometheus`, API 문서뿐이다.
- API 키 관리(`/v1/api-keys`)는 로그인 세션만, 도입사와 사용자 관리는 operator 세션만 할 수 있다.
- 원문은 발급 응답에만 나오고 다시 볼 수 없다(서버는 해시만 둔다).
- 키 모드(live, test)는 지금 접두사만 다르고 동작은 같다. test 키도 실제 데이터를 바꾸고 인보이스 확정과 결제 시도를 할 수 있다.
- 세션은 로그인 뒤 12시간에 끝난다(`meterengine.session.ttl`). 키를 교체하면 옛 키는 24시간 더 쓸 수 있다(`meterengine.api-key.replacement-grace`).

### 로컬 계정과 키

| 구분 | 값 | 비밀번호 | 내용 |
| --- | --- | --- | --- |
| live 키 | `me_live_QouOFuCiJyAbC0VGK1ltUCkG4IZM2LtA` | | 데모 도입사, admin 스코프. 프론트와 데모 CLI의 기본값 |
| test 키 | `me_test_wiCp766QYNqhyYm4Z7Xq9wYosNvQCeVC` | | 데모 도입사, admin 스코프 |
| member | `member@meterengine.local` | `local-member-password` | 데모 도입사와 `로컬 두 번째 도입사`의 대표관리자 |
| operator | `operator@meterengine.local` | `local-operator-password` | 소속이 없어 로그인하면 도입사가 없다 |

- 정본은 `src/main/resources/db/local/R__local_credentials.sql`이다.

### 부트스트랩

기동할 때 아래 환경변수로 첫 운영자와 프론트 콘솔 키를 넣는다. 값이 비면 그 항목을 건너뛴다(로컬과 CI). 배포 환경의 값은 SSM Parameter Store에서 오고 등록 절차는 `deploy/README.md`다.

| 환경변수 | 용도 |
| --- | --- |
| `OPERATOR_EMAIL` | 첫 운영자 이메일 |
| `OPERATOR_PASSWORD` | 첫 운영자 비밀번호. UTF-8로 72바이트 이하 |
| `FRONTEND_API_KEY` | 프론트 콘솔 키 원문. `me_live_`나 `me_test_` 뒤에 영문 대소문자와 숫자 32자 |
| `FRONTEND_ORGANIZATION_ID` | 콘솔 키가 속할 도입사 id |

- 아래 경우는 아무것도 쓰지 않고 기동이 실패한다.
  - `OPERATOR_EMAIL`과 `OPERATOR_PASSWORD` 가운데 하나만 있거나, 비밀번호가 72바이트를 넘는다.
  - `FRONTEND_API_KEY` 형식이 틀렸거나, `FRONTEND_ORGANIZATION_ID`가 비었거나 UUID가 아니거나 없는 도입사다.
  - 그 키가 이미 다른 도입사의 키로 들어 있다.
- 운영자는 이메일(대소문자 무시)로 찾아 없으면 operator로 만든다. 있으면 비밀번호가 바뀌었을 때만 해시를 갱신하고 역할은 바꾸지 않는다.
- 콘솔 키는 해시로 찾아 없을 때만 admin 스코프로 넣는다. 폐기했거나 만료된 키는 그대로 두고 되살리지 않는다.
- 아래 경우는 기동하되 WARN 로그를 남긴다: 이메일이 operator가 아닌 계정의 것이다, 콘솔 키가 폐기됐거나 만료됐다, 콘솔 키의 도입사가 활성 상태가 아니다.

콘솔 키 원문은 아래 스크립트로 만든다. DB에는 쓰지 않는다.

```bash
cd backend
scripts/issue-api-key.sh        # live 키. test 키는 인자로 test를 준다
```

- 원문을 `FRONTEND_API_KEY`에 넣는다. 프론트는 이 키를 쓰지 않고, 배포 compose도 이 값을 넘기지 않는다.

### 배포 환경의 운영 키 발급

브리지, 데모 CLI, k6 키는 운영자 세션으로 발급한다. 키는 세션의 도입사에 발급되므로 운영자를 대상 도입사에 소속시키고 세션의 도입사를 바꾼 뒤 발급한다.

```bash
BASE=https://<호스트>
OPERATOR_EMAIL=<운영자 이메일>

# 1. 운영자로 로그인한다. 비밀번호는 화면에 보이지 않게 받고, 응답의 token을 세션 토큰으로 둔다
read -rsp '운영자 비밀번호: ' OPERATOR_PASSWORD && echo
SESSION=$(printf '%s' "$OPERATOR_PASSWORD" \
  | jq -n --arg email "$OPERATOR_EMAIL" --rawfile password /dev/stdin \
      '{email: $email, password: $password}' \
  | curl -s $BASE/v1/sessions -H 'Content-Type: application/json' --data @- \
  | jq -r .token)
unset OPERATOR_PASSWORD

# 세션 토큰을 명령 인자 대신 헤더 파일로 싣는다
api() {
  curl -s -H @<(printf 'Authorization: Bearer %s\n' "$SESSION") \
    -H 'Content-Type: application/json' "$@"
}

# 2. 운영자 id(user_account.id)와 대상 도입사 id를 확인한다
api $BASE/v1/sessions/current
api "$BASE/v1/organizations?status=active"

# 3. 운영자를 대상 도입사에 소속시키고 세션의 도입사를 바꾼다
api $BASE/v1/organizations/<도입사 id>/members -d '{"user_account_id":"<운영자 id>"}'
api -X PUT $BASE/v1/sessions/current/organization -d '{"organization_id":"<도입사 id>"}'

# 4. 키를 발급한다. 응답의 secret이 원문이다
api $BASE/v1/api-keys -d '{"label":"<용도>","mode":"live","scopes":["ingest","read"]}'

# 5. 로그아웃한다
api -X DELETE $BASE/v1/sessions/current
unset SESSION
```

- jq 1.6 이상이 필요하다. zsh에서는 1의 `read`를 `read -rs '?운영자 비밀번호: ' OPERATOR_PASSWORD`로 바꾼다.
- `SESSION`이 `null`이면 로그인에 실패한 것이다.
- 이미 소속된 도입사면 3의 첫 요청이 409 `organization_member_already_exists`다. 전환만 한다.
- 소속은 남는다. 다음 로그인 때 세션의 도입사는 소속된 활성 도입사 가운데 가장 먼저 소속된 곳이다.

### operator가 하나도 남지 않았을 때

- `OPERATOR_EMAIL`을 아직 계정이 없는 새 주소로 바꾸고 다시 배포한다. 부트스트랩이 새 operator를 만든다.
- 기존 주소로는 되돌릴 수 없다. 부트스트랩은 있는 계정의 역할을 바꾸지 않는다.

## API 문서

- 계약의 정본은 `openapi.yaml`이다. 컨트롤러와 DTO에서 생성하므로 손으로 고치지 않는다.
- 앱을 띄우면 같은 문서를 `/scalar`(UI), `/v3/api-docs`(JSON), `/v3/api-docs.yaml`에서 볼 수 있다.
- 파라미터, 응답 스키마, 오류 코드는 `openapi.yaml`을 본다.
- 모든 `/v1` 응답에 `X-Request-ID` 헤더가 붙는다. 요청 감사 행의 `detail.request_id`와 같은 값이다(이벤트 수집 요청은 감사 행을 남기지 않는다).
- 로그인, 가입 신청, 초대 확인과 수락을 뺀 모든 오퍼레이션이 Bearer 인증(`bearerAuth`)을 요구한다.
- 핸들러마다 `global.security`의 접근 어노테이션(`@ReadAccess`, `@IngestAccess`, `@AdminAccess`, `@MemberAccess`, `@OperatorAccess`, `@PublicAccess`) 가운데 하나를 단다. 빠지면 `ApiAccessCoverageTest`가 실패한다.

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
- 인증, 권한, 한도 오류도 같은 형식이다.
  - 401 `authentication_required`: Authorization 헤더가 없다. `invalid_credentials`: 키나 토큰의 형식이 틀렸거나, 모르는 값이거나, 폐기 또는 만료됐다. 로그인 실패도 같다. 401에는 `WWW-Authenticate: Bearer`가 붙는다.
  - 403 `permission_denied`: 키의 스코프나 계정의 역할 밖이거나, 도입사가 없는 세션의 도입사 범위 요청이거나, 소속이 아닌 도입사로 전환하려 했다. `organization_not_active`: 도입사가 pending, rejected, suspended다. `consent_required`: 로그인 세션의 사용자가 필수 안내에 동의하지 않았다. 동의 전에는 `GET /v1/sessions/current`, `DELETE /v1/sessions/current`, `POST /v1/sessions/current/consents`만 된다.
  - 429 `rate_limit_exceeded`: API 키의 분당 요청 한도를 넘었다. `Retry-After` 헤더의 초만큼 기다린 뒤 다시 보낸다.
- 한도는 API 키마다 인스턴스 메모리에서 센다. 발급 시 `rate_limit_per_minute`를 양수로 지정하고, 생략하거나 null이면 무제한이다. 로그인 세션은 세지 않는다.
- code 값의 정본은 `ErrorCode` enum이고 목록은 `openapi.yaml`이 낸다.
- 오류를 더하거나 고칠 때 따르는 규칙은 `docs/contributing/error-handling.md`에 있다.

## 구조

단일 Gradle 모듈이다. `com.meterengine` 아래에 도메인 패키지를 두고, 도메인 안의 계층은 `ClassNamingConventionTest.java`의 패키지 규칙이 검사한다.

| 패키지 | 내용 | 경로 |
| --- | --- | --- |
| `event` | 사용량 이벤트 수집과 조회 | `/v1/events` |
| `metric` | 미터 등록, 수정, 삭제, 조회, 고객별 월 사용량 집계(속성 값 조합별로 나눔) | `/v1/billable-metrics`, `/v1/usage` |
| `pricing` | 단가 등록(같은 조합이면 덮어쓰기)과 삭제, 미터별 가격 목록 | `/v1/billable-metrics/{code}/price-rates`, `/v1/billable-metric-prices` |
| `invoice` | 청구 예정액 조회(속성 값 조합별 사용량에 같은 조합의 단가를 곱함), 인보이스 일괄 확정(확정한 고객마다 청구 메일을 남김), 확정 인보이스 목록 조회(금액 구성과 라인 포함) | `/v1/invoices`, `/v1/invoices/draft` |
| `customer` | 고객 등록, 수정, 삭제, 조회 | `/v1/customers` |
| `payment` | 인보이스 결제 요청과 결제 시도 기록. PG는 항상 승인하는 Mock이다 | `/v1/invoices/{id}/payment-attempts` |
| `mail` | 보낼 메일 기록. 남긴 메일의 본문과 받는 주소는 고치지 않는다 | 없음 |
| `organization` | 현재 도입사 조회, 도입사 목록과 상태 변경(승인, 거절, 정지, 재개), 소속 추가 | `/v1/organization`, `/v1/organizations` |
| `apikey` | API 키 발급, 폐기, 교체와 키 검증 | `/v1/api-keys` |
| `account` | 로그인 세션과 도입사 전환, 가입 신청과 동의, 사용자 목록과 역할 변경, 기동 시 부트스트랩 | `/v1/sessions`, `/v1/signups`, `/v1/user-accounts` |
| `audit` | 핵심 동작과 `/v1` 요청의 감사 기록, 도입사와 운영자의 감사 기록 조회. 이벤트 수집 요청은 제외하며 남긴 행은 고치지 않는다 | `/v1/audit-logs`, `/v1/operations/audit-logs` |
| `webhook` | 웹훅 엔드포인트 등록, 수정, 삭제, 서명한 통보 발송과 재시도, 전송 기록 조회, 수동 재시도 | `/v1/webhook-endpoints` |
| `dashboard` | 운영자의 서비스 운영 현황 조회(도입사 수, 결제와 웹훅 실패, 예약 작업 마지막 성공) | `/v1/operations` |
| `global.security` | 인증 필터, 접근 어노테이션, `@CurrentOrganizationId`, 레이트리밋, 요청 로그 | 없음 |
| `scheduling` | 예약 작업 실행 기록과 실행기. 예약 작업은 `@Scheduled(cron = ..., zone = "Asia/Seoul")` 메서드에서 `ScheduledJobRunService.run(작업 이름, 슬롯 시각(지금을 분 단위로 자른 값처럼 인스턴스마다 같은 값), 작업)`을 불러, 인스턴스가 여럿이어도 같은 슬롯을 한 번만 돌린다 | 없음 |
| `global.error` | 오류 계약과 예외 핸들러 | 없음 |
| `global.config` | OpenAPI, 시계, 인자 리졸버 등록 | 없음 |
| `global.crypto` | 비밀값 암호화 변환기와 키 설정, 로그에 남길 비밀값 가림 | 없음 |

- 루트(`com.meterengine`)에는 부트스트랩(`MeterEngineApplication`)만 둔다. 도메인에 속하지 않는 설정은 `global.config`에 둔다.
- 다른 패키지가 쓰는 것만 public으로 열고 나머지는 package-private을 유지한다. 경계는 코드 리뷰로 지킨다.
- `customer`가 아래층이고 `event`, `metric`, `invoice`가 그것을 쓴다. 역방향은 고객 삭제가 이벤트와 결제 기록 유무를 묻는 `customer` -> `event`, `customer` -> `payment`다. 미터 수정과 삭제는 이벤트 유무를 `event`에, 삭제는 단가 유무를 `pricing`에 묻는다(`metric` -> `event`, `metric` -> `pricing`). 결제 요청은 인보이스를 `invoice`에서 읽는다(`payment` -> `invoice`). 인보이스 확정은 도입사 이름을 `organization`에서 읽고 청구 메일을 `mail`에 남긴다(`invoice` -> `organization`, `invoice` -> `mail`).
- 인증은 키와 세션 검증을 `apikey`, `account`에, 도입사 상태를 `organization`에 묻는다(`global.security` -> `apikey`, `account`, `organization`). 로그인, 가입, 사용자 목록, 부트스트랩은 도입사를 `organization`에서 읽거나 넣고, 부트스트랩은 콘솔 키를 `apikey`에 넣는다(`account` -> `organization`, `apikey`). 소속 추가는 사용자를 확인한다(`organization` -> `account`).
- 웹훅 발송은 예약 작업 실행기를 쓰고 비밀값을 `global.crypto`로 풀며 엔드포인트 변경을 감사 기록에 남긴다(`webhook` -> `scheduling`, `global.crypto`, `audit`). 운영 현황은 도입사, 결제, 웹훅, 예약 작업 표를 SQL로 직접 센다.

### 웹훅

- 엔드포인트는 도입사마다 10개까지 등록한다. 서명 비밀값(`whsec_`)은 등록 응답에만 나온다.
- 통보는 `webhook-id`, `webhook-timestamp`, `webhook-signature` 헤더와 `User-Agent: MeterEngine-Webhooks/1.0`을 싣는다. 수신 측 방화벽의 허용 목록에는 이 User-Agent를 쓴다.
- 발송 작업은 30초마다 돌고 한 번에 최대 100건, 15초까지 보낸다. 시도마다 전송을 2분 선점한다.
- 실패하면 1분, 5분, 30분, 2시간, 8시간 뒤 다시 보내 모두 6번 시도한다. 성공(2xx), 6번 소진, 막힌 주소(`blocked_address`), 발송을 끄거나 지운 엔드포인트(`endpoint_disabled`)에서 끝난다.
- 실패로 끝난 전송은 수동 재시도(`POST /v1/webhook-endpoints/{id}/deliveries/{delivery_id}/retries`)로 같은 `webhook-id`를 다시 보낸다.

### 마이그레이션

- 파일은 `src/main/resources/db/migration/`에 있고 기동 때 자동 적용된다.
- `R__seed.sql`은 반복 마이그레이션이라 파일 내용이 바뀌면 다시 적용된다. 데모가 쓰는 도입사, 고객, 미터, 가격을 이 파일이 정한다.
- 로컬 계정과 키는 `src/main/resources/db/local/R__local_credentials.sql`이 넣는다. 이 로케이션은 `local` 프로파일(`application-local.properties`)에서만 읽는다.
- `create_webhook_tables` 마이그레이션은 `webhook_event`와 `webhook_delivery_attempt`의 UPDATE, DELETE, TRUNCATE를 트리거로 막는다. 통보와 시도 기록은 고치거나 지우지 않는다.
- 이미 적용된 `V__` 파일은 주석만 고쳐도 체크섬이 바뀌어 그 DB의 기동이 실패한다. 아래 "문제 해결" 참조.

## 문제 해결

| 증상 | 원인 | 조치 |
| --- | --- | --- |
| 기동이 Flyway 체크섬 불일치로 실패한다 | 이미 적용된 `V__` 파일을 고쳤다 | 로컬은 볼륨을 지우고 다시 만든다 (아래 명령). 배포 DB는 `deploy/README.md` |
| 기동이 스키마 검증에서 실패한다 | 엔티티와 실제 테이블이 어긋났다 | 마이그레이션을 더하거나 엔티티를 맞춘다 |
| `./gradlew build`가 컨테이너를 띄우지 못하고 실패한다 | Docker 데몬이 꺼져 있다 | Docker Desktop을 켠다 |
| 빌드가 포맷 검사에서 실패한다 | google-java-format 결과와 다르다 | `./gradlew spotlessApply` |
| 빌드가 이름 검사에서 실패한다 | Checkstyle의 케이스나 약어 규칙에 어긋난다 | `backend/build/reports/checkstyle/`의 보고서가 가리키는 이름을 고친다 |
| 요청이 401 `authentication_required`다 | Authorization 헤더를 싣지 않았다 | `Authorization: Bearer <키>`를 싣는다 (위 "실행") |
| 로컬 키로 보낸 요청이 401 `invalid_credentials`다 | `local` 프로파일 없이 띄워 로컬 키가 DB에 없다 | `./gradlew bootRun`으로 띄우거나 IDE 실행 설정에 `local` 프로파일을 지정한다 |
| 기동이 Flyway 검증에서 `not resolved locally`로 실패한다 | `local` 프로파일로 마이그레이션한 DB를 프로파일 없이 띄웠다 | `local` 프로파일을 지정한다 |
| 발급한 키나 세션이 401 `invalid_credentials`다 | 키를 폐기했거나, 교체 유예가 지났거나, 세션이 만료됐거나 로그아웃했다 | 새 키를 발급하거나 다시 로그인한다 |
| 요청이 403 `organization_not_active`다 | 키나 세션의 도입사가 승인 전, 거절, 정지 상태다 | 승인 전이나 정지면 operator가 active로 바꾼다 (`PUT /v1/organizations/{id}/status`). 거절한 도입사는 되돌릴 수 없다 |
| 기동이 `OPERATOR_`나 `FRONTEND_`로 시작하는 오류로 실패한다 | 부트스트랩 환경변수가 규칙에 어긋난다 | 위 "부트스트랩"의 규칙대로 값을 고친다 |

```bash
# 로컬 DB를 비우고 다시 만든다 (레포 루트에서, 데이터가 사라진다)
docker compose down -v
docker compose up -d
```

- 서버 로그는 `./gradlew bootRun`을 띄운 터미널에 그대로 나온다. 요청마다 한 줄을 남기고 Authorization 값과 쿼리 문자열은 남기지 않는다.
- DB에 직접 붙을 때는 `docker exec -it meterengine-postgres psql -U meterengine -d meterengine`.
