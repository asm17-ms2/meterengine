# 이름 규칙

규칙은 아래 표다. 왜 이렇게 정했는지와 검토한 대안은 RFC-001에 있다. 표준이 답하는 항목은 표준을 따르고, 표준이 침묵하는 항목은 팀이 다르게 지을 여지가 가장 작은 쪽을 골랐다.

## 원칙

표의 모든 행은 아래 원칙에서 나온다. 표에 없는 경우를 만나면 이 원칙으로 짓는다.

- **읽는 자리에서 답이 나온다.** 선언부로 올라가지 않아도 호출부만 보고 무엇(대상)이고 어떤 종류(계층, 역할)인지 안다. 그래서 주입 필드는 타입명, 클래스는 계층 접미사, 테스트는 관점 접미사다 ([Google TS][gts] "이름은 새 독자에게 서술적이고 분명해야 한다")
- **같은 개념에 같은 낱말, 다른 개념에 다른 낱말.** 한 대상을 파일마다 다르게 부르지 않고, 엔티티를 약칭하지 않는다 ([AIP-140][aip-140])
- **타입과 문맥에 이미 있는 정보를 되풀이하지 않는다.** 컬렉션에 `List`를, 클래스가 말하는 대상을 메서드에, 응답 리소스명을 그 필드에 다시 적지 않는다 ([Google TS][gts] "타입에 있는 정보로 이름을 꾸미지 않는다")
- **줄이지 않는다.** 사전에 있는 낱말은 약어로 만들지 않고 이름 길이에 상한을 두지 않는다. 주석이 없으므로 이름이 길어지는 쪽이 뜻이 빠지는 쪽보다 싸다 ([Google Java 5.3][gj-5.3], [Google TS][gts])
- **고를 자유도를 남기지 않는다.** 팀이 같은 것을 다르게 부를 수 있는 규칙은 규칙이 아니다. 형식이 하나로 정해지는 쪽을 택한다 (우리 기준. 표준 문서가 침묵하는 항목을 가르는 데 썼다)

규칙은 아래 표가 전부다. 표에서 찾고, 이유가 궁금하면 근거 열과 RFC-001의 검토한 선택지를 본다. 예 열은 규칙을 적용한 이름이라 지금 코드와 다를 수 있다. 코드 개명은 별도 티켓에서 한다. 근거 열은 칸마다 종류를 앞에 붙였다. 어기면 깨지는 것과 권고를 같은 무게로 읽지 않기 위해서다.

- 명세: 규범 문서가 정한 규칙. 강제인지는 조항마다 다르다. OpenAPI 3.1의 `operationId` 유일은 강제이고 JavaBeans의 `getX`/`isX`는 권고다
- 도구 동작: 명세가 아니라 구현이 그렇게 도는 것. 어기면 깨지고, 버전이나 설정으로 바뀔 수 있다. springdoc이 메서드명을 `operationId`로 쓰고 Spring Data가 `findBy`를 파싱한다
- 스타일 가이드: 한 조직이 정해 공개한 규칙집. 널리 쓰이는지는 별개다. Google Java Style Guide, AIP, Zalando
- 관례: 실물이 그렇게 하고 있는 것. 문서로 정한 적은 없다. JDK의 `Collectors`, PetClinic의 `OwnerRepository`

외부 근거 없이 팀이 정한 행은 종류 대신 출처를 적었다. 이미 코드에 있으면 그 커밋, 노션 제안서에서 정했으면 그 제안서, RFC-001이 처음 정하면 "RFC-001"이다. 링크는 문서 끝 참고 문서에 모았다.

## 백엔드 Java

### 패키지

| 식별자 | 케이스 | 형식 | 예 | 근거 |
| --- | --- | --- | --- | --- |
| 패키지 | 소문자, 밑줄 없음 | `com.meterengine.<domain>.<layer>`, domain은 단수 한 단어, layer는 `controller` `dto` `entity` `repository` `service`. 도메인 없는 공용 코드는 `global.<역할>`. 최상위 루트에는 진입점만 | `com.meterengine.invoice.dto`, `com.meterengine.global.config.OpenApiConfig` | 스타일 가이드: [Google Java 5.2.1][gj-5.2.1](소문자). 관례: 루트에 진입점만 두는 것은 [Spring Boot 레퍼런스 코드 구조][sb-structure]의 예제 배치와 [PetClinic][petclinic]. 도메인 없는 코드를 한 패키지에 모으는 것은 PetClinic의 `system`. 이름 `global`과 단복수는 RFC-001 |

예외와 예외 핸들러의 이름과 패키지는 추후 별도의 오류 처리 RFC로 결정한다.

### 클래스

