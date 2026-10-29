결과: 끝 상태 328/328건 (fixed 245, refuted 17, already-fixed 39(그중 fixed-above 12), needs-decision 17, blocked 10(그중 no-evidence 0))
push: feat/account-permissions 62개(마지막 643ae66), fix/s456-445-t-yang 15개(마지막 22dfb35), fix/s456-447-t-yang 1개(마지막 84bb5e7), fix/s456-t-yang-invoice-retry 1개(마지막 59e548e), fix/s456-t-yang-public-invoice 1개(마지막 22943db), fix/s456-t-yang-lumen 1개(마지막 62e8eee), fix/s456-t-yang-demo-bridge-web 1개(마지막 c1ee7a8), fix/s456-t-yang-toss-key 1개(마지막 ffa9cd3), fix/s456-t-yang-audit-env 1개(마지막 04c93d9), fix/s456-t-yang-ops-metrics 1개(마지막 6152c0b), fix/s456-t-yang-readme 1개(마지막 6da19c6), fix/s456-t-yang-e2e 1개(마지막 b78630c), fix/s456-t-yang-env-ui 1개(마지막 b303189), fix/s456-t-yang-backend-cleanup 1개(마지막 6b954c5), fix/s456-t-yang-demo-web-target 1개(마지막 a34c969)
정할 것: 17건 (G-050, C442-002, D-136, D-170, D-179, D-236, G-054, G-059, G-187, X-046, X-104, Ck2-017, Nk3-006, Ck3-447-03, Ck3-447-06, D-245, G-183). 로컬 DB 다시 만들기: 없음

## 층별 상태

| 층 | 전체 | fixed | refuted | already-fixed | needs-decision | blocked | open |
| --- | --- | --- | --- | --- | --- | --- | --- |
| #442 | 143 | 103 | 9 | 17 | 11 | 3 | 0 |
| #445 | 68 | 58 | 0 | 9 | 1 | 0 | 0 |
| #447 | 35 | 22 | 5 | 3 | 5 | 0 | 0 |
| #449 | 24 | 13 | 2 | 3 | 0 | 6 | 0 |
| #452 | 19 | 16 | 0 | 3 | 0 | 0 | 0 |
| #453 | 39 | 33 | 1 | 4 | 0 | 1 | 0 |

## 고친 것

