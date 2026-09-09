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
- 외부 근거 없이 팀이 정한 행은 종류 대신 "이 파일"이나 그 결정이 나온 RFC, 노션 제안서, 커밋을 적는다. 링크는 문서 끝 참고 문서에 모은다.

### 근거

- 이름 규칙은 행마다 같은 다섯 속성을 갖고, 읽는 사람은 식별자로 찾아 읽는다. 조회표라 표가 맞다. 불릿으로 풀면 행마다 같은 라벨을 되풀이한다.
- 셀에 산문을 넣으면 GitHub에서 가로 스크롤이 생기고 줄 댓글이 행 전체에 붙어 어느 문장을 지적했는지 보이지 않는다. 그래서 긴 규칙은 표 밖으로 뺀다.
- 근거에 종류를 붙이는 것은 어기면 깨지는 것과 권고를 같은 무게로 읽지 않기 위해서다.

### 검토한 대안

- 식별자마다 소제목을 두고 불릿으로 (Google Java Style Guide 꼴): 규칙마다 설명이 길면 낫지만, 행 대부분이 케이스 하나와 예 하나라 소제목이 과하다.

## 원칙

### 규칙

표의 모든 행은 아래 원칙에서 나온다. 표에 없는 경우를 만나면 이 원칙으로 짓는다.

- **읽는 자리에서 답이 나온다.** 선언부로 올라가지 않아도 호출부만 보고 무엇(대상)이고 어떤 종류(계층, 역할)인지 안다. 그래서 주입 필드는 타입명, 클래스는 계층 접미사, 테스트는 관점 접미사다 ([Google TS][gts] "이름은 새 독자에게 서술적이고 분명해야 한다")
- **같은 개념에 같은 낱말, 다른 개념에 다른 낱말.** 한 대상을 파일마다 다르게 부르지 않고, 엔티티를 약칭하지 않는다 ([AIP-140][aip-140])
- **타입과 문맥에 이미 있는 정보를 되풀이하지 않는다.** 컬렉션에 `List`를, 클래스가 말하는 대상을 메서드에, 응답 리소스명을 그 필드에 다시 적지 않는다 ([Google TS][gts] "타입에 있는 정보로 이름을 꾸미지 않는다")
- **줄이지 않는다.** 사전에 있는 낱말은 약어로 만들지 않고 이름 길이에 상한을 두지 않는다. 주석이 없으므로 이름이 길어지는 쪽이 뜻이 빠지는 쪽보다 싸다 ([Google Java 5.3][gj-5.3], [Google TS][gts])
- **고를 자유도를 남기지 않는다.** 팀이 같은 것을 다르게 부를 수 있는 규칙은 규칙이 아니다. 형식이 하나로 정해지는 쪽을 택한다 (우리 기준. 표준 문서가 침묵하는 항목을 가르는 데 썼다)

### 근거

- 원칙마다 괄호 안에 근거가 있다.

### 검토한 대안

- 초기 규칙이라 기록 없음. 절을 고칠 때 채운다.

## 백엔드 Java

### 규칙

#### 패키지

| 식별자 | 케이스 | 형식 | 예 | 근거 |
| --- | --- | --- | --- | --- |
| 패키지 | 소문자, 밑줄 없음 | `com.meterengine.<domain>.<layer>`, domain은 단수 한 단어, layer는 `controller` `dto` `entity` `repository` `service`. 도메인 없는 공용 코드는 `global.<역할>`. 최상위 루트에는 진입점만 | `com.meterengine.invoice.dto`, `com.meterengine.global.config.OpenApiConfig` | 스타일 가이드: [Google Java 5.2.1][gj-5.2.1](소문자). 관례: 루트에 진입점만 두는 것은 [Spring Boot 레퍼런스 코드 구조][sb-structure]의 예제 배치와 [PetClinic][petclinic]. 도메인 없는 코드를 한 패키지에 모으는 것은 PetClinic의 `system`. 이름 `global`과 단복수는 RFC-001 |

#### 클래스

| 식별자 | 케이스 | 형식 | 예 | 근거 |
| --- | --- | --- | --- | --- |
| 엔티티 | UpperCamelCase | 도메인 명사 그대로, 접미사 없음, 약칭 금지(`Metric` 아니고 `BillableMetric`). 약칭 금지는 엔티티 낱말이 나타나는 모든 자리에 적용된다. 자바 클래스와 변수(DTO, 서비스, 예외 이름 안의 엔티티 낱말 포함), 프론트 타입과 라우트와 API 모듈 파일, URL 경로와 경로 변수, JSON 키와 쿼리 파라미터, 다른 테이블에서 이 엔티티를 가리키는 컬럼(DB 컬럼 행)이 대상이다(`BillableMetric`이면 `/v1/billable-metrics`, `billable_metric_code`). 패키지만 대상이 아니다. 패키지는 엔티티가 아니라 도메인의 이름이고(`metric` 패키지에 `BillableMetric`과 `MetricUsage`가 같이 산다) 패키지 행이 한 단어로 정했다 | `Customer`, `BillableMetric` | 명세: [JLS §6.1][jls-6.1](클래스명은 서술적 명사), [Jakarta Persistence][jpa-spec](엔티티 이름 기본값이 클래스 단순명이라 접미사가 없어야 함). 약칭 금지는 [클래스명 제안서][prop-class]. 적용 범위는 RFC-001 |
| 식별자 값 타입 | UpperCamelCase | `<엔티티>Id` | `BillableMetricId` | 스타일 가이드: [Google Java 5.3][gj-5.3](`Id`) |
| 리포지토리 | UpperCamelCase | `<엔티티>Repository` | `InvoiceLineRepository` | 관례: [Spring Data JPA 레퍼런스][sd-repos] `PersonRepository`, [Spring 가이드][guide-jpa] `CustomerRepository`, [PetClinic][petclinic] `OwnerRepository` |
| 서비스 | UpperCamelCase | `<대상>Service`, 동작 하나면 `<대상><동작명사>Service` | `CustomerService`, `EventIngestionService` | 스타일 가이드: [Google Java 5.2.2][gj-5.2.2](클래스명은 명사구라 동작도 명사형). 관례: `<대상>Service`는 [Spring Security][ss-uds] `UserDetailsService` |
| 컨트롤러 | UpperCamelCase | `<대상>Controller` | `CustomerController` | 관례: [Spring 가이드][guide-rest] `GreetingController`, [PetClinic][petclinic] `OwnerController` |
| 설정 | UpperCamelCase | `<무엇>Config`, 외부 설정값 `<무엇>Properties` | `OpenApiConfig`, `TossPaymentsProperties` | 관례: `Properties`는 [Spring Boot 레퍼런스][sb-props] 예제 `MyProperties`, `Config`는 [Spring 가이드][guide-security] 예제 `WebSecurityConfig` |
| 상수 묶음 클래스 | UpperCamelCase 복수 | | `ConstraintNames` | 관례: JDK [`Collectors`][jdk-collectors], `Collections`, `Objects` |
| 쓰지 않는 접미사 | | 우리 체계와 겹치는 다른 관례의 접미사 `Dto` `Vo` `Cmd` `Json` `Model` `Entity` `Impl`, 하는 일을 말하지 않는 접미사 `Util` `Helper` `Manager`, 요소라는 것만 말하는 접미사 `Entry` `Item` `Row` `Data` `Info`. 한 개념에 이름이 둘 생기는 것을 막고, 이름이 무엇인지를 말하게 한다 | | RFC-001 |