| 식별자 | 케이스 | 형식 | 예 | 근거 |
| --- | --- | --- | --- | --- |
| 엔티티 | UpperCamelCase | 도메인 명사 그대로, 접미사 없음, 약칭 금지(`Metric` 아니고 `BillableMetric`). 약칭 금지는 엔티티 낱말이 나타나는 모든 자리에 적용된다. 자바 클래스와 변수(DTO, 서비스, 예외 이름 안의 엔티티 낱말 포함), 프론트 타입과 라우트와 API 모듈 파일, URL 경로와 경로 변수, JSON 키와 쿼리 파라미터, 다른 테이블에서 이 엔티티를 가리키는 컬럼(DB 컬럼 행)이 대상이다(`BillableMetric`이면 `/v1/billable-metrics`, `billable_metric_code`). 패키지만 대상이 아니다. 패키지는 엔티티가 아니라 도메인의 이름이고(`metric` 패키지에 `BillableMetric`과 `MetricUsage`가 같이 산다) 패키지 행이 한 단어로 정했다 | `Customer`, `BillableMetric` | 명세: [JLS §6.1][jls-6.1](클래스명은 서술적 명사), [Jakarta Persistence][jpa-spec](엔티티 이름 기본값이 클래스 단순명이라 접미사가 없어야 함). 약칭 금지는 [클래스명 제안서][prop-class]. 적용 범위는 RFC-001 |
| 식별자 값 타입 | UpperCamelCase | `<엔티티>Id` | `BillableMetricId` | 스타일 가이드: [Google Java 5.3][gj-5.3](`Id`) |
| 리포지토리 | UpperCamelCase | `<엔티티>Repository` | `InvoiceLineRepository` | 관례: [Spring Data JPA 레퍼런스][sd-repos] `PersonRepository`, [Spring 가이드][guide-jpa] `CustomerRepository`, [PetClinic][petclinic] `OwnerRepository` |
| 서비스 | UpperCamelCase | `<대상>Service`, 동작 하나면 `<대상><동작명사>Service` | `CustomerService`, `EventIngestionService` | 스타일 가이드: [Google Java 5.2.2][gj-5.2.2](클래스명은 명사구라 동작도 명사형). 관례: `<대상>Service`는 [Spring Security][ss-uds] `UserDetailsService` |
| 컨트롤러 | UpperCamelCase | `<대상>Controller` | `CustomerController` | 관례: [Spring 가이드][guide-rest] `GreetingController`, [PetClinic][petclinic] `OwnerController` |
| 설정 | UpperCamelCase | `<무엇>Config`, 외부 설정값 `<무엇>Properties` | `OpenApiConfig`, `TossPaymentsProperties` | 관례: `Properties`는 [Spring Boot 레퍼런스][sb-props] 예제 `MyProperties`, `Config`는 [Spring 가이드][guide-security] 예제 `WebSecurityConfig` |
| 상수 묶음 클래스 | UpperCamelCase 복수 | | `ErrorCodes` | 관례: JDK [`Collectors`][jdk-collectors], `Collections`, `Objects` |
| 쓰지 않는 접미사 | | 우리 체계와 겹치는 다른 관례의 접미사 `Dto` `Vo` `Cmd` `Json` `Model` `Entity` `Impl`, 하는 일을 말하지 않는 접미사 `Util` `Helper` `Manager`, 요소라는 것만 말하는 접미사 `Entry` `Item` `Row` `Data` `Info`. 한 개념에 이름이 둘 생기는 것을 막고, 이름이 무엇인지를 말하게 한다 | | RFC-001 |

### DTO

| 식별자 | 케이스 | 형식 | 예 | 근거 |
| --- | --- | --- | --- | --- |
| 요청 DTO | UpperCamelCase | `<동사><리소스>Request`, 동사는 컨트롤러 메서드와 서비스 메서드와 같은 낱말. 만들기와 고치기는 요청을 따로 둔다(`Create`/`Update`), `Save` 하나로 합치지 않는다 | `CreateCustomerRequest`, `UpdateCustomerRequest`, `IngestEventRequest` | 스타일 가이드: [AIP-133][aip-133], [AIP-134][aip-134] |
| 응답 DTO | UpperCamelCase | 목록 `List<리소스들>Response`, 하나 `<리소스>Response` | `ListCustomersResponse`, `DraftInvoiceResponse` | 스타일 가이드: 목록은 [AIP-132][aip-132]. 단건 접미사는 [4945e92][l-response]([AIP-131][aip-131]과 [AIP-133][aip-133]은 리소스 자체를 응답으로 함) |
| 응답 안 요소 | UpperCamelCase | 요소가 독립 리소스면 그 리소스의 응답 타입을 그대로 씀(목록 응답의 요소 = 단건 응답 타입). 그 응답에만 속하는 객체는 `<응답 리소스><JSON 필드명 단수>`. 중첩 record로 두더라도 이름은 이 형식(springdoc이 바깥 클래스명을 버리므로 접두어가 부모 역할) | `ListCustomersResponse.customers`는 `CustomerResponse`, `DraftInvoiceResponse.customers`는 `DraftInvoiceCustomer`, `lines`는 `DraftInvoiceLine` | 스타일 가이드: [AIP-132][aip-132](목록 요소는 리소스 자체). 관례: [Stripe SDK][stripe-java-invoice] `Invoice.StatusTransitions`, [Google Calendar API][gcal-events] `Event.reminders`와 `EventAttendee`(필드명으로 부르고 부모로 한정). 도구 동작: [springdoc][springdoc]이 스키마 이름을 클래스 단순명으로 만들고, [OpenAPI Generator][oag-inline]는 인라인 스키마를 `<부모>_<필드>`로 만든다. 접두어 형식은 [1eb7906][l-prefix] |
| 서비스 사이 전달 타입 | UpperCamelCase | `<대상><내용>` 명사구. HTTP를 타지 않으므로 `Request`/`Response` 없음, 요소 접미사(`Entry` `Row`)도 없음 | `BillableMetricUsage`, `CustomerUsage`, `CustomerQuantity` | 명세: [JLS §6.1][jls-6.1](명사구). 접미사를 빼는 것은 RFC-001 |

