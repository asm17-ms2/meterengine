미완료 255건, 결과: 끝 상태 74/328건 (fixed 33, refuted 1, already-fixed 7(그중 fixed-above 7), needs-decision 30, blocked 3(그중 no-evidence 0))
push: feat/account-permissions 16개(마지막 b25b1e4)
정할 것: 29건 (G-009, G-027, G-039, G-050, G-056, G-067, C442-002, C442-010, D-136, D-170, D-179, D-236, G-007, G-010, G-011, G-013, G-017, G-018, G-030, G-038, G-041, G-054, G-057, G-059, G-096, G-160, G-187, X-046, X-104). 로컬 DB 다시 만들기: 없음

## 층별 상태

| 층 | 전체 | fixed | refuted | already-fixed | needs-decision | blocked | open |
| --- | --- | --- | --- | --- | --- | --- | --- |
| #442 | 143 | 33 | 1 | 7 | 30 | 3 | 69 |
| #445 | 68 | 0 | 0 | 0 | 0 | 0 | 68 |
| #447 | 35 | 0 | 0 | 0 | 0 | 0 | 35 |
| #449 | 24 | 0 | 0 | 0 | 0 | 0 | 24 |
| #452 | 19 | 0 | 0 | 0 | 0 | 0 | 19 |
| #453 | 39 | 0 | 0 | 0 | 0 | 0 | 39 |

## 고친 것

| id | 층 | 커밋 | 설명 | 고치기 전 실패한 시험 |
| --- | --- | --- | --- | --- |
| G-045 | #442 | b84cf7a | WorkspaceError를 기존 ErrorAlert로 모음 | a workspace load failure offers an in-place retry button: 실패 Error: expect(locator).toBeVisible() failed, Locator: getByRole('button', { name: '다시 시도' }) |
| D-003 | #442 | ae1dc84 | 공용 표 작업 열을 fixed end로 고정 | 없음(보이는 변경: 실화면 환경에서 대표관리자로 /members, /integrations를 1440x900, 1024x768, 390x844로 고치기 전과 뒤 캡처(evidence/X-008/before, after).) |
| D-006 | #442 | 35e43ac | 청구 월 입력에 형식 pattern과 안내 | 없음(보이는 변경: 시험 브라우저가 Chromium뿐이라 type=month가 지원되어 pattern 실패를 재현할 수 없고 이 층에는 MonthFilter를 그리는 화면이 없다. lint, typecheck, build) |
| D-012 | #442 | db2d22d | 화면별 문서 제목 | each screen has its own document title: 실패 Expected "로그인 \| MeterEngine", Received "MeterEngine 관리자 콘솔" |
| D-026 | #442 | ae1dc84 | 공용 표 작업 열을 fixed end로 고정 | 없음(보이는 변경: 실화면 환경에서 대표관리자로 /members, /integrations를 1440x900, 1024x768, 390x844로 고치기 전과 뒤 캡처(evidence/X-008/before, after).) |
| D-029 | #442 | 35e43ac | 청구 월 입력에 형식 pattern과 안내 | 없음(보이는 변경: 시험 브라우저가 Chromium뿐이라 type=month가 지원되어 pattern 실패를 재현할 수 없고 이 층에는 MonthFilter를 그리는 화면이 없다. lint, typecheck, build) |
| D-031 | #442 | 212b604 | 사이드바에 미터 항목 추가 | the sidebar lists the meter screen and marks it as the current place: 실패 locator.click: Test timeout of 45000ms exceeded(미터 링크 없음) |
| D-043 | #442 | 07d30c7 | 확정 실패 뒤 닫으면 요청 키 초기화 | 없음(이 층에는 확정 실패를 돌려주는 작업(결제 취소는 #445부터)과 계약 장치(workspace.contract는 #453부터)가 없어 고치기 전 실패 시험을 만들 수 없다. lint, typecheck, build와) |
| D-049 | #442 | ae1dc84 | 공용 표 작업 열을 fixed end로 고정 | 없음(보이는 변경: 실화면 환경에서 대표관리자로 /members, /integrations를 1440x900, 1024x768, 390x844로 고치기 전과 뒤 캡처(evidence/X-008/before, after).) |
| D-050 | #442 | 65fc925 | 403, 404 조회 실패의 제목과 다시 시도 구분 | a forbidden workspace load says so without a retry: 실패 Error: expect(locator).toBeVisible() failed, Locator: getByText('조회 권한이 없습니다') |
| D-066 | #442 | ae1dc84 | 공용 표 작업 열을 fixed end로 고정 | 없음(보이는 변경: 실화면 환경에서 대표관리자로 /members, /integrations를 1440x900, 1024x768, 390x844로 고치기 전과 뒤 캡처(evidence/X-008/before, after).) |
| D-086 | #442 | c0171f0 | 역할 변경 시 업무 권한이 전체 관리로 바뀐다고 안내 | 없음(보이는 변경(문구): 대화상자 설명과 관리자 선택지 문구를 백엔드 changeRole 동작과 대조했다. lint, build, auth.contract.spec.ts, auth.real.spec.ts 통과) |
| D-090 | #442 | db2d22d | 화면별 문서 제목 | each screen has its own document title: 실패 Expected "로그인 \| MeterEngine", Received "MeterEngine 관리자 콘솔" |
| D-091 | #442 | ae1dc84 | 공용 표 작업 열을 fixed end로 고정 | 없음(보이는 변경: 실화면 환경에서 대표관리자로 /members, /integrations를 1440x900, 1024x768, 390x844로 고치기 전과 뒤 캡처(evidence/X-008/before, after).) |
| D-127 | #442 | 2db50b4 | 발급 값 블록을 앞으로 옮기고 포커스, 복사 전 닫기 확인 | an issued secret is shown first and closing before copying asks for confirmation: 실패 Error: expect(locator).toBeFocused() failed, Received: inactive |
| D-128 | #442 | b4b9800 | 업무 대화상자 액션 예외를 잡아 입력 유지 | a workspace action that throws keeps the dialog and its input: 실패 Error: expect(locator).toBeVisible() failed, element(s) not found |
| D-150 | #442 | ae1dc84 | 공용 표 작업 열을 fixed end로 고정 | 없음(보이는 변경: 실화면 환경에서 대표관리자로 /members, /integrations를 1440x900, 1024x768, 390x844로 고치기 전과 뒤 캡처(evidence/X-008/before, after).) |
| D-154 | #442 | 212b604 | 사이드바에 미터 항목 추가 | the sidebar lists the meter screen and marks it as the current place: 실패 locator.click: Test timeout of 45000ms exceeded(미터 링크 없음) |
| D-172 | #442 | 128a1e0 | 업무 대화상자 닫은 뒤 연 버튼으로 포커스 복귀 | closing a workspace dialog returns focus to its trigger: 실패 Error: expect(locator).toBeFocused() failed, Received: inactive |
| D-184 | #442 | 35e43ac | 청구 월 입력에 형식 pattern과 안내 | 없음(보이는 변경: 시험 브라우저가 Chromium뿐이라 type=month가 지원되어 pattern 실패를 재현할 수 없고 이 층에는 MonthFilter를 그리는 화면이 없다. lint, typecheck, build) |
| D-189 | #442 | ae1dc84 | 공용 표 작업 열을 fixed end로 고정 | 없음(보이는 변경: 실화면 환경에서 대표관리자로 /members, /integrations를 1440x900, 1024x768, 390x844로 고치기 전과 뒤 캡처(evidence/X-008/before, after).) |
| D-250 | #442 | ae1dc84 | 공용 표 작업 열을 fixed end로 고정 | 없음(보이는 변경: 실화면 환경에서 대표관리자로 /members, /integrations를 1440x900, 1024x768, 390x844로 고치기 전과 뒤 캡처(evidence/X-008/before, after).) |
| D-256 | #442 | 35e43ac | 청구 월 입력에 형식 pattern과 안내 | 없음(보이는 변경: 시험 브라우저가 Chromium뿐이라 type=month가 지원되어 pattern 실패를 재현할 수 없고 이 층에는 MonthFilter를 그리는 화면이 없다. lint, typecheck, build) |
| D-258 | #442 | 212b604 | 사이드바에 미터 항목 추가 | the sidebar lists the meter screen and marks it as the current place: 실패 locator.click: Test timeout of 45000ms exceeded(미터 링크 없음) |
| X-000 | #442 | 128a1e0 | 업무 대화상자 닫은 뒤 연 버튼으로 포커스 복귀 | closing a workspace dialog returns focus to its trigger: 실패 Error: expect(locator).toBeFocused() failed, Received: inactive |
| X-004 | #442 | 2db50b4 | 발급 값 블록을 앞으로 옮기고 포커스, 복사 전 닫기 확인 | an issued secret is shown first and closing before copying asks for confirmation: 실패 Error: expect(locator).toBeFocused() failed, Received: inactive |
| X-008 | #442 | ae1dc84 | 공용 표 작업 열을 fixed end로 고정 | 없음(보이는 변경: 실화면 환경에서 대표관리자로 /members, /integrations를 1440x900, 1024x768, 390x844로 고치기 전과 뒤 캡처(evidence/X-008/before, after).) |
| X-015 | #442 | b3cbfd8 | 쿠키 없는 서버 액션을 proxy가 넘기고 로그인 만료로 안내 | a server action after the session cookie disappears goes to login instead of the error screen: 실패 Expected pattern /login?reason=expired$, Received /customers?o |
| X-016 | #442 | db2d22d | 화면별 문서 제목 | each screen has its own document title: 실패 Expected "로그인 \| MeterEngine", Received "MeterEngine 관리자 콘솔" |
| X-019 | #442 | 322afb1 | 사이드바 Menu tabIndex -1 복구 | the sidebar menu list is not a separate tab stop: 실패 Expected "-1", Received "0" |
| X-020 | #442 | 50d7032 | 복사 결과 상태 영역을 미리 둠 | copy results are announced through a status region that is already present: 실패 Expected 1, Received 0 |
| X-021 | #442 | eb3e1bc | 표 스크롤 영역 tabindex와 region 이름 | a workspace table scroll area is a named keyboard stop: 실패 Error: expect(locator).toHaveAttribute(expected) failed, Locator: getByRole('region', { name: 'API 키  |
| X-025 | #442 | 212b604 | 사이드바에 미터 항목 추가 | the sidebar lists the meter screen and marks it as the current place: 실패 locator.click: Test timeout of 45000ms exceeded(미터 링크 없음) |