#### DTO

| 식별자 | 케이스 | 형식 | 예 | 근거 |
| --- | --- | --- | --- | --- |
| 요청 DTO | UpperCamelCase | `<동사><리소스>Request`, 동사는 컨트롤러 메서드와 서비스 메서드와 같은 낱말. 만들기와 고치기는 요청을 따로 둔다(`Create`/`Update`), `Save` 하나로 합치지 않는다 | `CreateCustomerRequest`, `UpdateCustomerRequest`, `IngestEventRequest` | 스타일 가이드: [AIP-133][aip-133], [AIP-134][aip-134] |
| 응답 DTO | UpperCamelCase | 목록 `List<리소스들>Response`, 하나 `<리소스>Response` | `ListCustomersResponse`, `DraftInvoiceResponse` | 스타일 가이드: 목록은 [AIP-132][aip-132]. 단건 접미사는 [4945e92][l-response]([AIP-131][aip-131]과 [AIP-133][aip-133]은 리소스 자체를 응답으로 함) |
| 응답 안 요소 | UpperCamelCase | 요소가 독립 리소스면 그 리소스의 응답 타입을 그대로 씀(목록 응답의 요소 = 단건 응답 타입). 그 응답에만 속하는 객체는 `<응답 리소스><JSON 필드명 단수>`. 중첩 record로 두더라도 이름은 이 형식(springdoc이 바깥 클래스명을 버리므로 접두어가 부모 역할) | `ListCustomersResponse.customers`는 `CustomerResponse`, `DraftInvoiceResponse.customers`는 `DraftInvoiceCustomer`, `lines`는 `DraftInvoiceLine` | 스타일 가이드: [AIP-132][aip-132](목록 요소는 리소스 자체). 관례: [Stripe SDK][stripe-java-invoice] `Invoice.StatusTransitions`, [Google Calendar API][gcal-events] `Event.reminders`와 `EventAttendee`(필드명으로 부르고 부모로 한정). 도구 동작: [springdoc][springdoc]이 스키마 이름을 클래스 단순명으로 만들고, [OpenAPI Generator][oag-inline]는 인라인 스키마를 `<부모>_<필드>`로 만든다. 접두어 형식은 [1eb7906][l-prefix] |
| 서비스 사이 전달 타입 | UpperCamelCase | `<대상><내용>` 명사구. HTTP를 타지 않으므로 `Request`/`Response` 없음, 요소 접미사(`Entry` `Row`)도 없음 | `BillableMetricUsage`, `CustomerUsage`, `CustomerQuantity` | 명세: [JLS §6.1][jls-6.1](명사구). 접미사를 빼는 것은 RFC-001 |

#### 메서드

| 식별자 | 케이스 | 형식 | 예 | 근거 |
| --- | --- | --- | --- | --- |
| 서비스 메서드 | lowerCamelCase | 동사 단독(클래스가 대상을 말함). 저장하는 것 `create` `update` `delete`, 읽는 것 `list`(여럿, 필터와 페이지가 있어도 `list`이며 `query` `search`를 따로 두지 않음) `find`(하나, 없을 수 있음) `get`(하나, 없으면 예외), 계산하는 것 `aggregate`(집계 결과) `preview`(저장하지 않는 결과), 상태 전이는 도메인 동사. 동사는 컨트롤러 메서드, 요청 DTO와 같은 낱말 | `create`, `list`, `preview`, `finalize` | 스타일 가이드: [Google Java 5.2.3][gj-5.2.3]은 동사 또는 동사구까지만 정함. 관례: 동사 단독은 Spring Data [`CrudRepository`][sd-crud]의 `save` `delete` `count`와 JPA [`EntityManager`][jpa-em]의 `persist` `find` `remove`(타입이 대상을 말하면 메서드는 동사만). `find`/`get` 구분은 `EntityManager.find`(없으면 null)와 `getReference`(없으면 예외)의 짝. 스타일 가이드: `list`가 필터와 페이지를 갖는 것은 [AIP-132][aip-132]와 [AIP-160][aip-160]. 관례: `create` `update` `delete` 쌍은 Spring Security [`UserDetailsManager`][ss-udm]의 `createUser` `updateUser` `deleteUser`(리포지토리의 `save`는 upsert라 만들기와 고치기는 서비스 계층이 가름). 나머지 어휘 목록은 RFC-001 |
| 컨트롤러 메서드 | lowerCamelCase | `<동사><리소스>`, 동사는 서비스 메서드와 같은 낱말, 프로젝트 전역 유일. springdoc이 메서드명을 operationId로 쓰고 OpenAPI가 유일을 요구하며, 프론트 생성 클라이언트의 함수명이 된다 | `listCustomers`, `ingestEvents`, `previewDraftInvoice` | 명세: [OpenAPI 3.1][oas-op](`operationId` 유일, 강제). 도구 동작: [springdoc][springdoc]이 메서드명을 `operationId`로 씀 |
| 리포지토리 메서드 | lowerCamelCase | 파생 쿼리는 `find` `exists` `count` `delete` + `By...`만(Spring Data는 `find`와 `get`을 동의어로 보므로 `find`만 씀). `OrderBy` 둘 이상이나 조건 셋 이상이면 `@Query`, JPQL이 이름과 어긋나면 서술형 이름 | `findByOrganizationId`, `findBaseUnitPrices` | 도구 동작: [Spring Data][sd-query]가 접두사를 파싱해 쿼리를 만들고 `find` `read` `get` `query` `search` `stream`을 동의어로 봄. `@Query` 전환 기준은 RFC-001 |
| 접근자와 변경자 | lowerCamelCase | JPA 엔티티의 getter는 `getX()`, boolean은 `isX()`. setter를 두지 않고 상태 변경은 뜻을 말하는 도메인 동사 메서드로. record는 컴포넌트 접근자 `x()`, record 안 파생 값은 `get` 없는 명사 메서드 | `getSupplyAmount()`, `isNew()`, `rename(name)`, `quantity()`, `totalAmount()` | 명세: [JavaBeans][javabeans] 8.3(`getX`, boolean `isX`, 권고), [JLS §8.10.3][jls-8.10.3](record 접근자). setter 없음은 RFC-001 |
| 정적 팩터리, 변환 메서드 | lowerCamelCase | 한 인자 변환 `from`, 여러 인자 조립 `of`, 새 객체로 변환 `toX`, 뷰 `asX` | `CustomerResponse.from(customer)` | 관례: JDK [`LocalDate.of`][jdk-localdate], `Instant.from`, [`List.of`][jdk-list], `Collectors.toList`, `Arrays.asList` |

