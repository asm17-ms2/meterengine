# 이름 규칙

- 기본은 Google Java Style Guide, Google TypeScript Style Guide, Zalando RESTful API Guidelines, Google AIP다.
- 표는 그 가이드와 다르게 간 것과 가이드가 정하지 않은 팀 선택, 그리고 팀이 같은 꼴로 맞추기로 한 것을 적는다.
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

## 백엔드 Java

### 패키지

| 식별자 | 케이스 | 형식 | 예 |
| --- | --- | --- | --- |
| 패키지 | 소문자, 밑줄 없음 | `com.meterengine.<domain>.<layer>`. layer는 `config` `controller` `dto` `entity` `repository` `service`. 공용은 `global.<역할>`, 루트에는 진입점만 | `com.meterengine.invoice.dto`, `com.meterengine.global.config` |

### 클래스

| 식별자 | 케이스 | 형식 | 예 |
| --- | --- | --- | --- |
| 엔티티 | UpperCamelCase | 도메인 명사 그대로, 접미사 없음. 약칭 금지는 이름이 나타나는 모든 자리(URL, JSON 키, DB 컬럼까지)에 적용 | `BillableMetric` (`Metric` 아님) |
| 식별자 값 타입 | UpperCamelCase | `<엔티티>Id` | `BillableMetricId` |
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
| 접근자와 변경자 | lowerCamelCase | 엔티티 getter는 `getX()`, boolean은 `isX()`. setter 없이 도메인 동사 메서드. record는 `x()` | `isNew()`, `rename(name)`, `quantity()` |
| 정적 팩터리, 변환 메서드 | lowerCamelCase | 한 인자 변환 `from`, 여러 인자 조립 `of`, 새 객체로 변환 `toX`, 뷰 `asX` | `CustomerResponse.from(customer)`, `IngestEventResponse.stored(transactionId)` |

### 필드와 변수

| 식별자 | 케이스 | 형식 | 예 |
| --- | --- | --- | --- |
| 주입 필드 | lowerCamelCase | 타입명 그대로. 같은 타입 둘 이상일 때만 수식어 | `customerRepository`, `mvc` |
| 컬렉션 | lowerCamelCase | 복수형, 접미사 없음. Map은 `<값>By<키>` | `customerQuantities`, `priceByBillableMetricId` |
| 그 외 변수, 파라미터 | lowerCamelCase | 값의 정체를 말하는 명사구, 조회 메서드 이름과 맞춤 | `billableMetricUsage`, `targetMonth` |
| boolean | lowerCamelCase | 필드, 변수, JSON 키, DB 컬럼 전부 접두사 없는 형용사나 과거분사. `is` `has` `can`은 접근자에만 | 필드 `finalized`, 접근자 `isFinalized()` |
| 시간 | lowerCamelCase | 순간 `At`, 날짜 `Date` | `finalizedAt`, `billingDate` |
| 상수, enum 상수 | UPPER_SNAKE_CASE | 깊은 불변 `static final`만 | `BILLING_ZONE`, `DRAFT` |
| 로거 | lowerCamelCase | `logger`. `static final`이어도 상수가 아니다 | `logger` |

### 낱말

| 식별자 | 케이스 | 형식 | 예 |
| --- | --- | --- | --- |
| 약어 | 낱말처럼 camel | 낱말 하나로 보고 첫 글자만 대문자. 사전에 있는 단어는 줄이지 않고, 업계 약어(`id` `api` `url` `json` `krw`)는 그대로 | `customerId`, `OpenApiConfig` |
| 이름 길이 | | 상한 없음. DB 식별자만 PostgreSQL 63바이트 제한 |  |

### 테스트