### 메서드

| 식별자 | 케이스 | 형식 | 예 | 근거 |
| --- | --- | --- | --- | --- |
| 서비스 메서드 | lowerCamelCase | 동사 단독(클래스가 대상을 말함). 저장하는 것 `create` `update` `delete`, 읽는 것 `list`(여럿, 필터와 페이지가 있어도 `list`이며 `query` `search`를 따로 두지 않음) `find`(하나, 없을 수 있음) `get`(하나, 없으면 예외), 계산하는 것 `aggregate`(집계 결과) `preview`(저장하지 않는 결과), 상태 전이는 도메인 동사. 동사는 컨트롤러 메서드, 요청 DTO와 같은 낱말 | `create`, `list`, `preview`, `finalize` | 스타일 가이드: [Google Java 5.2.3][gj-5.2.3]은 동사 또는 동사구까지만 정함. 관례: 동사 단독은 Spring Data [`CrudRepository`][sd-crud]의 `save` `delete` `count`와 JPA [`EntityManager`][jpa-em]의 `persist` `find` `remove`(타입이 대상을 말하면 메서드는 동사만). `find`/`get` 구분은 `EntityManager.find`(없으면 null)와 `getReference`(없으면 예외)의 짝. 스타일 가이드: `list`가 필터와 페이지를 갖는 것은 [AIP-132][aip-132]와 [AIP-160][aip-160]. 관례: `create` `update` `delete` 쌍은 Spring Security [`UserDetailsManager`][ss-udm]의 `createUser` `updateUser` `deleteUser`(리포지토리의 `save`는 upsert라 만들기와 고치기는 서비스 계층이 가름). 나머지 어휘 목록은 RFC-001 |
| 컨트롤러 메서드 | lowerCamelCase | `<동사><리소스>`, 동사는 서비스 메서드와 같은 낱말, 프로젝트 전역 유일. springdoc이 메서드명을 operationId로 쓰고 OpenAPI가 유일을 요구하며, 프론트 생성 클라이언트의 함수명이 된다 | `listCustomers`, `ingestEvents`, `previewDraftInvoice` | 명세: [OpenAPI 3.1][oas-op](`operationId` 유일, 강제). 도구 동작: [springdoc][springdoc]이 메서드명을 `operationId`로 씀 |
| 리포지토리 메서드 | lowerCamelCase | 파생 쿼리는 `find` `exists` `count` `delete` + `By...`만(Spring Data는 `find`와 `get`을 동의어로 보므로 `find`만 씀). `OrderBy` 둘 이상이나 조건 셋 이상이면 `@Query`, JPQL이 이름과 어긋나면 서술형 이름 | `findByOrganizationId`, `findBaseUnitPrices` | 도구 동작: [Spring Data][sd-query]가 접두사를 파싱해 쿼리를 만들고 `find` `read` `get` `query` `search` `stream`을 동의어로 봄. `@Query` 전환 기준은 RFC-001 |
| 접근자와 변경자 | lowerCamelCase | JPA 엔티티의 getter는 `getX()`, boolean은 `isX()`. setter를 두지 않고 상태 변경은 뜻을 말하는 도메인 동사 메서드로. record는 컴포넌트 접근자 `x()`, record 안 파생 값은 `get` 없는 명사 메서드 | `getSupplyAmount()`, `isNew()`, `rename(name)`, `quantity()`, `totalAmount()` | 명세: [JavaBeans][javabeans] 8.3(`getX`, boolean `isX`, 권고), [JLS §8.10.3][jls-8.10.3](record 접근자). setter 없음은 RFC-001 |
| 정적 팩터리, 변환 메서드 | lowerCamelCase | 한 인자 변환 `from`, 여러 인자 조립 `of`, 새 객체로 변환 `toX`, 뷰 `asX` | `CustomerResponse.from(customer)` | 관례: JDK [`LocalDate.of`][jdk-localdate], `Instant.from`, [`List.of`][jdk-list], `Collectors.toList`, `Arrays.asList` |