## 정할 것

| id | 층 | 질문 |
| --- | --- | --- |
| G-009 | #442 | 초대 메서드(invitations, invite, revoke, preview, accept)를 새 OrganizationInvitationService로 옮겨 목록 읽기를 list로 부를지(파일 넷), 아니면 쓰지 않는 2인자 list를 지우고 invitations를 인자 수만 다른 list로 바꿀지, 아니면 그대로 둘지 정한다. |
| G-027 | #442 | 초대 수락 경로 POST /v1/organization-invitations/{token}/accept를 명사 하위 리소스(/acceptance)로 바꾸고 위층의 /revoke, /lookup, /export, /search 동사 경로도 같은 기준으로 맞출지, 아니면 naming.md에 상태 전이 동사 경로를 예외로 적을지 정한다. |
| G-039 | #442 | 롤백 범위(Q5)가 정해지면 SSM 파라미터 organization-id, organization-name, frontend-api-key를 지우고 이미 만든 '프론트 콘솔' 키를 폐기하며 measurements terraform의 같은 파라미터를 정리할지 정한다. |
| G-050 | #442 | 비밀번호, 이름, 이메일 검사를 D-103이 만드는 frontend/src/components/auth/credential-rules.ts로 모으려고 서버 액션 네 파일(admin, (public), 초대, workspace-actions)을 함께 바꿀지, 아니면 workspace-actions.ts createMember 사전 검사만 더하고 복사는 그대로 둘지. |
| G-056 | #442 | 초대 토큰 형식 검사와 me_invitation_flow 쿠키 설정, 삭제를 초대 화면 공용 모듈 하나로 모을지(새 파일 하나와 세 화면 파일을 바꾸고, 쿠키 이름 상수화는 Q7 G-055와 겹친다) 정한다. |
| G-067 | #442 | 동의 문서의 현재 판을 백엔드가 관리하도록 바꿔서 판이 다른 가입이나 동의 요청을 400으로 막고 consent_required를 '현재 판 동의 없음'으로 계산할지, 그리고 정식 약관이 정해지기 전에 운영 도메인에서 가입을 열어 둘지 정한다. |
| C442-002 | #442 | 관리자나 운영자가 정한 초기 비밀번호를 본인이 바꾸게 할지(첫 동의 단계에서 바꾸게 하거나 새 마이그레이션으로 변경 강제 열을 둔다), 도입사의 계정 직접 생성을 없애고 초대만 남길지, 지금대로 둘지 정한다. |
| C442-010 | #442 | 롤백 범위(Q5)가 정해지면 SSM 파라미터 organization-id, organization-name, frontend-api-key를 지우고 이미 만든 '프론트 콘솔' 키를 폐기하며 measurements terraform의 같은 파라미터를 정리할지 정한다. |
| D-136 | #442 | 콘솔의 월 선택을 antd MonthPicker 하나로 통일하고(폼 제출 화면은 onChange와 hidden input) 월 라벨과 적용, 초기화 버튼 문구를 한 벌로 정할지, 정하면 #442 MonthFilter와 #446, #448, #450, #451 화면을 각 층 담당이 바꾼다. |
| D-170 | #442 | 콘솔의 월 선택을 antd MonthPicker 하나로 통일하고(폼 제출 화면은 onChange와 hidden input) 월 라벨과 적용, 초기화 버튼 문구를 한 벌로 정할지, 정하면 #442 MonthFilter와 #446, #448, #450, #451 화면을 각 층 담당이 바꾼다. |
| D-179 | #442 | 사이드바를 antd Menu 대신 nav 안 그룹 제목과 ul, li, a 링크 목록(aria-current만 유지)으로 바꾸고, #443의 Menu 테마 토큰(itemSelectedBg 등)과 .ant-menu 스타일을 CSS로 옮겨 모양을 지킬지 정한다. |
| D-236 | #442 | 콘솔의 월 선택을 antd MonthPicker 하나로 통일하고(폼 제출 화면은 onChange와 hidden input) 월 라벨과 적용, 초기화 버튼 문구를 한 벌로 정할지, 정하면 #442 MonthFilter와 #446, #448, #450, #451 화면을 각 층 담당이 바꾼다. |
| G-007 | #442 | 롤백 범위(Q5)가 정해지면 SSM 파라미터 organization-id, organization-name, frontend-api-key를 지우고 이미 만든 '프론트 콘솔' 키를 폐기하며 measurements terraform의 같은 파라미터를 정리할지 정한다. |
| G-010 | #442 | 필수 동의 검증과 Consent 저장을 account 쪽 한 컴포넌트로 모아 SignupService, OrganizationAccountService, SessionConsentService 세 경로가 부르게 할지(InvitationConsentRequest의 리터럴은 organization이 account를 import하지 않는 방향을 지키려면 상수를 organization.api로 옮겨야 한다), 아니면 이번 스택에서는 고치지 않을지 정한다. |
| G-011 | #442 | organization이 account를 import하지 않는 지금 의존 방향을 지키면서 계정 이메일, 이름, 비밀번호 규칙을 global.validation 조합 제약으로 옮겨 account와 organization DTO 다섯 곳과 OrganizationPeopleService가 함께 쓰게 할지 정한다. |
| G-013 | #442 | #442에서 OrganizationAccounts.revoke와 UserSessionRepository.detachMembership을 세션 폐기를 드러내는 이름(revokeSessions 등)으로 바꾸고 requireMember, activeOrganization의 boolean 인자를 메서드로 나누는 본문 파일 4개 이름 정리를 할지, 담당자 판단으로 두고 넘어갈지 정한다. |
| G-017 | #442 | #442에서 아무도 부르지 않는 오버로드와 생성자를 11개 파일(CurrentSession, SessionService, ApiKeyService, OrganizationMemberService, OrganizationPeopleService, 요청 DTO 넷, OrganizationInvitation, ApiKeySecretResponse)에서 한 번에 지울지, CurrentSession과 SessionService의 null 분기 둘만 지울지, 그대로 둘지 정한다. |
| G-018 | #442 | ErrorCode에서 #442가 쓰지 않는 위층 code 33개를 각각 처음 던지는 층(#444, #445, #446, #447, #449, #450)으로 옮기고 아무 층도 쓰지 않는 CUSTOMER_HAS_PRICING은 지울지, 아니면 #442에 모아 둔 채 HttpStatus 구역 자리만 #442에서 맞출지 정한다. |
| G-030 | #442 | 롤백 범위(Q5)가 정해지면 SSM 파라미터 organization-id, organization-name, frontend-api-key를 지우고 이미 만든 '프론트 콘솔' 키를 폐기하며 measurements terraform의 같은 파라미터를 정리할지 정한다. |
| G-038 | #442 | #442에 미리 들어온 위층 오류 code 33개와 workspace-actions.ts의 상수 false 분기를 #442에서 지우고 #444, #445, #446, #447, #449, #450이 각자 code와 분기를 다시 더하게 할지, 스택을 한 번에 머지하는 전제(Q4)로 보고 그대로 둘지 정한다. |
| G-041 | #442 | src/lib/api 밖 백엔드 경로 리터럴(층 29개, 정점 97개)을 operationId 이름의 lib/api 함수로 모으고 401 처리를 한 곳에 둘지, 지금 구조를 인정하고 #442 README 161행만 고칠지(#452 29a78d0은 이 줄을 이미 지웠다) 정한다. |
| G-054 | #442 | #442에서 쓰이지 않는 DevStateSwitch.tsx, layout-sizes의 SIDER_WIDTH, CONTENT_MAX_WIDTH, NAV_DRAWER_SIZE, TenantScope, WorkspaceOperations를 네 파일에 걸쳐 지우고 README와 lib/api 오류 문구의 표 상태 스위치 안내를 고칠지, 아니면 표 상태 스위치를 셸에 되살릴지. |
| G-057 | #442 | workspaceHref의 inheritFilters 기본값을 ScopedLink, LinkButton과 같은 상속 안 함으로 바꾸고, 기본 상속에 기대는 위층 호출부(NavMenu, ConsoleShell 진행 순서 링크, workspace-href.test.mjs)에 inheritFilters: true를 명시하게 할지. |
| G-059 | #442 | #442에서 도입사 목록 신청일, workspace-href 월 계산, WorkspaceTable 숫자와 금액 표기의 세 파일을 formatKstDateTime, formatKstMonth, formatDecimal과 formatKrw로 모을지(WorkspaceTable 소수 자릿수가 12에서 10으로 줄어 보이는 값이 바뀐다) 정한다. |
| G-096 | #442 | 롤백 범위(Q5)가 정해지면 SSM 파라미터 organization-id, organization-name, frontend-api-key를 지우고 이미 만든 '프론트 콘솔' 키를 폐기하며 measurements terraform의 같은 파라미터를 정리할지 정한다. |
| G-160 | #442 | 롤백 범위(Q5)가 정해지면 SSM 파라미터 organization-id, organization-name, frontend-api-key를 지우고 이미 만든 '프론트 콘솔' 키를 폐기하며 measurements terraform의 같은 파라미터를 정리할지 정한다. |
| G-187 | #442 | CI에 프론트 단위 시험, 계약 명세, demo, deploy, terraform 잡을 더할지(팀 전체에 거는 제약이라 Discussion 합의가 필요하다). |
| X-046 | #442 | 콘솔의 월 선택을 antd MonthPicker 하나로 통일하고(폼 제출 화면은 onChange와 hidden input) 월 라벨과 적용, 초기화 버튼 문구를 한 벌로 정할지, 정하면 #442 MonthFilter와 #446, #448, #450, #451 화면을 각 층 담당이 바꾼다. |
| X-104 | #442 | 로그아웃 요청이 401 밖의 이유(네트워크, 시간 초과, 5xx)로 실패했을 때 지금처럼 쿠키를 남겨 다시 시도하게 할지(계약 시험 logout keeps the session after a revocation failure가 이 동작을 고정한다), 쿠키를 먼저 지우고 서버 세션 종료를 확인하지 못했다고 알릴지 정한다. |