#### 필드와 변수

| 식별자 | 케이스 | 형식 | 예 | 근거 |
| --- | --- | --- | --- | --- |
| 주입 필드 | lowerCamelCase | 타입명 그대로, 같은 타입 둘 이상일 때만 수식어 | `customerRepository` | 관례: [Spring Framework 레퍼런스][sf-di] DI 예제 `private final MovieFinder movieFinder`. [PetClinic][petclinic]은 `owners`와 `vetRepository`가 섞임. 적용은 [2f48aea][l-inject] |
| 컬렉션 | lowerCamelCase | 복수형, 접미사 없음. Map은 `<값>By<키>` | `customerQuantities`, `priceByBillableMetricId` | 스타일 가이드: 복수형은 [Zalando 규칙 120][zalando-120], [AIP-140][aip-140](API 필드 규칙을 변수명에 유추 적용). Map 형식은 [b1f70fe][l-map](`unitPriceByMetricCode`) |
| 그 외 변수, 파라미터 | lowerCamelCase | 값의 정체를 말하는 명사구, 조회 메서드 이름과 맞춤 | `baseUnitPrices`, `billableMetricUsage` | 스타일 가이드: [Google Java 5.2.7][gj-5.2.7](케이스). 명사구와 조회 메서드 일치는 [ea7abd2][l-nounphrase](`baseUnitPrices` = `findBaseUnitPrices`) |
| boolean | lowerCamelCase | 필드, 변수, record 컴포넌트, JSON 키, DB 컬럼 전부 접두사 없는 형용사나 과거분사로 같은 이름. 접두사 `is` `has` `can`은 접근자 메서드에만 | 필드 `finalized`, 접근자 `isFinalized()`, JSON과 컬럼 `finalized` | 명세: [JavaBeans][javabeans] 8.3.2(접근자 `isX()`). 스타일 가이드: [Google Java][gj]는 필드 접두사 규칙 없음, JSON 접두사 생략은 [AIP-140][aip-140]. 한 값에 이름 하나를 두어 `@Column`과 `@JsonProperty`로 이름을 맞출 일을 없애는 것은 RFC-001 |
| 시간 | lowerCamelCase | 순간 `At`, 날짜 `Date` | `finalizedAt`, `billingDate` | 스타일 가이드: [Zalando 규칙 235][zalando-235](`_at`와 `_date` 둘 다 허용), [AIP-142][aip-142]는 `_time`. 관례: [Spring Data 감사 예제][sd-audit]는 `createdDate`. Java 표준 없음, JSON 키와 1:1 대응은 RFC-001 |
| 상수, enum 상수 | UPPER_SNAKE_CASE | 깊은 불변 `static final`만 | `BILLING_ZONE`, `DRAFT` | 스타일 가이드: [Google Java 5.2.4][gj-5.2.4] |
| 로거 | lowerCamelCase | `logger`. `static final`이어도 상수가 아니다 | `logger` | 스타일 가이드: [Google Java 5.2.4][gj-5.2.4]의 "Not constants" 예시 `static final Logger logger`. 관례: Spring Framework 자체 코드의 `protected final Log logger` |
| 타입 변수 | `T` 또는 `XxxT` | | `RequestT` | 스타일 가이드: [Google Java 5.2.8][gj-5.2.8] |

#### 낱말

| 식별자 | 케이스 | 형식 | 예 | 근거 |
| --- | --- | --- | --- | --- |
| 약어 | 낱말처럼 camel | 케이스 규칙이지 허용 목록이 아니다. 약어는 낱말 하나로 보고 첫 글자만 대문자(`Api`, `Url`, `Jdbc`). 사전에 있는 단어는 줄이지 않는다(`customer`를 `cust`로 쓰지 않음). 업계에서 약어로만 쓰는 것(`id` `api` `url` `pg` `vat` `krw` `kst` `utc` `json` `jdbc`)은 그대로 쓴다 | `customerId`, `OpenApiConfig`, `jdbcTemplate`, `formatKrw` | 스타일 가이드: [Google Java 5.3][gj-5.3]. 도구 동작: [Checkstyle google_checks][checkstyle-abbr]가 같은 규칙을 검사함 |
| 이름 길이 | | 상한 없음. 같은 정보가 두 번 들어가면 줄임. DB 식별자만 PostgreSQL 63바이트 제한 | | RFC-001. 주석 없는 코드라 긴 쪽 |

#### 테스트