| id | 층 | 커밋 | 설명 | 고치기 전 실패한 시험 |
| --- | --- | --- | --- | --- |
| C442-005 | #442 | 88f0ab2 | frontend test:unit 스크립트 | pnpm test:unit: 실패 ERR_PNPM_RECURSIVE_EXEC_FIRST_FAIL Command "test:unit" not found |
| C442-011 | #442 | 9679307 | 새 계정 초대 수락에 남은 세션을 싣지 않음 | accepting an invitation as a new account does not send a stale session: 실패 toHaveURL Received /organization-invitations/me_invite_..., 알림 '인증 정보가 올바르지 않습니다.' |
| C442-012 | #442 | 9cd9421 | 재로그인 돌아갈 주소의 도입사로 세션 전환 | logging in to a return path of another member organization opens that organization: 실패 Expected 0, Received 1 (도입사 선택이 바뀌었습니다) |
| C442-013 | #442 | 0b601c9 | 초대 화면 로그아웃 뒤 같은 초대로 복귀 | logging out on an invitation keeps the invitation open: 실패 toHaveURL Expected /organization-invitations/me_invite_ContractExisting..., Received /login |
| C442-014 | #442 | 07f5a83 | Playwright 설정의 고정 임시 경로 제거 | 없음(설정 변경: git grep -n 'meterengine-orchestration\|/tmp\|/private/tmp' 두 설정 파일이 고친 뒤 비었다. typecheck, lint 통과. playwright --lis) |
| C442-021 | #442 | 0699a0b | 운영자 생성 거절 감사 검사를 actor_id 기준으로 | 다른_생성이_먼저_이메일을_점유하면_기다린_운영자_생성은_409이고_기존_계정은_그대로다: 실패 AssertionFailedError: expected: 0 but was: 1 (감사 기록 선행 변이) |
| C442-023 | #442 | 6028b62 | NotEmpty 가드를 기본 그룹 검증만 세게 | 신규_초대를_빈_동의로_수락하면_400이고_계정과_수락을_남기지_않는다: 실패 AssertionFailedError: [HTTP status code] expected: 400 but was: 201 (동의 검사 제거 변이) |
| C442-024 | #442 | 9fff495 | 마지막 운영자 보호 행 잠금 동시성 시험 | 다른_운영자를_먼저_내리는_중이면_기다린_자기_하향은_409이고_역할은_그대로다: 실패 java.lang.AssertionError: Expecting code to raise a throwable (@Lock 제거 변이) |
| D-060 | #442 | 643ae66 | 인증 프레임 현재 단계 표시 | signup sends displayed consent versions without stale Bearer or a password-bearing follow-up: 실패 Locator getByRole('complementary', { name: '가입 절차' }).locator(' |
| D-061 | #442 | b931df4 | 비로그인 초대 수신자에게 도입사 선택 링크 숨김 | an invitation opened without a session does not offer the organization picker: 실패 Expected 0, Received 1 |
| D-103 | #442 | 2e973ac | 비밀번호 안내를 글자 수로 | 없음(보이는 변경(문구): git grep -nE 'UTF-8 기준\|72바이트' frontend/src가 고친 뒤 비었다. lint, build, auth.contract.spec.ts, auth.real.spec.ts ) |
| D-104 | #442 | 57176f7 | 비활성 버튼 이유를 설명으로 잇고 세로 배치 | a disabled operator action explains itself through its description: 실패 Expected pattern /.+/, Received "" |
| D-141 | #442 | fb8329e | 필수 입력 오류를 인라인 요약으로 통일 | native validity becomes a short inline message without the label: 실패 ERR_MODULE_NOT_FOUND field-validation.ts |
| D-232 | #442 | 99fb104 | 동의 블록 마크업 통일 | signup and invitation show the same required consent block: 실패 Expected 2, Received 0 |
| G-001 | #442 | 00794cc | 가입 이메일 중복을 저장 전 조회로, 제약 이름 비교 제거 | 이미_있는_이메일로_가입하면_저장하지_않고_409_예외다: 실패 Expecting actual throwable to be an instance of: ConflictException but was: NullPointerException |
| G-005 | #442 | a53cc1b | 공통 403 설명에 consent_required | 세션으로_부르는_경로의_403은_동의_대기_code를_문서화한다: 실패 Expecting actual "code=permission_denied: ..." to contain "code=consent_required" |
| G-006 | #442 | d2b69cf | 사용자와 초대 API 오류 code 문서화 | 공개_경로인_초대_수락은_403_404_409_오류_code를_ErrorResponse로_문서화한다: 실패 AssertionFailedError: [403 응답 ...] expected: "#/components/schemas/ErrorResponse" but was: "" |
| G-008 | #442 | 6a03fbf | OrganizationPeopleService 안의 IssuedInvitation, InvitationPre | 없음(JSON 본문이 같은 이름 변경: openapi.yaml에서 스키마 이름 셋과 $ref만 바뀌고 속성은 같다; ./gradlew build, frontend lint, typecheck, build 통과(6b954c) |
| G-042 | #442 | 36f7e74 | 업무 작업 이름 목록과 화면, 액션 대조 시험 | every workspace panel operation is a known permission and has a workspace action case: 실패 SyntaxError: does not provide an export named 'WORKSPACE_OPERATIONS' |
| G-045 | #442 | b84cf7a | WorkspaceError를 기존 ErrorAlert로 모음 | a workspace load failure offers an in-place retry button: 실패 Error: expect(locator).toBeVisible() failed, Locator: getByRole('button', { name: '다시 시도' }) |
| G-052 | #442 | a87832f | 사용자와 초대 응답 타입 이름을 백엔드 스키마에 맞춤 | 없음(타입 변경: typecheck, lint, build 통과) |
| G-058 | #442 | 6ae4ec0 | 결과 문구를 작업별 표로 | result labels come from the operation, not from response fields: 실패 ERR_MODULE_NOT_FOUND workspace-operation-results.ts |
| G-065 | #442 | 872dffe | 백엔드 호출 시간 초과와 연결 실패 구분, 가린 로그 | a timeout and a connection failure become different errors with status 0: 실패 ERR_MODULE_NOT_FOUND transport-failure.ts |
| G-069 | #442 | eb01be8 | randomUUID 없는 환경의 요청 키 대체 | a request id is a version 4 UUID even without crypto.randomUUID: 실패 ERR_MODULE_NOT_FOUND request-id.ts |
| X-032 | #442 | 32a3356 | 새 계정용 초대에서 현재 로그인 상태 안내 | a new-account invitation opened while signed in as someone else says who is signed in: 실패 Error: expect(locator).toContainText failed, Locator: getByRole('statu |
| X-034 | #442 | b7e5d38 | 가입 신청 접수 안내 | signup sends displayed consent versions without stale Bearer or a password-bearing follow-up: 실패 Error: expect(locator).toBeVisible() failed, Locator: getByRole |
| X-040 | #442 | 0451016 | 위험 행 버튼 link, 확정 버튼 primary | a dangerous row action is a quiet link and its final confirmation carries the weight: 실패 Error: expect(locator).toHaveClass(expected) failed, Expected pattern:  |
| X-055 | #442 | 0b31ea4 | 범위 밖 정수 입력을 필드 오류로 | a whole number outside its range names the field instead of blaming JSON: 실패 SyntaxError: does not provide an export named 'InputFieldError' |
| X-061 | #442 | 1dcdc54 | 새 탭 링크 안내 | a one-time link that opens in a new tab says so: 실패 Expected "새 탭에서 열립니다.", Received "" |
| X-066 | #442 | 4b992f3 | 작업별 성공 안내 문구 | each workspace action that shows a toast says what it did: 실패 ERR_MODULE_NOT_FOUND workspace-action-feedback.ts |
| X-084 | #442 | 7842944 | 초대, 동의 화면 보조 동작 access-actions 묶음 | 없음(보이는 변경: 이 층에는 access-actions 스타일이 없어 화면 차이가 없다. lint, build, auth.contract.spec.ts, auth.real.spec.ts 통과. 간격은 #443 이상에서 ) |
| X-095 | #442 | 109526b | 권한 이름과 순서 통일 | scope labels use one name per scope in read, ingest, admin order: 실패 SyntaxError: does not provide an export named 'ACCESS_SCOPE_LABELS' |
| X-097 | #442 | d26d695 | 필수 선택 묶음 오류 조사 수정 | a required choice group error reads without a broken particle: 실패 Expected 0, Received 2 ('을 하나 이상') |
| X-101 | #442 | 9cab573 | 빈 이름과 길이 초과 안내 분리 | signup with a blank name asks for that name instead of a length limit: 실패 Error: expect(locator).toBeVisible() failed, Locator: getByText('도입사 이름을 입력하세요.') |
| X-103 | #442 | 180d80f | 만료 로그인 이동에 returnTo | an expired session login keeps a safe return path: 실패 SyntaxError: does not provide an export named 'expiredLoginHref' |
| C442-009 | #442 | 16ae391 | 소속 보관 시 대기 초대 취소 | 소속을_보관하면_같은_이메일의_대기초대를_취소해_다시_들어오지_못한다: 실패 [HTTP status code] expected: 403 but was: 201 |
| C442-018 | #442 | 3f74137 | 없는 업무 환경 전환 안내 제거 | 없음(보이는 변경(문구): 이 층에 환경 전환 요소가 없는 것과 대조했다. lint, build, auth.contract.spec.ts, auth.real.spec.ts 통과) |
| D-003 | #442 | ae1dc84 | 공용 표 작업 열을 fixed end로 고정 | 없음(보이는 변경: 실화면 환경에서 대표관리자로 /members, /integrations를 1440x900, 1024x768, 390x844로 고치기 전과 뒤 캡처(evidence/X-008/before, after).) |
| D-006 | #442 | 35e43ac | 청구 월 입력에 형식 pattern과 안내 | 없음(보이는 변경: 시험 브라우저가 Chromium뿐이라 type=month가 지원되어 pattern 실패를 재현할 수 없고 이 층에는 MonthFilter를 그리는 화면이 없다. lint, typecheck, build) |
| D-012 | #442 | db2d22d | 화면별 문서 제목 | each screen has its own document title: 실패 Expected "로그인 \| MeterEngine", Received "MeterEngine 관리자 콘솔" |
| D-017 | #442 | 013230e | 분당 한도 안내 문구와 무제한 표시 | an API key without a rate limit is listed as unlimited: 실패 Expected substring "무제한", Received "한도 없는 키사용 중운영 키조회0000---..." |
| D-026 | #442 | ae1dc84 | 공용 표 작업 열을 fixed end로 고정 | 없음(보이는 변경: 실화면 환경에서 대표관리자로 /members, /integrations를 1440x900, 1024x768, 390x844로 고치기 전과 뒤 캡처(evidence/X-008/before, after).) |
| D-029 | #442 | 35e43ac | 청구 월 입력에 형식 pattern과 안내 | 없음(보이는 변경: 시험 브라우저가 Chromium뿐이라 type=month가 지원되어 pattern 실패를 재현할 수 없고 이 층에는 MonthFilter를 그리는 화면이 없다. lint, typecheck, build) |
| D-031 | #442 | 212b604 | 사이드바에 미터 항목 추가 | the sidebar lists the meter screen and marks it as the current place: 실패 locator.click: Test timeout of 45000ms exceeded(미터 링크 없음) |
| D-043 | #442 | 07d30c7 | 확정 실패 뒤 닫으면 요청 키 초기화 | 없음(이 층에는 확정 실패를 돌려주는 작업(결제 취소는 #445부터)과 계약 장치(workspace.contract는 #453부터)가 없어 고치기 전 실패 시험을 만들 수 없다. lint, typecheck, build와) |
| D-049 | #442 | ae1dc84 | 공용 표 작업 열을 fixed end로 고정 | 없음(보이는 변경: 실화면 환경에서 대표관리자로 /members, /integrations를 1440x900, 1024x768, 390x844로 고치기 전과 뒤 캡처(evidence/X-008/before, after).) |
| D-050 | #442 | 65fc925 | 403, 404 조회 실패의 제목과 다시 시도 구분 | a forbidden workspace load says so without a retry: 실패 Error: expect(locator).toBeVisible() failed, Locator: getByText('조회 권한이 없습니다') |
| D-059 | #442 | ff89f04 | 역할 변경 설명 조사와 역할 이름 수정 | 없음(보이는 변경(문구): 선택지 라벨, 백엔드 역할 값, 마지막 대표관리자 보호와 대조했다. lint, build, auth.contract.spec.ts, auth.real.spec.ts 통과) |
| D-066 | #442 | ae1dc84 | 공용 표 작업 열을 fixed end로 고정 | 없음(보이는 변경: 실화면 환경에서 대표관리자로 /members, /integrations를 1440x900, 1024x768, 390x844로 고치기 전과 뒤 캡처(evidence/X-008/before, after).) |
| D-078 | #442 | 9dff033 | 머리말 children을 workspace-actions로 묶음 | 없음(이 층에는 머리말 children을 넘기는 화면이 없어 화면 차이가 없다. lint, typecheck, build, auth.contract.spec.ts, auth.real.spec.ts 통과. 수납 상세 캡처는) |
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
| D-227 | #442 | ff89f04 | 역할 변경 설명 조사와 역할 이름 수정 | 없음(보이는 변경(문구): 선택지 라벨, 백엔드 역할 값, 마지막 대표관리자 보호와 대조했다. lint, build, auth.contract.spec.ts, auth.real.spec.ts 통과) |
| D-250 | #442 | ae1dc84 | 공용 표 작업 열을 fixed end로 고정 | 없음(보이는 변경: 실화면 환경에서 대표관리자로 /members, /integrations를 1440x900, 1024x768, 390x844로 고치기 전과 뒤 캡처(evidence/X-008/before, after).) |
| D-256 | #442 | 35e43ac | 청구 월 입력에 형식 pattern과 안내 | 없음(보이는 변경: 시험 브라우저가 Chromium뿐이라 type=month가 지원되어 pattern 실패를 재현할 수 없고 이 층에는 MonthFilter를 그리는 화면이 없다. lint, typecheck, build) |
| D-258 | #442 | 212b604 | 사이드바에 미터 항목 추가 | the sidebar lists the meter screen and marks it as the current place: 실패 locator.click: Test timeout of 45000ms exceeded(미터 링크 없음) |
| G-011 | #442 | 6b954c5 | CreateManagedMemberRequest와 CreateOrganizationInvitationRequ | 직접생성과_초대의_이메일_형식_오류는_가입과_같은_문구다: 실패 [a@b] 정규식 기본 문구가 errors[].message로 나감 |
| G-016 | #442 | 4aa624a | 닿지 않는 대표관리자 전용 분기 제거 | 없음(닿지 않는 분기를 지우는 것이라 고치기 전 실패 시험이 없다. e2e/permissions.test.mjs owner-only workspace operations need the owner role with adm) |
| G-018 | #442 | 0c8881a | 스택이 main에 한 번에 들어와 위층 code 32개는 각 기능이 실제로 던지므로, code를 층별로 옮기 | 상수는_HTTP_상태_순서로_놓인다: 실패 [ORGANIZATION_NOT_FOUND] 404 to be greater than or equal to 409 |
| G-022 | #442 | 193978f | backend README API 키 한도 설명 | 없음(문서 변경: CreateApiKeyRequest의 rate_limit_per_minute(@Positive, null 허용)와 대조했다. backend build 통과) |
| G-040 | #442 | c6327b2 | frontend README를 세션 로그인과 새 화면에 맞춤 | 없음(문서 변경: dev-state.ts, transport.ts, session.ts, proxy.ts, 각 page.tsx와 문장을 대조했다. frontend lint, build 통과) |
| G-060 | #442 | 50a9a03 | 운영자 도입사 조회에 status 필터 | the operator organization list asks the backend for the selected status: 실패 Expected value "/v1/organizations?status=pending", Received ["/v1/organizations", .. |
| G-066 | #442 | e4b7522 | 운영자 사용자 화면의 도입사 목록 곱 렌더 제거 | the operator user list sends the active organizations once instead of once per user: 실패 Expected < 100, Received 930 |
| G-068 | #442 | c6327b2 | frontend README를 세션 로그인과 새 화면에 맞춤 | 없음(문서 변경: dev-state.ts, transport.ts, session.ts, proxy.ts, 각 page.tsx와 문장을 대조했다. frontend lint, build 통과) |
| X-000 | #442 | 128a1e0 | 업무 대화상자 닫은 뒤 연 버튼으로 포커스 복귀 | closing a workspace dialog returns focus to its trigger: 실패 Error: expect(locator).toBeFocused() failed, Received: inactive |
| X-004 | #442 | 2db50b4 | 발급 값 블록을 앞으로 옮기고 포커스, 복사 전 닫기 확인 | an issued secret is shown first and closing before copying asks for confirmation: 실패 Error: expect(locator).toBeFocused() failed, Received: inactive |
| X-008 | #442 | ae1dc84 | 공용 표 작업 열을 fixed end로 고정 | 없음(보이는 변경: 실화면 환경에서 대표관리자로 /members, /integrations를 1440x900, 1024x768, 390x844로 고치기 전과 뒤 캡처(evidence/X-008/before, after).) |
| X-015 | #442 | b3cbfd8 | 쿠키 없는 서버 액션을 proxy가 넘기고 로그인 만료로 안내 | a server action after the session cookie disappears goes to login instead of the error screen: 실패 Expected pattern /login?reason=expired$, Received /customers?o |
| X-016 | #442 | db2d22d | 화면별 문서 제목 | each screen has its own document title: 실패 Expected "로그인 \| MeterEngine", Received "MeterEngine 관리자 콘솔" |
| X-019 | #442 | 322afb1 | 사이드바 Menu tabIndex -1 복구 | the sidebar menu list is not a separate tab stop: 실패 Expected "-1", Received "0" |
| X-020 | #442 | 50d7032 | 복사 결과 상태 영역을 미리 둠 | copy results are announced through a status region that is already present: 실패 Expected 1, Received 0 |
| X-021 | #442 | eb3e1bc | 표 스크롤 영역 tabindex와 region 이름 | a workspace table scroll area is a named keyboard stop: 실패 Error: expect(locator).toHaveAttribute(expected) failed, Locator: getByRole('region', { name: 'API 키  |
| X-025 | #442 | 212b604 | 사이드바에 미터 항목 추가 | the sidebar lists the meter screen and marks it as the current place: 실패 locator.click: Test timeout of 45000ms exceeded(미터 링크 없음) |
| X-072 | #442 | 09d27a3 | 표 빈 상태 작은 삽화 | an empty workspace table uses the small empty illustration: 실패 Expected 1, Received 0 |
| X-094 | #442 | 646a22e | 초대 재발급 시 이전 링크 유효 안내 | 없음(보이는 변경(문구): 백엔드 invite가 이전 초대를 취소하지 않는 것과 대조했다. lint, build, auth.contract.spec.ts, auth.real.spec.ts 통과) |
| C445-003 | #445 | b74ebeb | 더 새 시도가 있는 NOT_FOUND 실패의 7일 창 제외 | 수동_생성은_더_새_시도가_있는_NOT_FOUND_PAYMENT_실패면_7일이_지나도_다시_조회해_새로_승인한다: 실패 [HTTP status code] expected: 201 but was: 409 |
| C445-008 | #445 | 5dc3ac9 | 동시 부분 취소 시험을 409 in_progress로 좁힘 | 같은_시도에_동시에_부분_취소하면_취소는_하나이고_나머지는_409_in_progress다: 실패 expected: 409 but was: 500 (findForUpdate 제거 변이) |
| C445-010 | #445 | d3dd916 | 웹훅 API 설명의 없는 토큰 인증 | 없음(OpenApiDocumentTest로 openapi.yaml 재생성, 해당 문장만 바뀜; backend build 통과) |
| C445-011 | #445 | 87448f2 | 결제 요청 409 설명의 payment_mode_mismatch | 없음(OpenApiDocumentTest로 openapi.yaml 재생성, 한 줄 추가; backend build 통과) |
| C445-012 | #445 | 2469efb | 시크릿 키 문자 검사로 500 대신 400 | 시크릿_키에_콜론이나_공백이나_ASCII_밖_문자가_있으면_400이고_토스를_부르지_않는다: 실패 expected: 400 but was: 500 |
| C445-013 | #445 | 62fb032 | 짝 없는 완료 취소를 토스 재조회 뒤 누락 기록 | 거래_목록에_없는_완료_취소도_재조회한_결제의_cancels에_있으면_누락으로_남기지_않고_금액만_비교한다: 실패 Expecting actual [("missing_in_toss_payments", ..., "tx_cancel_missing", ...)] |
| C445-014 | #445 | 22dfb35 | Playwright 결제 설정이 없는 e2e 파일을 가리킴 | playwright test -c playwright.payment.config.ts: 실패 Process from config.webServer was not able to start |
| C445-015 | #445 | 576f200 | retry exhausted 지표의 상한 초과 실패 중복 | 세_번째_시도가_실패하면_retry_exhausted가_한_번_오르고_상한을_넘긴_실패는_세지_않는다: 실패 expected: 1.0 but was: 2.0 |
| C445-016 | #445 | f84e87e | 재시도 재확인 분기 시험 | 후보를_읽은_뒤_더_늦은_실패_시도가_생기면_새_시도를_만들지_않는다: 실패 Expected size: 2 but was: 3 (재확인 조건 제거 변이) |
| C445-017 | #445 | c76428e | 재시도 시험 집계 단언이 자기 후보를 확인하지 못함 | 건너뜀 분기를 예외로 바꾼 변이에서 고친 PaymentRetryServiceTest: 실패 4건 (고치기 전 시험은 같은 변이에서 23건 통과) |
| D-126 | #445 | 3903b2c | 카드 등록 화면 h2가 h1보다 먼저 나옴 | 없음(보이는 변경: 실화면 카드 등록, 등록 완료 화면 1440, 390 폭 고치기 전과 뒤 캡처(evidence/fe-445/before, after). 고친 뒤 제목 목록이 h1부터 시작하고 소개 문장 모양은 같다; ) |
| D-149 | #445 | 6d9f804 | 수납 상태 이름 한 벌로 | each invoice payment status has one name used by both the list and the detail: 실패 ERR_MODULE_NOT_FOUND invoice-payment-status.ts |
| D-163 | #445 | 978930a | 결제 취소 영역 승인 줄의 원시 모드 값 | 없음(보이는 변경: 실화면 수납 상세 1440, 390 폭 고치기 전과 뒤 캡처(evidence/fe-445/before, after). 고치기 전 '승인 / test', 고친 뒤 '승인 / 테스트 결제'; lint, b) |
| D-207 | #445 | ebcb263 | 카드 등록 단계 목록의 현재 단계 표시 | 없음(보이는 변경: 실화면 카드 등록, 등록 완료 화면 1440, 390 폭 고치기 전과 뒤 캡처(evidence/fe-445/before, after). 고친 뒤 등록 화면은 1단계, 완료 화면은 3단계만 진하다; li) |
| D-217 | #445 | c331448 | 토스 키 저장 미확정 안내가 결제와 취소 내역을 가리킴 | workspace-action-feedback.test.mjs: 실패 SyntaxError does not provide an export named 'unconfirmedMessage' |
| D-238 | #445 | c32f651 | 수납과 취소 화면에만 있는 탭 줄 | 없음(보이는 변경: 실화면 /payments?month=2026-09 1440, 390 폭 고치기 전과 뒤 캡처(evidence/fe-445/before, after). 고친 뒤 탭 줄이 없다; lint, build 통과) |
| D-248 | #445 | 593a78b | 수납과 취소 진행 중인 달 빈 상태 문구 | 없음(보이는 변경: 실화면 /payments?month=2026-10과 2026-09 1440, 390 폭 고치기 전과 뒤 캡처(evidence/fe-445/before, after). 고친 뒤 진행 중인 달은 새 빈 상) |
| D-255 | #445 | 9863110 | 수납 상세 조회 실패 때 제목과 목록 복귀 없음 | 없음(보이는 변경: 실화면에서 없는 인보이스 id의 수납 상세 1440, 390 폭 고치기 전과 뒤 캡처(evidence/fe-445/before, after). 고친 뒤 제목 '수납 내역을 찾을 수 없습니다'와 수납 목) |
| G-097 | #445 | 5549e89 | 정지 도입사의 카드 등록 링크와 자동 재결제 중단 | 정지된_도입사의_링크는_조회도_발급도_404이고_토스를_부르지_않으며_재개하면_다시_조회된다: 실패 [HTTP status code] expected: 404 but was: 200 |
| G-098 | #445 | 2bc72d8 | 토스 키 저장과 결제 모드 변경을 세션 전용으로 | admin_API_키로는_키와_같은_모드의_키도_저장할_수_없고_403이며_토스를_부르지_않고_저장된_키는_그대로다: 실패 [HTTP status code] expected: 403 but was: 200 |
| G-102 | #445 | 6d5fad4 | 카드 등록과 교체 감사 기록 | 카드를_등록하거나_바꾸면_도입사_감사_행을_시스템_행위자로_남기고_교체면_이전_카드를_싣는다: 실패 Expected size: 2 but was: 0 |
| G-180 | #445 | 02bb3ec | 결제 취소의 화면 낡음 409를 새로 조회 상태로 다룸 | workspace-action-feedback.test.mjs: 실패 SyntaxError does not provide an export named 'staleCancellationMessage' |
| X-009 | #445 | 6d9f804 | 수납 상태 이름 한 벌로 | each invoice payment status has one name used by both the list and the detail: 실패 ERR_MODULE_NOT_FOUND invoice-payment-status.ts |
| X-029 | #445 | 978930a | 결제 취소 영역 승인 줄의 원시 모드 값 | 없음(보이는 변경: 실화면 수납 상세 1440, 390 폭 고치기 전과 뒤 캡처(evidence/fe-445/before, after). 고치기 전 '승인 / test', 고친 뒤 '승인 / 테스트 결제'; lint, b) |
| X-031 | #445 | 3a60ae1 | 카드 자동결제 안내 펼침과 금액 변동 안내 | 없음(보이는 변경: 실화면에서 카드 등록 화면을 1440, 390 폭으로 고치기 전과 뒤 캡처(evidence/X-031/before, after). 고친 뒤 안내 세 문장이 펼쳐져 있고 axe, 콘솔 오류, 가로 넘침은) |
| X-049 | #445 | c46e4fa | 결과 미확인 완료 화면 제목 | 없음(보이는 변경: 결과 쿠키 없이 완료 화면을 1440, 390 폭으로 고치기 전과 뒤 캡처(evidence/X-049/before, after). h1이 '카드 등록을 완료하지 못했습니다'에서 '카드 등록 결과를 확인) |
| X-051 | #445 | 1cf7396 | 토스 키 누락 오류의 안내 | workspace-action-feedback.test.mjs: 실패 SyntaxError does not provide an export named 'errorMessageForCode' |
| X-087 | #445 | 3a5b72a | 카드 등록 오류 알림의 role 중첩 | 없음(lint, build 통과; 실화면 카드 등록 화면 1440, 390 폭 고치기 전과 뒤 캡처(evidence/fe-445/before, after)에서 axe, 콘솔 오류, 가로 넘침이 늘지 않음. 오류 알림은 토) |
| X-100 | #445 | 738fc52 | 카드 등록 링크 유효기간 연도와 시간대 | 없음(보이는 변경: 실화면 카드 등록 화면 1440, 390 폭 고치기 전과 뒤 캡처(evidence/fe-445/before, after). 고치기 전 '10월 30일 오전 01:05까지', 고친 뒤 연도와 초, KST) |
| C445-002 | #445 | ad5872e | 결제 설정과 토스 키 변경의 키 모드 검사 | test_API_키로_live로_바꾸면_409_payment_mode_mismatch이고_설정을_남기지_않는다: 실패 [HTTP status code] expected: 409 but was: 200 |
| C445-005 | #445 | ab25813 | 카드 등록 링크 생성의 키 모드 검사 | API_키의_모드가_결제_모드와_다르면_링크를_만들지_않고_409이고_같으면_만든다: 실패 [HTTP status code] expected: 409 but was: 201 |
| Ck2-016 | #445 | fdf3b2c | 예약 작업 스레드 수를 작업 수 이상으로 | 예약_스레드_풀은_예약_작업_수_이상이다: 실패 Expecting actual: 2 to be greater than or equal to: 4 |
| D-042 | #445 | 3db6a0e | 카드, 키 없을 때 결제 버튼 대신 준비 안내 | a payment can be requested only with the mode's Toss key and a registered card: 실패 ERR_MODULE_NOT_FOUND card-state.ts |
| D-085 | #445 | 3db6a0e | 카드, 키 없을 때 결제 버튼 대신 준비 안내 | a payment can be requested only with the mode's Toss key and a registered card: 실패 ERR_MODULE_NOT_FOUND card-state.ts |
| D-105 | #445 | 4f5aa20 | 결제 모드 변경 버튼만 패널 폭 전체 | 없음(보이는 변경: 실화면 도입사 설정 1440, 390 폭 고치기 전과 뒤 캡처(evidence/fe-445/before, after). 고친 뒤 버튼이 내용 폭이다; lint, build 통과) |
| D-148 | #445 | 3db6a0e | 카드, 키 없을 때 결제 버튼 대신 준비 안내 | a payment can be requested only with the mode's Toss key and a registered card: 실패 ERR_MODULE_NOT_FOUND card-state.ts |
| D-181 | #445 | 3db6a0e | 카드, 키 없을 때 결제 버튼 대신 준비 안내 | a payment can be requested only with the mode's Toss key and a registered card: 실패 ERR_MODULE_NOT_FOUND card-state.ts |
| D-212 | #445 | 3db6a0e | 카드, 키 없을 때 결제 버튼 대신 준비 안내 | a payment can be requested only with the mode's Toss key and a registered card: 실패 ERR_MODULE_NOT_FOUND card-state.ts |
| X-006 | #445 | 3db6a0e | 카드, 키 없을 때 결제 버튼 대신 준비 안내 | a payment can be requested only with the mode's Toss key and a registered card: 실패 ERR_MODULE_NOT_FOUND card-state.ts |
| X-069 | #445 | 5879eca | 토스 키 저장 창의 클라이언트 키 가림과 접두어 안내 | 없음(보이는 변경: 실화면 도입사 설정의 토스 키 저장 창 1440, 390 폭 고치기 전과 뒤 캡처(evidence/fe-445/before, after의 settings-toss-key-dialog). 고친 뒤 두 칸) |
| X-086 | #445 | 4fff860 | 청구 안내 메일 패널 문구 | 없음(보이는 변경: 실화면 도입사 설정 1440, 390 폭 고치기 전과 뒤 캡처(evidence/fe-445/before, after). 고친 뒤 새 문구; lint, build 통과) |
| C447-002 | #447 | 01bb890 | 보관한 기한부 단가와 요금제가 종료월 뒤에도 이전 버전을 가림 | 기한부_단가_보관은_원래_종료월부터_구단가로_돌아간다: 실패 expected: 1000L but was: 0L |
| C447-003 | #447 | ec9581a | 토스 키 교체 뒤 확인, 취소 확인, 웹훅이 이전 키를 쓰는지 시험 | TossPaymentsCredentialService.historical이 find()를 돌려주게 한 변이: 실패 키_이력_없이_현재_키만_시도_뒤에_바뀌었으면_토스를_부르지_않고_pending으로_둔다 NeverWantedButInvoked: tossPaymentsClient.find |
| C447-004 | #447 | e64caaf | 보관한 카드, 토스 키, 고객으로 청구와 등록이 나가지 않는지 시험 | findBillingKey의 archivedAt 필터 삭제 변이: 실패 보관한_카드나_보관한_결제_모드의_키로는_409이고_시도를_남기지_않으며_토스를_부르지_않는다 [HTTP status code] expected: 409 but was: 500 |
| C447-005 | #447 | 5697a04 | 보관한 단가와 요금제의 같은 슬롯 재등록과 소급 복원 거절 시험 추가 | PriceRateService.create의 retainedSlot 거절을 뺀 변이: 실패 java.lang.AssertionError: Expecting code to raise a throwable. |
| C447-006 | #447 | d65557d | 다른 도입사 단가 복원 시험이 도입사 비교 전에 통과함 | PriceRateService.restore의 organization_id 비교(210행)를 뺀 변이: 실패 java.lang.AssertionError: Expecting code to raise a throwable. |
| C447-007 | #447 | 858ef35 | 조합 DELETE 시험이 어느 조합을 보관했는지 보지 않음 | PriceRateService.delete의 조합 비교(193행)를 뺀 변이: 실패 expected: null but was: "2026-11" |
| C447-008 | #447 | 16acaa7 | 고객 카드 보관 때 커밋 뒤 토스 빌링키 폐기 | 카드를_보관하면_커밋한_뒤_토스_빌링키를_한_번만_폐기하고_폐기가_거절돼도_204다: 실패 Wanted but not invoked: tossPaymentsClient.deleteBillingKey("test_sk_..", "archiveBillingKey.."); Actually, t |
| C447-009 | #447 | 2734417 | 필터 선택지에서 보관한 고객과 미터가 빠짐 | list filter options ask for archived customers and metrics too: 실패 listCustomers in billing/page.tsx, actual '{}', expected '{ status: "all" }' |
| C447-010 | #447 | 68d9a5f | 겹친 보관 기간을 한 번 집계하는지 확인하지 못하는 단언 | BillableMetricUsageService.aggregate가 겹친 기간마다 항목을 만드는 변이: 실패 java.lang.AssertionError: Expected size: 1 but was: 2 |
| G-105 | #447 | 75d3017 | 보관했거나 없는 카드의 실패 결제를 재시도 후보와 next_retry_at에서 뺌 | findRetryCandidates는_카드를_보관했거나_카드가_없는_실패_시도를_돌려주지_않는다: 실패 Expecting [34145d37-.., 4e24ddfb-.., bd1d67d8-..] not to contain [..] but found [bd1d67d8-.., 34145d37 |
| G-147 | #447 | 7da6d60 | SDK 웹훅 엔드포인트 목록에 보관 상태 필터와 archived_at이 없음 | 웹훅 엔드포인트 목록은 보관 상태를 query로 보내고 archived_at을 돌려준다: 실패 AssertionError expected: 'archived', actual: null (tsc: TS2554 Expected 0 arguments, but got 1) |
| Nk3-004 | #447 | f766ae8 | 저장하지 않은 모드의 토스페이먼츠 키를 보관하면 500 대신 404를 돌려준다 | test_키만_저장한_도입사가_live_키를_보관하면_404_toss_payments_credential_not_found다: 실패 [HTTP status code] expected: 404 but was: 500 |
| Nk3-005 | #447 | 56a230c | 보관한 자원을 거절하는 409와 보관, 복원 경로의 404를 API 문서에 적는다 | 보관한_자원을_쓰는_경로는_409_보관_code를_ErrorResponse로_문서화한다: 실패 [409 응답: {"200":{...IngestEventResponse...},...}] expected: "#/components/schemas/ErrorResponse" but was: " |
| U19-B1 | #447 | 8e329ff | 지난 월 요금제 삭제 시험이 삭제 효과를 보지 않음 | PricePlanService.delete가 지난 월 요금제를 이번 달부터 보관하는 변이: 실패 expected: "2026-11" but was: "2026-10" |
| Ck3-447-10 | #447 | 4ec2ebd | 보관 상태로 거르는 목록의 status에 받는 값을 API 문서에 적는다 | 보관_상태로_거르는_목록의_status는_active_archived_all만_받고_기본이_active다: 실패 [listCustomers] Expecting actual: [] to contain exactly (and in same order): ["active", "archived |
| Ck3-447-11 | #447 | 84bb5e7 | README 구조 절이 없어진 삭제 가드 의존과 삭제 동작을 그대로 적음 | 없음(보이는 변경(README 구조 절 글만 바뀌고 실행 시험 대상이 없다). 고친 줄을 git show 5371d1e의 CustomerService(import에 event, payment 없음), BillableMet) |
| Ck3-447-13 | #447 | f69fd31 | 결제 보관 잠금을 FOR NO KEY UPDATE로 바꿔 이벤트 수집을 막지 않음 | 도입사_잠금을_쥐고_있어도_그_도입사의_이벤트_수집은_기다리지_않는다: 실패 java.util.concurrent.TimeoutException |
| Ck3-447-14 | #447 | 1f269b5 | 미터 수정이 도입사 행 잠금을 쥔 채 색인 없는 이벤트 유무 질의를 돌림 | BillableMetricServiceTest.이벤트_유무는_도입사_잠금_전에_확인한다: 실패 Verification in order failure Wanted but not invoked: organizationRepository.findForUpdateById(...) Wanted  |
| D-108 | #447 | cc2baea | 미터 목록 이름 열 폭 0 | 없음(보이는 변경: 실화면 캡처는 따로; lint, tsc, corepack pnpm build, 단위 시험 e2e/*.test.mjs 23건 통과) |
| D-223 | #447 | 449b06c | 이벤트가 있는 미터를 고칠 때 이름만 바꿀 수 있다고 알린다 | 이벤트가_있는_미터의_event_type을_바꾸면_409이고_미터는_그대로다: 실패 Expecting actual: "집계된 이벤트가 있어 event_type과 target_property를 바꾸거나 미터를 삭제할 수 없습니다" not to contain: "삭제" |
| D-241 | #447 | 1b50aeb | 관리 상태 사용 중이 화면마다 다른 색과 형태 | 없음(보이는 변경: 실화면 캡처는 따로; lint, tsc, corepack pnpm build, 단위 시험 e2e/*.test.mjs 23건 통과) |
| X-011 | #447 | cc2baea | 미터 목록 이름 열 폭 0 | 없음(보이는 변경: 실화면 캡처는 따로; lint, tsc, corepack pnpm build, 단위 시험 e2e/*.test.mjs 23건 통과) |
| C449-003 | #449 | 10cae67 | 본문으로 이동 뒤 새로고침하면 공개 청구서 로딩에서 멈춤 | 발급 권한, 인보이스 경계와 공개 조회의 결제 및 취소 금액을 검증한다: 실패 expect(locator).toBeVisible() getByText(고객명) not found(본문으로 이동 뒤 새로고침) |
| C449-005 | #449 | 6eacbc5 | main에서도 customer-payment-retry 명세 beforeAll이 /private/tmp/me | customer-payment-retry.real.spec.ts: 실패 Command failed: docker exec -i meterengine-0696-e2e (컨테이너 이름을 바꾼 환경, 고치기 전) |
| C449-008 | #449 | 59e548e | 일반 거절 자동, 고객 재시도 경합 시험 | 일반_거절_뒤_자동_재시도와_고객_재시도가_겹치면_인보이스_잠금을_기다려_새_승인은_한번뿐이다: 실패 expected 2 but was 1 (인보이스 잠금 제거 변이) |
| D-068 | #449 | 22943db | 모바일 공개 청구서 항목 목록 | 발급 권한, 인보이스 경계와 공개 조회의 결제 및 취소 금액을 검증한다: 실패 expect(locator).toBeHidden() getByRole('region', { name: '청구 항목 상세' }) |
| D-110 | #449 | feef72e | 토큰 없는 공개 청구서 안내 | 조회 링크 누락과 일시 조회 실패를 안내하고 원래 링크로 다시 조회한다: 실패 getByRole('heading', { name: '청구서 조회 정보가 없습니다' }) not found |
| D-111 | #449 | 9cd255c | 공개 청구서 10원 미만 절사 안내 | 발급 권한, 인보이스 경계와 공개 조회의 결제 및 취소 금액을 검증한다: 실패 getByText('항목별 공급가액은 ... 10원 미만을 버린 값입니다.') not found |
| D-123 | #449 | 26c1d79 | 공개 청구서 다시 조회 진행과 결과 표시 | 조회 링크 누락과 일시 조회 실패를 안내하고 원래 링크로 다시 조회한다: 실패 expect(locator).toBeDisabled() getByRole('button', { name: '조회 중' }) |
| D-225 | #449 | 3db6ef6 | 최소 사용료와 총량 단가 줄 이름 | public-invoice-lines.test.mjs: 실패 SyntaxError does not provide an export named 'publicInvoiceLineName' |
| X-080 | #449 | 922afac | 공개 청구서 결제 상태 배지 | 조회 링크 누락과 일시 조회 실패를 안내하고 원래 링크로 다시 조회한다: 실패 toHaveCSS color Expected rgb(169, 35, 52) |
| C449-002 | #449 | bddcb58 | 재시도 허용 조회 링크 발급의 키 모드 검사 | test_API_키로_live_결제모드의_재시도_링크를_만들면_409이고_링크를_남기지_않는다: 실패 [HTTP status code] expected: 409 but was: 201 |
| C449-004 | #449 | a5ec894 | 조회 전용 링크 재시도 거절의 code 확인 | 기존_조회_링크와_옵션을_생략한_링크는_고객의_결제를_허용하지_않는다: 실패 expected payment_retry_unavailable but was payment_retry_stale (허용 플래그 검사 제거 변이) |
| L-002 | #453 | 6da19c6 | mvp.real.spec.ts 280-284행은 unbilled_usage_detected 웹훅을 100초  | 없음(문서와 실패 문구 변경: application.properties cron 기본값, application-local 사설 주소 허용과 대조. frontend lint, typecheck 통과; 6da19c6 검사: ) |
| C452-000 | #452 | d89309a | 영구 거절 사용량 자동 재전송 중단 | MeterEngine이 영구 거절한 사용량은 자동으로 다시 보내지 않고 직접 재전송으로만 보낸다: 실패 actual 2 expected 1 |
| C452-001 | #452 | 7deeea5 | 실행 묶음에서 .env와 로컬 DB 제외 | 실행 묶음은 앱 폴더의 .env와 로컬 DB를 복사하지 않는다: 실패 (고치기 전 스크립트) |
| C452-002 | #452 | c6bade8 | 브리지의 외부 ID 없는 옛 고객 이어 쓰기 | test_외부_ID_없이_만든_옛_고객은_이름으로_이어_받는다: 실패 (고치기 전 새 고객 등록) |
| C452-004 | #452 | f76af6a | 다른 고객 사용량 격리 시험 보강 | 조회 응답에 다른 고객의 사용량, 인보이스, 납부 상태를 섞지 않는다: 실패 (service.ts 고객 필터 제거 변이) |
| C452-005 | #452 | 361c229 | 포털 고객 사용량 격리 시험 | 도입사 서버가 조회한 전체 사용량에서도 선택한 고객의 정보만 화면에 전달한다: 실패 actual [['tokens',123],['tokens',456]] (고객 필터 제거 변이) |
| C452-006 | #452 | 163a6c5 | main에서도 tenant-service.real.spec.ts 244-245행이 이번 달(baseline. | 없음(Lumen 실화면은 띄우는 법이 없어 workspace 응답(그 달 본인 고객 인보이스)과 대조, demo typecheck, lint 통과; b78630c 검사: frontend lint, typecheck, bu) |
| C452-007 | #452 | c1ee7a8 | 웹훅 수신의 브라우저 요청과 본문 상한 | 브라우저 요청과 상한을 넘는 본문은 대상 선택 전에 기록 없이 거절한다: 실패 actual 400 expected 403 |
| C452-008 | #452 | 62e8eee | 메뉴 이동 때 체험 시작 화면 깜빡임 | 없음(demo 검사 통과(62e8eee): unittest, pnpm -r typecheck, lint, test(llm-service 39, web 85), build, playwright --list; 보이는 변경: ) |
| C452-009 | #452 | 600c74c | Lumen 청구 오류 문구 | 부분 조회 실패를 가짜 0원 성공으로 바꾸지 않는다: 실패 actual '청구 예정액, 요금 설정를 확인하지 못했습니다. 연결 상태를 확인한 뒤 새로고침하세요.' |
| C452-011 | #452 | bb7d3c5 | main demo/README.md 458행은 eslint-naming-rules.mjs를 sdk/와 web | 없음(문서 변경: demo/llm-service/eslint.config.mjs의 import와 대조; 6da19c6 검사: backend build, frontend lint와 build, 단위 시험, demo 검사 통) |
| C452-012 | #452 | 0b402fd | 재생성 사용량 시각 시험 | 실패한 응답을 나중에 다시 만들어도 처음 요청 시각으로 사용량을 보낸다: 실패 (이벤트 시각을 지금 시각으로 바꾼 변이) |
| C452-013 | #452 | 5c83728 | 계정별 재전송 격리 시험 보강 | 한 계정의 실패 재전송이 다른 계정의 큐를 처리하지 않는다: 실패 (store.ts 계정 조건 제거 변이) |
| C452-014 | #452 | 8dbbd99 | main의 demo/llm-service/playwright.config.ts outputDir가 /priv | 없음(Lumen 실화면은 띄우는 법이 없어 playwright test --list로 설정 읽기 확인, demo typecheck, lint 통과; b78630c 검사: frontend lint, typecheck, bu) |
| C452-016 | #452 | b3f414e | 원문의 시점 문제(#452 README가 #453 기능을 설명)는 #453 squash 94a48a20이 환 | 없음(문서 변경: environment.ts 환경 경로, ConsoleShell 안내, backend README와 대조; 6da19c6 검사: backend build, frontend lint와 build, 단위 시험) |
| C453-001 | #453 | d255870 | 대사의 옛 키 버전 실패 격리 | 대사는_창_안에_쓰이지_않은_옛_키를_부르지_않고_한_버전이_실패해도_다른_버전을_대사한다: 실패 expected: 0 but was: 1 |
| C453-002 | #453 | 04c93d9 | 도입사 공통 작업 감사의 환경 분리 | 도입사_공통_작업의_감사는_모든_환경의_감사_조회에_보인다: 실패 expected: 1 but was: 0 |
| C453-006 | #453 | a34c969 | 고친다. 전역 비밀값을 legacy 대상에만 주는 규칙은 METERENGINE_TARGETS의 여러 대상이  | 단일 대상에서 환경을 지정하면 전역 비밀값으로 그 환경의 웹훅을 받는다: 실패 actual 400 expected 200 |
| C453-008 | #453 | ffa9cd3 | 취소 확인의 환경과 키 버전 시험 | 요청문맥_없는_취소_확인도_원본환경과_결제당시_키를_사용한다: 실패 status pending (환경 래퍼 제거 변이와 키 기준 시각 변이) |
| C453-009 | #453 | 320bc27 | main에서도 mvp.real.spec.ts 145행이 now+1시간 미래 이벤트를 보내고 151-152행이 | 없음(바뀐 단언은 main에서 앞 단계(발급 키 창)에 막혀 실행되지 않는다. UnbilledEventService의 month 범위와 대조, lint, typecheck 통과; b78630c 검사: frontend li) |
| C453-011 | #453 | b78630c | main에서도 query-workspace.real.spec.ts는 playwright.config.ts p | 없음(playwright -c playwright.mvp.config.ts --list에 나오고 query-workspace.real.spec.ts 1건 통과; b78630c 검사: frontend lint, typech) |
| C453-012 | #453 | 82ac370 | main의 permissions.real.spec.ts beforeAll도 같은 /private/tmp 고정 | permissions.real.spec.ts: 실패 Command failed: docker exec -i meterengine-0696-e2e (컨테이너 이름을 바꾼 환경, 고치기 전) |
| D-204 | #453 | 4907257 | main의 EnvironmentSwitch.tsx:19는 window.confirm이 false면 안내 없이 | 격리 계약: 업무 환경 전환은 목적지를 밝힌 확인 창을 거치고 화살표 키로는 바뀌지 않는다: 실패 locator.click Test timeout(기본 select에 옵션 선택 불가) |
| G-044 | #453 | c7898bb | main workspace-actions.ts:29-30과 38은 mutation-scope.ts:8,10의 | 격리 계약: 업무 환경이 바뀐 API 키 발급은 공용 문구로 거절하고 전송하지 않는다: 실패 Received '열어 둔 화면과 요청의 업무 환경이 다릅니다' |
| G-133 | #453 | 6152c0b | test 환경 실패를 운영 지표와 경보에서 제외 | 환경별_수신처와_아웃박스와_실제_전송이_독립적이다: 실패 [legacy] Expecting actual not to be null(환경 태그 없음) |
| G-145 | #453 | ca5e4a1 | 로컬 시드 키는 R__local_credentials.sql 31-41행에서 environment_id 없이 | 없음(문서 변경: BusinessEnvironmentInterceptor 기존 키 검사, R__local_credentials.sql, webhook-receipt.ts, webhook-signature.ts와 대조; 6) |
| G-175 | #453 | 2ecaca4 | main backend/README.md 308행은 고객 external_id가 도입사 안에서 고유하다고 적 | 없음(문서 변경: V45 organization_environment, 고유 인덱스, event_pk와 대조; 6da19c6 검사: backend build, frontend lint와 build, 단위 시험, demo ) |
| G-189 | #453 | ec42c75 | main frontend/package.json 12행에는 #442 squash 1b2c555d로 test: | 없음(문서 변경: package.json scripts와 세 playwright 설정의 포트와 변수와 대조; 6da19c6 검사: backend build, frontend lint와 build, 단위 시험, demo 검) |
| U10-B1 | #453 | 82f5ad6 | 자동 재시도 저장 직전 키 보관 재확인 | 재시도_확인_뒤_토스_조회_중에_키를_보관하면_새_시도를_만들지_않고_skipped로_센다: 실패 Expected size: 1 but was: 2 |
| X-060 | #453 | 4907257 | main의 EnvironmentSwitch.tsx:17-26은 네이티브 select의 change에서 바로  | 격리 계약: 업무 환경 전환은 목적지를 밝힌 확인 창을 거치고 화살표 키로는 바뀌지 않는다: 실패 locator.click Test timeout(기본 select) |
| D-014 | #453 | b303189 | main 설정 화면 settings/page.tsx:29,31은 같은 payment_setting.mode를 | 없음(보이는 변경(문구): archive-filters 실화면 2건 통과, environment-cycle은 바뀐 문구 단계를 지나 기준선과 같은 개요 제목에서 멈춘다; b303189 검사: frontend lint, t) |
| D-219 | #453 | b303189 | main에서 '환경'이 업무 환경(environment.ts:8)과 결제 모드 두 뜻으로 쓰인다. 결제 모드 | 없음(보이는 변경(문구): archive-filters 실화면 2건 통과, environment-cycle은 바뀐 문구 단계를 지나 기준선과 같은 개요 제목에서 멈춘다; b303189 검사: frontend lint, t) |
| X-003 | #453 | 3aba86f | main의 ConsoleShell.tsx:75 환경 안내 띠는 className이 고정이고 globals.c | environment.test.mjs: 실패 SyntaxError does not provide an export named 'environmentNoticeClass' |
| X-033 | #453 | b303189 | 결제 모드 하나가 '현재 결제 환경', 'PG 결제 환경', '연동 모드'(settings/page.tsx: | 없음(보이는 변경(문구): archive-filters 실화면 2건 통과, environment-cycle은 바뀐 문구 단계를 지나 기준선과 같은 개요 제목에서 멈춘다; b303189 검사: frontend lint, t) |
| NT-yang-001 | #442 | 1e6c5df | AuthFrame 안내 h2를 문단으로 | the sign-in frame starts its headings with the page h1: 실패 Expected "H1", Received "H2" |
| NT-yang-004 | #442 | 91713b2 | main의 workspace.contract.spec.ts 키 발급 시험 두 개가 이 패키지 커밋 2db50b4(복사 전 닫기 window.confirm) 뒤로 창을 닫지 못해 실패했다. 시험이 그 확인을 받아들이게 고쳤다. | 격리 계약: 발급 중 닫기를 막고 도착한 비밀값을 그 창에서만 보여 준다: 실패 expect(locator).toHaveCount (main 기준선) |

