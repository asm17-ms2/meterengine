# 정본 표: 여러 페이지에 걸치는 사실

- 이 파일은 공개하지 않는다(.mintignore). 가이드를 고치는 사람이 읽는 규칙이다.
- 규칙: 사실은 "정본 페이지"에만 자세히 쓴다. 다른 페이지에는 "한 줄 요약"을 그대로 또는 문맥에 맞게 조금 고쳐 쓰고 정본 페이지로 링크한다. 숫자, code, 화면 이름은 요약에서도 정본과 같게 쓴다.
- 확인 필요 열은 팀이 아직 정하지 않아 가이드가 임시로 정한 것이다. 그 문장 바로 앞에 `{/* 확인 필요: 내용 */}` 주석이 있다.
- 정본 페이지를 쓰는 사람은 쓰기 전에 사실을 코드로 다시 확인한다. 이 표의 요약은 코드로 확인한 범위에서 썼지만 문장 단위 근거는 정본 페이지 쪽에서 단다.
- 새 중복 사실을 발견하면 이 표에 행을 더하고 정본을 하나 고른다. 정본 선택 기준: (1) 그 사실을 설명하는 데 가장 많은 문단이 필요한 페이지 (2) 같으면 독자가 먼저 읽는 페이지.

## [1] 서비스 전반

| 사실 | 정본 페이지 | 다른 페이지에서 쓰는 한 줄 요약 | 주로 쓰는 페이지 | 확인 필요 |
| --- | --- | --- | --- | --- |
| 서비스는 미리 보기 단계다. 테스트 환경 검증 용도로 쓰고 실제 고객 개인정보와 실결제는 쓰지 않는다 | /index | MeterEngine은 지금 미리 보기 단계라 테스트 환경에서 연동을 검증하는 용도로 씁니다. | /quickstart, /account/signup, /api-reference/introduction | 외부 가입을 받는지와 미리 보기 단계 표현 |
| 콘솔은 https://meterengine.com, API는 같은 도메인의 /v1/ 아래. 테스트와 실제 환경도 주소가 같고 키가 환경을 정한다 | /api-reference/introduction | API는 https://meterengine.com/v1/ 아래에 있고, 어느 환경에 기록될지는 API 키가 정합니다. | /index, /quickstart, /authentication/api-keys, /payments/toss-setup | 공개 API 주소 |
| SDK를 배포하지 않는다. 예시는 curl과 짧은 코드 조각이다 | /index | MeterEngine은 SDK를 배포하지 않으므로 예시는 HTTP 요청과 코드 조각으로 보여 줍니다. | /quickstart, /integration/events, /integration/retries, /webhooks/verify-signatures |  |
| MeterEngine은 메일을 보내지 않는다(가입 승인, 초대, 청구, 링크 모두) | /index | MeterEngine은 메일을 보내지 않으니 링크와 안내는 도입사가 직접 전달합니다. | /account/signup, /account/members, /billing/finalize-invoices, /payments/invoice-links, /payments/card-registration | 승인 알림 없음과 처리 시간 |
| 전자세금계산서를 발행하지 않는다. invoice_number는 세금계산서 번호가 아니다 | /account/supplier | MeterEngine은 전자세금계산서를 발행하지 않습니다. | /index, /concepts/amounts | 공급자 정보 사용처 |
| 문의와 가입 승인 요청 경로(GitHub 이슈, 적으면 안 되는 정보, 제목 형식) | /index | 문의와 승인 요청 방법은 소개 페이지의 문의 절을 보세요. | /account/signup, /quickstart, /api-basics/errors, /troubleshooting/events-not-billed | 문의 경로(GitHub 이슈) |

## [2] 계정, 인증, 권한