## 막힌 것

| id | 층 | 까닭 |
| --- | --- | --- |
| G-012 | #442 | do-not-touch G-031 V26__organization_people_and_invitations: 소속 보관 열 revoked_at(V26:36)을 archived_at으로 맞추려면 V26 내용이나 새 V__ 파일이 필요하다. 열 이름은 공개 이름이기도 하다 |
| G-019 | #442 | do-not-touch G-031 G-032 V26__organization_people_and_invitations: 감사 인덱스와 anonymous 행위자 변경을 #448로 옮기려면 V26 내용을 바꾸고 #448에 새 V__ 파일을 더해야 한다 |
| X-057 | #442 | not-owner #444: #442의 두 화면(integrations, members)은 표 앞에 h2를 둬 카드 h3가 건너뛰지 않는다. h2 없이 WorkspaceTable을 처음 부르는 화면은 #444 integrations/[id]이고 같은 건너뜀은 #445 #446 #447 #450 #451 화면에서 생긴다. 지적의 AuthFrame 부분은 원인이 달라 NT-yang-001로 떼어 냈다 |

## 새 지적

| id | 층 | 상태 | 내용 |
| --- | --- | --- | --- |
| NT-yang-001 | #442 | open | 인증 화면 프레임(AuthFrame)이 h1보다 앞에 h2를 둔다(X-057에서 떼어 냄) |

