# 이름 규칙

- 기본은 Google Java Style Guide, Google TypeScript Style Guide, Zalando RESTful API Guidelines, Google AIP다. 케이스만 정하는 행은 두지 않고 그 가이드대로 간다.
- 가이드와 다르게 간 것은 아래뿐이다.
  - 테스트 메서드 이름은 한국어 문장.
  - 문자열 열거값과 오류 코드는 소문자 snake_case.
  - 컴포넌트 파일명은 PascalCase.
  - URL 버전은 `/v1/` 경로 접두사.
- 표는 가이드가 정하지 않은 팀 선택과 팀이 같은 꼴로 맞추기로 한 것을 적는다.
- 표에 없는 이름은 아래 "원칙"으로 짓고 행을 더하지 않는다. 같은 패턴이 세 번 이상 반복되거나 사람마다 다르게 지어 충돌하면 그때 행을 더한다.
- 표의 열은 식별자, 케이스, 형식, 예. 형식이 길면 표 아래 굵은 제목 불릿으로 빼고 셀은 그 제목을 가리킨다.
- 예 열은 규칙을 적용한 이름이라 지금 코드와 다를 수 있다.

## 원칙

- 표의 모든 행은 아래 원칙에서 나온다.
- **읽는 자리에서 답이 나온다.** 선언부로 올라가지 않아도 호출부만 보고 무엇(대상)이고 어떤 종류(계층, 역할)인지 안다. 그래서 주입 필드는 타입명, 클래스는 계층 접미사, 테스트는 관점 접미사다.
- **같은 개념에 같은 낱말, 다른 개념에 다른 낱말.** 한 대상을 파일마다 다르게 부르지 않고, 엔티티를 약칭하지 않는다.
- **타입과 문맥에 이미 있는 정보를 되풀이하지 않는다.** 컬렉션에 `List`를, 클래스가 말하는 대상을 메서드에, 응답 리소스명을 그 필드에 다시 적지 않는다.
- **줄이지 않는다.** 사전에 있는 낱말은 약어로 만들지 않고 이름 길이에 상한을 두지 않는다. 주석이 없으므로 이름이 길어지는 쪽이 뜻이 빠지는 쪽보다 싸다.
- **고를 자유도를 남기지 않는다.** 팀이 같은 것을 다르게 부를 수 있는 규칙은 규칙이 아니다. 형식이 하나로 정해지는 쪽을 택한다.
- **밖에서 정한 이름은 그대로.** HTTP 헤더, 프레임워크 예약 export, 구현하는 인터페이스의 접근자처럼 우리가 짓지 않은 이름에는 표를 대지 않는다.

## 공통

백엔드와 프론트에 같이 적용된다.

| 식별자 | 케이스 | 형식 | 예 |
| --- | --- | --- | --- |
| 약어 | 낱말처럼 camel | 낱말 하나로 보고 첫 글자만 대문자. 사전에 있는 단어는 줄이지 않고, 업계 약어(`id` `api` `url` `json` `krw`)는 그대로 | `customerId`, `OpenApiConfig` |
| 변수, 프로퍼티, 함수 | lowerCamelCase | 변수는 값의 정체를 말하는 명사구이고 조회 메서드 이름과 맞춘다. 함수는 동사구 | `targetMonth`, `listCustomers` |
| 컬렉션 | lowerCamelCase | 복수형, 접미사 없음. Map은 `<값>By<키>`. JSON 키를 그대로 담는 dto의 Map은 대상 밖 | `customerQuantities`, `priceByBillableMetricId` |
| boolean | lowerCamelCase | 자바 필드와 변수, 프론트 prop과 객체 필드, JSON 키, DB 컬럼은 접두사 없는 형용사나 과거분사. 자바 접근자는 `isX()`, 인터페이스가 접근자 이름을 정한 필드는 그 이름(`Persistable`의 `isNew`), 프론트 변수와 상태는 `is` `has` `can` 접두사 | 필드 `finalized`, 접근자 `isFinalized()`, 프론트 변수 `isOpen` |
| 시간 | | 필드, record 컴포넌트, JSON 키, DB 컬럼은 순간 `At`/`_at`, 날짜 `Date`/`_date` | `finalizedAt`, `billingDate` |