| 식별자 | 케이스 | 형식 | 예 | 근거 |
| --- | --- | --- | --- | --- |
| 테스트 클래스 | UpperCamelCase | `<대상>[<동작>][<관점>]Test`. 대상은 클래스, 리소스, 또는 산출물(시드, OpenAPI 문서, 스키마 제약). 대상이 클래스 하나이고 그 클래스만 검증하면 동작과 관점 없이 `<클래스>Test`. 관점은 무엇을 검증하는지 말하는 명사이지 쓰는 도구가 아니며(`MockMvc` `Testcontainers` 아님), 같은 성질에 다른 낱말을 만들지 않는다. 동작은 한 연산만 다룰 때 넣고 서비스 동사와 같은 낱말 | `CustomerServiceTest`, `EventIngestIntegrationTest`, `CustomerDeleteConcurrencyTest`, `InvoiceRoundTripTest`, `SeedDataTest` | 스타일 가이드: [Google Java 5.2.2][gj-5.2.2](클래스 하나를 다루면 그 클래스명 + `Test`, 아니면 `HashIntegrationTest`처럼), [네이버 핵데이][naver]. 관례: Spring 자체는 `Tests`. 관점의 기준은 RFC-001 |
| 테스트 위치 | | 단위 테스트(`<클래스>Test`)는 대상과 같은 패키지. 관점이 붙는 테스트는 도메인 루트. 도메인에 속하지 않는 것은 `global`이며, 공용 클래스의 단위 테스트는 그 클래스와 같은 `global.<역할>`, 산출물 테스트(시드, OpenAPI 문서, 스키마 제약)와 관점이 붙는 테스트는 `global` 바로 아래. 루트에는 애플리케이션 테스트만. 메인 코드의 루트에 진입점만 두는 것과 같은 기준 | `customer/service/CustomerServiceTest`, `invoice/InvoiceRoundTripTest`, `global/SeedDataTest` | 관례: 대상과 같은 패키지는 [PetClinic][petclinic]. 관점이 붙는 테스트를 도메인 루트에 둔 것은 [4945e92][l-test-loc]. `global`은 패키지 행 |
| 테스트 메서드 | 한국어 밑줄 문장 | "~한다"/"~다" 어미, `@DisplayName` 없음. 한국어는 `@Test`와 `@Nested` 이름에만 | `이벤트가_있는_고객은_지울_수_없다` | 스타일 가이드: [Google Java 5.1][gj-5.1](ASCII만)의 의도적 예외. 관례: [prolog][prolog], [2023-zipgo][zipgo]. 적용은 [4945e92][l-test-ko] |
| 테스트 헬퍼, 픽스처 | lowerCamelCase, `<Domain>Fixture` | 영어 | `insertCustomer`, `InvoiceFixture` | 스타일 가이드: [Google Java 5.1][gj-5.1](ASCII만). 한국어 예외를 테스트 메서드로 한정한 결과 |

### 근거

- 근거는 표의 근거 열에 행마다 있다.

### 검토한 대안

- DTO 어순은 동사 먼저 `CreateCustomerRequest`, `ListCustomersResponse` (대안: 대상 먼저 `CustomerListResponse`). [AIP-133][aip-133]과 [AIP-132][aip-132]. 단건 `<리소스>Response` 접미사만 팀 선택이다. 지금 코드의 `CustomerListResponse`는 `ListCustomersResponse`가 되고, `SaveCustomerRequest`는 `CreateCustomerRequest`와 `UpdateCustomerRequest`로 갈린다
- 만드는 동사는 `create` (대안: `register`). [AIP-133][aip-133], [Stripe][stripe], Spring Security [`UserDetailsManager`][ss-udm]의 `createUser`/`updateUser`/`deleteUser`, JDK [`Files.createFile`][jdk-files], [Kill Bill][killbill]. `register`는 콜백이나 드라이버를 등록소에 거는 동작에 쓰이는 낱말이다. 지금 코드의 `BillableMetricService.register()`, `PricePolicyService.register()`가 `create()`가 된다
- 컬렉션은 복수형, 접미사 없음 (대안: `List` 접미사). [Zalando 규칙 120][zalando-120], [AIP-140][aip-140], [Google TS][gts]. 리스트와 타입명이 헷갈리면 요소 타입을 단수 개념으로 다시 짓는다. [내부 이름 구체화 리뷰][pr-111]의 `metricQuantitiesByCustomerList` 대 `metricQuantitiesByCustomers`에서 뒤쪽이다
- 응답 안 요소는 `<응답 리소스><JSON 필드명 단수>` (대안: `springdoc.use-fqn=true`는 패키지 경로가 새고, `@Schema(name)`은 잊으면 조용히 병합된다). Stripe와 Google 클라이언트가 중첩으로 얻는 한정을 접두어로 얻는다. 사용량 응답과 청구 응답에 `CustomerEntry` 중첩 record가 둘 있어 springdoc이 바깥 클래스명을 버리고 openapi.yaml에서 하나로 합쳤던 것([1eb7906][c-1eb7906])을 `DraftInvoiceCustomer`처럼 접두어로 막는다
- 주입 필드는 타입명 (대안: 복수 도메인 명사 `customers`, PetClinic 방식). [Spring Framework 레퍼런스][sf-di]의 DI 예제가 `movieFinder`이고, [PetClinic][petclinic] 안에서도 `owners`와 `vetRepository`가 섞였다. 주입 필드는 역할이 곧 타입이라 타입명이 곧 역할명이다
- 도메인 없는 공용 코드는 `global.<역할>`, 최상위 루트에는 진입점만 (대안: 최상위 루트에 그냥 두기, PetClinic의 `system`). [Spring Boot 레퍼런스][sb-structure]는 진입점을 루트 패키지에 두라고만 하고, 예제 배치의 루트에는 진입점뿐이다. [PetClinic][petclinic]은 루트에 진입점만 두고 설정과 공용 컨트롤러를 `system`에 모은다. 패키지 이름은 표준이 없어 오류 처리 RFC가 쓰려는 `global`에 맞춘다. 역할 이름은 그 역할을 들여오는 RFC가 정하며 RFC-001은 `config`만 정한다
- 한국어 테스트 메서드명. [Google Java 5.1][gj-5.1](ASCII만)과 [네이버 핵데이][naver]에 어긋난다. 테스트 이름이 곧 명세라 한국어가 가장 정확하고, 우아한테크코스 [prolog][prolog]와 [2023-zipgo][zipgo]가 쓴다. 변수와 헬퍼로 번지지 않게 `@Test`와 `@Nested`에만 허용한다

## 프론트엔드 TypeScript

### 규칙

#### 컴포넌트

| 식별자 | 케이스 | 형식 | 예 | 근거 |
| --- | --- | --- | --- | --- |
| 컴포넌트 | PascalCase | 여럿이면 복수, 하나면 단수, 셀 수 없는 명사(`Billing` `Usage`)는 그대로. 접미사 `Screen` `Section` `Table` `FormDialog` `DeleteDialog` `Drawer`, 공용은 역할명 | `CustomersTable`, `CustomerFormDialog`, `UsageTable`, `Pagination` | 도구 동작: React가 소문자 이름을 DOM 태그로 봄([React 문서][react-component]). 관례: 복수형은 [next-learn][next-learn]. 접미사 목록은 RFC-001 |
| 훅 | `use` + PascalCase | 훅을 호출하지 않으면 `use` 금지 | `useCollapse` | 도구 동작: `use` 접두사로 훅 규칙을 검사함([React 문서][react-hooks], eslint-plugin-react-hooks) |
| 이벤트 핸들러 | lowerCamelCase | 함수는 `handle<Event>`, prop은 `on<Event>` | `handleSubmit`, `onDelete` | 관례: [React 문서][react-events]가 `handle`/`on`을 관례로 소개 |

