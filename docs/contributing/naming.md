# 이름 규칙

- 기본은 Google Java Style Guide, Google TypeScript Style Guide, Zalando RESTful API Guidelines, Google AIP다.
- 가이드와 다르게 간 것은 아래뿐이다.
  - 테스트 메서드 이름은 한국어 문장.
  - 문자열 열거값과 오류 코드는 소문자 snake_case.
  - 컴포넌트 파일명은 PascalCase.
  - URL 버전은 `/v1/` 경로 접두사.
- 표에 없는 영역 안의 이름은 아래 "원칙"대로 담당자가 정한다.

## 원칙

- **읽는 자리에서 답이 나온다.** 선언부로 올라가지 않아도 호출부만 보고 무엇(대상)이고 어떤 종류(계층, 역할)인지 안다.
- **같은 개념에 같은 낱말, 다른 개념에 다른 낱말.** 한 대상을 파일마다 다르게 부르지 않고, 엔티티를 약칭하지 않는다.
- **타입과 문맥에 이미 있는 정보를 되풀이하지 않는다.** 컬렉션에 `List`를, 클래스가 말하는 대상을 메서드에, 응답 리소스명을 그 필드에 다시 적지 않는다.
- **줄이지 않는다.** 사전에 있는 낱말은 약어로 만들지 않고 이름 길이에 상한을 두지 않는다.
- **고를 자유도를 남기지 않는다.** 형식이 하나로 정해지는 쪽을 택한다.
- **밖에서 정한 이름은 그대로.** HTTP 헤더, 프레임워크 예약 export, 구현하는 인터페이스의 접근자는 우리가 짓지 않는다.

## 도구가 보는 것

여기 다시 적지 않는다. 실패하면 규칙 이름이 곧 설명이다.

- 백엔드 케이스와 약어: Checkstyle(`backend/config/checkstyle/checkstyle.xml`).
- 패키지, 클래스 접미사와 쓰지 않는 접미사, 서비스와 컨트롤러와 리포지토리 메서드 꼴, boolean, 시간, 컬렉션, Map, 주입 필드, 상수, 핸들러, 엔티티 테이블명: ArchUnit(`backend/src/test/java/com/meterengine/global/ClassNamingConventionTest.java`, `MemberNamingConventionTest.java`).
- 테스트 클래스와 메서드 이름, 테스트 위치: ArchUnit(`TestNamingConventionTest.java`).
- API 파라미터와 JSON 키의 snake_case, operationId 유일: `OpenApiDocumentTest.java`.
- 프론트 케이스, boolean 접두사, 모듈 수준 상수, 파일과 폴더 이름: ESLint(`frontend/eslint.config.mjs`).

## 도구가 보지 않는 것

다른 사람이 가져다 쓰거나 고치기 어려운 이름만 둔다.

| 식별자 | 형식 | 예 |
| --- | --- | --- |
| 엔티티 | 도메인 명사 그대로. 약칭 금지는 URL, JSON 키, DB 컬럼, 프론트까지 적용 | `BillableMetric` (`Metric` 아님) |
| 서비스 메서드 동사 | 저장 `create` `update` `delete`, 읽기 `list` `find`(없을 수 있음) `get`(없으면 예외), 계산 `aggregate` `preview`(저장 안 함), 상태 전이는 도메인 동사 | `preview`, `finalize` |
| 요청 DTO | `<동사><리소스>Request`. 동사는 서비스 메서드와 같은 낱말 | `CreateCustomerRequest` |
| 응답 DTO | 목록 `List<리소스들>Response`, 하나 `<리소스>Response`. 그 응답에만 속하는 요소는 `<응답 리소스><JSON 필드명 단수>` | `ListCustomersResponse`, `DraftInvoiceCustomer` |
| 서비스 사이 전달 타입 | `<대상><내용>` 명사구. `Request`/`Response` 접미사 없음 | `CustomerUsage` |
| URL 경로 | kebab-case 복수 명사, 동사 없음. 하나뿐인 하위 리소스와 셀 수 없는 명사는 단수 | `/v1/billable-metrics/{code}/price-policy` |
| 경로 변수 | 앞 세그먼트 리소스의 식별 JSON 키. 리소스명을 되풀이하지 않고, 둘 이상이면 `<리소스 단수>_<키>` | `/v1/customers/{id}` |
| DB 컬럼 | 다른 테이블을 가리키면 `<참조 테이블>_<참조 컬럼>` | `customer_id` |
| DB 제약 | `<테이블>_<대상>_<종류>`, 종류는 `pk` `fk` `unique` `check` `not_null`. 도입사 경계 복합 FK는 `<테이블>_<참조 테이블>_same_organization_fk` | `customer_organization_fk` |
| 오류 코드 상수 | 대상을 주어로 한 상태는 대상 뒤, 대상을 꾸미는 한 낱말은 대상 앞 | `CUSTOMER_NOT_FOUND`, `INVALID_BILLABLE_METRIC` |
| 프론트 API 타입과 함수 | 백엔드 스키마 이름과 operationId 그대로 | `ListCustomersResponse`, `listCustomers` |
