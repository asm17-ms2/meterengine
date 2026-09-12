# invoice 정책

청구 예정액(draft invoice) 조회가 쓰는 값이다. 확정 인보이스는 아직 이 파일이 정하는 값이 없다.

## 금액 계산

| 항목 | 값 | 코드 위치 |
|---|---|---|
| 라인 금액 | 수량 x 단가에서 원 미만을 절사 | `DraftInvoiceService.charge` |
| 절사 단위 | 라인(고객 x 미터)마다 절사한 뒤 합산 | `DraftInvoiceServiceTest.절사는_라인별로_하고_합산한다` |
| 단가가 없는 미터 | 라인에서 뺀다 | `DraftInvoiceService.preview`의 `baseUnitPrices` 필터, `DraftInvoiceServiceTest.단가가_없는_미터는_라인에서_빠진다` |

## 응답에 담기는 것

| 항목 | 값 | 코드 위치 |
|---|---|---|
| 이벤트가 없는 고객 | 단가가 있는 모든 미터 라인을 수량 0, 금액 0으로 갖는다 | `DraftInvoiceService.BillableMetricQuantitiesByCustomer.toDraftInvoiceLine`, `DraftInvoiceIntegrationTest.이벤트가_없는_고객도_금액_0으로_응답에_들어간다` |
| 사용량이 0인 라인의 단가 | 0이 아니라 실제 단가 | `DraftInvoiceServiceTest.이벤트가_없는_고객은_사용량_0_금액_0이다` |

## 집계 기준

| 항목 | 값 | 코드 위치 |
|---|---|---|
| 시간대 | KST | `BillableMetricUsageService.BILLING_ZONE` |
| month를 생략하면 | 이번 달 | `DraftInvoiceController.previewDraftInvoice`, `BillableMetricUsageService.currentMonth` |