#### 타입

| 식별자 | 케이스 | 형식 | 예 | 근거 |
| --- | --- | --- | --- | --- |
| props, 상태, 표시용 타입 | PascalCase | `<컴포넌트>Props`, `<대상>State`, `<대상>RowView`. `I` 접두사 없음 | `BillableMetricFormState`, `CustomerRowView` | 스타일 가이드: [Google TS][gts](`I` 접두사 금지), [Palmer Group TS 가이드][palmer](`<Component>Props`/`State`). `RowView`는 [6e96e33][l-rowview] |
| API 응답 타입 | PascalCase | 백엔드 스키마 이름 그대로. `Response`가 붙어 있으면 선 위의 모양, `RowView`면 화면용, `State`면 폼 상태로 구분해 읽는다 | `CustomerResponse`, `DraftInvoiceCustomer` | RFC-001. 이름이 같아야 openapi.yaml에서 바로 찾는다 |

#### 함수, 변수, 상수

| 식별자 | 케이스 | 형식 | 예 | 근거 |
| --- | --- | --- | --- | --- |
| 함수, 변수, 프로퍼티 | lowerCamelCase | 함수는 동사구, API 함수는 백엔드 동사 어휘 | `listCustomers`, `readMonth` | 스타일 가이드: [Google TS][gts], [Airbnb 23.2][airbnb] |
| boolean | lowerCamelCase | `is` `has` `can` 접두사 | `isOpen` | 관례: [typescript-eslint 문서 예시][ts-eslint-naming]. 스타일 가이드: [Palmer Group][palmer]. 표준 없음 |
| 모듈 수준 상수 | UPPER_SNAKE_CASE | export, const, `as const`일 때만. 초기값은 `<STATE>_IDLE`. 함수 안의 const는 lowerCamelCase. 프레임워크가 이름을 정한 export(`metadata`, `config` 등 Next.js 예약)는 그 이름 그대로 | `FORM_IDLE`, `PAGE_SIZE` | 스타일 가이드: [Google TS][gts], [Airbnb 23.10][airbnb]. 도구 동작: 예약 export는 [Next.js][next-metadata]가 이름으로 찾음 |
| 약어 | 낱말처럼 camel | 백엔드의 약어 규칙과 같음 | `customerId`, `apiClient` | 스타일 가이드: [Google TS][gts]. [Airbnb 23.9][airbnb](전부 대문자)와 다르게 감 |

#### 파일과 폴더

| 식별자 | 케이스 | 형식 | 예 | 근거 |
| --- | --- | --- | --- | --- |
| 컴포넌트 파일 | PascalCase | 파일명 = 컴포넌트명, 파일 하나에 하나 | `CustomerFormDialog.tsx` | 스타일 가이드: [Airbnb React][airbnb-react] |
| 그 외 파일과 폴더 | kebab-case | 라우트, 컴포넌트 폴더, API 모듈은 같은 이름. 라우트 아래는 `state.ts` `actions.ts`. 공용 폴더는 역할명 단수 | `lib/api/customers.ts`, `dev-state.ts` | 도구 동작: [Next.js 예약 파일][next-files]. 관례: [next-learn][next-learn] |

### 근거

- 근거는 표의 근거 열에 행마다 있다.

### 검토한 대안

- 컴포넌트는 여럿이면 복수 (대안: 접미사별 고정). [next-learn][next-learn]은 복수, [react-admin][react-admin]은 단수. 이름이 "무엇이 여럿인가"를 말하게 한다
- 초기값 상수는 `FORM_IDLE` (대안: `initialFormState`, React 문서 관례). 둘 다 표준 안이고, `IDLE`은 초기값이자 되돌아가는 값이라 더 정확하다
- 컴포넌트 파일 PascalCase. Next.js 생태계 다수([next-learn][next-learn], [shadcn/ui][shadcn])와 [Google TS][gts]는 kebab이다. [Airbnb React][airbnb-react]를 따르며, 파일명만으로 컴포넌트가 구분되는 이점을 택한다

## API와 DB

### 규칙

#### API

| 식별자 | 케이스 | 형식 | 예 | 근거 |
| --- | --- | --- | --- | --- |
| URL 경로 | kebab-case | 복수 명사, 동사 없음. 부모 아래 하나뿐인 하위 리소스는 단수 | `/v1/price-policies`, `/v1/billable-metrics/{code}/price-policy` | 스타일 가이드: [Zalando 규칙 129][zalando-129], [134][zalando-134], [141][zalando-141]. [AIP-122][aip-122], 단수 하위 리소스는 [AIP-156][aip-156] |
| 쿼리 파라미터 | snake_case | JSON 키와 같은 이름 | `customer_id` | 스타일 가이드: [Zalando 규칙 130][zalando-130] |
| 경로 변수 | snake_case | 바로 앞 세그먼트가 가리키는 리소스의 식별 JSON 키와 같은 이름. 앞 세그먼트가 리소스를 말하므로 리소스명을 되풀이하지 않는다(`{customer_id}` 아니고 `{id}`). 한 경로에 식별자가 둘 이상이면 모두 `<리소스 단수>_<키>`로 한정한다(OpenAPI가 같은 이름을 허용하지 않음). URL의 이름은 snake_case로 어노테이션 값에 적고, 그 값을 받는 자바 변수는 변수 규칙대로 camelCase로 짓는다(`{transaction_id}`는 `@PathVariable("transaction_id") String transactionId`, 한 단어라 둘이 같으면 `@PathVariable String code`). 쿼리 파라미터의 `@RequestParam(name = "customer_id") UUID customerId`와 같은 방식 | `/v1/customers/{id}`, `/v1/billable-metrics/{code}/price-policy`, `/v1/events/{transaction_id}`(아직 없는 경로, 이벤트의 식별 키가 `transaction_id`) | 스타일 가이드: [Zalando 규칙 129][zalando-129]는 세그먼트만 kebab으로 정하고 변수는 예제가 kebab(`{shipment-order-id}`), [AIP-122][aip-122]는 `{book}`(리소스 단수, 접미사 없음). 표준이 갈려 쿼리 파라미터 행([Zalando 규칙 130][zalando-130])에 맞춘 것과 되풀이하지 않는 것은 RFC-001 |
| JSON 키 | snake_case | `@JsonProperty`로 지정, boolean은 접두사 없는 형용사 | `customer_id`, `active` | 스타일 가이드: [Zalando 규칙 118][zalando-118], boolean 접두사 생략은 [AIP-140][aip-140]. 관례: [Stripe][stripe]. Google REST와 Azure는 camelCase라 Stripe/Zalando 계열을 택함 |
| 문자열 열거값, 에러 코드 | lowercase snake_case | 이미 나간 에러 코드와 통일 | `customer_not_found` | 관례: [Stripe][stripe]. 스타일 가이드: [Zalando 규칙 240][zalando-240]과 [AIP-126][aip-126]은 UPPER. 이미 나간 에러 코드([4945e92][l-has-code])를 깨지 않으려고 Stripe를 따름 |

