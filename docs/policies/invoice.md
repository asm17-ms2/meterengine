# invoice 정책

청구 예정액(draft invoice) 조회가 쓰는 값이다. 확정 인보이스는 아직 이 파일이 정하는 값이 없다.

## 금액 계산

| 항목 | 값 | 코드 위치 | 근거 |
|---|---|---|---|
| 라인 금액 | 수량 x 단가에서 원 미만을 절사 | `DraftInvoiceService.charge` | 초기 관례 |
| 절사 단위 | 라인(고객 x 미터)마다 절사한 뒤 합산 | `DraftInvoiceServiceTest.절사는_라인별로_하고_합산한다` | 초기 관례 |
| 금액 타입 | 원 단위 정수 | `DraftInvoiceResponse.MetricLineItem.amount`, `openapi.yaml`의 `MetricLineItem` | 초기 관례 |
| 수량 타입 | 소수를 허용 | `DraftInvoiceResponse.MetricLineItem.quantity` | 이벤트 properties의 숫자를 그대로 합산한 값이다 |
| 단가가 없는 미터 | 라인에서 뺀다 | `DraftInvoiceService.preview`의 `baseUnitPrices` 필터, `DraftInvoiceServiceTest.단가가_없는_미터는_라인에서_빠진다` | PR #43 리뷰 |

왜 라인별 절사: 합산 후 한 번만 절사하면 화면의 라인 금액을 다 더한 값이 소계와 어긋난다.

왜 라인을 빼는가: 가격 정책 등록과 단가 등록이 분리돼 있어 단가가 아직 없는 미터는 정상 상태다. 0원 라인으로 내보내면 단가 누락이 화면에서 정상처럼 보인다.

## 응답에 담기는 것

| 항목 | 값 | 코드 위치 | 근거 |
|---|---|---|---|
| 합계와 소계를 계산하는 쪽 | 서버 | `DraftInvoiceService.preview`의 `totalAmount`, `DraftInvoiceService.customerEntry`의 `amount` | 초기 관례 |
| 이벤트가 없는 고객 | 단가가 있는 모든 미터 라인을 수량 0, 금액 0으로 갖는다 | `DraftInvoiceService.MetricQuantitiesByCustomer.lineFor`, `DraftInvoiceIntegrationTest.이벤트가_없는_고객도_금액_0으로_응답에_들어간다` | 초기 관례 |
| 사용량이 0인 라인의 단가 | 0이 아니라 실제 단가 | `DraftInvoiceServiceTest.이벤트가_없는_고객은_사용량_0_금액_0이다` | 단가는 사용량과 무관한 미터의 속성이다 |
| 고객 순서 | 이름 오름차순, 같으면 id 오름차순 | `CustomerRepository.findByOrganizationIdOrderByNameAscIdAsc` | 초기 관례 |

왜 서버가 계산: 화면이 금액을 직접 더하기 시작하면 위 절사 규칙이 프론트엔드에 복제된다.

왜 빈 고객도 라인을 갖는가: 화면이 고객마다 같은 행 구조를 그린다.

## 집계 기준

| 항목 | 값 | 코드 위치 | 근거 |
|---|---|---|---|
| 기간 | KST 기준의 달 | `BillableMetricUsageService.BILLING_ZONE` | 초기 관례 |
| month 파라미터 | `yyyy-MM`. 생략하면 이번 달 | `DraftInvoiceController.preview`, `BillableMetricUsageService.currentMonth` | 초기 관례 |
| 계산 시각 | 응답을 만든 시각(KST). 저장하지 않는다 | `DraftInvoiceService.preview`의 `calculatedAt` | 초기 관례 |

왜 저장하지 않는가: 청구 예정액은 확정본이 아니라 조회 시점에 계산해 내보내는 값이다. 화면이 표시하는 계산 시각의 정본이 이 필드다.