## 백엔드 Java

### 패키지

| 식별자 | 케이스 | 형식 | 예 |
| --- | --- | --- | --- |
| 패키지 | 소문자, 밑줄 없음 | `com.meterengine.<domain>.<layer>`. layer는 `config` `controller` `dto` `entity` `repository` `service`. 공용은 `global.<역할>`, 루트에는 진입점만 | `com.meterengine.invoice.dto`, `com.meterengine.global.config` |

### 클래스

| 식별자 | 케이스 | 형식 | 예 |
| --- | --- | --- | --- |
| 엔티티 | UpperCamelCase | 도메인 명사 그대로, 접미사 없음. 복합키 클래스는 `<엔티티>Id`. 약칭 금지는 이름이 나타나는 모든 자리(URL, JSON 키, DB 컬럼, 프론트까지)에 적용 | `BillableMetric` (`Metric` 아님), `BillableMetricId` |
| 리포지토리 | UpperCamelCase | `<대상>Repository`. 대상은 엔티티, 또는 엔티티가 없는 테이블의 읽기 모델 | `InvoiceLineRepository`, `BillableMetricUsageRepository` |
| 서비스 | UpperCamelCase | `<대상>Service`, 동작 하나면 `<대상><동작명사>Service` | `CustomerService`, `EventIngestionService` |
| 컨트롤러 | UpperCamelCase | `<대상>Controller` | `CustomerController` |
| 설정 | UpperCamelCase | `<무엇>Config`, 외부 설정값 `<무엇>Properties` | `OpenApiConfig`, `TossPaymentsProperties` |
| 쓰지 않는 접미사 | | `Dto` `Vo` `Cmd` `Json` `Model` `Entity` `Impl` `Util` `Helper` `Manager` `Entry` `Item` `Row` `Data` `Info` |  |

### DTO

| 식별자 | 케이스 | 형식 | 예 |
| --- | --- | --- | --- |
| 요청 DTO | UpperCamelCase | `<동사><리소스>Request`. 동사는 컨트롤러와 서비스 메서드와 같은 낱말. 만들기와 고치기는 따로(`Create`/`Update`) | `CreateCustomerRequest` |
| 응답 DTO | UpperCamelCase | 목록 `List<리소스들>Response`, 하나 `<리소스>Response` | `ListCustomersResponse`, `DraftInvoiceResponse` |
| 응답 안 요소 | UpperCamelCase | 독립 리소스면 그 리소스의 응답 타입 그대로. 그 응답에만 속하면 `<응답 리소스><JSON 필드명 단수>` | `DraftInvoiceResponse.customers`는 `DraftInvoiceCustomer` |
| 서비스 사이 전달 타입 | UpperCamelCase | `<대상><내용>` 명사구. `Request`/`Response`와 요소 접미사 없음 | `CustomerUsage` |

### 메서드

| 식별자 | 케이스 | 형식 | 예 |
| --- | --- | --- | --- |
| 서비스 메서드 | lowerCamelCase | 동사 단독. 저장 `create` `update` `delete`, 읽기 `list` `find`(없을 수 있음) `get`(없으면 예외), 계산 `aggregate` `preview`(저장 안 함), 상태 전이는 도메인 동사 | `create`, `preview`, `finalize` |
| 컨트롤러 메서드 | lowerCamelCase | `<동사><리소스>`. 동사는 서비스 메서드와 같은 낱말. 프로젝트 전역 유일(operationId가 된다) | `listCustomers`, `previewDraftInvoice` |
| 리포지토리 메서드 | lowerCamelCase | 파생 쿼리는 `find` `exists` `count` `delete` + `By...`. 읽기 힘들면 `@Query`와 서술형 이름 | `findByOrganizationIdOrderByNameAscIdAsc`, `findPage` |
| 접근자와 변경자 | lowerCamelCase | setter 없이 도메인 동사 메서드. boolean 접근자는 `isX()` | `rename(name)`, `isNew()` |
| 정적 팩터리, 변환 메서드 | lowerCamelCase | 한 인자 변환 `from`, 여러 인자 조립 `of`, 새 객체로 변환 `toX`, 뷰 `asX` | `CustomerResponse.from(customer)`, `IngestEventResponse.stored(transactionId)` |