#### DB

| 식별자 | 케이스 | 형식 | 예 | 근거 |
| --- | --- | --- | --- | --- |
| DB 테이블 | snake_case 단수 | 엔티티명이 곧 테이블명, `@Table` 불필요 | `invoice_line` | 도구 동작: [Spring Boot 기본 네이밍 전략][sb-naming]이 엔티티명을 그대로 씀(`TelephoneNumber`가 `telephone_number`). 스타일 가이드: [SQL Style Guide][sqlstyle]는 복수. 관례: [PetClinic][petclinic]도 복수 |
| DB 컬럼 | snake_case | 다른 테이블을 가리키는 컬럼은 `<참조 테이블>_<참조 컬럼>`, FK 제약이 없어도 같다. 순간 `_at`, 날짜 `_date`, boolean은 접두사 없는 형용사 | `customer_id`, `billable_metric_code`, `finalized_at` | 명세: [Jakarta Persistence `@JoinColumn`][jpa-joincolumn]의 기본 이름이 참조 필드명 + `_` + 참조 PK 컬럼. 관례: 참조 컬럼은 [Rails Active Record][rails-ar](`singularized_table_name_id`, `line_item_id`)와 지금 스키마의 `organization_id` `customer_id` `invoice_id`. 스타일 가이드: `_id` `_date`는 [SQL Style Guide][sqlstyle], `_at`는 [Zalando 규칙 235][zalando-235](JSON 키와 맞춤) |

### 근거

- 근거는 표의 근거 열에 행마다 있다.

### 검토한 대안

- 열거값 lowercase snake_case. [Zalando 규칙 240][zalando-240]과 [AIP-126][aip-126]은 UPPER다. 이미 나간 에러 코드와 통일한다

## 오류 처리

### 규칙

| 식별자 | 케이스 | 형식 | 예 | 근거 |
| --- | --- | --- | --- | --- |
| 오류 코드 enum | UpperCamelCase 단수 | `ErrorCode` | `ErrorCode` | 관례: [Kill Bill][killbill-repo] `ErrorCode`, [spring-guide][spring-guide] `ErrorCode` |
| 오류 코드 상수 | UPPER_SNAKE_CASE | 영어 어순. 낱말 순서는 아래 "오류 코드 상수의 낱말 순서" | `CUSTOMER_NOT_FOUND`, `INVALID_EVENT`, `INVOICE_EXPIRED` | 케이스는 [Google Java 5.2.4][gj-5.2.4]. 낱말 순서의 근거는 아래 "오류 코드 상수의 낱말 순서" |
| 오류 코드 문자열 | lowercase snake_case | 상수명을 소문자로 내린 것. 따로 짓지 않는다 | `customer_not_found` | 명세: [RFC 6749 5.2][rfc6749-5.2]의 `error` 값. 관례: [Stripe][stripe-errors], [Chargebee][chargebee], [Lago][lago]. 문자열 열거값과 같은 케이스 |
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

## 이 파일의 근거