## 정할 것

| id | 층 | 질문 |
| --- | --- | --- |
| G-050 | #442 | 비밀번호, 이름, 이메일 검사를 D-103이 만드는 frontend/src/components/auth/credential-rules.ts로 모으려고 서버 액션 네 파일(admin, (public), 초대, workspace-actions)을 함께 바꿀지, 아니면 workspace-actions.ts createMember 사전 검사만 더하고 복사는 그대로 둘지. |
| C442-002 | #442 | 관리자나 운영자가 정한 초기 비밀번호를 본인이 바꾸게 할지(첫 동의 단계에서 바꾸게 하거나 새 마이그레이션으로 변경 강제 열을 둔다), 도입사의 계정 직접 생성을 없애고 초대만 남길지, 지금대로 둘지 정한다. |
| D-136 | #442 | 콘솔의 월 선택을 antd MonthPicker 하나로 통일하고(폼 제출 화면은 onChange와 hidden input) 월 라벨과 적용, 초기화 버튼 문구를 한 벌로 정할지, 정하면 #442 MonthFilter와 #446, #448, #450, #451 화면을 각 층 담당이 바꾼다. |
| D-170 | #442 | 콘솔의 월 선택을 antd MonthPicker 하나로 통일하고(폼 제출 화면은 onChange와 hidden input) 월 라벨과 적용, 초기화 버튼 문구를 한 벌로 정할지, 정하면 #442 MonthFilter와 #446, #448, #450, #451 화면을 각 층 담당이 바꾼다. |
| D-179 | #442 | 사이드바를 antd Menu 대신 nav 안 그룹 제목과 ul, li, a 링크 목록(aria-current만 유지)으로 바꾸고, #443의 Menu 테마 토큰(itemSelectedBg 등)과 .ant-menu 스타일을 CSS로 옮겨 모양을 지킬지 정한다. |
| D-236 | #442 | 콘솔의 월 선택을 antd MonthPicker 하나로 통일하고(폼 제출 화면은 onChange와 hidden input) 월 라벨과 적용, 초기화 버튼 문구를 한 벌로 정할지, 정하면 #442 MonthFilter와 #446, #448, #450, #451 화면을 각 층 담당이 바꾼다. |
| G-054 | #442 | #442에서 쓰이지 않는 DevStateSwitch.tsx, layout-sizes의 SIDER_WIDTH, CONTENT_MAX_WIDTH, NAV_DRAWER_SIZE, TenantScope, WorkspaceOperations를 네 파일에 걸쳐 지우고 README와 lib/api 오류 문구의 표 상태 스위치 안내를 고칠지, 아니면 표 상태 스위치를 셸에 되살릴지. |
| G-059 | #442 | #442에서 도입사 목록 신청일, workspace-href 월 계산, WorkspaceTable 숫자와 금액 표기의 세 파일을 formatKstDateTime, formatKstMonth, formatDecimal과 formatKrw로 모을지(WorkspaceTable 소수 자릿수가 12에서 10으로 줄어 보이는 값이 바뀐다) 정한다. |
| G-187 | #442 | CI에 프론트 단위 시험, 계약 명세, demo, deploy, terraform 잡을 더할지(팀 전체에 거는 제약이라 Discussion 합의가 필요하다). |
| X-046 | #442 | 콘솔의 월 선택을 antd MonthPicker 하나로 통일하고(폼 제출 화면은 onChange와 hidden input) 월 라벨과 적용, 초기화 버튼 문구를 한 벌로 정할지, 정하면 #442 MonthFilter와 #446, #448, #450, #451 화면을 각 층 담당이 바꾼다. |
| X-104 | #442 | 로그아웃 요청이 401 밖의 이유(네트워크, 시간 초과, 5xx)로 실패했을 때 지금처럼 쿠키를 남겨 다시 시도하게 할지(계약 시험 logout keeps the session after a revocation failure가 이 동작을 고정한다), 쿠키를 먼저 지우고 서버 세션 종료를 확인하지 못했다고 알릴지 정한다. |
| Ck2-017 | #445 | 실패하거나 빠진 날의 결제 대사를 다시 돌리는 경로를 키별 워터마크 자동 따라잡기(새 마이그레이션 필요)로 둘지, 날짜를 받는 운영자 재실행 API와 deploy/README 런북으로 둘지, 이번 스택에서는 두지 않고 후속 이슈로 넘길지 정한다. |
| Nk3-006 | #447 | GET /v1/price-plans에 month를 주면, status와 상관없이 그 달 청구에 적용되는 요금제 버전 하나만 돌려주도록 계약을 바꿀지 정해야 합니다. 바꾸지 않는다면 지금처럼 그 달에 걸친 이력을 status로 걸러 돌려주고, demo verify와 콘솔이 status=all 목록에서 보관 가림을 포함한 청구 규칙으로 직접 고르게 합니다. |
| Ck3-447-03 | #447 | #447에서 결제 이력 목록을 저장소가 아니라 DTO from(KST 변환)과 서비스의 문서대로 정렬로 만들도록 저장소와 두 서비스를 고칠지, 컨트롤러 설명을 이력 행 순서에 맞게 고칠지, 그리고 교체로 물러난 키와 카드를 보관 목록에 보관으로 보일지 정합니다. |
| Ck3-447-06 | #447 | CustomerRepository에서 호출처 없는 existsWithPricing과 findByOrganizationIdAndIdIn을 지우고, PricePlanService와 PriceRateService의 읽기 트랜잭션을 실제로 호출되는 4인자 list로 옮기는 세 파일 정리를 #447에서 할지 정합니다. |
| D-245 | #447 | #447에서 쓰이지 않게 된 antd 고객 파일 7개와 customers/actions.ts, state.ts, #451에서 쓰이지 않게 된 antd 가격 파일 6개와 billable-metric-prices/actions.ts, state.ts를 지우고 README의 antd 쓰기 폼 설명을 고칠지, 지운다면 #447과 #451 층마다 지울지 정점에서 한 번에 지울지 정한다. |
| G-183 | #447 | #447 고객 화면 교체 뒤 쓰이지 않는 components/customers 7개 파일, customers/actions.ts, state.ts와 lib/api/customers.ts의 create/update/deleteCustomer를 지울지, 지운다면 이 파일들을 고친 #451 e5fca11과의 충돌을 어느 층에서 누가 풀지 정한다. |

