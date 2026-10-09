# invoice 정책

청구 예정액(draft invoice) 조회와 인보이스 확정이 쓰는 값이다.

## 금액 계산

라인 금액과 절사 단위, 금액 타입, 단가가 없는 미터의 정본은 `backend/openapi.yaml`의 `previewDraftInvoice`와 `createPricePolicy` description이다.

## 응답에 담기는 것

이벤트가 없는 고객과 고객 순서의 정본은 `backend/openapi.yaml`의 `previewDraftInvoice` description이다.

| 항목 | 값 | 코드 위치 |
|---|---|---|
| 사용량이 0인 라인의 단가 | 0이 아니라 실제 단가 | `DraftInvoiceServiceTest.이벤트가_없는_고객은_사용량_0_금액_0이다` |

## 집계 기준

기간과 월 경계와 `month` 파라미터는 청구 예정액이 집계에서 그대로 받아 쓴다. 그 값의 정본은 `backend/openapi.yaml`의 `aggregateBillableMetricUsages` description이다.

## 확정

라인 금액과 절사는 청구 예정액과 같은 계산을 쓴다. 합계의 정본은 `backend/openapi.yaml`의 `listInvoices` description이다.

| 항목 | 값 | 코드 위치 |
|---|---|---|
| 단가에 세금이 들어 있는지 | 세금 별도(공급가액) | `InvoiceFinalizeIntegrationTest.단가는_세금_별도이고_세액은_공급가액의_10퍼센트이며_합계는_둘의_합이다` |
| 세액률 | 공급가액의 10% | `InvoiceFinalizationService.TAX_RATE_PERCENT` |
| 단가가 없는 미터 | 라인에서 빠진 채 확정한다 | `InvoiceFinalizeIntegrationTest.단가가_없는_미터는_라인에서_빠진_채_확정한다` |
| 확정 뒤에 들어온 그 달 이벤트 | 수집 기간(`docs/policies/event.md`) 안이면 저장하지만 어느 인보이스에도 청구하지 않는다 | `InvoiceFinalizeIntegrationTest.확정_뒤에_새_이벤트가_들어오고_단가가_바뀌어도_금액과_라인이_변하지_않는다` |
| 확정 뒤의 단가 변경 | 확정 금액과 라인의 수량, 단가가 바뀌지 않는다 | `InvoiceFinalizeIntegrationTest.확정_뒤에_새_이벤트가_들어오고_단가가_바뀌어도_금액과_라인이_변하지_않는다` |