### 필드와 변수

| 식별자 | 케이스 | 형식 | 예 | 근거 |
| --- | --- | --- | --- | --- |
| 주입 필드 | lowerCamelCase | 타입명 그대로, 같은 타입 둘 이상일 때만 수식어 | `customerRepository` | 관례: [Spring Framework 레퍼런스][sf-di] DI 예제 `private final MovieFinder movieFinder`. [PetClinic][petclinic]은 `owners`와 `vetRepository`가 섞임. 적용은 [2f48aea][l-inject] |
| 컬렉션 | lowerCamelCase | 복수형, 접미사 없음. Map은 `<값>By<키>` | `customerQuantities`, `priceByBillableMetricId` | 스타일 가이드: 복수형은 [Zalando 규칙 120][zalando-120], [AIP-140][aip-140](API 필드 규칙을 변수명에 유추 적용). Map 형식은 [b1f70fe][l-map](`unitPriceByMetricCode`) |
| 그 외 변수, 파라미터 | lowerCamelCase | 값의 정체를 말하는 명사구, 조회 메서드 이름과 맞춤 | `baseUnitPrices`, `billableMetricUsage` | 스타일 가이드: [Google Java 5.2.7][gj-5.2.7](케이스). 명사구와 조회 메서드 일치는 [ea7abd2][l-nounphrase](`baseUnitPrices` = `findBaseUnitPrices`) |
| boolean | lowerCamelCase | 필드, 변수, record 컴포넌트, JSON 키, DB 컬럼 전부 접두사 없는 형용사나 과거분사로 같은 이름. 접두사 `is` `has` `can`은 접근자 메서드에만 | 필드 `finalized`, 접근자 `isFinalized()`, JSON과 컬럼 `finalized` | 명세: [JavaBeans][javabeans] 8.3.2(접근자 `isX()`). 스타일 가이드: [Google Java][gj]는 필드 접두사 규칙 없음, JSON 접두사 생략은 [AIP-140][aip-140]. 한 값에 이름 하나를 두어 `@Column`과 `@JsonProperty`로 이름을 맞출 일을 없애는 것은 RFC-001 |
| 시간 | lowerCamelCase | 순간 `At`, 날짜 `Date` | `finalizedAt`, `billingDate` | 스타일 가이드: [Zalando 규칙 235][zalando-235](`_at`와 `_date` 둘 다 허용), [AIP-142][aip-142]는 `_time`. 관례: [Spring Data 감사 예제][sd-audit]는 `createdDate`. Java 표준 없음, JSON 키와 1:1 대응은 RFC-001 |
| 상수, enum 상수 | UPPER_SNAKE_CASE | 깊은 불변 `static final`만 | `BILLING_ZONE`, `DRAFT` | 스타일 가이드: [Google Java 5.2.4][gj-5.2.4] |
| 타입 변수 | `T` 또는 `XxxT` | | `RequestT` | 스타일 가이드: [Google Java 5.2.8][gj-5.2.8] |

### 낱말

| 식별자 | 케이스 | 형식 | 예 | 근거 |
| --- | --- | --- | --- | --- |
| 약어 | 낱말처럼 camel | 케이스 규칙이지 허용 목록이 아니다. 약어는 낱말 하나로 보고 첫 글자만 대문자(`Api`, `Url`, `Jdbc`). 사전에 있는 단어는 줄이지 않는다(`customer`를 `cust`로 쓰지 않음). 업계에서 약어로만 쓰는 것(`id` `api` `url` `pg` `vat` `krw` `kst` `utc` `json` `jdbc`)은 그대로 쓴다 | `customerId`, `OpenApiConfig`, `jdbcTemplate`, `formatKrw` | 스타일 가이드: [Google Java 5.3][gj-5.3]. 도구 동작: [Checkstyle google_checks][checkstyle-abbr]가 같은 규칙을 검사함 |
| 이름 길이 | | 상한 없음. 같은 정보가 두 번 들어가면 줄임. DB 식별자만 PostgreSQL 63바이트 제한 | | RFC-001. 주석 없는 코드라 긴 쪽 |

### 테스트