## 막힌 것

| id | 층 | 까닭 |
| --- | --- | --- |
| G-012 | #442 | do-not-touch G-031 V26__organization_people_and_invitations: 소속 보관 열 revoked_at(V26:36)을 archived_at으로 맞추려면 V26 내용이나 새 V__ 파일이 필요하다. 열 이름은 공개 이름이기도 하다 |
| G-019 | #442 | do-not-touch G-031 G-032 V26__organization_people_and_invitations: 감사 인덱스와 anonymous 행위자 변경을 #448로 옮기려면 V26 내용을 바꾸고 #448에 새 V__ 파일을 더해야 한다 |
| X-057 | #442 | not-owner #444: #442의 두 화면(integrations, members)은 표 앞에 h2를 둬 카드 h3가 건너뛰지 않는다. h2 없이 WorkspaceTable을 처음 부르는 화면은 #444 integrations/[id]이고 같은 건너뜀은 #445 #446 #447 #450 #451 화면에서 생긴다. 지적의 AuthFrame 부분은 원인이 달라 NT-yang-001로 떼어 냈다 |
| D-216 | #449 | needs-new-migration: 확정 시점 미터 이름을 담을 invoice_line 열이 없다. 필요한 SQL: ALTER TABLE invoice_line ADD COLUMN billable_metric_name varchar(255); 확정 때 채우고 공개 응답에 billable_metric_name을 더한다. 지금 이름을 읽을 때 붙이는 길은 이름을 바꾼 뒤 확정 청구서가 달라져 결정이 필요하다 |
| G-135 | #453 | do-not-touch M4-B3: 쓰이지 않는 조회 인덱스를 빼거나 고치려면 V48 내용을 바꾸거나 새 V__가 있어야 한다(T-yang brief가 G-135를 do-not-touch로 두라 함). 조회를 environment_scope_id 조건으로 바꾸는 길은 문인호 님 open 파일 WorkspaceQueryRepository를 함께 바꿔야 한다 |
| NT-yang-002 | #442 | not-owner 문인호 님이 main 실화면 명세 실패(environment-cycle, mvp)를 첫 PR에서 고치기로 했다(#논의 스택 스레드 2026-10-29 15:25, 박성종 님 15:26 동의). 겹치지 않게 이 세션은 두 명세 파일을 건드리지 않는다 |
| NT-yang-003 | #453 | not-owner 문인호 님이 main 실화면 명세 실패(environment-cycle, mvp)를 첫 PR에서 고치기로 했다(#논의 스택 스레드 2026-10-29 15:25, 박성종 님 15:26 동의). 겹치지 않게 이 세션은 두 명세 파일을 건드리지 않는다 |

## 새 지적

| id | 층 | 상태 | 내용 |
| --- | --- | --- | --- |
| NT-yang-001 | #442 | fixed | 인증 화면 프레임(AuthFrame)이 h1보다 앞에 h2를 둔다(X-057에서 떼어 냄) |
| NT-yang-002 | #442 | blocked | mvp 실화면 명세가 복사 전 닫기 확인에 막혀 실패한다 |
| NT-yang-003 | #453 | blocked | environment-cycle 실화면 명세가 #451의 개요 제목 변경을 따라가지 않는다 |
| NT-yang-004 | #442 | fixed | 업무 계약 명세가 복사 전 닫기 확인에 막혀 실패한다 |

## 돌리지 못한 검사

| 브랜치 | sha | kind | 까닭 |
| --- | --- | --- | --- |
| fix/s456-445-t-yang | 318ee02 | screen | 실화면 환경 동기화 실패 |

## 위층 반영 때 볼 것

