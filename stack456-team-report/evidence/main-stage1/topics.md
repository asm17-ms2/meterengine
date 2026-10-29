# 1단계 묶음 목록 (main af15d7f5, 사람 승인 2026-10-29: PR 8개)

## 이벤트 수집, 조회, 내보내기 백엔드 (`fix/s456-t-inho-events-query`)
- 항목 46건: Nk3-001 G-132 C450-007 Ck3-450-06 Ck3-450-12 G-127 Ck3-451-03 G-126 G-170 C450-006 C450-012 C451-003 C451-009 Ck3-450-08 Ck3-450-09 G-118 Ck3-450-02 Ck3-450-03 Ck3-450-04 Ck3-450-07 Ck3-450-11 Ck3-450-13 G-117 G-119 G-123 G-124 G-176 G-182 Nk3-009 G-144 Ck3-450-01 G-116 C450-009 G-146 D-186 X-038 D-019 D-072 D-135 D-160 D-062 D-144 X-096 G-121 G-122 Ck3-451-04
- 리뷰어: sjparkx1129
- 실화면 확인: /billable-metrics, /billing, /customers, /events, /invoices, /pricing, /usage
- 결정안:
  - G-118 (fix): status 뜻은 그대로 두고 metric_counts만 그 달 청구 대상 미터로 좁힌다
  - Ck3-450-02 (fix): 문자열 목록은 두고 목록끼리와 V46 CHECK가 같은지 보는 시험만 더한다
  - Ck3-450-03 (fix): 두 삼항식을 PrincipalKind 전체 switch로만 바꾼다. 시그니처와 U17-B2 줄은 그대로 둔다
  - Ck3-450-04 (fix): UsagePriceDiagnostics 계약을 수집이 소유하는 event.api로 옮긴다
  - Ck3-450-07 (doc): 공개 API 호환을 위해 지금 동작(목록은 무시, 내보내기와 search는 400)을 유지하고 엔드포인트별 규칙을 API 설명에 적는다
  - Ck3-450-11 (fix): check, active를 잠금이 드러나는 이름으로 바꾼다(내부 계약, 공개 이름 아님)
  - Ck3-450-13 (fix): 월 구간, cutoff, 과금 시간대를 global의 작은 도우미 하나로 모아 event와 metric이 함께 쓴다
  - G-117 (fix): 확정한 (고객, 달)의 단가 없는 사용량에는 단가 등록 대신 확정한 달이라 이번 청구에 들어가지 않는다는 안내를 준다. 공개 reason 값은 그대로 둔다
  - G-119 (doc): 집계 구조 변경과 큰 이벤트 표 인덱스는 배포 준비 한도(G-161) 때문에 지금 하지 않는다. G-132의 환경 조건 수정만 들어가고 남은 비용을 README에 적는다
  - G-123 (doc): 두 엔드포인트의 기간 규칙은 시험으로 고정된 지금대로 두고 차이를 API 설명에 적는다
  - G-124 (fix): 공용 조건으로 모으지 않고 세 정의가 같은 결과를 내는지 보는 시험만 더한다
  - G-176 (doc): event_rejection도 다른 기록 표처럼 지우지 않는다. README에 적는다
  - G-182 (doc): 이벤트 문제와 거절 search는 0부터를 유지하고(이벤트, 감사, 웹훅 목록과 같음) README에 엔드포인트별 기준을 적는다
  - Nk3-009 (doc): 요약 record 구조 변경은 하지 않고 자원별 summary 키를 API 설명에 적는다
  - Ck3-451-04 (fix): CSV 이스케이프, 인용, BOM, 응답 헤더를 global 공용 도우미 하나로 모은다