## 돌리지 못한 검사

| 브랜치 | sha | kind | 까닭 |
| --- | --- | --- | --- |

## 위층 반영 때 볼 것

| id | 층 | 내용 |
| --- | --- | --- |
| C442-004 | #442 | #442부터 #452까지는 같은 경로가 열려 있다. 정점 client.ts:18은 여전히 헤더를 그대로 실어 보내며 백엔드 인터셉터가 막는 구조다. |
| G-009 | #442 | 정점 0fe7d2a에서 invitations 사용처는 OrganizationPeopleController.java:115 하나뿐이고 OrganizationPeopleService는 위층에서 바뀌지 않았다. 컨트롤러는 23b1844가 두 줄 바꿨으니 서비스를 옮기면 위층 컨트롤러의 호출과 주입 필드도 같이 맞춘다. |
| G-027 | #442 | 바꾸기로 하면 #442에서 OrganizationInvitationController.java의 @PostMapping, SecurityConfig.java permitAll 매처, OrganizationPeopleIntegrationTest와 UserPermissionsIntegrationTest의 경로, frontend/src/app/organization-invitations/[token]/actions.ts의 `${path}/accept`, backend/openapi.yaml을 함께 고친다. 위층에서는 #448의 backend/src/test/java/com/meterengine/global/security/RequestLogFilterTest.java:222 @ 0fe7d2a와 각 층의 openapi.yaml 재생성이 필요하다. 다른 동사 경로는 EventController.java:212, EventExportController.java:38, EventIssueController.java:34, EventRejectionController.java:34, InvoiceViewLinkController.java:75,87, WorkspaceQueryController.java:46,101 @ 0fe7d2a다. |
| G-039 | #442 | 반영 때 compose.prod.yml이 충돌한다: #449 7ce9d36이 프론트 METERENGINE_API_KEY 줄 뒤에 METERENGINE_PUBLIC_ORIGIN을, #454 a39db70과 21feb94가 백엔드 환경에 호출 제한과 신뢰 프록시 줄을 더했다. 정점 기준 지울 줄은 x-backend 24-26행과 frontend 86-88행이다. #449 deploy/tests/test_public_origin.py와 #454 test_trusted_proxies.py의 DUMMY_ENV에서 FRONTEND_API_KEY, ORGANIZATION_ID, ORGANIZATION_NAME을 빼도 되고, 남겨도 compose가 읽지 않아 통과한다. #452(C452-003)의 compose 정리와 같은 줄을 다룬다. |
| G-045 | #442 | 정점에서 WorkspaceError를 쓰는 16개 파일은 모두 message만 넘겨 그대로 컴파일된다. 위층 명세에 '다시 시도' 링크를 찾는 곳은 없다(정점 e2e 검색). WorkspaceTable과 WorkspaceHeader를 기존 부품과 합칠지는 남은 결정이다. |
| G-050 | #442 | 정점 0fe7d2a에서 같은 검사가 (public)/actions.ts:41, admin/actions.ts:28, organization-invitations/[token]/actions.ts:44에 있고 createMember는 workspace-actions.ts:45로 옮겨 있다. #442에서 고치면 위층 merge 때 (public)/actions.ts(a39db70이 호출 제한을 더함)와 workspace-actions.ts(80a3ef1 이후 여러 층이 case를 더함)에서 충돌을 맞춰야 한다. |
| G-056 | #442 | 정점까지 정규식과 쿠키 줄은 그대로다. e5fca11(#451)이 continue/page.tsx의 이웃 줄(링크 대상)만 바꿔서, 모으기로 하면 위층을 다시 쌓을 때 작은 충돌이 날 수 있다. 백엔드 OrganizationInvitationController.java 36, 45행의 @Pattern과 SecureTokens.generate("me_invite_")가 같은 형식에 묶여 있다. |
| G-067 | #442 | CONSENT_VERSION 상수는 정점 0fe7d2a의 real e2e 여러 곳(aggregation, archive-filters, customer-payment-retry, environment-cycle, events 등 frontend/e2e/*.real.spec.ts)에서 가입 요청의 판으로 쓰인다. 상수를 없애거나 서버에서 받게 바꾸면 그 파일들도 같이 고쳐야 한다. 결제 수단 등록의 consent_document_version(CreateBillingKeyRequest, #445 이후)도 같은 방식으로 판을 그대로 받는다. |
| C442-010 | #442 | 반영 때 compose.prod.yml이 충돌한다: #449 7ce9d36이 프론트 METERENGINE_API_KEY 줄 뒤에 METERENGINE_PUBLIC_ORIGIN을, #454 a39db70과 21feb94가 백엔드 환경에 호출 제한과 신뢰 프록시 줄을 더했다. 정점 기준 지울 줄은 x-backend 24-26행과 frontend 86-88행이다. #449 deploy/tests/test_public_origin.py와 #454 test_trusted_proxies.py의 DUMMY_ENV에서 FRONTEND_API_KEY, ORGANIZATION_ID, ORGANIZATION_NAME을 빼도 되고, 남겨도 compose가 읽지 않아 통과한다. #452(C452-003)의 compose 정리와 같은 줄을 다룬다. |
| C442-016 | #442 | fixed-above는 #442부터 #445까지 아직 고장이다. 층마다 가격 화면 진입을 따로 열지는 배포 단위 결정 뒤 조율 담당이 본다. #451 e5fca11부터는 /billable-metric-prices가 /pricing으로 리다이렉트하므로 가격 항목을 아래층에 넣으면 위층에서 중복 메뉴가 된다. |
| C442-017 | #442 | #442만 main에 들어가고 #443이 아직인 사이 상태에서만 목록과 행 버튼이 두 벌 보인다. 층마다 따로 고치기로 정하면 #443 globals.css 221행과 767px 미디어 쿼리 240~241행에 해당하는 최소 규칙(.workspace-mobile-records 기본 display none, 모바일 폭에서 .workspace-desktop-table none, .workspace-mobile-records grid)을 #442 globals.css에 넣으면 되고, #443의 같은 규칙은 그대로 두면 겹쳐도 결과가 같다. |
| D-006 | #442 | #445부터 MonthFilter를 쓰는 화면(수납과 취소)에 그대로 적용된다. 23b1844가 WorkspaceHeader.tsx를 바꿨지만 15행 입력과는 겹치지 않는다. |
| D-012 | #442 | 위층에서 더한 화면(#444 integrations/[id], #445 payments와 settings, #446 pricing, #447 customers/[id], #448 audit, #449 invoices 상세, #450 event-issues, event-rejections, #451 overview 등)에는 metadata.title이 없어 루트 기본값 'MeterEngine 관리자 콘솔'이 보인다. 각 층 담당이 같은 방식으로 title을 더한다. 정점 layout.tsx는 f671a4f가 description을 바꿨으니 title 틀만 옮긴다. |
| D-029 | #442 | #445부터 MonthFilter를 쓰는 화면(수납과 취소)에 그대로 적용된다. 23b1844가 WorkspaceHeader.tsx를 바꿨지만 15행 입력과는 겹치지 않는다. |
| D-031 | #442 | #445 4ace811(/settings 추가)과 #446 614d3bc(/pricing 추가)가 NavMenu.tsx 14행을 바꿔 반영 때 충돌한다. 각 층의 과금과 연동 items에 { href: "/billable-metrics", label: "미터" }를 남기고, #446부터는 요금 설계 바로 뒤에 둔다. 가격 항목은 넣지 않는다(#451부터 /billable-metric-prices는 /pricing으로 이동). |
| D-043 | #442 | #453 workspace.contract.spec.ts에 확정 실패 뒤 창을 닫았다 다시 열어 보내면 새 request_id가 실리는지 보는 시험을 더할 수 있다(T-yang #453 차례). |
| D-050 | #442 | 위층 WorkspaceError 호출부(정점 16개 파일)는 status를 넘기지 않아 지금처럼 일반 실패로 보인다. 상세 화면(#444 integrations/[id], #445 payments/[id], #447 customers/[id] 등)에서 404를 구분하려면 그 층이 status={...error.status}를 넘긴다. |
| D-086 | #442 | 정점 members/page.tsx 23행이 같은 문구다. 2ec5d85, 15692f8이 같은 파일을 바꿨으니 반영 때 이 줄의 두 문구가 남았는지 본다. |
| D-090 | #442 | 위층에서 더한 화면(#444 integrations/[id], #445 payments와 settings, #446 pricing, #447 customers/[id], #448 audit, #449 invoices 상세, #450 event-issues, event-rejections, #451 overview 등)에는 metadata.title이 없어 루트 기본값 'MeterEngine 관리자 콘솔'이 보인다. 각 층 담당이 같은 방식으로 title을 더한다. 정점 layout.tsx는 f671a4f가 description을 바꿨으니 title 틀만 옮긴다. |
| D-127 | #442 | 복사하지 않고 닫으면 확인 창이 뜬다. Playwright는 확인 창을 기본으로 거절하므로 #453 23b1844가 더한 명세가 반영 뒤 실패한다: workspace.contract.spec.ts 39-42행 closeDialog(비밀값 표시 뒤 X로 닫음, 153행과 168행 호출), mvp.real.spec.ts 128행과 136행 Escape, people.real.spec.ts 135행 닫기. 반영 때 닫기 앞에 page.once("dialog", d => d.accept())를 두거나 복사 버튼을 먼저 누르게 맞춘다. #453은 T-yang 층이라 그 차례에 함께 본다. 정점 ActionPanel.tsx는 23b1844가 environment 숨은 입력만 더해 같은 수정이 맞는다. |
| D-128 | #442 | #445 4ace811부터 결제 작업(createPaymentAttempt, createPaymentCancellation, updateTossPaymentsCredential)이 ActionPanel을 탄다. 그 층에서는 같은 catch가 결제 작업이면 isUnconfirmed: true와 내역 재조회 안내를 돌려주도록 맞춘다(#445는 T-yang 층). 정점 ActionPanel.tsx 45-46행은 층과 같고 23b1844의 environment 줄과 겹치지 않는다. |
| D-136 | #442 | 통일하면 #442는 WorkspaceHeader.tsx MonthFilter와 MonthPicker.tsx를 바꾸는데, #451 e5fca11이 MonthPicker.tsx에 onChange, id, label을 따로 더해 반영 merge에서 충돌한다. 위층은 AuditScreen.tsx:65(#448), EventFilters.tsx:182(#451), event-issues/page.tsx:43, event-rejections/page.tsx:33(#450), EmptyState.tsx:11 기본값을 맞춘다. |
| D-154 | #442 | #445 4ace811(/settings 추가)과 #446 614d3bc(/pricing 추가)가 NavMenu.tsx 14행을 바꿔 반영 때 충돌한다. 각 층의 과금과 연동 items에 { href: "/billable-metrics", label: "미터" }를 남기고, #446부터는 요금 설계 바로 뒤에 둔다. 가격 항목은 넣지 않는다(#451부터 /billable-metric-prices는 /pricing으로 이동). |
| D-170 | #442 | 통일하면 #442 WorkspaceHeader.tsx와 MonthPicker.tsx(#451 e5fca11이 onChange 모드를 따로 더해 충돌), #446 PricingWorkspace.tsx:187, #450 event-issues/page.tsx:38, event-rejections/page.tsx:28, #451 EventFilters.tsx:158을 바꾼다. EventFilters를 바꾸면 events.real.spec.ts:127,240의 getByLabel('조회 월').fill도 맞춰야 한다. |
| D-172 | #442 | 같은 언마운트 구조가 main에서 온 BillableMetricsScreen(closeForm, closeDelete)과 InvoiceFinalizeDialog에도 있고, #451 e5fca11의 RoutedEventDetail에도 있다. 스택이 들인 ActionPanel만 이 층에서 고쳤다. 정점 ActionPanel.tsx 141-142행은 23b1844가 environment 숨은 입력만 더해 같은 수정이 그대로 맞는다. |
| D-179 | #442 | 하기로 정하면 #442 NavMenu.tsx와 globals.css에 목록 마크업과 스타일을 넣는다. 그다음 #443(f671a4f)에서는 AppProviders.tsx 19행의 Menu 토큰과 globals.css 129-130행의 .ant-menu 규칙을 새 클래스로 옮겨야 한다. 정점 ConsoleShell.tsx 55행 사이드바와 60행 Drawer가 같은 NavMenu를 쓰므로 Drawer 안의 onNavigate 닫힘도 링크 onClick으로 옮긴다. |
| D-184 | #442 | #445부터 MonthFilter를 쓰는 화면(수납과 취소)에 그대로 적용된다. 23b1844가 WorkspaceHeader.tsx를 바꿨지만 15행 입력과는 겹치지 않는다. |
| D-236 | #442 | 정점에는 ResourceSearchControls.tsx:107이 hidden input 방식의 MonthPicker를 이미 쓴다(#451 e5fca11). #442에서 MonthFilter를 그 방식으로 바꾸려면 MonthPicker.tsx에 같은 onChange 모드를 더해야 해 #451과 충돌하므로, 결정 뒤 #451 위에서 MonthFilter만 바꾸는 편이 충돌이 적다. |
| D-256 | #442 | #445부터 MonthFilter를 쓰는 화면(수납과 취소)에 그대로 적용된다. 23b1844가 WorkspaceHeader.tsx를 바꿨지만 15행 입력과는 겹치지 않는다. |
| D-258 | #442 | #445 4ace811(/settings 추가)과 #446 614d3bc(/pricing 추가)가 NavMenu.tsx 14행을 바꿔 반영 때 충돌한다. 각 층의 과금과 연동 items에 { href: "/billable-metrics", label: "미터" }를 남기고, #446부터는 요금 설계 바로 뒤에 둔다. 가격 항목은 넣지 않는다(#451부터 /billable-metric-prices는 /pricing으로 이동). |
| G-007 | #442 | 반영 때 compose.prod.yml이 충돌한다: #449 7ce9d36이 프론트 METERENGINE_API_KEY 줄 뒤에 METERENGINE_PUBLIC_ORIGIN을, #454 a39db70과 21feb94가 백엔드 환경에 호출 제한과 신뢰 프록시 줄을 더했다. 정점 기준 지울 줄은 x-backend 24-26행과 frontend 86-88행이다. #449 deploy/tests/test_public_origin.py와 #454 test_trusted_proxies.py의 DUMMY_ENV에서 FRONTEND_API_KEY, ORGANIZATION_ID, ORGANIZATION_NAME을 빼도 되고, 남겨도 compose가 읽지 않아 통과한다. #452(C452-003)의 compose 정리와 같은 줄을 다룬다. |
| G-010 | #442 | 정점 0fe7d2a에서 SignupService.validate 호출은 OrganizationAccountService.java:83과 SessionConsentService.java:42 두 곳뿐이고 consentRepository 저장도 SignupService.java:90, OrganizationAccountService.java:86, SessionConsentService.java:44 그대로라 #442에서 고치면 위층은 merge로 그대로 따라간다. InvitationConsentRequest는 AcceptOrganizationInvitationRequest.java:13 @ 0fe7d2a에서만 쓰인다. |
| G-011 | #442 | 정점 0fe7d2a에서 NewAccount는 AcceptOrganizationInvitationRequest.java 13-14행에만 있고 위층 사용처가 없다. 72e8718..0fe7d2a 사이에 세 DTO, OrganizationPeopleService, UserAccount를 바꾼 커밋이 없어 #442에서 고치면 위층은 머지로 따라온다. openapi.yaml은 그룹 제약을 반영하지 않아 NewAccount를 지워도 바뀌지 않는다. |
| G-012 | #442 | 이름을 맞추기로 정하면 위층 사용처도 같이 바꾼다: OrganizationPeopleService.java:72,178,275,327,338, SessionService.java:175,265,274, OrganizationMemberRepository.java:15,21,28, OrganizationMemberService.java:66,78, frontend/src/app/(console)/members/page.tsx:40, frontend/src/lib/api/management.ts:14,25 (모두 @ 0fe7d2a), 그리고 backend/openapi.yaml. 초대 링크(InvoiceViewLinks.tsx:87, invoice-view-links.ts:5)와 API 키, 세션, 초대의 revoked_at은 되돌릴 수 없는 폐기라 그대로 둔다. |
| G-013 | #442 | 정점 0fe7d2a에서도 OrganizationAccounts.java:20, OrganizationAccountService.java:98-99, UserSessionRepository.java:25, OrganizationPeopleService.java:145, 304-320이 그대로이고 git grep으로 본 사용처는 이 네 파일뿐이며 시험에서 이 이름을 부르는 곳이 없다. #442에서 이름을 바꾸면 위층은 merge로 그대로 따라오고 따로 맞출 호출부가 없다. |
| G-017 | #442 | ApiKeySecretResponse 9인자 생성자를 지우면 #453 23b1844가 같은 생성자 본문을 고쳐 반영 merge에서 충돌하므로 #453에서 그 생성자를 함께 지우고 0fe7d2a의 ApiKeyIntegrationTest.java 126행 호출을 11인자로 바꾼다. ApiKeyService.list(UUID), create 5인자와 나머지 짧은 오버로드는 git grep 0fe7d2a에서 부르는 곳이 없어 위층 빌드는 깨지지 않는다. SessionService와 SessionServiceTest는 정점까지 바뀐 커밋이 없어 그대로 따라간다. |
| G-018 | #442 | code를 옮기는 안: #442에서 지운 뒤 위로 반영하면 아래 층에서 컴파일이 깨진다. 각 층이 상수를 다시 더해야 한다. #444는 WEBHOOK_DELIVERY_NOT_RETRYABLE, INVALID_WEBHOOK_URL, INVALID_WEBHOOK_EVENT_TYPE, WEBHOOK_ENDPOINT_NOT_FOUND, WEBHOOK_ENDPOINT_DISABLED, WEBHOOK_ENDPOINT_LIMIT_EXCEEDED. #445는 토스와 결제 code 16개. #446은 PRICE_RATE_HISTORY_IMMUTABLE, PRICE_PLAN_HISTORY_IMMUTABLE, PRICE_PLAN_NOT_FOUND. #447은 CUSTOMER_ARCHIVED, RESOURCE_ARCHIVED, ARCHIVE_RESTORATION_CONFLICT. #449는 INVOICE_VIEW_LINK_NOT_FOUND, PAYMENT_RETRY_UNAVAILABLE, PAYMENT_RETRY_STALE. #450은 EVENT_NOT_FOUND. #442의 frontend/src/app/(console)/workspace-actions.ts:67은 payment_result_unconfirmed, payment_cancellation_in_progress를 문자열로 비교하므로 함께 볼지 정한다. CUSTOMER_HAS_PRICING은 정점에서도 openapi.yaml:8456과 ErrorCode.java:86에만 있다. 구역만 맞추는 안: #450의 8bb17c3이 ORGANIZATION_NOT_ACTIVE 뒤에 ENVIRONMENT_MISMATCH를, 409 구역 머리에 RESOURCE_VERSION_CONFLICT, IDEMPOTENCY_CONFLICT, ENVIRONMENT_NOT_READY를 더했다. 그래서 CONSENT_REQUIRED는 ORGANIZATION_NOT_ACTIVE 앞에, API_KEY_UNAVAILABLE은 409 구역 끝에 둬야 반영 merge 충돌이 줄어든다. 위층마다 openapi.yaml enum 순서가 바뀌므로 반영한 뒤 각 층에서 openapi.yaml을 다시 만든다. |
| G-019 | #442 | 정점에서는 V41__create_invoice_view_links가 actor_type 검사에 invoice_view_link를 더하고, AuditLogController가 anonymous 필터를 받는다. V26에서 audit 변경을 빼려면 #448 이후 층의 이 사용처도 같이 맞춰야 한다. |
| G-030 | #442 | 반영 때 compose.prod.yml이 충돌한다: #449 7ce9d36이 프론트 METERENGINE_API_KEY 줄 뒤에 METERENGINE_PUBLIC_ORIGIN을, #454 a39db70과 21feb94가 백엔드 환경에 호출 제한과 신뢰 프록시 줄을 더했다. 정점 기준 지울 줄은 x-backend 24-26행과 frontend 86-88행이다. #449 deploy/tests/test_public_origin.py와 #454 test_trusted_proxies.py의 DUMMY_ENV에서 FRONTEND_API_KEY, ORGANIZATION_ID, ORGANIZATION_NAME을 빼도 되고, 남겨도 compose가 읽지 않아 통과한다. #452(C452-003)의 compose 정리와 같은 줄을 다룬다. |
| G-034 | #442 | fixed-above: #443부터 #452까지의 head(0f1d1bf에서 2aa2be2까지)의 backend/README.md 운영 키 발급 3단계는 아직 scopes 없이 소속시키고, 인증 절 104행도 옛 문장이다. #453 f014e03부터 고쳐져 있다. 층마다 따로 고칠지는 배포 단위 결정 뒤 박성종 님이 정한다. 이 층에서 같은 줄을 고치면 23b1844와 같은 내용이어야 #453 반영 때 충돌이 나지 않는다. |
| G-038 | #442 | ErrorCode.java는 72e8718..0fe7d2a 사이에 8bb17c3(#450)만 바꾸므로 #442에서 지운 줄이 #443~#449로 그대로 반영되어 사용처가 있는 층의 빌드가 깨진다. 지운다면 다시 더할 층은 #444 WEBHOOK_DELIVERY_NOT_RETRYABLE, INVALID_WEBHOOK_URL, INVALID_WEBHOOK_EVENT_TYPE, WEBHOOK_ENDPOINT_NOT_FOUND, WEBHOOK_ENDPOINT_DISABLED, WEBHOOK_ENDPOINT_LIMIT_EXCEEDED; #445 INVALID_TOSS_PAYMENTS_CREDENTIAL, PAYMENT_METHOD_REJECTED, TOSS_PAYMENTS_TEST_CODE_IN_LIVE_MODE, PAYMENT_METHOD_NOT_FOUND, PAYMENT_METHOD_REGISTRATION_NOT_FOUND, PAYMENT_ATTEMPT_NOT_FOUND, PAYMENT_RESULT_UNCONFIRMED, TOSS_PAYMENTS_CREDENTIAL_MISSING, PAYMENT_METHOD_MISSING, PAYMENT_MODE_MISMATCH, PAYMENT_ATTEMPT_NOT_CANCELABLE, PAYMENT_ATTEMPT_FULLY_CANCELED, PAYMENT_CANCELLATION_AMOUNT_EXCEEDED, PAYMENT_CANCELLATION_IN_PROGRESS, PAYMENT_CANCELLATION_IDEMPOTENCY_CONFLICT, TOSS_PAYMENTS_UNAVAILABLE; #446 PRICE_RATE_HISTORY_IMMUTABLE, PRICE_PLAN_HISTORY_IMMUTABLE, PRICE_PLAN_NOT_FOUND; #447 CUSTOMER_ARCHIVED, RESOURCE_ARCHIVED, ARCHIVE_RESTORATION_CONFLICT; #449 INVOICE_VIEW_LINK_NOT_FOUND, PAYMENT_RETRY_UNAVAILABLE, PAYMENT_RETRY_STALE; #450 EVENT_NOT_FOUND이고 각 층 openapi.yaml을 다시 만든다. CUSTOMER_HAS_PRICING은 정점에서도 쓰이지 않는다. workspace-actions.ts는 80a3ef1(#444), 4ace811(#445), 614d3bc(#446), 2ec5d85(#447), 8bb17c3(#450), e5fca11(#451)이 false 자리를 실제 조건으로 바꿨으므로 #442에서 줄을 지우면 이 층들에 반영 충돌이 나고, 각 층이 자기 operation 조건 줄을 다시 넣어야 한다. |
| G-041 | #442 | 모으기로 하면 정점 0fe7d2a의 사용처는 git grep '"/v1/\\|`/v1/' 0fe7d2a -- frontend/src ':!frontend/src/lib/api'로 31개 파일이 나오고, 그중 sessions/current는 account/consents/actions.ts:15, organization-invitations/[token]/actions.ts:58, organization-invitations/[token]/page.tsx:24, session-actions.ts:15,27, lib/auth/session.ts:20이다. #444 이후 층의 workspace-actions.ts(35개), exports/[resource]/route.ts(7개), payments/[id]/page.tsx(5개) 등 다른 사람 층 파일도 걸린다. README만 고치기로 하면 #442의 161행을 고치고 #452는 29a78d0에서 이미 지웠으니 위층 merge 때 충돌만 맞추면 된다. |
| G-054 | #442 | 정점 0fe7d2a에서도 DevStateSwitch, TenantScope, WorkspaceOperations, SIDER_WIDTH, CONTENT_MAX_WIDTH, NAV_DRAWER_SIZE는 정의 밖 사용처가 없다(git grep). #443 f671a4f가 DevStateSwitch.tsx를 고쳤으므로 #442에서 지우면 위층으로 merge할 때 수정 대 삭제 충돌이 난다. 정점 frontend/README.md 117행의 개발 모드 스위치 설명과 lib/api 7개 파일(billable-metric-prices, billable-metrics, billing, customers, events, invoices, usage)의 오류 문구도 함께 맞춘다. HEADER_HEIGHT(64)와 정점 globals.css 117행 grid-template-rows 68px, 225행 min-height 68px의 불일치는 #443에서 맞춘다. |
| G-057 | #442 | 상속 안 함으로 맞추면 #442에서는 workspace-href.ts:11을 inheritFilters !== true일 때 멈추게 하고 NavMenu.tsx:23을 <Link inheritFilters href={item.href}>로 바꾼다(그 밖의 #442 호출부는 organization_id만 담은 source라 결과가 같다). 위층에서는 NavMenu.tsx:24(0fe7d2a)를 같은 꼴로, ConsoleShell.tsx:57 WORKFLOW 링크(/usage, /pricing, /billing, /payments)에 inheritFilters: true를 넣고 :49의 중복 { inheritFilters: false }를 빼며, workspace-href.test.mjs:54-59(#453 23b1844)에 inheritFilters: true를 명시해야 한다. LinkButton.tsx(#443 f671a4f)는 이미 기본값 false라 동작은 맞고 ScopedLink 로직 복사본 정리는 #443 몫이다. |
| G-059 | #442 | 정점에서 workspace-href.ts의 같은 계산은 e5fca11(#451)로 17행으로 밀렸을 뿐 같다. 같은 sv-SE 월 계산 사본이 #446(614d3bc)의 frontend/src/app/(console)/pricing/page.tsx:20, frontend/src/components/billable-metrics/BillableMetricsSection.tsx:30에도 있고, 정점 payments/[id]/page.tsx는 72행 formatKrw(10자리)와 115행 WorkspaceTable isMoney(12자리)를 한 화면에 함께 쓴다. #442에서 WorkspaceView를 formatKrw, formatDecimal로 바꾸면 위층 isMoney 열은 따로 고칠 필요 없이 맞춰지고, #446 사본은 그 층에서 formatKstMonth로 바꾼다. |
| G-096 | #442 | 반영 때 compose.prod.yml이 충돌한다: #449 7ce9d36이 프론트 METERENGINE_API_KEY 줄 뒤에 METERENGINE_PUBLIC_ORIGIN을, #454 a39db70과 21feb94가 백엔드 환경에 호출 제한과 신뢰 프록시 줄을 더했다. 정점 기준 지울 줄은 x-backend 24-26행과 frontend 86-88행이다. #449 deploy/tests/test_public_origin.py와 #454 test_trusted_proxies.py의 DUMMY_ENV에서 FRONTEND_API_KEY, ORGANIZATION_ID, ORGANIZATION_NAME을 빼도 되고, 남겨도 compose가 읽지 않아 통과한다. #452(C452-003)의 compose 정리와 같은 줄을 다룬다. |
| G-160 | #442 | 반영 때 compose.prod.yml이 충돌한다: #449 7ce9d36이 프론트 METERENGINE_API_KEY 줄 뒤에 METERENGINE_PUBLIC_ORIGIN을, #454 a39db70과 21feb94가 백엔드 환경에 호출 제한과 신뢰 프록시 줄을 더했다. 정점 기준 지울 줄은 x-backend 24-26행과 frontend 86-88행이다. #449 deploy/tests/test_public_origin.py와 #454 test_trusted_proxies.py의 DUMMY_ENV에서 FRONTEND_API_KEY, ORGANIZATION_ID, ORGANIZATION_NAME을 빼도 되고, 남겨도 compose가 읽지 않아 통과한다. #452(C452-003)의 compose 정리와 같은 줄을 다룬다. |
| G-187 | #442 | ci.yml은 #442부터 #455까지 바뀌지 않았다. 합의되면 ci.yml 한 곳에 잡을 더하면 되고, 정점 기준 대상은 frontend/e2e/*.test.mjs, contract 프로젝트 3종(auth, payment, workspace 설정), demo의 pnpm과 python 시험, deploy/tests, measurements/terraform/modules/rds-auto-stop/tests다. paths-filter에 demo, deploy, measurements를 더하고 workflows README 표도 같이 고친다. |
| X-000 | #442 | 같은 언마운트 구조가 main에서 온 BillableMetricsScreen(closeForm, closeDelete)과 InvoiceFinalizeDialog에도 있고, #451 e5fca11의 RoutedEventDetail에도 있다. 스택이 들인 ActionPanel만 이 층에서 고쳤다. 정점 ActionPanel.tsx 141-142행은 23b1844가 environment 숨은 입력만 더해 같은 수정이 그대로 맞는다. |
| X-004 | #442 | 복사하지 않고 닫으면 확인 창이 뜬다. Playwright는 확인 창을 기본으로 거절하므로 #453 23b1844가 더한 명세가 반영 뒤 실패한다: workspace.contract.spec.ts 39-42행 closeDialog(비밀값 표시 뒤 X로 닫음, 153행과 168행 호출), mvp.real.spec.ts 128행과 136행 Escape, people.real.spec.ts 135행 닫기. 반영 때 닫기 앞에 page.once("dialog", d => d.accept())를 두거나 복사 버튼을 먼저 누르게 맞춘다. #453은 T-yang 층이라 그 차례에 함께 본다. 정점 ActionPanel.tsx는 23b1844가 environment 숨은 입력만 더해 같은 수정이 맞는다. |
| X-015 | #442 | 정점 proxy.ts는 4ace811(#445), c9a86c4(#449)가 공개 경로를 늘리고 23b1844(#453)가 환경 검사와 x-meterengine-environment 헤더를 더해 쿠키 검사가 16행으로 내려갔다. 반영 때 같은 조건(&& !request.headers.has("next-action"))을 그 쿠키 검사에 붙인다. 통과한 액션 요청도 requestHeaders를 실어 환경 헤더가 유지된다. session.ts 19행은 정점까지 같다. |
| X-016 | #442 | 위층에서 더한 화면(#444 integrations/[id], #445 payments와 settings, #446 pricing, #447 customers/[id], #448 audit, #449 invoices 상세, #450 event-issues, event-rejections, #451 overview 등)에는 metadata.title이 없어 루트 기본값 'MeterEngine 관리자 콘솔'이 보인다. 각 층 담당이 같은 방식으로 title을 더한다. 정점 layout.tsx는 f671a4f가 description을 바꿨으니 title 틀만 옮긴다. |
| X-019 | #442 | 위층 커밋들이 NavMenu.tsx의 NAV_GROUPS와 Menu 줄을 바꿨다. 반영 때 정점 Menu 요소에 tabIndex={-1}이 남았는지 본다. |
| X-021 | #442 | 위층 WorkspaceTable 호출부는 label을 넘기지 않아도 스크롤 영역이 포커스를 받는다. 이름 있는 region이 필요하면 각 층이 label을 넘긴다. |
| X-025 | #442 | #445 4ace811(/settings 추가)과 #446 614d3bc(/pricing 추가)가 NavMenu.tsx 14행을 바꿔 반영 때 충돌한다. 각 층의 과금과 연동 items에 { href: "/billable-metrics", label: "미터" }를 남기고, #446부터는 요금 설계 바로 뒤에 둔다. 가격 항목은 넣지 않는다(#451부터 /billable-metric-prices는 /pricing으로 이동). |
| X-046 | #442 | 통일하면 #442 WorkspaceHeader.tsx(.month-apply 버튼 포함), #446 PricingWorkspace.tsx:105,187, #448 AuditScreen.tsx:65, #450 event-issues/page.tsx:38,43, event-rejections/page.tsx:28,33, #451 EventFilters.tsx:158,182와 EmptyState.tsx:11 기본값을 각 층에서 바꾸고, events.real.spec.ts:127,240의 '조회 월' 레이블 검사도 맞춘다. |
| X-057 | #442 | WorkspaceView.tsx는 72e8718부터 0fe7d2a까지 바뀐 커밋이 없고 카드 제목은 그대로 h3다. 층마다 맞출 방법은 다음과 같다. (1) h2 없이 WorkspaceTable을 부르는 화면에서는 표 앞에 sr-only h2(예: '고객 목록')를 둔다. 정점 기준 위치는 customers/page.tsx:25(#447 2ec5d85에서 시작, #451 e5fca11), event-issues/page.tsx:48과 event-rejections/page.tsx:38(#450 8bb17c3), integrations/[id]/page.tsx:26(#444 80a3ef1), payments/page.tsx:17(#445 4ace811)이다. (2) #446 614d3bc의 BillingTable.tsx:254, InvoicesTable.tsx:228, UsageTable.tsx:164와 #451 e5fca11의 EventsTable.tsx:114 카드 목록 앞에도 같은 h2를 둔다. (3) BillableMetricFormDialog.tsx:286(#446)과 EventDetailDrawer.tsx:61(main 코드, #451에서 줄만 옮겨짐)의 Title은 level={3}으로 낮춘다. (4) RegistrationFrame.tsx:10(#445)의 h1 앞 h2는 AuthFrame과 같은 취향 차이라 선택 사항이다. #445 payments/page.tsx:17, RegistrationFrame.tsx:10과 #447 customers/page.tsx:19는 T-yang 담당 층이다. 조율에서 이 id를 나누면 그 층 차례에 고칠 수 있다. 대안으로 #442 WorkspaceTable에 headingLevel prop을 두는 방법도 있다. 그 경우에도 (1)의 위층 호출부가 모두 값을 넘겨야 하고, globals.css:244의 .workspace-mobile-record h3 선택자도 함께 맞춰야 한다. |

## fixed-above로 둔 것

| id | 층 | top_head |
| --- | --- | --- |
| C442-004 | #442 | fixed-above #453 23b1844 |
| C442-003 | #442 | fixed-above #454 a39db70 |
| C442-016 | #442 | fixed-above #446 614d3bc |
| C442-017 | #442 | fixed-above #443 f671a4f |
| G-029 | #442 | fixed-above #454 a39db70 |
| G-034 | #442 | fixed-above #453 23b1844 |
| G-063 | #442 | fixed-above #454 a39db70 |

## 마지막 실화면 점검

| 층 | sha | 통과한 명세/전체 | 화면 결과 |
| --- | --- | --- | --- |

## 사용량

사용량 도구가 없어 사람이 /usage로 지켜본다. 층별 사용량은 기록하지 못한다.

## 메모

- 첫 세션은 시작 점검에서 Docker가 꺼져 멈췄다. 두 번째 세션(2026-10-28)에서 점검을 다시 돌려 통과했다.