| id | 층 | 내용 |
| --- | --- | --- |
| C442-004 | #442 | #442부터 #452까지는 같은 경로가 열려 있다. 정점 client.ts:18은 여전히 헤더를 그대로 실어 보내며 백엔드 인터셉터가 막는 구조다. |
| C442-005 | #442 | #453 G-189가 같은 줄을 같은 자리에 더하므로 글자를 맞췄다. #449, #453에서 따로 고치지 않는다. CI에 단계를 더하는 일은 G-187(needs-decision)에 남는다. |
| C442-011 | #442 | 정점까지 48행이 같아 그대로 올라간다. 위층 e5fca11이 61행 수락 뒤 이동을 /overview로 바꿨으므로, 반영 뒤 이 계약 시험의 기대 경로(expectTenantUrl /customers)를 /overview로 맞춘다. 정점 auth.contract.spec.ts는 /customers와 /overview를 모두 받는 꼴을 쓴다. |
| C442-012 | #442 | 정점 (public)/actions.ts는 a39db70이 clientIp를 더해 로그인 요청 줄이 달라졌다. 반영 때 saveSession 뒤의 destination 계산을 이 커밋의 꼴로 맞춘다. 23b1844가 proxy.ts에 environment 검사를 더했지만 이 수정과 겹치지 않는다. |
| C442-013 | #442 | 정점 session-actions.ts는 e5fca11이 switchOrganizationAction만 바꿔 logoutAction은 같다. 반영 때 logoutAction의 returnTo 처리와 LogoutButton prop을 남긴다. |
| C442-021 | #442 | 80a3ef1이 OperatorAccountIntegrationTest의 다른 줄을 바꿨다. 반영 때 195-200행 검사가 actor_id 기준으로 남았는지 본다. |
| D-060 | #442 | 단계 강조 모양(is-current, is-done)은 #443 globals.css의 onboarding-steps 규칙에 더할 수 있다(문인호 님 층). |
| D-141 | #442 | 정점 명세에 브라우저 검증(validationMessage, :invalid)에 기대는 곳은 없다. 위층 대화상자의 숫자와 URL 입력도 같은 인라인 문구를 받는다. |
| D-232 | #442 | 체크박스 이름은 문서 제목으로 시작해 #453 people.real.spec.ts의 agree(/^이용 안내/, /^개인정보 처리 안내/)가 그대로 맞는다. 테두리와 크기는 #443 globals.css의 consent-fields 규칙으로 보인다. |
| G-001 | #442 | 가입 경로의 조회와 저장 사이 경합은 이제 409가 아니라 500이다(error-handling.md 규칙). 정점까지 이 파일을 바꾼 위층 커밋은 없다. |
| G-006 | #442 | 23b1844 등 위층 커밋이 같은 컨트롤러에 environment 관련 변경을 했다면 반영 때 @ApiResponses 블록을 남긴다. openapi.yaml은 각 층 빌드가 다시 만든다. |
| G-009 | #442 | 정점 0fe7d2a에서 invitations 사용처는 OrganizationPeopleController.java:115 하나뿐이고 OrganizationPeopleService는 위층에서 바뀌지 않았다. 컨트롤러는 23b1844가 두 줄 바꿨으니 서비스를 옮기면 위층 컨트롤러의 호출과 주입 필드도 같이 맞춘다. |
| G-027 | #442 | 바꾸기로 하면 #442에서 OrganizationInvitationController.java의 @PostMapping, SecurityConfig.java permitAll 매처, OrganizationPeopleIntegrationTest와 UserPermissionsIntegrationTest의 경로, frontend/src/app/organization-invitations/[token]/actions.ts의 `${path}/accept`, backend/openapi.yaml을 함께 고친다. 위층에서는 #448의 backend/src/test/java/com/meterengine/global/security/RequestLogFilterTest.java:222 @ 0fe7d2a와 각 층의 openapi.yaml 재생성이 필요하다. 다른 동사 경로는 EventController.java:212, EventExportController.java:38, EventIssueController.java:34, EventRejectionController.java:34, InvoiceViewLinkController.java:75,87, WorkspaceQueryController.java:46,101 @ 0fe7d2a다. |
| G-039 | #442 | 반영 때 compose.prod.yml이 충돌한다: #449 7ce9d36이 프론트 METERENGINE_API_KEY 줄 뒤에 METERENGINE_PUBLIC_ORIGIN을, #454 a39db70과 21feb94가 백엔드 환경에 호출 제한과 신뢰 프록시 줄을 더했다. 정점 기준 지울 줄은 x-backend 24-26행과 frontend 86-88행이다. #449 deploy/tests/test_public_origin.py와 #454 test_trusted_proxies.py의 DUMMY_ENV에서 FRONTEND_API_KEY, ORGANIZATION_ID, ORGANIZATION_NAME을 빼도 되고, 남겨도 compose가 읽지 않아 통과한다. #452(C452-003)의 compose 정리와 같은 줄을 다룬다. |
| G-042 | #442 | 반영하면 새 시험이 위층 head에서 실패한다: #444부터 #446까지 integrations/[id]/page.tsx의 restoreWebhookEndpoint, #447부터 #450까지 customers/page.tsx의 createCustomer에 workspace-actions.ts case가 없다(#451 e5fca11이 createCustomer case를 더한다). 각 층 담당이 그 층 workspace-actions.ts에 case를 더하거나 패널을 case가 생기는 층으로 옮긴다. #447, #449는 T-yang 몫이고 #444, #446, #448, #450은 다른 담당 몫이다. |
| G-045 | #442 | 정점에서 WorkspaceError를 쓰는 16개 파일은 모두 message만 넘겨 그대로 컴파일된다. 위층 명세에 '다시 시도' 링크를 찾는 곳은 없다(정점 e2e 검색). WorkspaceTable과 WorkspaceHeader를 기존 부품과 합칠지는 남은 결정이다. |
| G-050 | #442 | 정점 0fe7d2a에서 같은 검사가 (public)/actions.ts:41, admin/actions.ts:28, organization-invitations/[token]/actions.ts:44에 있고 createMember는 workspace-actions.ts:45로 옮겨 있다. #442에서 고치면 위층 merge 때 (public)/actions.ts(a39db70이 호출 제한을 더함)와 workspace-actions.ts(80a3ef1 이후 여러 층이 case를 더함)에서 충돌을 맞춰야 한다. |
| G-052 | #442 | 정점에서 OrganizationMember, OrganizationInvitation 이름을 쓰는 곳은 management.ts뿐이다. #448 15692f8이 management.ts의 감사 로그 타입을 바꿨으니 반영 때 그 부분과 겹치지 않는지 본다. AuditLog, AuditLogsResponse를 ListAuditLogsResponse 등으로 맞출지는 #448 담당이 본다. |
| G-056 | #442 | 정점까지 정규식과 쿠키 줄은 그대로다. e5fca11(#451)이 continue/page.tsx의 이웃 줄(링크 대상)만 바꿔서, 모으기로 하면 위층을 다시 쌓을 때 작은 충돌이 날 수 있다. 백엔드 OrganizationInvitationController.java 36, 45행의 @Pattern과 SecureTokens.generate("me_invite_")가 같은 형식에 묶여 있다. |
| G-058 | #442 | #453 workspace.contract.spec.ts 75행과 101행은 취소 작업의 '결제와 취소 내역 새로 조회', '새 취소 요청'을 찾고 문구 표가 같은 값을 준다. 위층에서 isUnconfirmed를 돌려주는 다른 작업(#445 updateTossPaymentsCredential 등)이 결제 문구를 원하면 그 층에서 COPY에 더한다. |
| G-065 | #442 | 정점 transport.ts 111-112행 catch가 같은 글자라 그대로 옮겨진다. e5fca11(#451)과 a39db70(#454)이 근처 줄을 바꿨지만 겹치지 않는다. H2-S1(#453)의 옛 백엔드 조합 호환은 이 수정 밖이다. |
| G-067 | #442 | CONSENT_VERSION 상수는 정점 0fe7d2a의 real e2e 여러 곳(aggregation, archive-filters, customer-payment-retry, environment-cycle, events 등 frontend/e2e/*.real.spec.ts)에서 가입 요청의 판으로 쓰인다. 상수를 없애거나 서버에서 받게 바꾸면 그 파일들도 같이 고쳐야 한다. 결제 수단 등록의 consent_document_version(CreateBillingKeyRequest, #445 이후)도 같은 방식으로 판을 그대로 받는다. |
| G-069 | #442 | main에서 온 CustomerFormDialog, BillableMetricFormDialog도 randomUUID를 쓰지만 이 지적 밖이다. |
| X-055 | #442 | 위층 amount 호출(결제 금액 등)은 기본 범위(0 이상 안전 정수)로 그대로 동작한다. 위층 catch에 JSON 조합과 구간 입력이 생기면(#446 요금제) 그 층에서 JSON 오류를 InputFieldError로 바꿔 필드를 알린다. |
| X-061 | #442 | 정점 명세 people.real.spec.ts와 mvp.real.spec.ts는 링크를 getByRole('link', { name, exact: true })로 찾아 이름이 그대로라 영향이 없다. |
| X-066 | #442 | 위층에서 더한 작업(결제, 웹훅, 고객 등)은 맵에 없어 '처리했습니다.'로 남는다. 각 층이 원하면 SUCCESS_MESSAGES에 문구를 더한다. #453 mvp.real.spec.ts 106행과 environment-cycle.real.spec.ts 72행은 updateSupplier라 영향이 없다. |
| X-095 | #442 | 정점 명세에 '사용량 수집'을 찾는 곳은 없다. |
| X-103 | #442 | 위층 session.ts(e5fca11, 80a3ef1)와 client.ts는 requireOperator와 헤더 처리만 바꿨다. 반영 때 네 곳의 만료 이동이 expiredLoginHref로 남았는지 본다. 위층 auth.contract.spec.ts의 같은 단언은 이 층 수정으로 함께 바뀐다. |
| C442-016 | #442 | fixed-above는 #442부터 #445까지 아직 고장이다. 층마다 가격 화면 진입을 따로 열지는 배포 단위 결정 뒤 조율 담당이 본다. #451 e5fca11부터는 /billable-metric-prices가 /pricing으로 리다이렉트하므로 가격 항목을 아래층에 넣으면 위층에서 중복 메뉴가 된다. |
| C442-017 | #442 | #442만 main에 들어가고 #443이 아직인 사이 상태에서만 목록과 행 버튼이 두 벌 보인다. 층마다 따로 고치기로 정하면 #443 globals.css 221행과 767px 미디어 쿼리 240~241행에 해당하는 최소 규칙(.workspace-mobile-records 기본 display none, 모바일 폭에서 .workspace-desktop-table none, .workspace-mobile-records grid)을 #442 globals.css에 넣으면 되고, #443의 같은 규칙은 그대로 두면 겹쳐도 결과가 같다. |
| C442-018 | #442 | #453 반영 때 integrations/page.tsx의 mode 필드는 #453 문구(environment === legacy 조건과 업무 환경 전환 안내)를 따른다. #443부터 #452까지는 이 커밋 문구가 맞다. |
| D-006 | #442 | #445부터 MonthFilter를 쓰는 화면(수납과 취소)에 그대로 적용된다. 23b1844가 WorkspaceHeader.tsx를 바꿨지만 15행 입력과는 겹치지 않는다. |
| D-012 | #442 | 위층에서 더한 화면(#444 integrations/[id], #445 payments와 settings, #446 pricing, #447 customers/[id], #448 audit, #449 invoices 상세, #450 event-issues, event-rejections, #451 overview 등)에는 metadata.title이 없어 루트 기본값 'MeterEngine 관리자 콘솔'이 보인다. 각 층 담당이 같은 방식으로 title을 더한다. 정점 layout.tsx는 f671a4f가 description을 바꿨으니 title 틀만 옮긴다. |
| D-029 | #442 | #445부터 MonthFilter를 쓰는 화면(수납과 취소)에 그대로 적용된다. 23b1844가 WorkspaceHeader.tsx를 바꿨지만 15행 입력과는 겹치지 않는다. |
| D-031 | #442 | #445 4ace811(/settings 추가)과 #446 614d3bc(/pricing 추가)가 NavMenu.tsx 14행을 바꿔 반영 때 충돌한다. 각 층의 과금과 연동 items에 { href: "/billable-metrics", label: "미터" }를 남기고, #446부터는 요금 설계 바로 뒤에 둔다. 가격 항목은 넣지 않는다(#451부터 /billable-metric-prices는 /pricing으로 이동). |
| D-043 | #442 | #453 workspace.contract.spec.ts에 확정 실패 뒤 창을 닫았다 다시 열어 보내면 새 request_id가 실리는지 보는 시험을 더할 수 있다(T-yang #453 차례). |
| D-050 | #442 | 위층 WorkspaceError 호출부(정점 16개 파일)는 status를 넘기지 않아 지금처럼 일반 실패로 보인다. 상세 화면(#444 integrations/[id], #445 payments/[id], #447 customers/[id] 등)에서 404를 구분하려면 그 층이 status={...error.status}를 넘긴다. |
| D-078 | #442 | #445 수납 상세 화면(payments/[id])에서 머리말 링크 둘이 오른쪽에 함께 놓이는지 그 층 차례에 캡처로 본다(T-yang 층). |
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
| G-016 | #442 | 정점 workspace-actions.ts 40행에 같은 분기가 남아 있다. 위층 커밋들이 이 파일을 바꿨으니 반영 때 그 분기를 지운다. |
| G-017 | #442 | ApiKeySecretResponse 9인자 생성자를 지우면 #453 23b1844가 같은 생성자 본문을 고쳐 반영 merge에서 충돌하므로 #453에서 그 생성자를 함께 지우고 0fe7d2a의 ApiKeyIntegrationTest.java 126행 호출을 11인자로 바꾼다. ApiKeyService.list(UUID), create 5인자와 나머지 짧은 오버로드는 git grep 0fe7d2a에서 부르는 곳이 없어 위층 빌드는 깨지지 않는다. SessionService와 SessionServiceTest는 정점까지 바뀐 커밋이 없어 그대로 따라간다. |
| G-018 | #442 | code를 옮기는 안: #442에서 지운 뒤 위로 반영하면 아래 층에서 컴파일이 깨진다. 각 층이 상수를 다시 더해야 한다. #444는 WEBHOOK_DELIVERY_NOT_RETRYABLE, INVALID_WEBHOOK_URL, INVALID_WEBHOOK_EVENT_TYPE, WEBHOOK_ENDPOINT_NOT_FOUND, WEBHOOK_ENDPOINT_DISABLED, WEBHOOK_ENDPOINT_LIMIT_EXCEEDED. #445는 토스와 결제 code 16개. #446은 PRICE_RATE_HISTORY_IMMUTABLE, PRICE_PLAN_HISTORY_IMMUTABLE, PRICE_PLAN_NOT_FOUND. #447은 CUSTOMER_ARCHIVED, RESOURCE_ARCHIVED, ARCHIVE_RESTORATION_CONFLICT. #449는 INVOICE_VIEW_LINK_NOT_FOUND, PAYMENT_RETRY_UNAVAILABLE, PAYMENT_RETRY_STALE. #450은 EVENT_NOT_FOUND. #442의 frontend/src/app/(console)/workspace-actions.ts:67은 payment_result_unconfirmed, payment_cancellation_in_progress를 문자열로 비교하므로 함께 볼지 정한다. CUSTOMER_HAS_PRICING은 정점에서도 openapi.yaml:8456과 ErrorCode.java:86에만 있다. 구역만 맞추는 안: #450의 8bb17c3이 ORGANIZATION_NOT_ACTIVE 뒤에 ENVIRONMENT_MISMATCH를, 409 구역 머리에 RESOURCE_VERSION_CONFLICT, IDEMPOTENCY_CONFLICT, ENVIRONMENT_NOT_READY를 더했다. 그래서 CONSENT_REQUIRED는 ORGANIZATION_NOT_ACTIVE 앞에, API_KEY_UNAVAILABLE은 409 구역 끝에 둬야 반영 merge 충돌이 줄어든다. 위층마다 openapi.yaml enum 순서가 바뀌므로 반영한 뒤 각 층에서 openapi.yaml을 다시 만든다. |
| G-019 | #442 | 정점에서는 V41__create_invoice_view_links가 actor_type 검사에 invoice_view_link를 더하고, AuditLogController가 anonymous 필터를 받는다. V26에서 audit 변경을 빼려면 #448 이후 층의 이 사용처도 같이 맞춰야 한다. |
| G-030 | #442 | 반영 때 compose.prod.yml이 충돌한다: #449 7ce9d36이 프론트 METERENGINE_API_KEY 줄 뒤에 METERENGINE_PUBLIC_ORIGIN을, #454 a39db70과 21feb94가 백엔드 환경에 호출 제한과 신뢰 프록시 줄을 더했다. 정점 기준 지울 줄은 x-backend 24-26행과 frontend 86-88행이다. #449 deploy/tests/test_public_origin.py와 #454 test_trusted_proxies.py의 DUMMY_ENV에서 FRONTEND_API_KEY, ORGANIZATION_ID, ORGANIZATION_NAME을 빼도 되고, 남겨도 compose가 읽지 않아 통과한다. #452(C452-003)의 compose 정리와 같은 줄을 다룬다. |
| G-034 | #442 | fixed-above: #443부터 #452까지의 head(0f1d1bf에서 2aa2be2까지)의 backend/README.md 운영 키 발급 3단계는 아직 scopes 없이 소속시키고, 인증 절 104행도 옛 문장이다. #453 f014e03부터 고쳐져 있다. 층마다 따로 고칠지는 배포 단위 결정 뒤 박성종 님이 정한다. 이 층에서 같은 줄을 고치면 23b1844와 같은 내용이어야 #453 반영 때 충돌이 나지 않는다. |
| G-038 | #442 | ErrorCode.java는 72e8718..0fe7d2a 사이에 8bb17c3(#450)만 바꾸므로 #442에서 지운 줄이 #443~#449로 그대로 반영되어 사용처가 있는 층의 빌드가 깨진다. 지운다면 다시 더할 층은 #444 WEBHOOK_DELIVERY_NOT_RETRYABLE, INVALID_WEBHOOK_URL, INVALID_WEBHOOK_EVENT_TYPE, WEBHOOK_ENDPOINT_NOT_FOUND, WEBHOOK_ENDPOINT_DISABLED, WEBHOOK_ENDPOINT_LIMIT_EXCEEDED; #445 INVALID_TOSS_PAYMENTS_CREDENTIAL, PAYMENT_METHOD_REJECTED, TOSS_PAYMENTS_TEST_CODE_IN_LIVE_MODE, PAYMENT_METHOD_NOT_FOUND, PAYMENT_METHOD_REGISTRATION_NOT_FOUND, PAYMENT_ATTEMPT_NOT_FOUND, PAYMENT_RESULT_UNCONFIRMED, TOSS_PAYMENTS_CREDENTIAL_MISSING, PAYMENT_METHOD_MISSING, PAYMENT_MODE_MISMATCH, PAYMENT_ATTEMPT_NOT_CANCELABLE, PAYMENT_ATTEMPT_FULLY_CANCELED, PAYMENT_CANCELLATION_AMOUNT_EXCEEDED, PAYMENT_CANCELLATION_IN_PROGRESS, PAYMENT_CANCELLATION_IDEMPOTENCY_CONFLICT, TOSS_PAYMENTS_UNAVAILABLE; #446 PRICE_RATE_HISTORY_IMMUTABLE, PRICE_PLAN_HISTORY_IMMUTABLE, PRICE_PLAN_NOT_FOUND; #447 CUSTOMER_ARCHIVED, RESOURCE_ARCHIVED, ARCHIVE_RESTORATION_CONFLICT; #449 INVOICE_VIEW_LINK_NOT_FOUND, PAYMENT_RETRY_UNAVAILABLE, PAYMENT_RETRY_STALE; #450 EVENT_NOT_FOUND이고 각 층 openapi.yaml을 다시 만든다. CUSTOMER_HAS_PRICING은 정점에서도 쓰이지 않는다. workspace-actions.ts는 80a3ef1(#444), 4ace811(#445), 614d3bc(#446), 2ec5d85(#447), 8bb17c3(#450), e5fca11(#451)이 false 자리를 실제 조건으로 바꿨으므로 #442에서 줄을 지우면 이 층들에 반영 충돌이 나고, 각 층이 자기 operation 조건 줄을 다시 넣어야 한다. |
| G-040 | #442 | #443부터 #451까지 frontend/README.md는 #442 문구라 그대로 올라간다. #449 7ce9d36이 설정 절에 METERENGINE_PUBLIC_ORIGIN을, #452 29a78d0이 실행, 설정, 백엔드 연동 절을 다시 쓰므로 충돌하면 그 절은 위층 문구를 따른다. 화면 표에서 이 커밋이 더한 줄 가운데 정점에 없는 줄은 정점 표 형식으로 남긴다. |
| G-041 | #442 | 모으기로 하면 정점 0fe7d2a의 사용처는 git grep '"/v1/\\|`/v1/' 0fe7d2a -- frontend/src ':!frontend/src/lib/api'로 31개 파일이 나오고, 그중 sessions/current는 account/consents/actions.ts:15, organization-invitations/[token]/actions.ts:58, organization-invitations/[token]/page.tsx:24, session-actions.ts:15,27, lib/auth/session.ts:20이다. #444 이후 층의 workspace-actions.ts(35개), exports/[resource]/route.ts(7개), payments/[id]/page.tsx(5개) 등 다른 사람 층 파일도 걸린다. README만 고치기로 하면 #442의 161행을 고치고 #452는 29a78d0에서 이미 지웠으니 위층 merge 때 충돌만 맞추면 된다. |
| G-054 | #442 | 정점 0fe7d2a에서도 DevStateSwitch, TenantScope, WorkspaceOperations, SIDER_WIDTH, CONTENT_MAX_WIDTH, NAV_DRAWER_SIZE는 정의 밖 사용처가 없다(git grep). #443 f671a4f가 DevStateSwitch.tsx를 고쳤으므로 #442에서 지우면 위층으로 merge할 때 수정 대 삭제 충돌이 난다. 정점 frontend/README.md 117행의 개발 모드 스위치 설명과 lib/api 7개 파일(billable-metric-prices, billable-metrics, billing, customers, events, invoices, usage)의 오류 문구도 함께 맞춘다. HEADER_HEIGHT(64)와 정점 globals.css 117행 grid-template-rows 68px, 225행 min-height 68px의 불일치는 #443에서 맞춘다. |
| G-057 | #442 | 상속 안 함으로 맞추면 #442에서는 workspace-href.ts:11을 inheritFilters !== true일 때 멈추게 하고 NavMenu.tsx:23을 <Link inheritFilters href={item.href}>로 바꾼다(그 밖의 #442 호출부는 organization_id만 담은 source라 결과가 같다). 위층에서는 NavMenu.tsx:24(0fe7d2a)를 같은 꼴로, ConsoleShell.tsx:57 WORKFLOW 링크(/usage, /pricing, /billing, /payments)에 inheritFilters: true를 넣고 :49의 중복 { inheritFilters: false }를 빼며, workspace-href.test.mjs:54-59(#453 23b1844)에 inheritFilters: true를 명시해야 한다. LinkButton.tsx(#443 f671a4f)는 이미 기본값 false라 동작은 맞고 ScopedLink 로직 복사본 정리는 #443 몫이다. |
| G-059 | #442 | 정점에서 workspace-href.ts의 같은 계산은 e5fca11(#451)로 17행으로 밀렸을 뿐 같다. 같은 sv-SE 월 계산 사본이 #446(614d3bc)의 frontend/src/app/(console)/pricing/page.tsx:20, frontend/src/components/billable-metrics/BillableMetricsSection.tsx:30에도 있고, 정점 payments/[id]/page.tsx는 72행 formatKrw(10자리)와 115행 WorkspaceTable isMoney(12자리)를 한 화면에 함께 쓴다. #442에서 WorkspaceView를 formatKrw, formatDecimal로 바꾸면 위층 isMoney 열은 따로 고칠 필요 없이 맞춰지고, #446 사본은 그 층에서 formatKstMonth로 바꾼다. |
| G-068 | #442 | #443부터 #451까지 frontend/README.md는 #442 문구라 그대로 올라간다. #449 7ce9d36이 설정 절에 METERENGINE_PUBLIC_ORIGIN을, #452 29a78d0이 실행, 설정, 백엔드 연동 절을 다시 쓰므로 충돌하면 그 절은 위층 문구를 따른다. 화면 표에서 이 커밋이 더한 줄 가운데 정점에 없는 줄은 정점 표 형식으로 남긴다. |
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
| C445-003 | #445 | #449(c9a86c4)와 #453(23b1844)에서 같은 규칙을 맞춘다. 정점 PaymentAttemptService.create의 isUnconfirmable 호출(150행)과 PaymentRetryEligibilityService(106행)도 최신 시도가 아닌 NOT_FOUND에는 7일 창을 걸지 않게 한다. #449는 T-yang 층이다. |
| C445-004 | #445 | #446부터 #449까지는 54bb1d5의 false 값을 그대로 물려받고, origin/feat/event-ingestion은 이미 listEvents를 sort: true, q: true, range: true로 다시 켜 두었으므로 위층에서 할 일이 없다. |
| C445-006 | #445 | C445-007(시험 412행 isIn(409, 500))은 #449 feat/customer-invoices에서 좁힌다. #449 head 2fe98d1과 정점 0fe7d2a의 PaymentAttemptIntegrationTest.java:412가 같고, 그 위에서는 23b1844(#453)가 findForUpdate를 lockInvoice로 바꿨을 뿐 잠금 순서는 같다. |
| C445-009 | #445 | #445 단독 머지 구간에만 남는다. 정점에서도 create의 예외 경로 정리는 없지만 organization 행 잠금으로 PK 경합이 사라져 이 지적의 실패 경로는 막힌다. 정점 시험은 backend/src/test/java/com/meterengine/payment/PaymentArchiveConcurrencyTest.java 87-122행이다. |
| C445-010 | #445 | 정점 0fe7d2a 컨트롤러 36행과 openapi.yaml 6452행에 같은 문장이 있다. 반영 때 openapi.yaml을 다시 만든다. |
| C445-011 | #445 | 반영 때 openapi.yaml을 다시 만든다. |
| C445-012 | #445 | 반영 때 openapi.yaml을 다시 만든다. |
| C445-014 | #445 | 반영 때 23b1844의 같은 두 파일과 겹친다. 내용은 동의 판 기대값 한 줄만 다르고, 위층도 X-031 반영 뒤 2026-10-28이 맞다. /tmp 스크린샷 경로(G-190)는 이 항목 범위 밖이라 두었다. |
| C445-017 | #445 | 정점에서는 f58e1b5(#453)가 같은 꼴의 skippedCount 단언을 484행에 더했다. 반영 때 그 단언도 assertSkipped로 바꾼다. |
| D-149 | #445 | #451 e5fca11의 인보이스 목록 수납 열(미납, 납부 완료)도 이 모듈 이름으로 맞춘다(문인호 님 층). |
| D-163 | #445 | 위층 c9a86c4(#449), e5fca11(#451)이 같은 파일을 바꿨다. 충돌하면 두 부제 줄만 같은 식으로 맞춘다. X-029 (3)의 EventDetailDrawer 라벨은 #451 층에서 고친다. |
| D-217 | #445 | e92f6a1이 토스 키 저장 링크를 결제 문구로 맞춘 시험을 이 커밋이 새 문구로 바꿨다. 반영 때 workspace-operation-results.ts와 그 시험을 이 커밋 쪽으로 맞춘다. |
| G-097 | #445 | #447 2ec5d85와 #453 23b1844가 findUsable과 재시도 후보 조회에 보관과 환경 조건을 더했다. 반영 때 도입사 활성 조건이 남았는지 본다. |
| G-098 | #445 | #447 2ec5d85가 더한 토스 키 보관 DELETE(정점 TossPaymentsCredentialController 117행)도 @AdminAccess라 그 층에서 같은 세션 전용으로 맞출지 본다(T-yang 층). 정점 실화면 명세는 이 PUT을 세션으로 부른다. |
| G-102 | #445 | #447 2ec5d85와 #453 23b1844가 replace에 보관 잠금과 환경 조건을 더했다. 반영 때 감사 기록 줄이 남았는지 본다. |
| X-009 | #445 | #451 e5fca11의 인보이스 목록 수납 열(미납, 납부 완료)도 이 모듈 이름으로 맞춘다(문인호 님 층). |
| X-029 | #445 | 위층 c9a86c4(#449), e5fca11(#451)이 같은 파일을 바꿨다. 충돌하면 두 부제 줄만 같은 식으로 맞춘다. X-029 (3)의 EventDetailDrawer 라벨은 #451 층에서 고친다. |
| X-031 | #445 | 동의 판이 2026-10-28로 바뀌어 #453 23b1844의 payment-registration.contract.spec.ts 38행이 기대하는 consent_document_version 2026-10-26을 반영 뒤 2026-10-28로 맞춘다(T-yang #453 차례). |
| X-049 | #445 | #453 payment-registration.contract.spec.ts 82행 뒤에 결과 미확인 제목 단언을 더할 수 있다(T-yang #453 차례). |
| X-075 | #445 | 위층(#446~#455)으로 옮길 때 9dff033의 WorkspaceHeader.tsx 한 줄 변경이 들어가면 정점 payments/[id]/page.tsx:99-100의 '고객으로 이동', '내역 새로 조회'도 한 묶음이 된다. 페이지 쪽에 따로 div.workspace-actions를 더하지 않는다. integrations/[id], members 화면은 이미 자식으로 div.workspace-actions를 넘겨 겹으로 감싸지지만 배치는 같다. |
| C445-005 | #445 | 위층이 2인자 create를 직접 부르므로 그 오버로드를 남겼다. 반영 때 findMode 뒤의 검사를 남기고 openapi.yaml을 다시 만든다. |
| Ck2-016 | #445 | 위층이 @Scheduled 메서드를 더하면 새 시험이 풀 크기를 그 수 이상으로 올리라고 실패한다. 정점의 예약 작업 수를 반영 때 확인한다. |
| Ck2-017 | #445 | 어느 안을 고르든 #453 23b1844가 PaymentReconciliationService.reconcile을 환경별로 나눴으므로 재실행이나 따라잡기 경로를 #453 head의 환경 구분 코드에도 맞춰야 한다. 슬롯을 cron 예정 시각에서 계산하는 부분은 PaymentJobScheduler.java 한 파일이며 위층에서 이 파일을 바꾼 커밋은 없다. |
| D-042 | #445 | #447 2ec5d85부터 카드와 토스 키 응답에 archived_at이 생기고 결제가 보관 카드를 거절하므로 #447에서 card-state가 보관 행을 없음으로 보게 맞춘다(T-yang 층). #449 c9a86c4와 #451 e5fca11이 같은 파일의 Promise.all과 머리를 바꿨으니 반영 때 손으로 맞춘다. |
| D-085 | #445 | #447 2ec5d85부터 카드와 토스 키 응답에 archived_at이 생기고 결제가 보관 카드를 거절하므로 #447에서 card-state가 보관 행을 없음으로 보게 맞춘다(T-yang 층). #449 c9a86c4와 #451 e5fca11이 같은 파일의 Promise.all과 머리를 바꿨으니 반영 때 손으로 맞춘다. |
| D-148 | #445 | #447 2ec5d85부터 카드와 토스 키 응답에 archived_at이 생기고 결제가 보관 카드를 거절하므로 #447에서 card-state가 보관 행을 없음으로 보게 맞춘다(T-yang 층). #449 c9a86c4와 #451 e5fca11이 같은 파일의 Promise.all과 머리를 바꿨으니 반영 때 손으로 맞춘다. |
| D-181 | #445 | #447 2ec5d85부터 카드와 토스 키 응답에 archived_at이 생기고 결제가 보관 카드를 거절하므로 #447에서 card-state가 보관 행을 없음으로 보게 맞춘다(T-yang 층). #449 c9a86c4와 #451 e5fca11이 같은 파일의 Promise.all과 머리를 바꿨으니 반영 때 손으로 맞춘다. |
| D-212 | #445 | #447 2ec5d85부터 카드와 토스 키 응답에 archived_at이 생기고 결제가 보관 카드를 거절하므로 #447에서 card-state가 보관 행을 없음으로 보게 맞춘다(T-yang 층). #449 c9a86c4와 #451 e5fca11이 같은 파일의 Promise.all과 머리를 바꿨으니 반영 때 손으로 맞춘다. |
| X-006 | #445 | #447 2ec5d85부터 카드와 토스 키 응답에 archived_at이 생기고 결제가 보관 카드를 거절하므로 #447에서 card-state가 보관 행을 없음으로 보게 맞춘다(T-yang 층). #449 c9a86c4와 #451 e5fca11이 같은 파일의 Promise.all과 머리를 바꿨으니 반영 때 손으로 맞춘다. |
| X-086 | #445 | 위층에서 메일 발송을 붙이면 이 문구를 다시 바꾼다. |
| C447-002 | #447 | 정점에서는 23b1844가 두 엔티티의 다른 부분을 바꿔 stops 줄 위치만 다르다(0fe7d2a PriceRate 81-86행, PricePlan 68-73행). 요금제 조회가 청구와 어긋나는 문제는 Nk3-006(결정 대기)이다. |
| C447-003 | #447 | #453 23b1844가 PaymentAttemptConfirmationService의 historical 호출 인자를 바꿨다. 이 층에서 더한 시험은 credential_updated_at을 넣지 않으므로 #453에서는 requestedAt 기준으로 돌아가 그대로 맞아야 한다. 위로 merge할 때 PaymentAttemptConfirmationServiceTest와 TossPaymentsWebhookIntegrationTest 충돌을 풀고 #453 head에서 두 클래스를 다시 돌린다. 시험이 TossPaymentsCredentialService.update와 webhook 수신을 쓰므로 #453의 환경 문맥(BusinessEnvironmentContext)이 필요하면 다른 시험처럼 맞춘다. PaymentEnvironmentIntegrationTest 276, 319행과 겹쳐도 지우지 않는다. |
| C447-004 | #447 | #447 시험은 위층으로 merge되어 그대로 통과해야 한다. #453 f58e1b5가 PaymentRetryServiceTest의 빌링키 없음 시험 바로 뒤에 보관 토스 키 시험을 넣었으니 충돌 나면 둘 다 남긴다(이 층에서 그 시험 이름이 '빌링키가_없으면_새_시도를_만들지_않는다'로 바뀌었다). 재시도 시험은 G-105의 후보 조회 JOIN을 전제로 확인 조회 중에 카드를 보관하므로, #453 G-103으로 재시도 키 조회가 historical로 바뀌어도 findPaymentByOrderId가 시도의 시크릿 키로 불리는지 #453 head에서 확인한다. 관리자 결제 생성 시험의 기대값은 #453(정점 474행 active())에서도 같다. |
| C447-005 | #447 | 위층 #448~#455에서 VersionedPricingIntegrationTest를 바꾼 23b1844, 07d3fb9, fb91179가 있어 층을 올릴 때 이 파일에서 충돌이 날 수 있다. 정점에서는 가드 줄이 PriceRateService 140, 247행, PricePlanService 113, 199행으로 옮겨졌고 #453의 환경 분리로 서비스 시그니처가 달라졌을 수 있으니 위층 반영 때 시험의 호출 인자를 그 층 시그니처에 맞춘다. |
| C447-006 | #447 | 정점 0fe7d2a의 같은 시험 162-163행도 같은 형태다. 8bb17c3(#450)이 이 파일의 다른 부분을 바꿨으니 반영 때 충돌만 본다. 정점 restore는 BusinessEnvironmentContext가 없으면 findById를 쓰므로 같은 단언이 그대로 의미를 가진다. |
| C447-007 | #447 | 위층에서 이 시험 파일을 바꾼 커밋이 없어 merge로 그대로 올라간다. #453 23b1844 뒤 delete는 환경 조건을 더하지만 시험은 기본 환경 키로 만든 행을 쓰므로 단언은 그대로 맞아야 한다. |
| C447-008 | #447 | #453 23b1844가 delete에 BusinessEnvironmentContext.matches 필터(정점 0fe7d2a 87행)를 더했으므로 위층 merge 때 그 필터 줄을 살리고, 시크릿 키 조회와 afterCommit 폐기 등록은 archive()와 감사 기록 뒤에 그대로 둔다. #453에서 TossPaymentsCredentialService의 키 선택(결제별 키 버전, 환경)이 바뀌면 폐기에 쓰는 active(organizationId, mode) 조회도 그 규칙에 맞춘다. 보관 뒤 고객이 카드를 다시 등록하면 BillingKeyService.replace가 보관된(이미 폐기한) 이전 키를 다시 폐기하려 해 거절 warn 로그가 한 번 남는다(동작에는 영향 없음, BillingKeyService는 담당 밖이라 그대로 뒀다). |
| C447-009 | #447 | #448 bc7e538, #449 2fe98d1, #450 7b1d1e5의 같은 네 화면에 같은 호출이 남아 있다(invoices 64,69, events 63,68, usage 74,79, billing 118,123). #447 수정을 위층으로 옮길 때 같은 줄을 status all로 맞춘다. #451 e5fca11은 이 호출을 customerOptions(searchOptions)와 listEventTypes로 바꾸고 (보관됨) 표시도 직접 붙이므로, #451로 옮길 때 충돌은 #451 쪽을 택한다. e2e/filter-options.test.mjs는 #451부터 선택지 조회가 readListQuery를 쓰지 않아 맞지 않으므로 #451 반영 때 빼거나 searchOptions 기준으로 바꾼다. archive-filters.real.spec.ts 새 단계는 #451에서 이벤트 화면 필터가 EventFilters, 인보이스 화면이 ResourceSearchControls로 바뀌므로 선택자를 그 화면에 맞춘다. |
| C447-010 | #447 | 정점 0fe7d2a에서 같은 검사가 328-343행에 그대로 있다. 위층 23b1844(#453), 8bb17c3(#450)이 이 파일의 다른 부분을 바꿨으므로 위층으로 올릴 때 같은 검사 본문에 같은 변경이 들어갔는지 확인한다. |
| G-105 | #447 | #453 23b1844가 두 billing_key 조인의 bk.mode 줄 바로 아래에 bk.environment_id IS NOT DISTINCT FROM 조건을 더하므로, 위층 merge 때 두 조인에 environment_id 줄과 archived_at IS NULL 줄을 모두 남기고 findRetryCandidates의 LEFT JOIN을 JOIN으로 바꾼 것이 유지되는지 본다(정점 0fe7d2a 79-83, 162-166행). InvoicePaymentService의 next_retry_at 조건(failed이고 getBillingKeyAuthenticatedAt()이 null이 아닐 때만 계산)도 #453 판 81행 근처 같은 자리에 맞춘다. #453 이후 두 시험의 카드 픽스처가 환경 열을 맞춰야 조인에 걸리는지 #453 head에서 다시 돌려 본다. InvoicePaymentResponse.next_retry_at 설명에 '지금 쓸 카드가 없으면 null'이 없다(DTO와 openapi.yaml은 담당 밖). |
| G-147 | #447 | #451 e5fca11이 read-contract.test.ts에 더한 '보관 상태 조회는 지원 API에만 보내고...' 시험의 bodies와 호출 목록에 /v1/webhook-endpoints와 listWebhookEndpoints({ status: ... })를 넣어 맞춘다. 정점 read-contract.test.ts 26행의 인자 없는 listWebhookEndpoints() 호출은 기본값 덕분에 바꿀 필요가 없다. 위층 merge 시 meter-engine-client.ts, types.ts를 바꾼 23b1844, e5fca11, 8bb17c3, c9a86c4와 충돌만 확인한다. |
| Nk3-004 | #447 | 위층으로 올릴 때 ErrorCode.java는 8bb17c3(#450)이, TossPaymentsCredentialService.java와 TossPaymentsCredentialController.java는 23b1844(#453)가 바꿨으니 404 묶음 상수, delete의 orElseThrow 줄(정점에서는 requireMode 뒤), DeleteMapping 위 @ApiResponses에서 충돌을 손으로 풀고 층마다 openapi.yaml을 다시 만든다. 다른 곳의 TOSS_PAYMENTS_CREDENTIAL_MISSING(ConflictException)은 그대로 둔다. |
| Nk3-005 | #447 | 위층은 같은 컨트롤러를 여러 번 바꿨으므로 위층으로 옮길 때 @ApiResponses 블록 충돌을 맞추고 층마다 openapi.yaml을 다시 만든다. #450의 배치 수집(항목별 rejected의 customer_archived)과 POST /v1/customers 재등록 409는 #450 범위라 여기서 고치지 않았다. 아래층에서 단가와 요금제 등록의 history_immutable 409를 따로 문서화하면 이 층의 새 409 블록과 합친다. |
| Nk3-006 | #447 | 정점의 list에는 BusinessEnvironmentContext로 listScoped를 쓰는 갈래가 더해졌다(0fe7d2a PricePlanService.java 56-59행). 정한 선택 규칙은 두 출처에 모두 걸어야 한다. 정점 activePlan은 0fe7d2a PricingWorkspace.tsx 36행, demo verify는 0fe7d2a verify_cmd.py 197-213행이다. |
| U19-B1 | #447 | 위층은 이 시험을 그대로 이어받는다(0fe7d2a 135-155행). C447-002의 stops 변경은 기한 없는 요금제에는 결과가 같아 이 단언들에 영향을 주지 않는다. |
| C447-001 | #447 | 위층에서 바꿀 것은 없다. 고객 상세 화면(customers/[id]/page.tsx)과 workspace-actions.ts의 updateCustomer case는 #451 e5fca11이 더하고, CreateCustomerRequest의 external_id와 같은 external_id 재요청 시 기존 고객 반환은 #450 8bb17c3이 더한다. 이 층의 createCustomer case(03574b9)는 #451이 더하는 줄과 같다. |
| Ck3-447-02 | #447 | 정점에서는 23b1844가 같은 파일에 환경 분리를 더했지만, 사본의 본문은 같다(0fe7d2a ChargeService.java 61-84행, BillableMetricPriceService.java 68-80행). 모으면 #453의 listScoped 갈래도 같은 부품을 거치게 맞춘다. |
| Ck3-447-03 | #447 | #453 23b1844가 같은 저장소의 methods(), credentials()에 environment_id 조건을 더하고 billing_key_history 삽입에 credential_updated_at, credentialVersions()를 더했다. #447에서 고치면 #453으로 넘길 때 환경 조건을 유지해야 하고, 정점의 호출부 PaymentMethodService.java:76, TossPaymentsCredentialService.java:80 @ 0fe7d2a도 같은 매핑과 정렬을 따른다. 반환 형을 바꾸면 정점의 PaymentArchiveConcurrencyTest.java:76,116이 저장소 methods(), credentials()를 직접 부르므로 같이 맞춘다. |
| Ck3-447-05 | #447 | 정점 0fe7d2a에서도 미터는 inactive_from, 단가와 요금제는 archived_from이다. 이름을 바꾸기로 하면 위층 사용처를 함께 맞춘다: backend/src/main/java/com/meterengine/metric/dto/BillableMetricResponse.java:20,36,50,68, backend/src/main/java/com/meterengine/query/repository/WorkspaceQueryRepository.java:172,342(열 이름을 그대로 두면 rs.getString만 유지), BillableMetricService.java:217 감사 키, frontend/src/lib/api/billable-metrics.ts:20,30, frontend/src/app/(console)/billable-metrics/state.ts:17, frontend/src/components/billable-metrics/BillableMetricsSection.tsx:45, BillableMetricsTable.tsx:124, 시험 BusinessArchiveIntegrationTest.java:131, BillableMetricServiceTest.java:305, 그리고 backend/openapi.yaml 재생성. |
| Ck3-447-06 | #447 | #451 e5fca11이 findByOrganizationIdAndIdIn을 environment_id IS NULL 조건의 @Query로 바꿔 두었습니다(0fe7d2a CustomerRepository.java:50-53). 그래서 위층 merge에서 충돌이 나고, 위층에서도 그 선언을 같이 지워야 합니다. existsWithPricing은 0fe7d2a CustomerRepository.java:90에 그대로 있고 사용처가 없습니다. 두 서비스의 4인자 list는 0fe7d2a PricePlanService.java:51, PriceRateService.java:77에 같은 모양으로 있어 annotation만 맞추면 됩니다. |
| Ck3-447-07 | #447 | 앞당기는 안이면 #450 8bb17c3의 CustomerRegistrationService, CustomerRegistrationRepository(ON CONFLICT 멱등 등록)와 CreateCustomerRequest가 충돌하므로 #450에서 그 등록 경로로 합친다(#450은 T-yang 담당이 아니다). V42를 옮기거나 지우는 안은 #453 V45__create_organization_environments.sql:37-39가 customer_organization_external_id_unique를 지우므로 쓸 수 없다. 정점 사용처: CustomerController, CustomerRegistrationService, CustomerRegistrationRepository, CustomerRepository, query 패키지(WorkspaceQueryRepository, WorkspaceQueryService, SearchParameters), event 저장소(EventRepository, EventIssueRepository), frontend customers 화면과 workspace-actions.ts, ResourceSearchControls.tsx, lib/api/customers.ts, demo/bridge/state.py, demo/core/api_client.py, demo/sdk. |
| Ck3-447-09 | #447 | 모으기로 하면 정점에서는 credentialUpdatedAt ?: requestedAt(카드 등록은 createdAt) 선택까지 그 메서드로 옮기고, git grep -n 'tossPaymentsCredentialService.historical(' 0fe7d2a가 보이는 PaymentAttemptConfirmationService 118,192, TossPaymentsWebhookService 82, PaymentCancellationService 288, PaymentAttemptService 143,192-196, PaymentRetryService 115-122, PaymentRetryEligibilityService 78,98, PaymentMethodRegistrationService 147,162를 맞춘다. #453 G-103 diff가 PaymentRetryService, PaymentAttemptService, PaymentMethodRegistrationService의 같은 호출부를 바꾸므로 그 뒤에 한다. |
| Ck3-447-10 | #447 | 위층에서 같은 핸들러를 바꾼 커밋이 있다: #450 8bb17c3(listCustomers 서명), #451 e5fca11(고객, 미터 목록에 기간 필터), #453 23b1844(결제와 미터 컨트롤러 환경 주석). 위층으로 올릴 때 status 인자 줄이 충돌하면 새 @Parameter를 살리고 층마다 openapi.yaml을 다시 만든다. 위층에서 새로 생긴 같은 꼴의 status가 있으면 OpenApiDocumentTest의 operationId 목록에 더한다. |
| Ck3-447-11 | #447 | 정점 0fe7d2a README의 255, 256, 259, 275행이 같은 글이고 5371d1e..0fe7d2a의 README 커밋은 이 줄들을 바꾸지 않아 #447 수정을 위층으로 merge하면 그대로 따라간다. 위층이 더한 idempotency, organization 의존 설명은 이 항목 밖이다. |
| Ck3-447-12 | #447 | 정점 TossPaymentsCredentialService.historical(90-95행)도 같은 줄을 고친다. #453 G-103 diff는 historical()에 archived_at 조건을 넣지 않기로 했으니 이 대체와 겹치지 않는다. |
| Ck3-447-13 | #447 | 정점 0fe7d2a의 PaymentArchiveRepository.java 28행도 같은 FOR UPDATE다. 23b1844(#453)는 이 파일의 다른 줄만 바꿨으니 merge로 그대로 올라간다. #453부터 EventRepository.insertIfAbsent가 BusinessEnvironmentContext.environmentId()를 쓰므로, 더한 시험의 event 삽입이 환경 문맥 없이 실패하지 않는지 #453 head에서 확인하고 필요하면 다른 시험처럼 환경을 정해 준다. 카드 보관 경합 시험은 #453의 PaymentMethodService.delete 환경 필터(BusinessEnvironmentContext.matches) 때문에 환경 문맥이 필요할 수 있다. #445 C445-009를 고칠 때 replace에 organization 행 잠금을 넣는다면 FOR NO KEY UPDATE를 쓴다. 같은 패턴인 WebhookEndpointRepository.java 38행(#444)은 담당 밖이라 건드리지 않았다. |
| Ck3-447-14 | #447 | 위층 #451 e5fca11이 update(organizationId, code, request, expectedVersion)와 VersionPrecondition.requireMatch를, #453 23b1844가 BillableMetricId의 환경 id를 더했다. 위로 올릴 때 #451부터는 VersionPrecondition.requireMatch와 보관 검사를 refresh 뒤 잠금 안에 두고, #453부터는 BusinessEnvironmentContext.environmentId()로 만든 id의 사전 조회를 잠금 앞에 둔다. BillableMetricServiceTest 생성자 호출에 EntityManager mock을 같이 넣는다. 도입사가 없을 때 update는 이제 ORGANIZATION_NOT_FOUND 대신 검증 오류나 BILLABLE_METRIC_NOT_FOUND를 내고(인증된 요청에서는 도입사가 늘 있다), 보관이 진행 중인 미터를 이벤트가 있는 상태로 바꾸려 하면 잠금을 기다리지 않고 RESOURCE_ARCHIVED 대신 BILLABLE_METRIC_HAS_EVENTS(같은 409)를 낼 수 있다. event (organization_id, type) 색인은 새 V__가 필요해 넣지 않았다. |
| D-223 | #447 | 정점에서 같은 줄은 ErrorCode.java:59-60과 actions.ts:128-137이다. 위층 커밋은 이 줄을 바꾸지 않아 그대로 올라간다. 정점 frontend/e2e/aggregation.real.spec.ts:214는 code만 확인하므로 문구를 바꿔도 깨지지 않는다. |
| D-241 | #447 | 위층 23b1844(#453), e5fca11(#451)이 PricingWorkspace.tsx의 다른 줄을 바꿔 정점에서는 같은 두 줄이 87, 96행에 있다. 위층 merge 때 줄 위치만 달라지므로 같은 치환을 그대로 옮긴다. BillableMetricsTable.tsx는 위층 변경이 없다. |
| D-245 | #447 | 0fe7d2a에서 CustomersSection, CustomersScreen, CustomersFrame, CustomersLoading, CustomersTable, CustomerFormDialog, CustomerDeleteDialog, createCustomerAction, updateCustomerAction, deleteCustomerAction을 git grep하면 components/customers 안과 customers/actions.ts 정의 말고 사용처가 없다. #447에서 지우면 #451 e5fca11이 바꾼 CustomerDeleteDialog, CustomerFormDialog, CustomersSection, CustomersTable, customers/actions.ts와 위층 merge에서 수정, 삭제 충돌이 나므로 위층에서는 삭제 쪽을 택한다. 가격 쪽 BillableMetricPricesSection, Screen, Frame, Loading, Table, PriceRateFormDialog와 billable-metric-prices/actions.ts, state.ts는 #451(e5fca11 redirect)부터 참조가 0이라 #451에서 지운다. lib/api/customers.ts의 create, update, deleteCustomer 정리는 같은 묶음 G-183과 겹친다. |
| G-183 | #447 | 0fe7d2a에서 git grep으로 components/customers와 customers/actions를 import하는 곳은 그 묶음 밖에 없다. 다만 #451 커밋 e5fca11이 CustomerDeleteDialog, CustomerFormDialog, CustomersSection, CustomersTable, customers/actions.ts, lib/api/customers.ts를 고쳤으므로 #447에서 지우면 #448부터 위로 옮길 때 #451에서 수정/삭제 충돌이 나고, #451에서 같은 파일을 지워 풀어야 한다. billable-metric-prices의 BillableMetricPricesSection, Loading, actions.ts와 lib/api/billable-metric-prices.ts unit_price 타입은 #451(17cc788) 페이지 리다이렉트 뒤에 쓰이지 않게 되므로 #451 담당 몫이다. |
| NT-yang-001 | #442 | #443 f671a4f의 globals.css 52행 `.auth-intro h2` 규칙이 이 문구를 꾸민다. 반영 때 선택자를 `.auth-intro .auth-intro-title`로 바꾼다(#443은 문인호 님 층). 바꾸지 않으면 문구가 `.auth-intro p` 모양으로 작게 보인다. |

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
| C445-006 | #445 | fixed-above #449 c9a86c4 |
| C445-009 | #445 | fixed-above #447 2ec5d85 |
| C445-018 | #445 | fixed-above #451 e5fca11 |
| C447-001 | #447 | fixed-above #451 e5fca11 |