### 필드와 변수

| 식별자 | 케이스 | 형식 | 예 |
| --- | --- | --- | --- |
| 주입 필드 | lowerCamelCase | 타입명 그대로. 같은 타입 둘 이상일 때만 수식어 | `customerRepository`, `mvc` |
| 상수, enum 상수 | UPPER_SNAKE_CASE | 깊은 불변 `static final`만 | `BILLING_ZONE`, `DRAFT` |

### 테스트

| 식별자 | 케이스 | 형식 | 예 |
| --- | --- | --- | --- |
| 테스트 클래스 | UpperCamelCase | `<대상>[<동작>][<관점>]Test`. 클래스 하나만 검증하면 `<클래스>Test`이고 대상과 같은 패키지. 관점 테스트는 도메인 루트, 도메인이 없으면 `global`, 루트에는 애플리케이션 테스트와 생성된 진입점만. 관점은 검증 대상이지 도구가 아니다 | `customer/service/CustomerServiceTest`, `event/EventIngestIntegrationTest` |
| 테스트 메서드 | 한국어 밑줄 문장 | "~한다"/"~다" 어미, `@DisplayName` 없음. 한국어는 `@Test`와 `@Nested` 이름에만. 문장 안의 영문 약어는 원 표기 그대로 | `이벤트가_있는_고객은_지울_수_없다`, `기간은_KST_월의_반열린_구간으로_넘긴다` |

## 프론트엔드 TypeScript

### 컴포넌트

| 식별자 | 케이스 | 형식 | 예 |
| --- | --- | --- | --- |
| 컴포넌트 | PascalCase | 여럿이면 복수, 하나면 단수. 화면 컴포넌트는 접미사 `Screen` `Section` `Frame` `Loading` `Meta` `Table` `FormDialog` `DeleteDialog` `Drawer`, 공용 컴포넌트는 접미사 없음. 접미사의 뜻은 `frontend/README.md` "구조" | `CustomersTable`, `FilterBar` |
| 이벤트 핸들러 | lowerCamelCase | 함수는 `handle<Event>`, prop은 `on<Event>` | `handleSubmit`, `onDelete` |
| DOM id | kebab-case | `<대상>-<요소>`. 대상은 컴포넌트 폴더 이름의 단수 | `customer-name`, `billable-metric-code` |

### 타입

| 식별자 | 케이스 | 형식 | 예 |
| --- | --- | --- | --- |
| 상태, 표시용 타입 | PascalCase | `<대상>State`, `<대상><무엇>View` | `CustomerFormState`, `BillingGroupView` |
| API 응답 타입과 함수 | PascalCase, lowerCamelCase | 응답 타입은 백엔드 스키마 이름, API 함수는 operationId 그대로 | `CustomerResponse`, `listCustomers` |

### 상수

| 식별자 | 케이스 | 형식 | 예 |
| --- | --- | --- | --- |
| 모듈 수준 상수 | UPPER_SNAKE_CASE | 모듈 수준 `const` 중 문자열, 숫자, boolean 값. 객체, 함수, Context, 폰트 인스턴스는 변수와 함수 규칙대로 | `PAGE_SIZE`, `TIMEOUT_MS` |

### 파일과 폴더

| 식별자 | 케이스 | 형식 | 예 |
| --- | --- | --- | --- |
| 컴포넌트 파일 | PascalCase | 파일명 = export하는 컴포넌트명. export는 파일당 하나, 비공개 보조 컴포넌트는 같은 파일에 둬도 된다 | `CustomerFormDialog.tsx` |
| 그 외 파일과 폴더 | kebab-case | 라우트, 컴포넌트 폴더, API 모듈은 같은 이름. 라우트 아래는 `state.ts` `actions.ts` | `lib/api/customers.ts`, `dev-state.ts` |

