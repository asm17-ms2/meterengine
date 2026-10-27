# 스택 #456 k6 넘기기

넘기기 지시를 받아 검증 도중 멈췄다. 이어받을 사람은 아래 "남은 것"부터 시작한다.

## 끝낸 것

- 판정을 마친 지적 id 153건(판정 항목 100개). confirmed 87개 항목(지적 140건), refuted 12, already-fixed 1, uncertain 0.
- 판정 근거와 고칠 계획은 k6.json의 results에 있다. confirmed 항목마다 pr(고칠 PR), in_k6_scope, fix_files, fix_plan(고칠 PR head 기준으로 바꿀 내용과 더할 테스트), e2e_selectors(같이 맞출 선택자), out_of_scope_parts(범위 밖 부분)를 넣었다. 수정은 이것부터 시작하면 된다.
- push한 수정 브랜치: 없다. 수정 커밋도 없다. 통과하지 못해 남긴 수정도 없다(수정 단계를 시작하지 않았다).
- 보고 브랜치 claude/s456-k6-report에 판정 10건마다 push했다.

## 남은 것

### 판정하지 못한 묶음 56개(지적 86건)

묶음 이름은 원인이 같다고 보고 미리 묶은 단위다. 검증하면서 원인이 다르면 나눈다.

| 묶음 | 내용 | 지적 id |
| --- | --- | --- |
| root-error-digest | 루트 error.tsx가 digest를 버림 | G-074, G-086 |
| readme-442 | #442 README 점검 위반 | G-040, G-068 |
| readme-443 | #443 README 점검 위반 | G-073, G-087 |
| readme-theme-font | README 글꼴 문장 모순 | C452-010 |
| readme-452-env-feature | #452 README가 #453 기능 설명 | C452-016 |
| public-origin-compose | METERENGINE_PUBLIC_ORIGIN compose 전달 | C452-003 |
| integrations-env-copy-442 | #442 API 키 양식이 없는 업무 환경 안내 | C442-018 |
| env-example-keys | .env.example과 배포가 안 쓰는 키 요구 | G-007 |
| owner-only-dup | 대표관리자 전용 판단 중복 | G-016 |
| backend-paths-outside-lib | 백엔드 경로 직접 사용 | G-041 |
| admin-formatting-dup | 기존 포맷 함수 대신 직접 포맷 | G-059 |
| admin-approve-full-list | 승인 확인에 전체 목록 조회 | G-060 |
| admin-users-unpaged | 운영자 화면 전체 조회와 곱 렌더 | G-066 |
| search-page-index-base | search 페이지 0/1 기준 불일치 | G-182 |
| legacy-customer-actions | 화면 교체 뒤 옛 고객, 단가 경로와 파일이 남음 | G-183, D-245 |
| customers-create-broken | 고객 화면 createCustomer 미처리 (high 또는 security: 두 관점으로 본다) | C447-001 |
| transaction-id-link | transaction_id 링크를 /events가 안 읽음 | C450-011 |
| csv-export-org-check | CSV 내보내기 도입사 대조 (high 또는 security: 두 관점으로 본다) | C451-008 |
| logout-cookie-on-failure | 로그아웃 실패 시 쿠키 유지 (high 또는 security: 두 관점으로 본다) | X-104 |
| security-headers | 프레이밍 차단, nosniff, X-Powered-By (high 또는 security: 두 관점으로 본다) | X-088 |
| admin-failure-success-color | 운영 현황 발송 실패 지표 성공색 | D-188, D-093 |
| admin-panel-spacing | 운영 현황 패널 간격 없음 | X-041, D-234 |
| metric-name-col-zero | 미터 목록 이름 열 폭 0 (high 또는 security: 두 관점으로 본다) | X-011, D-108 |
| metric-form-error-pos | 미터 저장 실패 메시지 위치 | D-109 |
| metric-form-close-guard | 미터 폼 바깥 클릭, Esc로 닫힘 | D-120 |
| metric-form-placeholder | placeholder '예:' 없음 | X-090 |
| metric-form-stepper-en | 증감 버튼 영어 이름 | X-091 |
| metric-empty-search | 빈 검색 결과 '등록된 미터는 0개' | D-112 |
| status-in-use-style | '사용 중' 색과 형태 화면마다 다름 | D-241 |
| metric-conflict-msg | 미터 수정 충돌 메시지 API 필드명, 삭제 불가 | D-223 |
| event-drawer-field-names | 이벤트 상세 서랍 API 필드명, 시각대 | D-116, D-199 |
| filter-buttons-vary | 조건 제출, 초기화 버튼 이름과 모양 | D-242 |
| mobile-event-link-a | 모바일 이벤트 링크 a 태그 | D-124 |
| event-drawer-focus | 이벤트 상세 닫으면 포커스 복귀 없음 | D-176 |
| event-issues-long-col | 진단 표 '확인할 내용' 열 | D-193, D-076, D-092 |
| event-issues-rejections-link | 거절 요청 안내가 감사 기록으로 | X-064, D-222 |
| event-issues-register-link | '등록하세요' 링크 없음 | D-056 |
| event-issues-separate-check | 미래, 음수 '별도로 확인' 링크 없음 | D-100 |
| rejections-placeholder-hint | 고객 ID 안내 placeholder | D-099 |
| payment-retry-focus | 결제 재시도 포커스 상실, 안내 미전달 (high 또는 security: 두 관점으로 본다) | X-002, D-175 |
| price-default-zero | 단가 입력 기본값 0 (high 또는 security: 두 관점으로 본다) | X-005, D-040, D-119, D-180 |
| default-month-current | 개요 기본 월이 진행 중인 달 (high 또는 security: 두 관점으로 본다) | D-247 |
| webhook-delivery-raw | 웹훅 발송 내역 원시 코드와 '결과 확인 중' | X-028, D-035, D-044, D-088, D-157, D-195, D-252 |
| webhook-list-status | 계속 실패하는 수신 주소도 '발송 중' | D-045 |
| api-key-limit-copy | API 키 한도 '백엔드 인스턴스별', 무제한 '-' | D-017 |
| role-change-copy | 역할 변경 설명 문법과 용어 | D-059, D-227 |
| role-escalation-notice | 관리자로 바꾸면 업무 권한 상승 안내 없음 | D-086 |
| members-actions-cut | 사용자 표 작업 열 잘림 | D-091, D-066 |
| members-audit-view | 변경 이력 보기 감사 기록 | X-024 |
| reinvite-old-link | 재발급 시 이전 링크 살아 있음 안내 없음 | X-094 |
| pay-with-card-without-card | 카드 없어도 '등록 카드로 결제' (high 또는 security: 두 관점으로 본다) | X-006, D-042, D-085, D-148, D-181, D-212 |
| env-terms-mixed | 테스트와 실제 가르는 말 혼재 | X-033, D-014, D-219 |
| mode-button-full-width | 결제 모드 변경 버튼 전체 폭 | D-105 |
| biz-number-hyphen | 사업자등록번호 하이픈 정규식 오류 (high 또는 security: 두 관점으로 본다) | X-014 |
| toss-key-dialog | 토스 키 창 클라이언트 키 가림, 접두어 안내 | X-069 |
| email-panel-empty | 청구 안내 메일 패널 상태, 동작 없음 | X-086 |