| 식별자 | 케이스 | 형식 | 예 |
| --- | --- | --- | --- |
| 테스트 클래스 | UpperCamelCase | `<대상>[<동작>][<관점>]Test`. 클래스 하나만 검증하면 `<클래스>Test`. 관점은 검증 대상이지 도구가 아니다 | `CustomerServiceTest`, `EventIngestIntegrationTest` |
| 테스트 위치 | | 단위 테스트는 대상과 같은 패키지, 관점 테스트는 도메인 루트, 도메인이 없으면 `global`. 루트에는 애플리케이션 테스트와 생성된 진입점만 | `customer/service/CustomerServiceTest`, `global/SeedDataTest` |
| 테스트 메서드 | 한국어 밑줄 문장 | "~한다"/"~다" 어미, `@DisplayName` 없음. 한국어는 `@Test`와 `@Nested` 이름에만 | `이벤트가_있는_고객은_지울_수_없다` |
| 테스트 헬퍼, 픽스처 | lowerCamelCase, `<Domain>Fixture` | 영어 | `insertCustomer`, `InvoiceFixture` |

## 프론트엔드 TypeScript

### 컴포넌트

| 식별자 | 케이스 | 형식 | 예 |
| --- | --- | --- | --- |
| 컴포넌트 | PascalCase | 여럿이면 복수, 하나면 단수. 접미사 `Screen` `Section` `Frame` `Loading` `Meta` `Table` `FormDialog` `DeleteDialog` `Drawer`. 뜻은 아래 "화면 접미사" | `CustomersTable`, `CustomerFormDialog` |
| 훅 | `use` + PascalCase | 훅을 호출하지 않으면 `use` 금지 | `useCollapse` |
| 이벤트 핸들러 | lowerCamelCase | 함수는 `handle<Event>`, prop은 `on<Event>` | `handleSubmit`, `onDelete` |
| DOM id | kebab-case | `<대상>-<요소>`. 대상은 컴포넌트 폴더 이름의 단수이고 약칭하지 않는다 | `customer-name`, `billable-metric-code` |

- **화면 접미사.** `Section`이 데이터를 기다렸다가 결과에 따라 그리는 바깥 경계, `Screen`이 데이터가 도착한 뒤 `Section`이 그리는 화면, `Loading`이 `Section`이 기다리는 동안 그 자리에 보이는 것, `Frame`이 `Section`의 오류 상태와 `Loading`이 함께 쓰는 틀, `Meta`가 화면 제목 옆에 붙는 요약이다.

### 타입

| 식별자 | 케이스 | 형식 | 예 |
| --- | --- | --- | --- |
| props, 상태, 표시용 타입 | PascalCase | `<컴포넌트>Props`, `<대상>State`, `<대상>RowView`. `I` 접두사 없음 | `BillableMetricFormState`, `CustomerRowView` |
| API 응답 타입 | PascalCase | 백엔드 스키마 이름 그대로 | `CustomerResponse` |

### 함수, 변수, 상수

| 식별자 | 케이스 | 형식 | 예 |
| --- | --- | --- | --- |
| 함수, 변수, 프로퍼티 | lowerCamelCase | 함수는 동사구, API 함수는 백엔드 동사 어휘 | `listCustomers`, `readMonth` |
| 컬렉션 | lowerCamelCase | 복수형, 접미사 없음. Map은 `<값>By<키>` | `visibleRows`, `customerGroupByCustomerId` |
| boolean | lowerCamelCase | 변수와 상태는 `is` `has` `can` 접두사. prop과 객체 필드는 접두사 없는 형용사 | `isOpen`, prop `disabled` |
| 모듈 수준 상수 | UPPER_SNAKE_CASE | 모듈 수준 `const`는 UPPER_SNAKE_CASE, 함수 안은 lowerCamelCase. 프레임워크 예약 export(`metadata`)는 그대로 | `PAGE_SIZE`, `FORM_IDLE` |
| 약어 | 낱말처럼 camel | 백엔드의 약어 규칙과 같음 | `customerId` |

### 파일과 폴더

| 식별자 | 케이스 | 형식 | 예 |
| --- | --- | --- | --- |
| 컴포넌트 파일 | PascalCase | 파일명 = export하는 컴포넌트명. export는 파일당 하나, 비공개 보조 컴포넌트는 같은 파일에 둬도 된다 | `CustomerFormDialog.tsx` |
| 그 외 파일과 폴더 | kebab-case | 라우트, 컴포넌트 폴더, API 모듈은 같은 이름. 라우트 아래는 `state.ts` `actions.ts` | `lib/api/customers.ts`, `dev-state.ts` |

## API와 DB

### API