## API와 DB

### API

| 식별자 | 케이스 | 형식 | 예 |
| --- | --- | --- | --- |
| URL 경로 | kebab-case | 복수 명사, 동사 없음. 부모 아래 하나뿐인 하위 리소스와 셀 수 없는 명사는 단수. 버전은 `/v1/` 경로 접두사 | `/v1/billable-metrics/{code}/price-policy`, `/v1/usage` |
| 경로 변수 | snake_case | 앞 세그먼트 리소스의 식별 JSON 키. 리소스명을 되풀이하지 않고(`{id}`), 둘 이상이면 `<리소스 단수>_<키>`. 자바 변수는 camelCase | `/v1/customers/{id}`, `/v1/billable-metrics/{code}/price-policy` |
| JSON 키 | snake_case | 두 낱말 이상이거나 키가 자바 이름과 다르면 `@JsonProperty`로 지정 | `customer_id`, 수집 요청의 `occurredAt`은 `@JsonProperty("timestamp")` |
| 문자열 열거값, 오류 코드 | lowercase snake_case | 오류 코드는 상수명을 소문자로 내린 것. 따로 짓지 않는다 | `sum`, `customer_not_found` |

### DB

| 식별자 | 케이스 | 형식 | 예 |
| --- | --- | --- | --- |
| DB 테이블 | snake_case 단수 | 엔티티명이 곧 테이블명, `@Table` 불필요 | `invoice_line` |
| DB 컬럼 | snake_case | 다른 테이블을 가리키면 `<참조 테이블>_<참조 컬럼>` | `customer_id` |
| DB 제약 | snake_case | `<테이블>_<대상>_<종류>`, 종류는 `pk` `fk` `unique` `check` `not_null`이고 끝에 온다. 대상은 아래 "제약 종류별 대상" | `customer_pk`, `customer_organization_fk`, `event_customer_same_organization_fk`, `invoice_period_check` |

- **제약 종류별 대상.** PK는 대상이 없다. FK는 참조하는 테이블이고, 도입사 경계를 함께 강제하는 복합 FK는 `<테이블>_<참조 테이블>_same_organization_fk`다. UNIQUE는 키 컬럼을 순서대로 잇고 참조 컬럼의 `_id` `_code`를 뗀다. CHECK와 NOT NULL은 컬럼 하나이며 PostgreSQL 자동 이름이 이미 이 형식이라 그대로 쓴다.

## 오류 처리

예외 계층과 핸들러의 자리는 `error-handling.md`가 정하고, 여기는 이름만 적는다.

| 식별자 | 케이스 | 형식 | 예 |
| --- | --- | --- | --- |
| 오류 코드 상수 | UPPER_SNAKE_CASE | 영어 어순. 대상을 주어로 한 상태(not found, already exists, has events)는 대상 뒤, 대상을 꾸미는 한 낱말(invalid, unknown, missing)은 대상 앞, 둘 다 되는 낱말(expired, canceled)은 뒤. 대상은 엔티티 이름 그대로 | `CUSTOMER_NOT_FOUND`, `INVALID_EVENT`, `INVOICE_EXPIRED` |
| 예외 종류 클래스 | UpperCamelCase | `<종류>Exception`. 종류는 HTTP 상태의 뜻. 대상별 전용 예외는 `<대상><종류>Exception` | `NotFoundException`, `CustomerNotFoundException` |
| 핸들러 메서드 | lowerCamelCase | `handle<예외 클래스 단순명>`. `Exception`을 떼지 않는다 | `handleBusinessException`, `handleException` |

## 일괄 개명과 정합성 점검

- 규칙이 바뀌거나 표에 행이 늘면 코드 개명은 레포 전체를 한 PR로 한다. 도메인이나 층으로 가르지 않는다.
- 정합성 점검은 주기적으로 레포 전체를 훑어 한 PR로 낸다. 점검 PR은 이 파일을 따랐는지만 본다.
