# pricing 정책

가격 정책 등록과 미터별 가격 조회가 쓰는 값이다. 단가 등록과 다차원 계산은 아직 이 파일이 정하는 값이 없다.

## 등록 검증

| 항목 | 값 | 코드 위치 |
|---|---|---|
| 도메인 검증 | 선언 안의 중복 키와 빈 키. 위반은 400 `invalid_price_policy` | `PricePolicyService.validate`, `PricePolicyIntegrationTest.선언에_중복_키나_빈_키가_있으면_400이고_저장은_0건이다` |

## 미터 확인

| 항목 | 값 | 코드 위치 |
|---|---|---|
| 없는 미터, 다른 도입사의 미터, 미등록 도입사 | 모두 404 `metric_not_found`이고 구별하지 않는다 | `MetricNotFoundException`, `PricePolicyIntegrationTest.없는_미터에_등록하면_404다`, `PricePolicyIntegrationTest.다른_도입사의_미터에는_등록할_수_없다` |

## 미터별 가격 조회

| 항목 | 값 | 코드 위치 |
|---|---|---|
| 순서 | 미터 code 오름차순 | `BillableMetricRepository.findByOrganizationIdOrderByCodeAsc`, `PricePolicyIntegrationTest.목록은_미터_code_오름차순이다` |