| 사실 | 정본 페이지 | 다른 페이지에서 쓰는 한 줄 요약 | 주로 쓰는 페이지 | 확인 필요 |
| --- | --- | --- | --- | --- |
| 가입은 승인제다. 승인 전, 거절, 정지된 도입사는 모두 403 organization_not_active를 받는다. 승인 알림은 없다 | /account/signup | 승인 전이거나 거절, 정지된 도입사는 403 organization_not_active를 받습니다. | /quickstart, /api-basics/errors, /authentication/api-keys | 승인 알림 없음과 처리 시간 |
| 가입 화면의 필수 동의(이용 안내, 개인정보 처리 안내)와 지금 안내가 정식 약관이 아니라는 점 | /account/signup | 필수 동의 항목인 '이용 안내'와 '개인정보 처리 안내'에 체크합니다. | /quickstart | 검증용 동의 안내 |
| API 키 발급 화면의 기본 권한은 read 하나다. 이벤트를 보내려면 ingest를 더 체크한다 | /authentication/api-keys | 키 발급 화면의 기본 권한은 read뿐이라 이벤트를 보내려면 ingest도 고릅니다. | /quickstart, /authentication/scopes | 없음 |
| 초대 링크와 계정 직접 생성의 기본 권한은 조회(read)다 | /account/members | 구성원을 추가할 때 기본 권한은 조회(read)입니다. | /authentication/scopes |  |
| read, ingest, admin의 뜻과 포함 관계(GET은 read, 고객 등록과 이벤트는 ingest, 나머지 변경은 admin) | /authentication/scopes | 조회는 read, 고객 등록과 이벤트 전송은 ingest, 그 밖의 변경은 admin 권한이 필요합니다. | /authentication/api-keys, /api-reference/introduction, 각 기능 페이지의 "필요 권한" 줄 | 없음 |
| 콘솔 로그인으로만 되는 기능(API 키 관리, 토스페이먼츠 키 저장, 구성원과 초대, 감사 기록 등). API 키로 부르면 거절된다 | /authentication/scopes | 이 기능은 콘솔에 로그인한 상태에서만 됩니다. API 키로 부르면 거절됩니다. | /authentication/api-keys, /account/members, /payments/toss-setup, /api-reference/introduction | API 참조에서 숨기는 범위 |
| 로그인 세션은 12시간 유지된다 | /authentication/scopes | 콘솔 로그인은 12시간 동안 유지됩니다. | /account/signup | 없음 |
| API 키 원문은 발급 직후 한 번만 보인다 | /authentication/api-keys | 키 원문은 발급 직후 한 번만 보이니 바로 비밀 저장소에 옮깁니다. | /quickstart, /authentication/key-rotation | 없음 |
| 대표관리자만 사용자와 초대를 관리한다 | /account/members | 사용자와 초대 관리는 대표관리자만 할 수 있습니다. | /authentication/scopes |  |

## [3] 업무 환경

| 사실 | 정본 페이지 | 다른 페이지에서 쓰는 한 줄 요약 | 주로 쓰는 페이지 | 확인 필요 |
| --- | --- | --- | --- | --- |
| 로그인하면 업무 환경이 '기존 데이터'로 열린다. 먼저 '테스트 환경'으로 전환한다. 다시 로그인하면 기존 데이터로 돌아간다 | /concepts/environments | 로그인 뒤 화면 위 '업무 환경'이 '테스트 환경'인지 확인하세요. | /quickstart, /authentication/api-keys, /payments/test-and-live | 새 도입사 기본 환경이 기존 데이터 |
| 기존 데이터에서 발급한 키는 me_live_, me_test_ 접두사가 붙어도 어느 환경에도 격리되지 않는다 | /concepts/environments | 기존 데이터에서는 API 키를 발급하지 마세요. | /authentication/api-keys | 새 도입사 기본 환경이 기존 데이터 |
| 테스트 환경의 정의: 데이터가 분리되고 실결제가 없지만 이벤트, 확정, 웹훅은 실제로 일어나고 되돌릴 수 없다. "샌드박스"라고 부르지 않는다 | /concepts/environments | 테스트 환경도 보낸 이벤트와 확정한 인보이스는 되돌릴 수 없습니다. | /quickstart, /payments/test-and-live, /api-reference/introduction | 테스트 키 정의와 mode 설명 |
| 테스트, 실제 환경 키는 발급 환경에 고정되고 X-MeterEngine-Environment 헤더를 생략해도 된다. 다른 환경을 명시하면 403 environment_mismatch | /concepts/environments | 키는 발급한 환경에 고정되어 환경 헤더를 따로 넣지 않아도 됩니다. | /authentication/api-keys, /api-basics/errors, /api-reference/introduction | 시험 화면 proxy와 테스트 키 안내 |
| 환경마다 따로인 것(고객, 미터, 단가, 키, 토스페이먼츠 키, 웹훅, 확정)과 공통인 것(계정, 구성원 권한). 환경을 옮겨도 데이터는 복사되지 않는다 | /concepts/environments | 고객, 미터, 단가는 업무 환경마다 따로 만들어야 합니다. | /integration/customers, /pricing/meters, /payments/toss-setup, /webhooks/overview, /billing/auto-finalization | 없음 |

