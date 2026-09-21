# metric 정책

집계 미터와 고객별 월 사용량 조회가 쓰는 값이다. 미터 등록 요청의 검증 문구와 응답 모양은 아직 이 파일이 정하지 않는다.

## 미터 모델

이벤트와 미터의 연결, 지원하는 집계 함수, 목록 정렬의 정본은 `backend/openapi.yaml`의 `createBillableMetric`과 `listBillableMetrics` description이다.

## 집계 기준

청구 기간의 시간대와 월 경계, 집계의 기준 축, 숫자가 아닌 값의 제외, `month` 파라미터의 정본은 `backend/openapi.yaml`의 `aggregateBillableMetricUsages` description이다.

| 항목 | 값 | 코드 위치 |
|---|---|---|
| 표기가 다른 같은 순간 | 같은 달에 귀속된다 | `BillableMetricUsageIntegrationTest.같은_순간을_UTC로_보낸_이벤트도_같은_달에_귀속된다` |

## 사용량 응답

응답에 무엇이 실리는지는 `backend/openapi.yaml`의 `aggregateBillableMetricUsages` description과 `BillableMetricUsageCustomer` 스키마가 정본이다.
