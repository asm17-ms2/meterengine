# customer 정책

고객 등록, 수정, 삭제, 목록 조회가 쓰는 값이다.

## 이름

| 항목 | 값 | 코드 위치 |
|---|---|---|
| 길이 상한 | 255자 | `Customer.NAME_MAX_LENGTH`, `CustomerIntegrationTest.이름은_255자까지_받고_256자는_거절한다` |
| 빈 이름 | 거절한다 | `CreateCustomerRequest.name`의 `@NotBlank`, `CustomerIntegrationTest.이름이_비었거나_공백뿐이면_400이고_저장은_0건이다` |
| 이름 중복 | 허용한다 | `CustomerIntegrationTest.같은_이름을_두_번_등록하면_둘_다_남는다` |

## 목록 정렬

| 항목 | 값 | 코드 위치 |
|---|---|---|
| 정렬 | 이름 오름차순, 같으면 id 오름차순 | `CustomerRepository.findByOrganizationIdOrderByNameAscIdAsc`, `CustomerIntegrationTest.목록은_이름_오름차순이고_다른_도입사_고객은_섞이지_않는다` |
| 이름 비교 | 한국어 사전순 | `customer.name`의 collation, `CustomerIntegrationTest.목록은_한국어_사전순이다` |

## 도입사 스코프

| 항목 | 값 | 코드 위치 |
|---|---|---|
| 남의 도입사 고객 | 미등록과 구별하지 않는다 | `CustomerRepository.findByOrganizationIdAndId`, `CustomerIntegrationTest.다른_도입사_고객은_지울_수_없다` |

## 삭제

| 항목 | 값 | 코드 위치 |
|---|---|---|
| 이벤트가 있는 고객 | 지우지 않는다 | `V1__create_initial_tables.sql`이 만들고 `V7__rename_usage_event_to_event.sql`이 개명한 `event_customer_same_org` 복합 FK, `CustomerIntegrationTest.이벤트가_있는_고객을_지우면_409이고_고객은_그대로다` |