### confirmed인데 고치지 않은 것(전부)

고칠 PR별로 정리했다. 범위 밖 표시는 고칠 곳이 k5 범위나 frontend 밖이라 k6에서 고치지 않을 것이다. 범위 안이지만 일부가 범위 밖인 항목은 k6.json out_of_scope에 같이 적었다.

- #442: C442-005(+3), C442-011, C442-013, C442-014(+3), D-060, D-061, D-103(+2), D-104, D-232(+5), G-020, G-050, G-056, X-032(+1), X-034(+2), X-055, X-066(+2), X-084, X-101
- #443: X-017 범위 밖
- #444: C444-008(+1)
- #445: C445-014(+1), D-126, D-149(+4), D-163(+6), D-207, D-217, D-238, D-248, D-255, G-180, X-029, X-031(+1), X-049, X-051, X-075(+2), X-087, X-100
- #446: C446-002, C446-009, C446-010, D-070, D-115, D-142, D-146, D-230, X-018, X-036(+2), X-052, X-067(+1), X-077, X-102
- #449: C449-003, C449-005(+1), D-068, D-110, D-111, D-123, D-216(+5), D-225(+1), X-080
- #451: C451-002, D-057, D-102(+1), D-113, D-118, D-121, D-125, D-132, D-143, D-156, D-166(+1), D-190(+2), D-231, G-178, X-026(+1), X-027, X-035(+1), X-050, X-071(+3), X-083, X-093
- #453: C453-009, C453-011, C453-012, G-044, G-189
- #454: G-099(+1)