| 식별자 | 케이스 | 형식 | 예 | 근거 |
| --- | --- | --- | --- | --- |
| 테스트 클래스 | UpperCamelCase | `<대상>[<동작>][<관점>]Test`. 대상은 클래스, 리소스, 또는 산출물(시드, OpenAPI 문서, 스키마 제약). 대상이 클래스 하나이고 그 클래스만 검증하면 동작과 관점 없이 `<클래스>Test`. 관점은 무엇을 검증하는지 말하는 명사이지 쓰는 도구가 아니며(`MockMvc` `Testcontainers` 아님), 같은 성질에 다른 낱말을 만들지 않는다. 동작은 한 연산만 다룰 때 넣고 서비스 동사와 같은 낱말 | `CustomerServiceTest`, `EventIngestIntegrationTest`, `CustomerDeleteConcurrencyTest`, `InvoiceRoundTripTest`, `SeedDataTest` | 스타일 가이드: [Google Java 5.2.2][gj-5.2.2](클래스 하나를 다루면 그 클래스명 + `Test`, 아니면 `HashIntegrationTest`처럼), [네이버 핵데이][naver]. 관례: Spring 자체는 `Tests`. 관점의 기준은 RFC-001 |
| 테스트 위치 | | 단위 테스트(`<클래스>Test`)는 대상과 같은 패키지. 관점이 붙는 테스트는 도메인 루트. 도메인에 속하지 않는 것은 `global`이며, 공용 클래스의 단위 테스트는 그 클래스와 같은 `global.<역할>`, 산출물 테스트(시드, OpenAPI 문서, 스키마 제약)와 관점이 붙는 테스트는 `global` 바로 아래. 루트에는 애플리케이션 테스트만. 메인 코드의 루트에 진입점만 두는 것과 같은 기준 | `customer/service/CustomerServiceTest`, `invoice/InvoiceRoundTripTest`, `global/SeedDataTest` | 관례: 대상과 같은 패키지는 [PetClinic][petclinic]. 관점이 붙는 테스트를 도메인 루트에 둔 것은 [4945e92][l-test-loc]. `global`은 패키지 행 |
| 테스트 메서드 | 한국어 밑줄 문장 | "~한다"/"~다" 어미, `@DisplayName` 없음. 한국어는 `@Test`와 `@Nested` 이름에만 | `이벤트가_있는_고객은_지울_수_없다` | 스타일 가이드: [Google Java 5.1][gj-5.1](ASCII만)의 의도적 예외. 관례: [prolog][prolog], [2023-zipgo][zipgo]. 적용은 [4945e92][l-test-ko] |
| 테스트 헬퍼, 픽스처 | lowerCamelCase, `<Domain>Fixture` | 영어 | `insertCustomer`, `InvoiceFixture` | 스타일 가이드: [Google Java 5.1][gj-5.1](ASCII만). 한국어 예외를 테스트 메서드로 한정한 결과 |

## 프론트엔드 TypeScript

### 컴포넌트

| 식별자 | 케이스 | 형식 | 예 | 근거 |
| --- | --- | --- | --- | --- |
| 컴포넌트 | PascalCase | 여럿이면 복수, 하나면 단수, 셀 수 없는 명사(`Billing` `Usage`)는 그대로. 접미사 `Screen` `Section` `Table` `FormDialog` `DeleteDialog` `Drawer`, 공용은 역할명 | `CustomersTable`, `CustomerFormDialog`, `UsageTable`, `Pagination` | 도구 동작: React가 소문자 이름을 DOM 태그로 봄([React 문서][react-component]). 관례: 복수형은 [next-learn][next-learn]. 접미사 목록은 RFC-001 |
| 훅 | `use` + PascalCase | 훅을 호출하지 않으면 `use` 금지 | `useCollapse` | 도구 동작: `use` 접두사로 훅 규칙을 검사함([React 문서][react-hooks], eslint-plugin-react-hooks) |
| 이벤트 핸들러 | lowerCamelCase | 함수는 `handle<Event>`, prop은 `on<Event>` | `handleSubmit`, `onDelete` | 관례: [React 문서][react-events]가 `handle`/`on`을 관례로 소개 |

### 타입

| 식별자 | 케이스 | 형식 | 예 | 근거 |
| --- | --- | --- | --- | --- |
| props, 상태, 표시용 타입 | PascalCase | `<컴포넌트>Props`, `<대상>State`, `<대상>RowView`. `I` 접두사 없음 | `BillableMetricFormState`, `CustomerRowView` | 스타일 가이드: [Google TS][gts](`I` 접두사 금지), [Palmer Group TS 가이드][palmer](`<Component>Props`/`State`). `RowView`는 [6e96e33][l-rowview] |
| API 응답 타입 | PascalCase | 백엔드 스키마 이름 그대로. `Response`가 붙어 있으면 선 위의 모양, `RowView`면 화면용, `State`면 폼 상태로 구분해 읽는다 | `CustomerResponse`, `DraftInvoiceCustomer` | RFC-001. 이름이 같아야 openapi.yaml에서 바로 찾는다 |