| 식별자 | 케이스 | 형식 | 예 |
| --- | --- | --- | --- |
| URL 경로 | kebab-case | 복수 명사, 동사 없음. 부모 아래 하나뿐인 하위 리소스와 셀 수 없는 명사는 단수 | `/v1/billable-metrics/{code}/price-policy`, `/v1/usage` |
| 쿼리 파라미터 | snake_case | JSON 키와 같은 이름 | `customer_id` |
| 경로 변수 | snake_case | 앞 세그먼트 리소스의 식별 JSON 키. 리소스명을 되풀이하지 않고(`{id}`), 둘 이상이면 `<리소스 단수>_<키>`. 자바 변수는 camelCase | `/v1/customers/{id}`, `@PathVariable("transaction_id") String transactionId` |
| JSON 키 | snake_case | 두 낱말 이상이면 `@JsonProperty`로 지정. boolean은 접두사 없는 형용사 | `customer_id`, `active` |
| 문자열 열거값, 에러 코드 | lowercase snake_case | 이미 나간 에러 코드와 통일 | `customer_not_found` |

### DB

| 식별자 | 케이스 | 형식 | 예 |
| --- | --- | --- | --- |
| DB 테이블 | snake_case 단수 | 엔티티명이 곧 테이블명, `@Table` 불필요 | `invoice_line` |
| DB 컬럼 | snake_case | 다른 테이블을 가리키면 `<참조 테이블>_<참조 컬럼>`. 순간 `_at`, 날짜 `_date`, boolean은 접두사 없는 형용사 | `customer_id`, `finalized_at` |

## 오류 처리

| 식별자 | 케이스 | 형식 | 예 |
| --- | --- | --- | --- |
| 오류 코드 enum | UpperCamelCase 단수 | `ErrorCode` | `ErrorCode` |
| 오류 코드 상수 | UPPER_SNAKE_CASE | 영어 어순. 낱말 순서는 아래 "오류 코드 상수의 낱말 순서" | `CUSTOMER_NOT_FOUND`, `INVALID_EVENT`, `INVOICE_EXPIRED` |
| 오류 코드 문자열 | lowercase snake_case | 상수명을 소문자로 내린 것. 따로 짓지 않는다 | `customer_not_found` |
| 예외 부모 | UpperCamelCase | `BusinessException`. 직접 던지지 않는다 | `BusinessException` |
| 예외 종류 클래스 | UpperCamelCase | `<종류>Exception`. 종류는 HTTP 상태의 뜻이고 상태 하나에 클래스 하나 | `NotFoundException`(404), `ConflictException`(409), `InvalidRequestException`(400) |
| 전용 예외 | UpperCamelCase | `<대상><종류>Exception`. 서비스가 타입으로 잡아야 할 때만 | `CustomerNotFoundException` |
| 전역 핸들러 | UpperCamelCase | `GlobalExceptionHandler` 하나. 컨트롤러별 짝 핸들러는 없다 | `GlobalExceptionHandler` |
| 핸들러 메서드 | lowerCamelCase | `handle<예외 클래스 단순명>`. `Exception`을 떼지 않는다 | `handleBusinessException`, `handleException` |
| 오류 응답 | UpperCamelCase | `ErrorResponse`. 안의 필드 오류 요소는 `ErrorResponse.FieldError` | `ErrorResponse` |
| 패키지 역할 | lowercase 한 단어 | `global.error` | `com.meterengine.global.error` |

- **오류 코드 상수의 낱말 순서.** 대상을 주어로 한 문장이 되는 상태(is not found, already exists, has events)는 대상 뒤에 붙고(`<대상>_<상태>`), 대상을 꾸미는 한 낱말(invalid, unknown, missing)은 대상 앞에 온다(`<형용사>_<대상>`). 둘 다 되는 낱말(expired, canceled)은 문장형으로 뒤에 둔다. 대상은 엔티티 이름 그대로이고 약칭하지 않는다.

## 일괄 개명과 정합성 점검

- 규칙이 바뀌거나 표에 행이 늘면 코드 개명은 레포 전체를 한 PR로 한다. 도메인이나 층으로 가르지 않는다.
- 정합성 점검은 주기적으로 레포 전체를 훑어 한 PR로 낸다. 점검 PR은 이 파일을 따랐는지만 본다.