## 마지막 실화면 점검

| 층 | sha | 통과한 명세/전체 | 화면 결과 |
| --- | --- | --- | --- |
| #442 | 643ae66 | 1/1 | 9개 라우트 3개 폭: 콘솔 오류 0, 가로 넘침 없음, axe 위반 늘지 않음 |
| #445 | 22dfb35 | 1/1 | 7개 라우트 2개 폭과 토스 키 저장 창: 콘솔 오류 0, 가로 넘침 없음, axe 위반 늘지 않음 |

## 사용량

사용량 도구가 없어 사람이 /usage로 지켜본다(주간 상한 70%). 층별 사용량은 기록하지 못한다.

## 메모

- 첫 세션은 시작 점검에서 Docker가 꺼져 멈췄다. 두 번째 세션(2026-10-28)에서 점검을 다시 돌려 통과했다.
- 2026-10-28 PR #442 댓글 요청: #442에 올린 커밋 19개(ae1dc84부터 9679307)의 Co-Authored-By 표기를 빼고 다시 push하거나 후속 브랜치로 옮기는 일은 force push나 스택 정리가 필요해 이 세션이 하지 않는다(common-brief 3절, 12절). 박성종 님과 정한 뒤 넘기기 자료로 받는다. 9679307 뒤로 올리는 커밋에는 이 표기를 붙이지 않는다.
- 2026-10-28 양성지 님 결정: #442 커밋 19개(ae1dc84부터 9679307)는 다시 쓰지 않고, PR을 squash 머지할 때 머지 창의 커밋 메시지에서 Co-authored-by 줄을 지운다. #442에만 있는 8개(65fc925부터 9679307)도 그대로 위층에 반영해도 된다. 이 세션은 9679307 뒤로 올리는 커밋에 그 표기를 붙이지 않는다.
- 2026-10-28 사람이 붙여 넣기 글을 다시 보냈다. 같은 세션이 계속 돌고 있었고, 시작 점검과 넘기기 자료 확인을 다시 돌려 통과한 뒤 이어 갔다.
- 2026-10-28 21:47 KST: push 방식을 바꿨다. 박성종 님 요청(PR #442 댓글)을 양성지 님이 승인해 #445부터 fix/s456-<PR>-t-yang 후속 브랜치에 push하고 층마다 PR을 하나 연다(merge하지 않음). #442 feat 브랜치에 올린 커밋은 그대로 둔다. check-report.mjs가 feat 브랜치만 알아 fix 브랜치 배치로 나는 오류는 걸러 내고, 그 층 항목의 branch에는 fix 브랜치를 적는다.
- G-098(2bc72d8)이 토스 키 저장과 결제 모드 변경을 세션 전용으로 올려, C445-002(ad5872e)의 API 키 모드 검사는 HTTP로는 닿지 않는 이중 방어로 남는다.
- fix/s456-445-t-yang의 merge 커밋 3326f31은 셸 변수 문제로 메시지가 'chore: origin/ 변경을 받는다'로 비었다(origin/feat/billing-payments를 받은 merge). 이미 push해 고쳐 쓰지 않았고 뒤 merge부터 고쳤다.
- origin/feat/billing-payments에 위층 반영으로 V28(create_webhook_tables) 내용이 바뀌어 들어와, 실화면 환경 DB를 다시 만들었다. 이 세션이 바꾼 마이그레이션이 아니다.
- 2026-10-29 #447 수정 중 멈춤: frontend/e2e/archive-filters.real.spec.ts가 로컬 2734417과 바꾸지 않은 head 5371d1e 모두에서 실패한다(evidence/stop-447/specs.txt). common-brief 10절 표에 없는 실패라 12절대로 stopped로 멈췄다. #447 수정 커밋은 push하지 않았다. 박성종 님과 정한 뒤 넘기기 자료로 받는다.
- #445 fix PR(#467)과 층 PR(#445)에서 마지막 두 push(c76428e, 22dfb35)에 달린 리뷰 댓글은 멈출 때까지 없었다. 박성종 님의 #467 댓글(feat/billing-payments ad9f7fa merge 요청)은 22dfb35에 들어 있다.
- 2026-10-29 재개: archive-filters.real.spec.ts가 넘기기 자료 10절 표에 들어가(b334d36) 12절 멈춤이 풀렸다. #447 수정 21건을 fix/s456-447-t-yang에 모았다. origin/feat/resource-archive(5371d1e에서 6d91d41로 움직임)와의 동기화 merge는 backend/openapi.yaml, PaymentMethodRegistrationController, PricePlanController, PriceRateController, PaymentMethodRegistrationIntegrationTest, PaymentAttemptRepositoryTest, PaymentRetryServiceTest에서 충돌한다. 9절대로 충돌은 풀지 않았고, 박성종 님이 fix 브랜치로 push만 하면 직접 반영한다고 해 merge 없이 5371d1e 기반으로 push하며 PR은 열지 않는다.
- 2026-10-29 다시 멈춤: 84bb5e7의 demo 검사가 corepack pnpm -r build 단계에서 ERR_PNPM_RECURSIVE_RUN_NO_SCRIPT로 실패했고, 바꾸지 않은 head 5371d1e에서도 같다. 10절 표에 없어 12절대로 멈추고 fix 브랜치는 push하지 않았다. 박성종 님이 바로 반영할 수 있게 21커밋 patch를 evidence/stop-447/patches에 둔다(5371d1e 기준 git am).
- 2026-10-29 박성종 님이 Slack에서 patch 대신 PR로 열어 주면 충돌을 풀어 스택에 merge하겠다고 해, demo build 단계만 실패한(바꾸지 않은 head와 같은) 84bb5e7을 fix/s456-447-t-yang로 push하고 PR #470을 열었다. 동기화 merge와 충돌 해결은 하지 않았다.
- 2026-10-29 15:00 KST: 스택 #442~#455가 2026-10-29 13:52 main(af15d7f)으로 squash 머지됐고 층 feat 브랜치와 fix/s456-445-t-yang, fix/s456-447-t-yang는 origin에서 지워졌다(#447 머지 head 5104b8a에 #470, 곧 84bb5e7의 21건이 들어 있다). state.json branches의 지워진 브랜치 8개(feat/account-permissions 643ae66, feat/billing-payments 54bb1d5, feat/resource-archive 8babc3d, feat/customer-invoices 2fe98d1, feat/llm-service-demo 2aa2be2, feat/environment-isolation f014e03, fix/s456-445-t-yang 22dfb35, fix/s456-447-t-yang 84bb5e7)를 main af15d7f 한 줄로 정리했다.
- 2026-10-29 15:00 KST: 넘기기 자료 41d92a5를 다시 꺼냈다(handover_sha 98dff66에서 바꿈). findings.json id는 늘거나 줄지 않았다.
- 2026-10-29 15:00 KST: 박성종 님의 머지 뒤 지침이 아직 없어 양성지 님이 정한 임시 방식으로 재개한다. open 82건만 origin/main에서 판정하고, 고치면 origin/main에서 만든 fix/s456-t-yang-<주제> 브랜치에 커밋해 push만 하고 PR은 열지 않는다. 검사는 10절 kind를 main에서 돌리고, 바꾸지 않은 origin/main 기준선에서도 실패하는 검사는 결과가 기준선보다 나빠지지 않으면 통과로 본다(양성지 님 허락). 문인호 님 open 항목이 가리키는 파일은 건드리지 않는다.
- 2026-10-29 15:00 KST: check-report.mjs가 지워진 브랜치의 커밋을 origin/<브랜치>에서 찾아 기존 기록 전부를 오류로 막아, 로컬 사본 .s456-work/tools/check-report-main.mjs를 고쳐 쓴다(커밋하지 않음). 고친 것: main에 머지돼 지워진 브랜치 8개는 항목 커밋을 그 브랜치 pushes의 sha로 보고 push의 origin 대조는 건너뛴다. fix/s456-t-yang-* 주제 브랜치는 origin/main 뒤 커밋을 이 세션 커밋으로 본다. 나머지 검사는 그대로다.
- check-report-main.mjs의 알려진 예외 1건: G-147 커밋 7da6d60을 담은 fix/s456-447-t-yang 84bb5e7의 demo 검사는 corepack pnpm -r build 단계에서 ERR_PNPM_RECURSIVE_RUN_NO_SCRIPT로 실패했고(바꾸지 않은 head 5371d1e도 같다) 박성종 님 요청으로 push했다. 예전 필터(cr.py)가 이 오류를 감추고 있어, 사본은 오류 대신 주의로 출력한다. 기록(checks의 fail)은 바꾸지 않았다.
- 2026-10-29 15:33 KST: 문인호 님이 main의 environment-cycle, mvp 실화면 명세를 고치기로 해(#논의 15:25, 박성종 님 15:26) 두 명세 파일(frontend/e2e/environment-cycle.real.spec.ts, frontend/e2e/mvp.real.spec.ts)을 문인호 님 쪽 파일로 보고 건드리지 않는다. 그 파일을 바꿔야 하는 항목은 문인호 님 PR이 main에 들어온 뒤 본다.
- 2026-10-29 16:10 KST: 박성종 님 머지 뒤 지침(#논의 스택 스레드 15:49)을 양성지 님이 붙여 넣어 반영한다. 주제 브랜치는 push하면 바로 main으로 PR을 연다. 300줄 상한은 적용하지 않고 주제별로만 나눈다. needs-decision은 세션이 근거를 PR 본문에 적고 정해서 고치며, 다른 사람 영역이나 팀 전체에 걸리면 그 사람을 리뷰어로 지정한다. 같은 파일을 건드리면 나중에 머지하는 PR이 main을 받는다. 그래서 문인호 님 쪽 파일도 필요하면 고치되, 문인호 님이 고치기로 한 environment-cycle, mvp 명세 실패(NT-yang-002, 003)는 겹치지 않게 그대로 둔다.
- 2026-10-29 16:10 KST: 연 PR: #472 fix/s456-t-yang-invoice-retry, #473 fix/s456-t-yang-public-invoice, #474 fix/s456-t-yang-lumen, #475 fix/s456-t-yang-demo-bridge-web, #476 fix/s456-t-yang-toss-key, #477 fix/s456-t-yang-audit-env(문인호 님 리뷰어 지정). 리뷰어는 CODEOWNERS가 붙였다.
- 2026-10-29: #478 fix/s456-t-yang-ops-metrics(G-133)를 열고 경보 결정 때문에 박성종 님과 문인호 님을 리뷰어로 지정했다.
- 2026-10-29: #479 fix/s456-t-yang-readme(C452-011, C452-016, G-145, G-175, G-189, L-002)를 열었다.
- 2026-10-29: #480 fix/s456-t-yang-e2e(C449-005, C453-012, C452-014, C452-006, C453-009, C453-011)를 열었다.
- 2026-10-29 16:35 KST: needs-decision 44건을 머지 뒤 지침대로 정했다. 이미 풀린 것 7건(G-039, C442-010, G-007, G-030, G-096, G-160, G-041), 머지로 해소된 것 4건(G-038, G-020, Ck3-447-07, Ck3-447-12)은 already-fixed, 바꾸지 않기로 한 12건(C449-006, G-027, G-067, G-056, G-057, Ck3-447-02, Ck3-447-05, Ck3-447-09, G-009, G-010, G-013, G-017)은 refuted로 두고 reason에 결정과 근거를 적었다. 나머지 21건은 고쳐 PR로 올린다.
- 2026-10-29: #481 fix/s456-t-yang-env-ui(D-204, X-060, X-003, D-014, D-219, X-033, G-044, NT-yang-004)를 열고 문인호 님을 리뷰어로 지정했다. open 항목은 모두 끝 상태가 됐다.
- 2026-10-29: #482 fix/s456-t-yang-backend-cleanup(G-018, G-008, G-011)을 열고 박성종 님과 문인호 님을 리뷰어로 지정했다.
- 2026-10-29: #483 fix/s456-t-yang-demo-web-target(C453-006)를 열었다.