## [4] 시간 규칙

| 사실 | 정본 페이지 | 다른 페이지에서 쓰는 한 줄 요약 | 주로 쓰는 페이지 | 확인 필요 |
| --- | --- | --- | --- | --- |
| 청구 월은 KST 달력 월이다 | /concepts/time-rules | 청구 월은 한국 시간(KST) 기준 달력 월입니다. | /integration/events, /pricing/price-models | 없음 |
| 이벤트는 수신 시각 기준 35일보다 오래된 timestamp면 400 event_too_old로 거절된다 | /concepts/time-rules | 35일보다 오래된 이벤트는 거절됩니다. | /integration/events, /integration/retries, /troubleshooting/events-not-billed | 없음 |
| 집계 마감은 다음 달 1일 13:00 KST(수신 시각 기준)다. 마감 뒤 받은 지난달 이벤트는 저장되지만 청구되지 않는다 | /concepts/time-rules | 지난달 사용량은 다음 달 1일 13:00 KST까지 받은 것만 청구됩니다. | /integration/retries, /integration/verify-usage, /billing/finalize-invoices, /troubleshooting/events-not-billed | 없음 |
| 지난달 단가는 새로 넣거나 바꿀 수 없다(1일 00:00 KST부터) | /concepts/time-rules | 단가는 이번 달 이후에만 넣을 수 있습니다. | /pricing/price-models, /concepts/how-it-works, /troubleshooting/events-not-billed | 없음 |
| 자동 확정 시각(켜져 있을 때 다음 달 1일 14:00 KST가 지난 뒤 매 정시) | /concepts/time-rules (시각만), 동작은 /billing/auto-finalization | 자동 확정은 켜져 있을 때 다음 달 1일 14:00 KST 이후에 돕니다. | /billing/finalize-invoices | 배포 서버 자동 확정 켜는 시점 |
| 예약 작업 시각(청구 제외 통보 매일 09:00, 결제 결과 확인, 결제 재시도, 대사 04:00) | /concepts/time-rules | 예약 작업 시각은 시간 규칙 페이지에 모았습니다. | /payments/charging, /payments/retries, /payments/reconciliation, /webhooks/events | 없음 |

## [5] 금액

| 사실 | 정본 페이지 | 다른 페이지에서 쓰는 한 줄 요약 | 주로 쓰는 페이지 | 확인 필요 |
| --- | --- | --- | --- | --- |
| 금액 용어: 청구 예정액(세전 공급가액, 조회할 때마다 계산), 확정 청구액(supply_amount, tax_amount, total_amount), 수납액(세금 포함) | /concepts/amounts | 청구 예정액은 세금을 뺀 공급가액이고, 결제는 세금을 포함한 확정 금액으로 합니다. | /quickstart, /integration/verify-usage, /billing/finalize-invoices, /payments/charging | 없음 |
| 금액은 인보이스 라인마다 10원 미만을 버린다 | /concepts/amounts | 금액은 라인마다 10원 미만을 버립니다. | /quickstart, /pricing/price-models, /pricing/price-plans, /pricing/calculation-example | 없음 |
| 세액은 공급가액의 10%다 | /concepts/amounts | 세액은 공급가액의 10%입니다. | /pricing/calculation-example, /billing/finalize-invoices | 없음 |
| 계산 예시의 숫자(구간 단가, 최소 과금, 기본료) | /pricing/calculation-example | 숫자로 따라가는 예시는 금액 계산 따라가기 페이지에 있습니다. | /concepts/amounts, /pricing/price-models, /pricing/price-plans | 없음 |
| 공급가액이 0원인 고객에게는 인보이스를 만들지 않는다 | /billing/finalize-invoices | 0원 고객은 확정해도 인보이스가 생기지 않습니다. | /concepts/amounts, /billing/auto-finalization | 없음 |

## [6] 가격 설계와 데이터 변경

