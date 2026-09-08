# metric 정책

집계 미터와 고객별 월 사용량 조회가 쓰는 값이다. 미터 등록 요청의 검증 문구와 응답 모양은 아직 이 파일이 정하지 않는다.

## 미터 모델

| 항목 | 값 | 코드 위치 | 근거 |
|---|---|---|---|
| 이벤트와 미터의 연결 | `event.type`이 미터의 `event_type`과 같으면 집계 대상 | `BillableMetricUsageRepository.sumQuantityByCustomerId`, `BillableMetricUsageIntegrationTest.미터의_event_type과_다른_이벤트는_합에서_빠진다` | PR #19 |
| 연결 방식 | FK 없는 논리 매칭 | `EventIngestIntegrationTest.유효한_이벤트는_저장되고_200을_받는다`, `BillableMetricUsageIntegrationTest.미터의_event_type과_다른_이벤트는_합에서_빠진다` | PR #19 |
| PK | `(organization_id, code)` 복합 | `BillableMetricId` | PR #19 |
| `aggregation` 타입 | enum이 아니라 String | `BillableMetric.aggregation` | PR #22 |
| 지원하는 집계 함수 | SUM뿐 | `BillableMetric.SUM`, `BillableMetricUsageServiceTest.아직_구현하지_않은_집계_방식이면_조용히_0을_내지_않고_멈춘다`, `BillableMetricServiceTest.SUM이_아닌_집계_함수는_Invalid다` | PR #22, 등록 거절은 PR #61 |
| 단가 | 미터에 두지 않는다 | `SchemaConstraintTest.billable_metric에는_더_이상_unit_price_열이_없다` | PR #41 |
| 목록 정렬 | code 오름차순 | `BillableMetricRepository.findByOrganizationIdOrderByCodeAsc` | PR #22 |

왜 code가 PK에 함께 들어가나: 미터가 도입사별로 code를 따로 갖는 구조라 code만으로는 도입사가 다른 두 미터를 구별하지 못한다.

왜 enum이 아닌가: enum이면 DB에 SUM 아닌 값이 있을 때 매핑 시점에 터지고, 그 미터와 무관한 조회까지 같이 실패한다. 지원 여부 판정은 집계 서비스가 미터 단위로 한다.

왜 정렬을 고정하나: 없으면 반환 순서가 보장되지 않아 같은 데이터에도 응답의 미터 순서가 달라지고, 화면과 테스트가 그때그때 다른 순서를 본다.

## 집계 기준

| 항목 | 값 | 코드 위치 | 근거 |
|---|---|---|---|
| 청구 기간의 시간대 | KST | `BillableMetricUsageService.BILLING_ZONE` | PR #22 |
| 기간 | 달의 반열린 구간 `[start, end)` | `BillableMetricUsageServiceTest.기간은_KST_월의_반열린_구간으로_넘긴다` | PR #22 |
| 월 경계 | `2026-08-31T23:59:59+09:00`은 8월, `2026-09-01T00:00:00+09:00`은 9월 | `BillableMetricUsageIntegrationTest.팔월_마지막_순간의_이벤트는_팔월에_귀속된다`, `.구월_첫_순간의_이벤트는_구월에_귀속된다` | PR #22 |
| 표기가 다른 같은 순간 | 같은 달에 귀속된다 | `BillableMetricUsageIntegrationTest.같은_순간을_UTC로_보낸_이벤트도_같은_달에_귀속된다`를 위 월 경계 두 테스트와 짝지어 읽는다. 같은 순간을 Z 표기와 `+09:00` 표기로 각각 넣어 같은 달을 단언한다 | PR #22 |
| 집계의 기준 축 | 이벤트가 아니라 고객 목록 | `BillableMetricUsageService.aggregate`, `BillableMetricUsageIntegrationTest.이벤트가_없는_고객도_사용량_0으로_응답에_들어간다` | PR #22 |
| `target_property` 값이 숫자가 아닌 이벤트 | 합계에서 뺀다 | `BillableMetricUsageRepository.sumQuantityByCustomerId`의 `jsonb_typeof` 필터, `BillableMetricUsageIntegrationTest.token이_숫자가_아니거나_없는_이벤트는_합에서_빠진다` | PR #22 |
| SUM인데 `target_property`가 빈 미터 | 조회에서 0을 내지 않고 멈춘다 | `BillableMetricUsageServiceTest.SUM인데_합할_대상_키가_없는_미터도_멈춘다`, 등록 거절은 `BillableMetricServiceTest.SUM인데_target_property가_없으면_Invalid다` | PR #22, 등록 거절은 PR #61 |
| 계산 시점 | 조회할 때 계산한다. 스냅샷 테이블을 두지 않는다 | `BillableMetricUsageController.aggregateBillableMetricUsages` | PR #22 |
| `month` 파라미터 | `yyyy-MM`. 생략하면 이번 달 | `BillableMetricUsageService.currentMonth`, `BillableMetricUsageIntegrationTest.month를_생략하면_이번_달_KST로_집계한다` | PR #22 |