### 함수, 변수, 상수

| 식별자 | 케이스 | 형식 | 예 | 근거 |
| --- | --- | --- | --- | --- |
| 함수, 변수, 프로퍼티 | lowerCamelCase | 함수는 동사구, API 함수는 백엔드 동사 어휘 | `listCustomers`, `readMonth` | 스타일 가이드: [Google TS][gts], [Airbnb 23.2][airbnb] |
| boolean | lowerCamelCase | `is` `has` `can` 접두사 | `isOpen` | 관례: [typescript-eslint 문서 예시][ts-eslint-naming]. 스타일 가이드: [Palmer Group][palmer]. 표준 없음 |
| 모듈 수준 상수 | UPPER_SNAKE_CASE | export, const, `as const`일 때만. 초기값은 `<STATE>_IDLE`. 함수 안의 const는 lowerCamelCase. 프레임워크가 이름을 정한 export(`metadata`, `config` 등 Next.js 예약)는 그 이름 그대로 | `FORM_IDLE`, `PAGE_SIZE` | 스타일 가이드: [Google TS][gts], [Airbnb 23.10][airbnb]. 도구 동작: 예약 export는 [Next.js][next-metadata]가 이름으로 찾음 |
| 약어 | 낱말처럼 camel | 백엔드의 약어 규칙과 같음 | `customerId`, `apiClient` | 스타일 가이드: [Google TS][gts]. [Airbnb 23.9][airbnb](전부 대문자)와 다르게 감 |

### 파일과 폴더

| 식별자 | 케이스 | 형식 | 예 | 근거 |
| --- | --- | --- | --- | --- |
| 컴포넌트 파일 | PascalCase | 파일명 = 컴포넌트명, 파일 하나에 하나 | `CustomerFormDialog.tsx` | 스타일 가이드: [Airbnb React][airbnb-react] |
| 그 외 파일과 폴더 | kebab-case | 라우트, 컴포넌트 폴더, API 모듈은 같은 이름. 라우트 아래는 `state.ts` `actions.ts`. 공용 폴더는 역할명 단수 | `lib/api/customers.ts`, `dev-state.ts` | 도구 동작: [Next.js 예약 파일][next-files]. 관례: [next-learn][next-learn] |

## API와 DB

### API

| 식별자 | 케이스 | 형식 | 예 | 근거 |
| --- | --- | --- | --- | --- |
| URL 경로 | kebab-case | 복수 명사, 동사 없음. 부모 아래 하나뿐인 하위 리소스는 단수 | `/v1/price-policies`, `/v1/billable-metrics/{code}/price-policy` | 스타일 가이드: [Zalando 규칙 129][zalando-129], [134][zalando-134], [141][zalando-141]. [AIP-122][aip-122], 단수 하위 리소스는 [AIP-156][aip-156] |
| 쿼리 파라미터 | snake_case | JSON 키와 같은 이름 | `customer_id` | 스타일 가이드: [Zalando 규칙 130][zalando-130] |
| 경로 변수 | snake_case | 바로 앞 세그먼트가 가리키는 리소스의 식별 JSON 키와 같은 이름. 앞 세그먼트가 리소스를 말하므로 리소스명을 되풀이하지 않는다(`{customer_id}` 아니고 `{id}`). 한 경로에 식별자가 둘 이상이면 모두 `<리소스 단수>_<키>`로 한정한다(OpenAPI가 같은 이름을 허용하지 않음). URL의 이름은 snake_case로 어노테이션 값에 적고, 그 값을 받는 자바 변수는 변수 규칙대로 camelCase로 짓는다(`{transaction_id}`는 `@PathVariable("transaction_id") String transactionId`, 한 단어라 둘이 같으면 `@PathVariable String code`). 쿼리 파라미터의 `@RequestParam(name = "customer_id") UUID customerId`와 같은 방식 | `/v1/customers/{id}`, `/v1/billable-metrics/{code}/price-policy`, `/v1/events/{transaction_id}`(아직 없는 경로, 이벤트의 식별 키가 `transaction_id`) | 스타일 가이드: [Zalando 규칙 129][zalando-129]는 세그먼트만 kebab으로 정하고 변수는 예제가 kebab(`{shipment-order-id}`), [AIP-122][aip-122]는 `{book}`(리소스 단수, 접미사 없음). 표준이 갈려 쿼리 파라미터 행([Zalando 규칙 130][zalando-130])에 맞춘 것과 되풀이하지 않는 것은 RFC-001 |
| JSON 키 | snake_case | `@JsonProperty`로 지정, boolean은 접두사 없는 형용사 | `customer_id`, `active` | 스타일 가이드: [Zalando 규칙 118][zalando-118], boolean 접두사 생략은 [AIP-140][aip-140]. 관례: [Stripe][stripe]. Google REST와 Azure는 camelCase라 Stripe/Zalando 계열을 택함 |
| 문자열 열거값, 에러 코드 | lowercase snake_case | 이미 나간 에러 코드와 통일 | `customer_not_found` | 관례: [Stripe][stripe]. 스타일 가이드: [Zalando 규칙 240][zalando-240]과 [AIP-126][aip-126]은 UPPER. 이미 나간 에러 코드([4945e92][l-has-code])를 깨지 않으려고 Stripe를 따름 |