### 추가 검토

- k6에는 추가 검토(maint-ops) 대상 PR이 없다.

## 이어받을 때 알아야 할 것

- 판정을 바꿀 수 있는 의심
  - high가 아닌 묶음은 검증 에이전트 하나가 판정했다. 고치기 전에 그 PR head에서 문제를 다시 확인하고, 아니면 고치지 않는다.
  - 디자인 지적(D-, X-)의 고칠 PR은 브랜치 head별 파일 비교로 정했다. 위층에서 같은 줄을 다시 바꾼 경우가 많아 수정 뒤 위층 반영 때 충돌이 날 수 있다(특히 workspace-actions.ts, payments/[id]/page.tsx, customers/[id]/page.tsx, overview/page.tsx, PricingWorkspace.tsx).
  - 문구를 바꾸는 수정은 auth.contract.spec.ts, workspace.contract.spec.ts와 *.real.spec.ts의 선택자를 grep해 같이 맞춰야 한다.
- 다른 묶음과 겹치는 파일
  - C442-005(+C449-007, C451-005, C453-013): package.json의 test:unit 스크립트는 k6 범위, ci.yml frontend 잡에 단계를 더하는 일은 k1 G-187(hermetic 테스트가 CI에서 돌지 않음)과 겹친다.
  - X-017(문장 속 링크 밑줄): 고칠 곳이 globals.css(k5)다.
  - 공용 상태 라벨(WorkspaceView.tsx STATUS_LABELS), ActionPanel, WorkspaceError, ResourceSearchControls, UsageQuality, ConsoleShell, lib/api 타입은 k5 범위다. D-149, D-113, D-255, X-026, D-143, X-035, X-071, X-051, D-217, C451-002, C446-002, G-020, G-050의 일부가 여기에 걸린다.
  - 백엔드가 필요한 부분: D-216 묶음(확정 시점 미터 이름), C446-009(10원 단위 검증), X-052(단가 직렬화 정밀도), G-178(고객별 결제수단 조회), D-103 묶음(백엔드 비밀번호 오류 문구), X-055(숫자 범위 초과 필드 오류), X-029(수납 실패 사유 필드).
- 돌리지 못한 검사
  - *.real.spec.ts 실제 스택 E2E는 돌리지 않았다. docker 데몬이 없고, 스펙 일부가 작성자 장비의 /private/tmp 파일과 컨테이너 이름에 묶여 있다(C449-005, C453-012로 confirmed).
  - 대신 PostgreSQL 18과 JDK 25를 설치해 최상단 백엔드(38300)와 프론트(33300)를 띄웠다. 클라우드 세션 컨테이너가 끝나면 사라진다.
- 수정 단계 준비물: 검사 묶음(install, lint, typecheck, node --test, build, 계약 E2E)은 #442 head 0245c15에서 모두 통과했다. Playwright 1.64.1은 chromium 1248이 필요해서 따로 받아야 한다(사전 설치본은 1243).