왜 반열린 구간인가: 끝을 `8/31 23:59:59`로 잡으면 `23:59:59.5` 같은 이벤트가 어느 달에도 속하지 않는다.

왜 SQL에서 `AT TIME ZONE`으로 월을 뽑지 않나: 컬럼에 함수를 씌우면 나중에 붙일 인덱스를 타지 못하고, 서버 timezone 설정에 결과가 흔들린다. `occurred_at`이 TIMESTAMPTZ라 반열린 구간 비교가 절대 시각으로 이뤄지고, 그래서 같은 순간을 어느 오프셋으로 표기해 보냈든 결과가 같다.

왜 고객 목록이 기준인가: 이벤트를 기준으로 하면 한 건도 보내지 않은 고객이 응답에 아예 나오지 않아, 상위 서비스가 고객 목록과 병합하는 로직을 따로 갖게 된다.

왜 숫자가 아닌 값을 거르나: 수집 API가 properties의 내용을 검증하지 않아 값이 문자열이거나 아예 없는 이벤트가 저장돼 있을 수 있다. 필터가 없으면 그런 이벤트 한 건이 numeric 캐스팅에서 터져 조회 전체가 500이 된다.

왜 조회에서 멈추는가: 조회는 이미 저장된 미터를 계산할 뿐이라 지원하지 않는 집계 방식이 걸려도 도입사가 요청을 고쳐 풀 수 있는 일이 아니고, 그래서 4xx가 아니라 예외로 500을 낸다. 조용히 0이나 부분 합을 내려보내면 그 값이 그대로 청구 근거가 된다. 같은 상황을 등록 경계에서는 400 `invalid_billable_metric`으로 막는다.

## 사용량 응답

| 항목 | 값 | 코드 위치 | 근거 |
|---|---|---|---|
| 수량 타입 | `BigDecimal` | `CustomerUsage.quantity`, `BillableMetricUsageIntegrationTest.소수_사용량은_자릿수가_잘리지_않고_합산된다` | PR #22 |
| 금액 | 내지 않는다 | `BillableMetricUsageIntegrationTest.응답에_미터_정보와_고객_이름이_함께_나온다`의 `unit_price` 부재 단언 | PR #22, 소비처는 PR #23 |
| 고객 식별 | 고객 UUID 하나. 도입사 ID를 함께 싣지 않는다 | `CustomerUsage.customerId` | PR #22 |
| 고객 이름 | 집계 시점의 이름을 함께 싣는다 | `CustomerUsage.customerName`, `BillableMetricUsageIntegrationTest.응답에_미터_정보와_고객_이름이_함께_나온다` | PR #22 |
| 미터가 없는 도입사 | 빈 배열 | `BillableMetricUsageIntegrationTest.미터가_없는_도입사는_billable_metric_usages가_빈_배열이다` | PR #22 |

왜 `BigDecimal`인가: properties의 숫자는 jsonb에 numeric으로 담겨 자릿수가 그대로 남는다. 토큰처럼 정수인 값만 오리라 가정하고 `long`으로 좁히면 소수를 실어 보내는 미터가 생겼을 때 청구 근거가 조용히 잘린다.

왜 금액을 내지 않나: 사용량에 단가를 곱하는 일은 청구 예정액 조회의 몫이고, 이 응답이 그 입력이 된다. 값은 `docs/policies/invoice.md`가 정한다.

왜 이름을 함께 싣나: 화면이 UUID 대신 보여줄 것이 필요하다.

왜 도입사 ID를 싣지 않나: 고객 UUID가 전역에서 유일해 그것만으로 지목된다.

남은 빈칸: 고객에 alias가 붙으면 응답의 고객 식별을 UUID 하나로 둘지 다시 봐야 한다.

## 구현 메모

| 항목 | 값 | 코드 위치 | 근거 |
|---|---|---|---|
| 사용량 집계 쿼리 | JPA가 아니라 `JdbcTemplate` | `BillableMetricUsageRepository` | PR #22 |
| 집계 한 번의 트랜잭션 | 읽기 트랜잭션 하나로 묶는다 | `BillableMetricUsageService.aggregate`의 `@Transactional(readOnly = true)` | PR #22 |
| `properties` 키를 바인딩할 때 | `?::text`로 타입을 명시한다 | `BillableMetricUsageRepository.sumQuantityByCustomerId` | PR #22 |

왜 JPA가 아닌가: `event`는 append-only 트리거가 걸려 있어 엔티티 매핑 없이 관리되는 테이블이고, properties(jsonb)에서 파라미터로 고른 키를 합산하는 쿼리는 JPQL로 표현할 수 없다.

왜 트랜잭션으로 묶나: 미터와 고객과 이벤트를 나눠 읽는데 그 사이에 데이터가 바뀌면 한 응답 안에서 서로 다른 시점을 섞어 보게 된다. 묶어 두면 JPA 레포지토리와 `JdbcTemplate`이 같은 커넥션을 쓴다.

왜 `?::text`인가: `->>`에 text와 integer 오버로드가 있어, 파라미터 타입을 명시하지 않으면 어느 연산자를 부르는지 모호해진다.