## 요금, 청구, 고객 API와 DB (`fix/s456-t-inho-billing-api`)
- 항목 28건: Nk3-002 G-107 Nk3-003 Ck3-446-03 Ck3-446-07 Ck3-446-08 Ck3-446-09 Nk3-008 Nk3-011 C450-002 C450-003 Ck3-451-05 Nk3-010 Ck3-451-01 Ck3-451-02 G-111 C446-005 C446-011 C446-012 C446-013 C447-011 C451-004 C451-006 G-151 Ck3-446-10 G-174 Ck2-011 H3-B2
- 리뷰어: sjparkx1129
- 실화면 확인: 없음
- 결정안:
  - Ck3-446-03 (fix): 엔티티 상수 통합은 하지 않고 요청과 응답 DTO에 @Schema(allowableValues)를 달아 openapi.yaml에 값 목록을 낸다
  - Ck3-446-07 (doc): 감사 target_id는 단가 버전 UUID로 유지하고(614d3bc4 의도), 미터 code는 detail에 있다고 README 감사 절에 적는다
  - Ck3-446-08 (fix): 운영 코드와 시험이 부르지 않는 보조 생성자를 세 DTO에서 함께 지운다(동작 변화 없음)
  - Ck3-446-09 (fix): 서비스 메서드 이름만 naming.md대로 get으로 바꾼다. 공개 operationId(findSupplier)는 그대로 둔다
  - Nk3-008 (doc): outstanding_amount 이름과 값은 유지하고 정의를 응답 설명에 적는다
  - Nk3-011 (keep): draft_invoice는 read API 키 호환 때문에 응답에 남긴다
  - Ck3-451-01 (doc): 최초 응답 재생에 기한이 없는 지금 계약을 유지하고 저장 내용과 기간을 API 설명과 README에 적는다
  - Ck3-451-02 (keep): PATCH는 If-Match 필수 전체 교체로 둔다(박성종 님 #451 설계, API 설명에 이미 적힘)
  - G-111 (doc): 공개 오류 코드를 바꾸지 않고 엔드포인트별 멱등 계약 차이를 API 설명에 적는다
  - Ck2-011 (fix): 문자열과 CHECK 관례를 유지하고 새 V__로 webhook_delivery.failure_reason에 시도 표와 같은 CHECK를 NOT VALID로 더한다

## 웹훅, 감사, 운영 배포 (`fix/s456-t-inho-webhook-audit-ops`)
- 항목 17건: Ck2-007 D-045 C444-002 G-140 Ck2-005 Ck2-010 NT-inho-003 G-092 C448-002 G-169 G-095 Ck1-448-04 NT-inho-004 U12-B1 Ck1-454-01 Ck1-454-02 G-165
- 리뷰어: sjparkx1129
- 실화면 확인: /admin, /admin/audit, /integrations
- 결정안:
  - C444-002 (fix): claim이 가장 오래 차례를 받지 못한 도입사 환경의 전송부터 꺼내게 한다. 직렬 발송과 15초 예산은 그대로 둔다. 새 오류 코드와 대기 상한은 더하지 않는다
  - G-140 (doc): 병렬 발송은 하지 않는다(박성종 님의 직렬 발송 설계 유지). C444-002의 공정 소비로 도입사 사이 지연을 줄이고, 느린 수신 주소가 실행 하나를 쓰는 동작을 README에 적는다
  - Ck2-005 (fix): failed_webhook_count는 지금 정의(현재 failed)를 유지하되 보관한 수신 주소의 전송은 뺀다. 설명에 적는다. 꺼 둔 주소(Q9)는 건드리지 않는다
  - Ck2-010 (fix): Ck2-005와 같은 결정
  - NT-inho-003 (doc): failure_reason은 connection_error 하나로 두고 원인은 운영자 로그로 가른다. README에 적는다
  - G-092 (fix): 주체 없는 요청 가운데 로그인과 가입 밖의 401과 429는 audit_log에 남기지 않고 지표와 요청 로그로 센다. 로그인과 가입 실패, 주체가 있는 실패는 계속 남긴다. 파티션은 두지 않는다
  - C448-002 (fix): G-092와 같은 결정
  - G-169 (fix): G-092와 같은 결정
  - G-095 (fix): GET /v1/audit-logs를 API 키 목록, 사용자 목록처럼 로그인 세션 전용(@MemberReadAccess)으로 좁힌다
  - Ck1-448-04 (fix): 새 조회 인수 없이 운영자 감사 조회의 q 검색 대상에 organization_id를 넣는다
  - NT-inho-004 (keep): 감사 화면 기본 보기는 지금처럼 모든 기록으로 둔다(#448 설계). 행동 조건으로 좁힌다

## 인증 호출 제한과 보안 헤더 (`fix/s456-t-inho-auth-security`)
- 항목 11건: Ck1-454-04 C454-008 C454-003 C454-007 G-101 Ck1-454-05 G-099 C454-002 C454-005 X-088 NT-inho-008
- 리뷰어: sjparkx1129
- 실화면 확인: 없음
- 결정안:
  - C454-002 (fix): 로그인 계정 한도를 (계정, IP) 쌍의 엄격한 한도와 느슨한 계정 전체 상한으로 바꾸고 IP 한도를 넘은 요청은 계정 버킷에 세지 않는다
  - C454-005 (fix): C454-002와 같은 결정
  - NT-inho-008 (keep): HSTS는 되돌릴 수 없어 이번에 더하지 않는다. 배포 도메인 기준이 정해진 뒤 따로 정한다

## 데모, SDK, 측정 인프라 (`fix/s456-t-inho-demo-infra`)
- 항목 15건: C446-008 C451-010 C450-010 Ck2-015 Nk4-004 Nk4-007 C455-002 C455-003 Nk4-002 Nk4-003 Nk4-008 G-162 Nk4-001 G-163 C455-005
- 리뷰어: skytre0, sjparkx1129
- 실화면 확인: 없음
- 결정안:
  - Ck2-015 (fix): 사본은 두고 frontend와 demo의 이름 규칙이 같은지 보는 시험만 더한다. ci.yml은 바꾸지 않는다
  - G-162 (fix): 0fe7d2a 백스톱 설계를 확정하고, root에 환경 목록 변수를 두고 두 SUT README 지우기 절에 환경을 먼저 빼는 단계를 넣는다
  - Nk4-001 (fix): 감지를 끌 때 variables.tf 기본값을 바꾸는 PR로 남기도록 README 복구 절을 고친다
  - G-163 (doc): 돌려 볼 수 없는 Step Functions 조회는 sut-start.sh에 넣지 않고 README에 수동 확인 절차로 적는다
  - C455-005 (fix): rds-auto-stop 시험(workflow.test.mjs, contracts.tftest.hcl)을 그 모듈 경로가 바뀔 때만 도는 CI job으로 더한다

## 목록과 조회 화면 (`fix/s456-t-inho-list-screens`)
- 항목 51건: D-115 D-142 D-185 X-037 D-134 D-159 D-001 D-025 D-047 D-067 D-133 D-147 D-177 X-023 D-201 X-039 D-073 D-161 X-053 D-139 X-106 C451-007 D-242 X-082 D-113 D-118 D-143 D-156 X-083 X-036 X-102 D-013 D-263 D-203 D-112 D-243 D-132 D-231 D-176 D-124 D-116 D-199 D-193 D-076 D-092 D-056 D-100 D-254 X-035 X-071 D-099
- 리뷰어: sjparkx1129
- 실화면 확인: /admin/audit, /audit, /billable-metrics, /billing, /customers, /event-issues, /event-rejections, /events, /events?transaction_id=<id>, /invoices, /overview, /payments/{id}, /pricing, /usage
- 결정안:
  - D-013 (fix): README 152행대로 목록 화면의 제출과 초기화를 '조건 적용'과 '조건 초기화'로 맞춘다. 모달의 '취소'와 '닫기'는 그대로 둔다
  - D-263 (fix): D-013과 같은 결정. 진단, 거절 화면도 같은 문구로 맞춘다
  - D-203 (fix): D-013과 같은 결정

## 업무 화면(요금 설계, 청구 확정, 고객 상세, 수납) (`fix/s456-t-inho-work-screens`)
- 항목 20건: X-018 C446-009 D-230 X-052 X-077 D-121 D-070 D-146 X-067 D-125 D-034 D-057 D-102 D-166 G-178 X-026 X-027 X-050 X-092 X-093
- 리뷰어: skytre0
- 실화면 확인: /billing, /customers, /customers/[id], /integrations, /integrations/[id], /members, /overview, /payments, /pricing, /usage

## 콘솔 셸과 디자인 (`fix/s456-t-inho-shell-design`)
- 항목 16건: X-012 D-071 G-075 G-085 C443-002 D-011 D-065 D-039 G-072 D-244 G-080 D-237 D-022 G-084 G-078 NT-inho-001
- 리뷰어: sjparkx1129
- 실화면 확인: /, /admin, /admin/organizations, /admin/users, /audit, /customers, /customers/[id], /integrations, /integrations/[id], /invoice-links, /login, /members, /overview, /payment-method-registrations/[token], /payments, /payments/[id], /pricing, /settings
- 결정안:
  - X-012 (fix): 같은 Pretendard 1.3.9의 KS X 1001 subset 가변 글꼴로 바꾼다. 줄어든 크기를 PR에 적고, 드문 음절은 지정한 대체 글꼴로 그린다
  - D-071 (fix): X-012와 같은 결정
  - G-075 (fix): X-012와 같은 결정
  - G-085 (fix): X-012와 같은 결정
  - C443-002 (fix): X-012와 같은 결정
  - D-011 (fix): 767px 이하에서 시연 안내, 권한 범위와 새로 조회, 로그아웃을 메뉴 서랍으로 옮긴다. 업무 환경 선택은 README 67행대로 헤더에 두고 sticky도 유지한다
  - D-065 (fix): D-011과 같은 결정
  - D-039 (fix): D-011과 같은 결정
  - G-072 (fix): README 143행의 여러 곳 방식은 유지하고, globals.css 안에서 이미 있는 :root 토큰과 같은 색 리터럴만 var()로 바꾼다
  - D-244 (fix): G-072와 같은 범위로 리터럴만 var()로 바꾼다. 토큰 이름 바꾸기와 기준값 통일은 하지 않는다
  - G-080 (fix): 숨겨 둔 kicker를 보이지 않는 채 남기지 않고, WorkspaceHeader의 kicker prop과 호출부 인자, 숨김 규칙을 지운다(화면 변화 없음)
  - D-237 (fix): 제목 컴포넌트 통합은 하지 않고 운영 화면 제목 크기와 고객 등록 버튼 위계만 맞춘다
  - D-022 (fix): 로고 마크를 공용 컴포넌트 하나로 묶고 크기만 prop으로 받는다. README 147행대로 로고 서체는 그대로 두고 다른 화면에 마크를 더하지 않는다
  - G-084 (fix): D-022와 같은 결정. 콘솔 비율을 기준으로 소개 화면은 크기만 키운다
  - G-078 (fix): LinkButton과 ScopedLink의 같은 주소 변환만 하나로 합친다. ConsoleShell과 NavMenu는 그대로 둔다
  - NT-inho-001 (keep): 시연 안내는 발표용 기능이라 '시연' 낱말을 유지한다