| 사실 | 정본 페이지 | 다른 페이지에서 쓰는 한 줄 요약 | 주로 쓰는 페이지 | 확인 필요 |
| --- | --- | --- | --- | --- |
| 안전한 준비 순서(미터와 단가 -> 고객 -> 이벤트, 고객은 앞에 와도 된다)와 되돌릴 수 없는 제약의 목록 | /concepts/how-it-works | 미터와 단가를 먼저 만들고 이벤트를 보내세요. | /quickstart, /pricing/meters, /integration/events | 없음 |
| 이벤트가 들어온 미터는 이름만 바꿀 수 있다 | /pricing/meters | 이벤트가 들어온 미터는 계산 기준을 바꿀 수 없습니다. | /concepts/how-it-works, /pricing/changes-and-archiving | 없음 |
| 단가 조합의 dimension_values 키 집합은 미터의 차원 속성과 정확히 같아야 하고 조합 사이 대체가 없다 | /pricing/dimensions | 단가가 없는 조합의 사용량은 청구되지 않습니다. | /pricing/price-models, /troubleshooting/events-not-billed | 없음 |
| 보낸 이벤트는 고치거나 지울 수 없다. sum 미터만 확정 전 상계 이벤트로 바로잡을 수 있다 | /integration/events | 보낸 이벤트는 고치거나 지울 수 없습니다. | /quickstart, /pricing/changes-and-archiving, /troubleshooting/events-not-billed, /billing/after-finalization, /pricing/meters | 음수 상계 이벤트로 정정 |
| 확정한 인보이스는 고치거나 취소, 재발행할 수 없다 | /billing/after-finalization | 확정한 인보이스는 되돌릴 수 없습니다. | /billing/finalize-invoices, /billing/auto-finalization, /payments/cancellations | 없음 |
| 보관과 복원 규칙(고객 보관은 즉시, 미터 보관은 다음 달부터 등) | /pricing/changes-and-archiving | 보관과 복원 규칙은 바꾸기, 보관, 복원 페이지를 보세요. | /integration/customers, /pricing/meters | 없음 |

## [7] 연동과 API 사용

| 사실 | 정본 페이지 | 다른 페이지에서 쓰는 한 줄 요약 | 주로 쓰는 페이지 | 확인 필요 |
| --- | --- | --- | --- | --- |
| 이벤트의 customer_id는 MeterEngine 고객 id(UUID)이고 외부 고객 ID가 아니다. id를 잃었을 때 다시 찾는 법 | /integration/customers | 이벤트의 customer_id에는 고객 등록 응답의 id(UUID)를 넣습니다. | /quickstart, /integration/events |  |
| 멱등 규칙 표(이벤트 transaction_id, 고객 external_id 또는 Idempotency-Key, 미터 Idempotency-Key, 단가와 요금제 덮어쓰기, 결제 취소 키) | /api-basics/idempotency | 같은 transaction_id로 다시 보내면 처음 것이 남습니다. | /integration/events, /integration/retries, /integration/customers, /pricing/meters | 없음 |
| 결제 요청에는 Idempotency-Key가 없다. 다시 요청하기 전에 결제 시도를 조회한다 | /payments/charging | 결제 요청은 멱등 키가 없으니 다시 요청하기 전에 결과를 먼저 조회합니다. | /api-basics/idempotency, /payments/retries | 없음 |
| 오류 응답 형식 {code, message, errors}와 code로 분기하는 규칙, 상태별 다시 보낼지 판단 | /api-basics/errors | 오류는 code로 분기하세요. | /integration/retries, /payments/charging | 없음 |
| 수집 요청은 감사 기록에 남지 않는다. 저장된 이벤트와 수집 거절 기록의 request_id, transaction_id로 찾는다 | /api-basics/errors | 이벤트 수집 요청은 감사 기록에 나오지 않으니 request_id와 transaction_id로 찾습니다. | /integration/verify-usage, /troubleshooting/events-not-billed |  |
| 이벤트를 다시 보낼 것과 멈출 것(영구 거절 code), 아웃박스 | /integration/retries | 영구 거절 code는 다시 보내지 마세요. | /integration/events, /api-basics/errors | 없음 |
| 배치는 한 요청에 1건에서 100건, 원소별 결과, HTTP 상태는 200 | /integration/events | 이벤트는 한 요청에 100건까지 묶어 보낼 수 있습니다. | /integration/retries, /api-basics/rate-limits | 없음 |
| 분당 요청 한도는 키에 정한 경우에만 걸리고 429 rate_limit_exceeded와 Retry-After를 준다 | /api-basics/rate-limits | 한도를 넘으면 429를 받으니 Retry-After만큼 기다렸다가 다시 보냅니다. | /api-basics/errors, /authentication/api-keys, /integration/retries | 없음 |
| 개요 화면 체크리스트 순서와 가이드 순서가 다르다. 체크리스트는 진행 표시다 | /quickstart | 개요 화면의 목록은 진행 표시이며 순서를 따를 필요는 없습니다. | /concepts/how-it-works | 개요 체크리스트 완료 조건 |
| API 참조 시험 화면에는 테스트 환경 키만 넣는다(Mintlify 서버 경유) | /api-reference/introduction | 시험 화면에는 테스트 환경 키(me_test_)만 넣으세요. | /authentication/api-keys | 시험 화면 proxy와 테스트 키 안내 |