- 결정 기록은 RFC-001(이름 규칙을 표준 관례에 맞춰 정한다)이다. 원칙과 백엔드, 프론트엔드, API와 DB 절의 표는 그 문서의 결정 절에서, 검토한 대안은 검토한 선택지에서 가져왔다. 고친 곳은 근거 열의 "이 RFC"를 "RFC-001"로, 선택지를 가리키던 문구를 이 파일의 절로 바꾼 것이다. RFC-001 본문은 머지된 결정 기록이라 고치지 않고, 규칙을 바꿀 때는 이 파일만 고친다.
- "표 양식과 근거의 종류"와 "오류 처리" 절은 오류 처리 규칙 PR(#162)에서 먼저 들어왔다. RFC-001의 표가 합쳐지면서 팀이 정한 행의 출처에 RFC와 노션 제안서를 더했고, 오류 처리 절의 `stripe-errors`와 `killbill-repo`는 같은 이름의 링크가 다른 곳을 가리켜 라벨만 바꿨다.

## 참고 문서

표와 본문의 링크가 가리키는 곳이다. 절 번호가 있는 링크는 그 절로 바로 간다.

- 명세: [JLS §6.1 Naming Conventions][jls-6.1], [JLS §8.10.3 record 멤버][jls-8.10.3], [JavaBeans 명세][javabeans](8.3 프로퍼티), [Jakarta Persistence 3.2][jpa-spec], [Jakarta Persistence JoinColumn][jpa-joincolumn], [OpenAPI 3.1 Operation Object][oas-op], [Jakarta REST NotFoundException][jakarta-rs], [RFC 6749 5.2][rfc6749-5.2]
- 도구 동작: [Spring Boot 코드 구조][sb-structure], [springdoc][springdoc], [Spring Data JPA 쿼리 메서드][sd-query], [Spring Boot Hibernate 네이밍 전략][sb-naming], [OpenAPI Generator 인라인 스키마][oag-inline], [React 컴포넌트][react-component], [React 훅][react-hooks], [Next.js 파일 규칙][next-files], [Next.js metadata][next-metadata], [Checkstyle AbbreviationAsWordInName][checkstyle-abbr], [Spring MVC 오류 응답][sf-error-responses]
- 스타일 가이드: [Google Java Style Guide][gj], [Google TypeScript Style Guide][gts], [Google AIP][aip](122, 126, 131, 132, 133, 134, 140, 142, 156, 160), [Zalando RESTful API Guidelines][zalando](118, 120, 129, 130, 134, 141, 235, 240), [Airbnb JavaScript][airbnb], [Airbnb React][airbnb-react], [Palmer Group TypeScript][palmer], [SQL Style Guide][sqlstyle], [네이버 핵데이 Java 컨벤션][naver], [Google AIP-193][aip-193], [Google API 오류][google-errors]
- 관례: JDK javadoc [Collectors][jdk-collectors], [LocalDate][jdk-localdate], [List][jdk-list], [Files][jdk-files]. [Jakarta Persistence EntityManager][jpa-em], [Spring Data 리포지토리 정의][sd-repos], [Spring Data CrudRepository][sd-crud], [Spring Data 감사][sd-audit], [Spring 가이드 Accessing Data with JPA][guide-jpa], [Spring 가이드 Building a RESTful Web Service][guide-rest], [Spring 가이드 Securing a Web Application][guide-security], [Spring Boot 설정 프로퍼티 예제][sb-props], [Spring Framework DI][sf-di], [UserDetailsService][ss-uds], [UserDetailsManager][ss-udm], [Spring PetClinic][petclinic], [Stripe API][stripe], [Rails Active Record][rails-ar], [stripe-java Invoice][stripe-java-invoice], [Google Calendar API Events][gcal-events], [Kill Bill API][killbill], [React 이벤트 핸들러][react-events], [next-learn][next-learn], [shadcn/ui][shadcn], [react-admin][react-admin], [prolog][prolog], [2023-zipgo][zipgo], [typescript-eslint naming-convention 문서][ts-eslint-naming] 오류 처리 절은 [spring-guide][spring-guide], [Camunda][camunda], [Kill Bill 저장소][killbill-repo], [Fineract][fineract], [Stripe 오류 코드][stripe-errors], [Chargebee][chargebee], [Lago][lago], [토스페이먼츠 오류 코드][toss-errors], [Slack Web API][slack-errors], [AWS DynamoDB 오류][aws-ddb-errors].
- 팀: 노션 제안서 [주입 필드][prop-inject], [클래스명][prop-class], [도메인 예외][prop-exception]. [PR #111][pr-111](`List` 접미사 논의). 커밋은 아래 표

### 커밋 인용

표의 근거 칸이 인용한 커밋과, 그 커밋이 들여온 이름이 있는 파일과 줄이다. 링크는 그 커밋 시점에 고정돼 있다.

| 문서 행 | 커밋 | 파일과 줄 |
| --- | --- | --- |
| 주입 필드 | [2f48aea][c-2f48aea] DraftInvoiceService 주입 필드를 타입명으로 | [DraftInvoiceService.java:27][l-inject] `private final CustomerRepository customerRepository` |
| 응답 DTO 단건 접미사 | [4945e92][c-4945e92] 고객 등록/수정/삭제와 목록 조회 | [CustomerResponse.java:15][l-response] |
| 문자열 열거값, 에러 코드 | 4945e92 | 이미 나간 에러 코드 [ErrorCodes.java:117][l-has-code] |
| 테스트 메서드 한국어 | 4945e92 | [CustomerCrudIntegrationTest.java:156][l-test-ko] |
| 테스트 위치 | 4945e92 | 도메인 루트의 [CustomerCrudIntegrationTest.java][l-test-loc], [CustomerDeleteConcurrencyTest.java][l-test-loc2] |
| 그 외 변수 | [ea7abd2][c-ea7abd2] DraftInvoiceService 내부 이름 구체화 | [DraftInvoiceService.java:45][l-nounphrase] `baseUnitPrices = priceRateRepository.findBaseUnitPrices(...)` |
| 컬렉션 Map | [b1f70fe][c-b1f70fe] 가격 정책 목록에 기본 단가 | [PricePolicyService.java:48][l-map] `unitPriceByMetricCode` |
| props 타입 `RowView` | [6e96e33][c-6e96e33] 이벤트 로그 화면 | [EventTable.tsx:13][l-rowview] `EventRowView` |
| 응답 안 요소 접두어 | [1eb7906][c-1eb7906] 청구 응답 스키마 이름 충돌 수정 | [DraftInvoiceResponse.java:32][l-prefix] `DraftInvoiceCustomerEntry`, 계약 [openapi.yaml:619][l-prefix-yaml] |

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
[jls-6.1]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#jls-6.1
[jls-8.10.3]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.10.3
[javabeans]: https://www.oracle.com/java/technologies/javase/javabeans-spec.html
[jpa-spec]: https://jakarta.ee/specifications/persistence/3.2/jakarta-persistence-spec-3.2
[jpa-em]: https://jakarta.ee/specifications/persistence/3.2/apidocs/jakarta.persistence/jakarta/persistence/entitymanager
[jpa-joincolumn]: https://jakarta.ee/specifications/persistence/3.2/apidocs/jakarta.persistence/jakarta/persistence/joincolumn
[oas-op]: https://spec.openapis.org/oas/v3.1.0.html#operation-object
[springdoc]: https://springdoc.org/
[sd-repos]: https://docs.spring.io/spring-data/jpa/reference/repositories/definition.html
[sd-query]: https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html
[sd-crud]: https://docs.spring.io/spring-data/commons/reference/repositories/core-concepts.html
[sd-audit]: https://docs.spring.io/spring-data/jpa/reference/auditing.html
[sb-structure]: https://docs.spring.io/spring-boot/reference/using/structuring-your-code.html
[sb-props]: https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.typesafe-configuration-properties
[sb-naming]: https://docs.spring.io/spring-boot/how-to/data-access.html#howto.data-access.configure-hibernate-naming-strategy
[oag-inline]: https://openapi-generator.tech/docs/customization/#inline-schema-naming
[guide-jpa]: https://spring.io/guides/gs/accessing-data-jpa
[guide-rest]: https://spring.io/guides/gs/rest-service
[guide-security]: https://spring.io/guides/gs/securing-web
[sf-di]: https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html
[ss-uds]: https://docs.spring.io/spring-security/site/docs/current/api/org/springframework/security/core/userdetails/UserDetailsService.html
[ss-udm]: https://docs.spring.io/spring-security/site/docs/current/api/org/springframework/security/provisioning/UserDetailsManager.html
[petclinic]: https://github.com/spring-projects/spring-petclinic
[jdk-collectors]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/stream/Collectors.html
[jdk-localdate]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/time/LocalDate.html
[jdk-list]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/List.html
[jdk-files]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/nio/file/Files.html
[aip]: https://google.aip.dev/
[aip-122]: https://google.aip.dev/122
[aip-156]: https://google.aip.dev/156
[aip-126]: https://google.aip.dev/126
[aip-131]: https://google.aip.dev/131
[aip-132]: https://google.aip.dev/132
[aip-133]: https://google.aip.dev/133
[aip-134]: https://google.aip.dev/134
[aip-140]: https://google.aip.dev/140
[aip-142]: https://google.aip.dev/142
[aip-160]: https://google.aip.dev/160
[zalando]: https://opensource.zalando.com/restful-api-guidelines/
[zalando-118]: https://opensource.zalando.com/restful-api-guidelines/#118
[zalando-120]: https://opensource.zalando.com/restful-api-guidelines/#120
[zalando-129]: https://opensource.zalando.com/restful-api-guidelines/#129
[zalando-130]: https://opensource.zalando.com/restful-api-guidelines/#130
[zalando-134]: https://opensource.zalando.com/restful-api-guidelines/#134
[zalando-141]: https://opensource.zalando.com/restful-api-guidelines/#141
[zalando-235]: https://opensource.zalando.com/restful-api-guidelines/#235
[zalando-240]: https://opensource.zalando.com/restful-api-guidelines/#240
[stripe]: https://docs.stripe.com/api
[stripe-java-invoice]: https://github.com/stripe/stripe-java/blob/master/src/main/java/com/stripe/model/Invoice.java
[gcal-events]: https://developers.google.com/calendar/api/v3/reference/events
[killbill]: https://killbill.github.io/slate/
[react-component]: https://react.dev/learn/your-first-component
[react-hooks]: https://react.dev/learn/reusing-logic-with-custom-hooks
[react-events]: https://react.dev/learn/responding-to-events
[next-files]: https://nextjs.org/docs/app/api-reference/file-conventions
[next-metadata]: https://nextjs.org/docs/app/api-reference/functions/generate-metadata
[next-learn]: https://github.com/vercel/next-learn
[airbnb]: https://github.com/airbnb/javascript#naming-conventions
[airbnb-react]: https://github.com/airbnb/javascript/tree/master/react#naming
[palmer]: https://github.com/palmerhq/typescript
[ts-eslint-naming]: https://typescript-eslint.io/rules/naming-convention/
[sqlstyle]: https://www.sqlstyle.guide/#naming-conventions
[rails-ar]: https://guides.rubyonrails.org/active_record_basics.html#schema-conventions
[checkstyle-abbr]: https://checkstyle.sourceforge.io/checks/naming/abbreviationaswordinname.html
[naver]: https://naver.github.io/hackday-conventions-java/
[prolog]: https://github.com/woowacourse/prolog
[zipgo]: https://github.com/woowacourse-teams/2023-zipgo
[shadcn]: https://github.com/shadcn-ui/ui
[react-admin]: https://github.com/marmelab/react-admin
[prop-inject]: https://app.notion.com/p/3cd0899b32b881e6982ace620f340449
[prop-class]: https://app.notion.com/p/3cd0899b32b881aa84e0cb60941bc3b6
[prop-exception]: https://app.notion.com/p/3cd0899b32b881328dcbd7a73bed819d
[c-2f48aea]: https://github.com/asm17-ms2/meterengine/commit/2f48aea
[c-4945e92]: https://github.com/asm17-ms2/meterengine/commit/4945e92
[c-ea7abd2]: https://github.com/asm17-ms2/meterengine/commit/ea7abd2
[c-6e96e33]: https://github.com/asm17-ms2/meterengine/commit/6e96e33
[c-1eb7906]: https://github.com/asm17-ms2/meterengine/commit/1eb7906
[c-b1f70fe]: https://github.com/asm17-ms2/meterengine/commit/b1f70fe
[pr-111]: https://github.com/asm17-ms2/meterengine/pull/111
[l-inject]: https://github.com/asm17-ms2/meterengine/blob/2f48aea/backend/src/main/java/com/meterengine/invoice/service/DraftInvoiceService.java#L27
[l-has-code]: https://github.com/asm17-ms2/meterengine/blob/4945e92/backend/src/main/java/com/meterengine/ErrorCodes.java#L117
[l-response]: https://github.com/asm17-ms2/meterengine/blob/4945e92/backend/src/main/java/com/meterengine/customer/dto/CustomerResponse.java#L15
[l-test-ko]: https://github.com/asm17-ms2/meterengine/blob/4945e92/backend/src/test/java/com/meterengine/customer/CustomerCrudIntegrationTest.java#L156
[l-test-loc]: https://github.com/asm17-ms2/meterengine/blob/4945e92/backend/src/test/java/com/meterengine/customer/CustomerCrudIntegrationTest.java
[l-test-loc2]: https://github.com/asm17-ms2/meterengine/blob/4945e92/backend/src/test/java/com/meterengine/customer/CustomerDeleteConcurrencyTest.java
[l-nounphrase]: https://github.com/asm17-ms2/meterengine/blob/ea7abd2/backend/src/main/java/com/meterengine/invoice/service/DraftInvoiceService.java#L45
[l-map]: https://github.com/asm17-ms2/meterengine/blob/b1f70fe/backend/src/main/java/com/meterengine/pricing/service/PricePolicyService.java#L48
[l-rowview]: https://github.com/asm17-ms2/meterengine/blob/6e96e33/frontend/src/components/events/EventTable.tsx#L13
[l-prefix]: https://github.com/asm17-ms2/meterengine/blob/1eb7906/backend/src/main/java/com/meterengine/invoice/dto/DraftInvoiceResponse.java#L32
[l-prefix-yaml]: https://github.com/asm17-ms2/meterengine/blob/1eb7906/backend/openapi.yaml#L619
[jakarta-rs]: https://jakarta.ee/specifications/restful-ws/4.0/apidocs/jakarta.ws.rs/jakarta/ws/rs/NotFoundException.html
[sf-error-responses]: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html
[spring-guide]: https://github.com/cheese10yun/spring-guide
[camunda]: https://github.com/camunda/camunda-bpm-platform
[killbill-repo]: https://github.com/killbill/killbill
[fineract]: https://github.com/apache/fineract
[stripe-errors]: https://docs.stripe.com/error-codes
[chargebee]: https://apidocs.chargebee.com/docs/api/error-handling
[lago]: https://getlago.com/docs/api-reference/errors
[rfc6749-5.2]: https://datatracker.ietf.org/doc/html/rfc6749#section-5.2
[aip-193]: https://google.aip.dev/193
[toss-errors]: https://docs.tosspayments.com/reference/error-codes
[slack-errors]: https://api.slack.com/methods/conversations.info#errors
[aws-ddb-errors]: https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Programming.Errors.html
[google-errors]: https://cloud.google.com/apis/design/errors