### DB

| 식별자 | 케이스 | 형식 | 예 | 근거 |
| --- | --- | --- | --- | --- |
| DB 테이블 | snake_case 단수 | 엔티티명이 곧 테이블명, `@Table` 불필요 | `invoice_line` | 도구 동작: [Spring Boot 기본 네이밍 전략][sb-naming]이 엔티티명을 그대로 씀(`TelephoneNumber`가 `telephone_number`). 스타일 가이드: [SQL Style Guide][sqlstyle]는 복수. 관례: [PetClinic][petclinic]도 복수 |
| DB 컬럼 | snake_case | 다른 테이블을 가리키는 컬럼은 `<참조 테이블>_<참조 컬럼>`, FK 제약이 없어도 같다. 순간 `_at`, 날짜 `_date`, boolean은 접두사 없는 형용사 | `customer_id`, `billable_metric_code`, `finalized_at` | 명세: [Jakarta Persistence `@JoinColumn`][jpa-joincolumn]의 기본 이름이 참조 필드명 + `_` + 참조 PK 컬럼. 관례: 참조 컬럼은 [Rails Active Record][rails-ar](`singularized_table_name_id`, `line_item_id`)와 지금 스키마의 `organization_id` `customer_id` `invoice_id`. 스타일 가이드: `_id` `_date`는 [SQL Style Guide][sqlstyle], `_at`는 [Zalando 규칙 235][zalando-235](JSON 키와 맞춤) |


## 참고 문서

표의 링크가 가리키는 곳이다. 절 번호가 있는 링크는 그 절로 바로 간다. 근거의 종류별 묶음과 커밋 인용 표는 RFC-001 참고 문서 절에 있다.