## [8] 청구와 결제

| 사실 | 정본 페이지 | 다른 페이지에서 쓰는 한 줄 요약 | 주로 쓰는 페이지 | 확인 필요 |
| --- | --- | --- | --- | --- |
| 자동 확정은 서버 전체 설정이라 도입사가 켜고 끌 수 없고, auto_finalize_enabled로 확인한다 | /billing/auto-finalization | 자동 확정 여부는 청구 예정액 응답의 auto_finalize_enabled로 확인합니다. | /billing/finalize-invoices, /quickstart, /concepts/time-rules | 배포 서버 자동 확정 켜는 시점 |
| 확정해도 결제는 자동으로 시작되지 않는다. 첫 결제는 도입사가 요청한다 | /payments/charging | 확정한 뒤 인보이스마다 결제를 요청해야 합니다. | /payments/overview, /billing/finalize-invoices, /billing/auto-finalization, /billing/after-finalization | 확정 뒤 자동 첫 결제 없음 |
| pending은 실패가 아니고 MeterEngine이 결과를 다시 확인한다 | /payments/charging | 결과 확인 중(pending)인 결제는 다시 요청하지 마세요. | /payments/retries, /payments/reconciliation | 없음 |
| 자동 재시도 조건과 다시 하지 않는 실패 | /payments/retries | 실패한 결제는 조건에 맞으면 자동으로 다시 시도합니다. | /payments/charging, /webhooks/events | 없음 |
| TossPayments-Test-Code는 테스트 결제에서만 쓰고 실결제에서 보내면 400 toss_payments_test_code_in_live_mode | /payments/test-and-live | 실패를 재현하려면 테스트 결제에서 TossPayments-Test-Code 헤더를 씁니다. | /payments/retries, /payments/charging | 토스 테스트 코드 헤더와 기존 데이터 결제 모드 |
| 토스페이먼츠 키는 업무 환경마다 따로 저장하고 콘솔 로그인으로만 저장한다 | /payments/toss-setup | 토스페이먼츠 키는 업무 환경마다 콘솔에서 저장합니다. | /payments/overview, /payments/test-and-live | API 참조에서 숨기는 범위 |
| 인보이스 조회 링크의 token은 발급 응답에만 있고 주소는 도입사가 조합해 전달한다 | /payments/invoice-links | 인보이스 링크는 발급 응답의 token으로 주소를 만들어 직접 전달합니다. | /payments/overview, /billing/after-finalization, /payments/retries | 공개 인보이스 조회 API와 카드 등록 링크 권한 |
| 카드 등록 링크의 token, 24시간, 한 번, MeterEngine 화면에서만 카드 입력 | /payments/card-registration | 카드 등록은 MeterEngine이 제공하는 링크 화면에서만 합니다. | /payments/overview, /payments/charging | 공개 인보이스 조회 API와 카드 등록 링크 권한 |
| 결제 취소해도 확정 금액은 그대로다 | /payments/cancellations | 결제를 취소해도 인보이스 금액은 바뀌지 않습니다. | /billing/after-finalization | 없음 |
| 수납 상태 값(not_required ~ canceled)과 콘솔 표기 | /payments/reconciliation | 수납 상태는 지금 결제 모드의 가장 늦은 결제 시도로 정합니다. 값 전체는 [수납 상태와 대사 확인](/payments/reconciliation)에 있습니다. | /payments/charging, /payments/cancellations, /reference/glossary | |

