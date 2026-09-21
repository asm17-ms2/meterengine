# invoice 정책

청구 예정액(draft invoice) 조회가 쓰는 값이다. 확정 인보이스는 아직 이 파일이 정하는 값이 없다.

## 금액 계산

라인 금액과 절사 단위, 금액 타입, 단가가 없는 미터의 정본은 `backend/openapi.yaml`의 `previewDraftInvoice`와 `createPricePolicy` description이다.

## 응답에 담기는 것

이벤트가 없는 고객과 고객 순서의 정본은 `backend/openapi.yaml`의 `previewDraftInvoice` description이다.

| 항목 | 값 | 코드 위치 |
|---|---|---|
| 사용량이 0인 라인의 단가 | 0이 아니라 실제 단가 | `DraftInvoiceServiceTest.이벤트가_없는_고객은_사용량_0_금액_0이다` |

## 집계 기준

기간과 월 경계와 `month` 파라미터는 청구 예정액이 집계에서 그대로 받아 쓴다. 그 값의 정본은 `backend/openapi.yaml`의 `aggregateBillableMetricUsages` description이다.