[aip-122]: https://google.aip.dev/122
[aip-126]: https://google.aip.dev/126
[aip-131]: https://google.aip.dev/131
[aip-132]: https://google.aip.dev/132
[aip-133]: https://google.aip.dev/133
[aip-134]: https://google.aip.dev/134
[aip-140]: https://google.aip.dev/140
[aip-142]: https://google.aip.dev/142
[aip-156]: https://google.aip.dev/156
[aip-160]: https://google.aip.dev/160
[airbnb]: https://github.com/airbnb/javascript#naming-conventions
[airbnb-react]: https://github.com/airbnb/javascript/tree/master/react#naming
[checkstyle-abbr]: https://checkstyle.sourceforge.io/checks/naming/abbreviationaswordinname.html
[gcal-events]: https://developers.google.com/calendar/api/v3/reference/events
[gj]: https://google.github.io/styleguide/javaguide.html
[gj-5.1]: https://google.github.io/styleguide/javaguide.html#s5.1-identifier-names
[gj-5.2.1]: https://google.github.io/styleguide/javaguide.html#s5.2.1-package-names
[gj-5.2.2]: https://google.github.io/styleguide/javaguide.html#s5.2.2-class-names
[gj-5.2.3]: https://google.github.io/styleguide/javaguide.html#s5.2.3-method-names
[gj-5.2.4]: https://google.github.io/styleguide/javaguide.html#s5.2.4-constant-names
[gj-5.2.7]: https://google.github.io/styleguide/javaguide.html#s5.2.7-local-variable-names
[gj-5.2.8]: https://google.github.io/styleguide/javaguide.html#s5.2.8-type-variable-names
[gj-5.3]: https://google.github.io/styleguide/javaguide.html#s5.3-camel-case
[gts]: https://google.github.io/styleguide/tsguide.html#identifiers
[guide-jpa]: https://spring.io/guides/gs/accessing-data-jpa
[guide-rest]: https://spring.io/guides/gs/rest-service
[guide-security]: https://spring.io/guides/gs/securing-web
[javabeans]: https://www.oracle.com/java/technologies/javase/javabeans-spec.html
[jdk-collectors]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/stream/Collectors.html
[jdk-list]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/List.html
[jdk-localdate]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/time/LocalDate.html
[jls-6.1]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#jls-6.1
[jls-8.10.3]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.10.3
[jpa-em]: https://jakarta.ee/specifications/persistence/3.2/apidocs/jakarta.persistence/jakarta/persistence/entitymanager
[jpa-joincolumn]: https://jakarta.ee/specifications/persistence/3.2/apidocs/jakarta.persistence/jakarta/persistence/joincolumn
[jpa-spec]: https://jakarta.ee/specifications/persistence/3.2/jakarta-persistence-spec-3.2
[l-has-code]: https://github.com/asm17-ms2/meterengine/blob/4945e92/backend/src/main/java/com/meterengine/ErrorCodes.java#L117
[l-inject]: https://github.com/asm17-ms2/meterengine/blob/2f48aea/backend/src/main/java/com/meterengine/invoice/service/DraftInvoiceService.java#L27
[l-map]: https://github.com/asm17-ms2/meterengine/blob/b1f70fe/backend/src/main/java/com/meterengine/pricing/service/PricePolicyService.java#L48
[l-nounphrase]: https://github.com/asm17-ms2/meterengine/blob/ea7abd2/backend/src/main/java/com/meterengine/invoice/service/DraftInvoiceService.java#L45
[l-prefix]: https://github.com/asm17-ms2/meterengine/blob/1eb7906/backend/src/main/java/com/meterengine/invoice/dto/DraftInvoiceResponse.java#L32
[l-response]: https://github.com/asm17-ms2/meterengine/blob/4945e92/backend/src/main/java/com/meterengine/customer/dto/CustomerResponse.java#L15
[l-rowview]: https://github.com/asm17-ms2/meterengine/blob/6e96e33/frontend/src/components/events/EventTable.tsx#L13
[l-test-ko]: https://github.com/asm17-ms2/meterengine/blob/4945e92/backend/src/test/java/com/meterengine/customer/CustomerCrudIntegrationTest.java#L156
[l-test-loc]: https://github.com/asm17-ms2/meterengine/blob/4945e92/backend/src/test/java/com/meterengine/customer/CustomerCrudIntegrationTest.java
[naver]: https://naver.github.io/hackday-conventions-java/
[next-files]: https://nextjs.org/docs/app/api-reference/file-conventions
[next-learn]: https://github.com/vercel/next-learn
[next-metadata]: https://nextjs.org/docs/app/api-reference/functions/generate-metadata
[oag-inline]: https://openapi-generator.tech/docs/customization/#inline-schema-naming
[oas-op]: https://spec.openapis.org/oas/v3.1.0.html#operation-object
[palmer]: https://github.com/palmerhq/typescript
[petclinic]: https://github.com/spring-projects/spring-petclinic
[prolog]: https://github.com/woowacourse/prolog
[prop-class]: https://app.notion.com/p/3cd0899b32b881aa84e0cb60941bc3b6
[rails-ar]: https://guides.rubyonrails.org/active_record_basics.html#schema-conventions
[react-component]: https://react.dev/learn/your-first-component
[react-events]: https://react.dev/learn/responding-to-events
[react-hooks]: https://react.dev/learn/reusing-logic-with-custom-hooks
[sb-naming]: https://docs.spring.io/spring-boot/how-to/data-access.html#howto.data-access.configure-hibernate-naming-strategy
[sb-props]: https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.typesafe-configuration-properties
[sb-structure]: https://docs.spring.io/spring-boot/reference/using/structuring-your-code.html
[sd-audit]: https://docs.spring.io/spring-data/jpa/reference/auditing.html
[sd-crud]: https://docs.spring.io/spring-data/commons/reference/repositories/core-concepts.html
[sd-query]: https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html
[sd-repos]: https://docs.spring.io/spring-data/jpa/reference/repositories/definition.html
[sf-di]: https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html
[springdoc]: https://springdoc.org/
[sqlstyle]: https://www.sqlstyle.guide/#naming-conventions
[ss-udm]: https://docs.spring.io/spring-security/site/docs/current/api/org/springframework/security/provisioning/UserDetailsManager.html
[ss-uds]: https://docs.spring.io/spring-security/site/docs/current/api/org/springframework/security/core/userdetails/UserDetailsService.html
[stripe]: https://docs.stripe.com/api
[stripe-java-invoice]: https://github.com/stripe/stripe-java/blob/master/src/main/java/com/stripe/model/Invoice.java
[ts-eslint-naming]: https://typescript-eslint.io/rules/naming-convention/
[zalando-118]: https://opensource.zalando.com/restful-api-guidelines/#118
[zalando-120]: https://opensource.zalando.com/restful-api-guidelines/#120
[zalando-129]: https://opensource.zalando.com/restful-api-guidelines/#129
[zalando-130]: https://opensource.zalando.com/restful-api-guidelines/#130
[zalando-134]: https://opensource.zalando.com/restful-api-guidelines/#134
[zalando-141]: https://opensource.zalando.com/restful-api-guidelines/#141
[zalando-235]: https://opensource.zalando.com/restful-api-guidelines/#235
[zalando-240]: https://opensource.zalando.com/restful-api-guidelines/#240
[zipgo]: https://github.com/woowacourse-teams/2023-zipgo