## [9] 웹훅

| 사실 | 정본 페이지 | 다른 페이지에서 쓰는 한 줄 요약 | 주로 쓰는 페이지 | 확인 필요 |
| --- | --- | --- | --- | --- |
| 통보 본문 {environment, type, timestamp, data}와 유형별 data. 모르는 필드는 무시한다 | /webhooks/events | 통보는 type으로 분기하고 모르는 필드는 무시하세요. | /webhooks/overview, /payments/charging, /payments/cancellations | 웹훅 data 공개 형태와 확정 통보 없음 |
| 인보이스 확정을 알리는 통보는 없다 | /webhooks/events | 확정 결과는 통보가 아니라 인보이스 목록 API로 확인합니다. | /billing/finalize-invoices, /billing/auto-finalization, /webhooks/overview | 웹훅 data 공개 형태와 확정 통보 없음 |
| payment_failed에는 실패 사유가 없다. 결제 시도 목록이나 수납 상태로 확인한다 | /webhooks/events | 실패 사유는 통보가 아니라 결제 시도 조회로 확인합니다. | /payments/retries, /payments/charging | 웹훅 data 공개 형태와 확정 통보 없음 |
| 서명 비밀값(whsec_)은 등록 직후 한 번만 보인다 | /webhooks/overview | 서명 비밀값은 등록 직후 한 번만 보입니다. | /webhooks/verify-signatures | 없음 |
| 2xx만 성공, 자동 재전송, webhook-id로 중복을 거른다, 순서를 보장하지 않는다 | /webhooks/delivery-and-retries | 같은 webhook-id는 한 번만 처리하고, 처리 전에 자원을 API로 다시 조회하세요. | /webhooks/overview, /webhooks/events, /webhooks/verify-signatures | 없음 |

## [10] 확인과 진단 (확인과 진단 그룹 작성 중 추가)

| 사실 | 정본 페이지 | 다른 페이지에서 쓰는 한 줄 요약 | 주로 쓰는 페이지 | 확인 필요 |
| --- | --- | --- | --- | --- |
| 페이지 번호 시작: 고객, 미터, 월별 가격, 사용량, 청구 예정액, 인보이스의 /search는 1부터, 이벤트 목록과 수집 거절, 이벤트 매핑 진단 검색은 0부터 | /reference/search-and-export | 진단 검색과 이벤트 목록의 page는 0부터, 자원의 /search는 1부터 셉니다. | /troubleshooting/events-not-billed, /api-reference/introduction | 없음 |
| CSV는 같은 조건 전체 결과를 최대 10,000행까지, UTF-8 BOM과 CRLF, 수식 문자열 앞 작은따옴표. 이벤트 원본 속성 포함 시 32MB 상한 | /reference/search-and-export | CSV는 같은 조건 전체를 최대 10,000행까지 내려받습니다. | /integration/verify-usage, /payments/reconciliation | 없음 |
| 이벤트의 request_id는 처음 저장한 요청의 응답 헤더 X-Request-ID 값이다 | /integration/verify-usage | 이벤트의 request_id는 그 요청 응답의 X-Request-ID 값입니다. | /api-basics/errors, /troubleshooting/events-not-billed | 없음 |
| 증상별 원인과 조치 표(수집 거절, 저장 뒤 미집계, 단가 없음, 마감 뒤 수신, 확정 뒤 이벤트) | /troubleshooting/events-not-billed | 증상별 원인과 조치는 이벤트가 청구에 안 보일 때를 보세요. | /integration/verify-usage, /integration/events, /integration/retries | 음수 상계 이벤트로 정정 |
| 마감 뒤에 받은 지난달 이벤트는 이벤트 매핑 진단과 청구 제외 집계(unbilled-events)에 잡히지 않는다. received_at으로 확인한다 | /troubleshooting/events-not-billed | 마감 뒤에 받은 이벤트는 진단 화면에 잡히지 않으니 received_at으로 확인합니다. | /concepts/time-rules, /integration/retries | 없음 |
