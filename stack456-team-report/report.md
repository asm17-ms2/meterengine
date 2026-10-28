미완료 209건, 결과: 끝 상태 163/372건 (fixed 111, refuted 2, already-fixed 8(그중 fixed-above 3), needs-decision 36, blocked 6(그중 no-evidence 0))
push: fix/s456-443-t-inho 33번(마지막 b99d2ef), fix/s456-444-t-inho 14번(마지막 c0a9f4a), fix/s456-446-t-inho 9번(마지막 a7a789e), fix/s456-448-t-inho 1번(마지막 1164c20)
정할 것: 38건 (X-012, D-071, G-075, G-085, D-011, D-065, G-072, G-080, C443-002, D-039, D-055, D-244, D-022, G-084, G-078, D-237, Ck2-007, D-045, C444-002, G-140, Ck2-005, Ck2-009, Ck2-010, Ck2-011, Ck2-012, Ck2-015, G-151, Ck3-446-03, Ck3-446-07, Ck3-446-08, Ck3-446-09, G-092, G-095, C448-002, G-169, Ck1-448-04, NT-inho-001, NT-inho-003). 로컬 DB 다시 만들기: fix/s456-444-t-inho V28__create_webhook_tables.sql

상태: running, 갱신 2026-10-29T00:41:02+09:00

## 층별 상태

| 층 | 브랜치 | 전체 | fixed | refuted | already-fixed | needs-decision | blocked | 미완료 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| #443 | fix/s456-443-t-inho | 92 | 75 | 1 | 0 | 16 | 0 | 0 |
| #444 | fix/s456-444-t-inho | 40 | 23 | 0 | 4 | 11 | 1 | 1 |
| #446 | fix/s456-446-t-inho | 50 | 12 | 0 | 3 | 4 | 4 | 27 |
| #448 | feat/request-audit | 24 | 1 | 1 | 1 | 5 | 0 | 16 |
| #450 | feat/event-ingestion | 38 | 0 | 0 | 0 | 0 | 0 | 38 |
| #451 | feat/billing-queries | 96 | 0 | 0 | 0 | 0 | 0 | 96 |
| #454 | feat/auth-rate-limit | 19 | 0 | 0 | 0 | 0 | 0 | 19 |
| #455 | feat/rds-auto-stop | 12 | 0 | 0 | 0 | 0 | 0 | 12 |
| #447 | feat/resource-archive | 1 | 0 | 0 | 0 | 0 | 1 | 0 |

## 고친 것

| id | 층 | 커밋 | 설명 | 고치기 전 실패한 시험 |
| --- | --- | --- | --- | --- |
| C443-000 | #443 | 3ec2f28 | 동작 줄이기에서 animation, transition을 none으로 덮던 규칙을 지워 antd 대화상자가 보이고 닫히게 했다 | with reduced motion a workspace dialog becomes visible and closing it releases the page: 실패 Expected "1" Received "0" (.ant-modal opacity) |
| D-024 | #443 | 0dfdbd3 | 사이드바 높이를 화면에 맞추고 넘치면 사이드바 안에서 스크롤 | the sidebar fits a short screen and scrolls by itself so its last link stays visible: 실패 Expected <= 720 Received 886.90625 (사이드바 아래 끝) |
| X-010 | #443 | 0dfdbd3 | 사이드바 높이를 화면에 맞추고 넘치면 사이드바 안에서 스크롤 | the sidebar fits a short screen and scrolls by itself so its last link stays visible: 실패 Expected <= 720 Received 886.90625 (사이드바 아래 끝) |
| X-001 | #443 | ee07130 | 테마 토큰 colorPrimaryBorder를 #5746ad로 두어 antd 포커스 테두리 대비를 7.28:1로 올렸다 | an antd button shows a focus ring in the brand color: 실패 Expected "rgb(87, 70, 173)" Received "rgb(193, 186, 212)" |
| X-042 | #443 | e26db2c | 지표 띠 초록 배경을 마지막 칸이 아니라 .is-settled 칸에만 건다 | 없음(git grep -n finance-flow HEAD -- frontend/src: 이 층에는 globals.css 정의만 있고 쓰는 화면이 없다; git grep -n finance-flow origin/feat/) |
| X-054 | #443 | cab9f45 | 한국어 404 화면(not-found.tsx)에 main-content와 홈, 도입사 선택 링크를 두었다 | an unknown address shows a Korean not-found page with a main landmark and a way back: 실패 heading '페이지를 찾을 수 없습니다' element(s) not found |
| G-073 | #443 | cc3de53 | README의 / 설명, 스타일 규칙, 테마, 글꼴 줄과 화면 표를 코드에 맞췄다 | 없음(문서 변경: 고친 README 줄을 layout.tsx 8-14행, AppProviders.tsx 10-19행, page.tsx, HomePage.module.css와 대조; 글꼴 버전: PretendardVaria) |
| G-074 | #443 | e72b5f2 | 루트 error.tsx가 digest를 안내 문단에 붙인다 | the root error page shows the error digest so a report can be matched with the server log: 실패 Expected pattern /\([0-9A-Za-z]+\)/ Received '화면을 불러오지 못했습니다서버 연결과 |
| G-086 | #443 | e72b5f2 | 루트 error.tsx가 digest를 안내 문단에 붙인다 | the root error page shows the error digest so a report can be matched with the server log: 실패 Expected pattern /\([0-9A-Za-z]+\)/ Received '화면을 불러오지 못했습니다서버 연결과 |
| D-153 | #443 | 57c1ad9 | 헤더 실제 높이를 --workspace-header-height에 쓰고 셸 격자와 scroll-padding이 그 변수를 쓴다 | the header height variable matches the real header at 390px: 실패 Expected true Received false (헤더 높이와 변수 차이 1px 초과, 900px도 같은 결과) |
| X-047 | #443 | 57c1ad9 | 헤더 실제 높이를 --workspace-header-height에 쓰고 셸 격자와 scroll-padding이 그 변수를 쓴다 | the header height variable matches the real header at 390px: 실패 Expected true Received false (헤더 높이와 변수 차이 1px 초과, 900px도 같은 결과) |
| D-002 | #443 | 0dfdbd3 | 사이드바 높이를 화면에 맞추고 넘치면 사이드바 안에서 스크롤 | the sidebar fits a short screen and scrolls by itself so its last link stays visible: 실패 Expected <= 720 Received 886.90625 (사이드바 아래 끝) |
| D-048 | #443 | 0dfdbd3 | 사이드바 높이를 화면에 맞추고 넘치면 사이드바 안에서 스크롤 | the sidebar fits a short screen and scrolls by itself so its last link stays visible: 실패 Expected <= 720 Received 886.90625 (사이드바 아래 끝) |
| D-064 | #443 | 0dfdbd3 | 사이드바 높이를 화면에 맞추고 넘치면 사이드바 안에서 스크롤 | the sidebar fits a short screen and scrolls by itself so its last link stays visible: 실패 Expected <= 720 Received 886.90625 (사이드바 아래 끝) |
| D-235 | #443 | 0dfdbd3 | 사이드바 높이를 화면에 맞추고 넘치면 사이드바 안에서 스크롤 | the sidebar fits a short screen and scrolls by itself so its last link stays visible: 실패 Expected <= 720 Received 886.90625 (사이드바 아래 끝) |
| D-249 | #443 | 0dfdbd3 | 사이드바 높이를 화면에 맞추고 넘치면 사이드바 안에서 스크롤 | the sidebar fits a short screen and scrolls by itself so its last link stays visible: 실패 Expected <= 720 Received 886.90625 (사이드바 아래 끝) |
| X-048 | #443 | fabaca1 | 표 본문 링크와 버튼에 scroll-margin-top 56px를 주어 역방향 Tab 때 고정 머리글 아래로 내려오게 했다 | 없음(커밋하지 않은 측정 스크립트 .s456-work/verify-x048.mjs: 1280x420 /customers에서 끝 행부터 Shift+Tab, 포커스 요소 위 끝 - 고정 머리글 아래 끝. 고치기 전 -27px) |
| D-191 | #443 | 38f991c | 권한 범위 표시를 버튼 밖으로 떼고 '권한 새로 조회' 버튼에 진행 표시와 완료 메시지를 달았다 | the header shows the access scope apart from a refresh button that confirms when it finishes: 실패 locator('.workspace-scope') element(s) not found |
| D-173 | #443 | 8bd1323 | Checkbox colorBorder를 #7c8799(3.63:1)로 올렸다 | an unchecked antd checkbox has a visible border: 실패 Expected "rgb(124, 135, 153)" Received "rgb(220, 225, 235)" |
| X-022 | #443 | 8bd1323 | 체크박스 테두리, 링크 hover, Descriptions 라벨 색을 기준 이상으로 올렸다 | an unchecked antd checkbox has a visible border: 실패 Expected "rgb(124, 135, 153)" Received "rgb(220, 225, 235)" |
| D-151 | #443 | ee07130 | 테마 토큰 colorPrimaryBorder를 #5746ad로 두어 antd 포커스 테두리 대비를 7.28:1로 올렸다 | an antd button shows a focus ring in the brand color: 실패 Expected "rgb(87, 70, 173)" Received "rgb(193, 186, 212)" |
| D-174 | #443 | ee07130 | 테마 토큰 colorPrimaryBorder를 #5746ad로 두어 antd 포커스 테두리 대비를 7.28:1로 올렸다 | an antd button shows a focus ring in the brand color: 실패 Expected "rgb(87, 70, 173)" Received "rgb(193, 186, 212)" |
| D-009 | #443 | 6730d65 | globals.css의 파랑 리터럴을 var(--blue), var(--focus), 테마 연보라 값으로 바꿨다 | 없음(보이는 색만 바뀌어 시험으로 잡을 수 없다. evidence/D-009 before, after 캡처(login, signup, pending, organizations, admin-organizations, adm) |
| D-187 | #443 | 6730d65 | globals.css의 파랑 리터럴을 var(--blue), var(--focus), 테마 연보라 값으로 바꿨다 | 없음(보이는 색만 바뀌어 시험으로 잡을 수 없다. evidence/D-009 before, after 캡처(login, signup, pending, organizations, admin-organizations, adm) |
| D-233 | #443 | 6730d65 | globals.css의 파랑 리터럴을 var(--blue), var(--focus), 테마 연보라 값으로 바꿨다 | 없음(보이는 색만 바뀌어 시험으로 잡을 수 없다. evidence/D-009 before, after 캡처(login, signup, pending, organizations, admin-organizations, adm) |
| X-043 | #443 | 6730d65 | globals.css의 파랑 리터럴을 var(--blue), var(--focus), 테마 연보라 값으로 바꿨다 | 없음(보이는 색만 바뀌어 시험으로 잡을 수 없다. evidence/D-009 before, after 캡처(login, signup, pending, organizations, admin-organizations, adm) |
| G-071 | #443 | d84372b | globals.css에서 뒤 규칙에 덮여 효과가 없던 :root, body, a, .workspace-content, .setup-list p, .detail-strip dt, .mobile-menu 재정의를 지웠다 | 없음(보이는 결과가 바뀌지 않는 정리: 정리 전후 18개 라우트 1440x900, 390x844 화소 비교에서 캡처 시각 글자 말고 같음(evidence/G-071/pixel-compare.txt)) |
| L-001 | #443 | f07a01f | 도입사 이름을 span으로 감싸 말줄임을 걸고 아이콘이 줄지 않게 했다 | a long organization name is cut with an ellipsis and keeps the header icon inside the link: 실패 Expected <= 300.71875 Received 317.90625 (320px, 아이콘 오른쪽 끝) |
| D-083 | #443 | 8abe196 | .workspace-actions를 세로 가운데로 맞추고 필드 줄은 버튼만 입력 아래선에 맞췄다 | 없음(보이는 정렬만 바뀌어 시험으로 잡을 수 없다. evidence/D-083 before, after 캡처(members, admin-users, customers)) |
| X-076 | #443 | 8abe196 | .workspace-actions를 세로 가운데로 맞추고 필드 줄은 버튼만 입력 아래선에 맞췄다 | 없음(보이는 정렬만 바뀌어 시험으로 잡을 수 없다. evidence/D-083 before, after 캡처(members, admin-users, customers)) |
| D-082 | #443 | 3901315 | .setup-list 조치 링크를 기본 글자 크기와 44px 높이로 맞췄다 | 없음(git grep -n setup-list HEAD -- frontend/src/**/*.tsx: 이 층에는 쓰는 화면이 없다; git grep -n setup-list origin/feat/rds-auto-stop:) |
| X-073 | #443 | 3901315 | .setup-list 조치 링크를 기본 글자 크기와 44px 높이로 맞췄다 | 없음(git grep -n setup-list HEAD -- frontend/src/**/*.tsx: 이 층에는 쓰는 화면이 없다; git grep -n setup-list origin/feat/rds-auto-stop:) |
| X-078 | #443 | b6d0822 | 옆 패널 h2를 clamp(22px, 1.8vw, 26px)로 낮춰 과업 제목보다 작게 했다 | the login side panel title is smaller than the task title: 실패 Expected < 34 Received 42 (1440px 패널 h2) |
| D-080 | #443 | e26db2c | 지표 띠 초록 배경을 마지막 칸이 아니라 .is-settled 칸에만 건다 | 없음(git grep -n finance-flow HEAD -- frontend/src: 이 층에는 globals.css 정의만 있고 쓰는 화면이 없다; git grep -n finance-flow origin/feat/) |
| D-084 | #443 | d4129fb | 767px 이하 WorkspaceTable 레코드 제목을 14px로 낮췄다 | 없음(보이는 크기만 바뀌어 시험으로 잡을 수 없다. evidence/D-084 before, after 캡처(members, integrations 390). integrations PNG는 키 정보가 보일 수 있어 보고) |
| D-097 | #443 | c002716 | .month-apply에 hover, 누름, 비활성 상태를 주었다 | 없음(보이는 상태만 바뀌어 저장소 시험으로 두지 않았다. /login에 .month-apply 버튼을 넣어 계산된 스타일을 쟀다(.s456-work/probe-month.mjs). 고치기 전 hover와 disabled가) |
| D-240 | #443 | c002716 | .month-apply에 hover, 누름, 비활성 상태를 주었다 | 없음(보이는 상태만 바뀌어 저장소 시험으로 두지 않았다. /login에 .month-apply 버튼을 넣어 계산된 스타일을 쟀다(.s456-work/probe-month.mjs). 고치기 전 hover와 disabled가) |
| D-081 | #443 | 18be9f0 | .operator-content를 flex column, gap 28px로 두었다 | 없음(보이는 간격만 바뀌어 시험으로 잡을 수 없다. evidence/D-081 before, after 캡처(admin-organizations, admin-users 1440, 390)) |
| G-076 | #443 | 4455641 | 어디에서도 쓰지 않는 선택자(.console-content, .tenant-*, .workspace-grid-wide, .setup-state)를 지웠다 | 없음(쓰지 않는 CSS 삭제라 실패하는 시험이 없다. 지운 선택자는 층 head와 정점 b8abfdf frontend/src(globals.css 제외)에서 git grep 0건. 삭제 전후 19개 라우트 화소 비교에서 ) |
| C443-001 | #443 | 55ce723 | 스켈레톤을 aria-hidden div로 감싸고 상태 영역에서 aria-busy, aria-label을 뺐다 | 없음(개발 서버 /customers?state=loading의 상태 영역 접근성 트리(.s456-work/verify-skeleton.mjs): 고치기 전 status 이름 '데이터를 불러오고 있습니다' 안에 list와 ) |
| G-087 | #443 | cc3de53 | README의 / 설명, 스타일 규칙, 테마, 글꼴 줄과 화면 표를 코드에 맞췄다 | 없음(문서 변경: 고친 README 줄을 layout.tsx 8-14행, AppProviders.tsx 10-19행, page.tsx, HomePage.module.css와 대조; 글꼴 버전: PretendardVaria) |
| G-083 | #443 | 57c1ad9 | 헤더 높이 리터럴을 --workspace-header-height로 모았다 | 없음(auth.contract.spec.ts 'the header height variable matches the real header at 390px', 'at 900px'; evidence/D-153 before, ) |
| X-058 | #443 | f07a01f | 도입사 이름을 span으로 감싸 말줄임을 걸고 아이콘이 줄지 않게 했다 | a long organization name is cut with an ellipsis and keeps the header icon inside the link: 실패 Expected <= 300.71875 Received 317.90625 (320px, 아이콘 오른쪽 끝) |
| X-079 | #443 | abb155d | 그룹 제목 선택자의 구체성을 올려 24px 시작선과 11px 글자를 적용했다 | sidebar group titles start on the same line as the sidebar label: 실패 Expected "24px" Received "16px" (padding-inline-start) |
| D-074 | #443 | 24c970f | body에 word-break: keep-all과 overflow-wrap: break-word를 두었다 | Korean text in the console wraps between words: 실패 Expected "keep-all" Received "normal" |
| X-098 | #443 | 24c970f | body에 word-break: keep-all과 overflow-wrap: break-word를 두었다 | Korean text in the console wraps between words: 실패 Expected "keep-all" Received "normal" |
| D-138 | #443 | d337ba0 | 안내 토글에서 aria-pressed를 지웠다 | the guide toggle keeps a name that says what it does instead of a pressed state: 실패 Expected not have attribute Received have attribute (aria-pressed) |
| D-178 | #443 | d337ba0 | 안내 토글에서 aria-pressed를 지웠다 | the guide toggle keeps a name that says what it does instead of a pressed state: 실패 Expected not have attribute Received have attribute (aria-pressed) |
| X-059 | #443 | d337ba0 | 안내 토글에서 aria-pressed를 지웠다 | the guide toggle keeps a name that says what it does instead of a pressed state: 실패 Expected not have attribute Received have attribute (aria-pressed) |
| D-021 | #443 | 90d920f | 사이드바 하단 링크 문구를 메뉴 이름 'API 키와 통보'로 맞췄다 | the sidebar calls the integrations screen by its menu name: 실패 Expected "API 키와 통보" Received "서버 연동 설정" |
| D-145 | #443 | 90d920f | 사이드바 하단 링크 문구를 메뉴 이름 'API 키와 통보'로 맞췄다 | the sidebar calls the integrations screen by its menu name: 실패 Expected "API 키와 통보" Received "서버 연동 설정" |
| D-220 | #443 | 90d920f | 사이드바 하단 링크 문구를 메뉴 이름 'API 키와 통보'로 맞췄다 | the sidebar calls the integrations screen by its menu name: 실패 Expected "API 키와 통보" Received "서버 연동 설정" |
| X-063 | #443 | 5147f0c | 도입사 링크의 펼침 아이콘을 전환 아이콘으로 바꿨다 | 없음(보이는 아이콘만 바뀌어 시험으로 잡을 수 없다. evidence/X-063 before, after 캡처(/customers 1440, 390)) |
| G-077 | #443 | 47e14c8 | 역할 라벨 정본 ORGANIZATION_ROLE_LABELS를 두고 헤더가 그것을 쓴다 | organization roles have one label definition that matches the member list: 실패 actual undefined, expected { owner: '대표관리자', admin: '관리자', member: '일반 사용자' } |
| D-020 | #443 | 38f991c | 권한 범위 표시를 버튼 밖으로 떼고 '권한 새로 조회' 버튼에 진행 표시와 완료 메시지를 달았다 | the header shows the access scope apart from a refresh button that confirms when it finishes: 실패 locator('.workspace-scope') element(s) not found |
| D-038 | #443 | 38f991c | 권한 범위 표시를 버튼 밖으로 떼고 '권한 새로 조회' 버튼에 진행 표시와 완료 메시지를 달았다 | the header shows the access scope apart from a refresh button that confirms when it finishes: 실패 locator('.workspace-scope') element(s) not found |
| D-058 | #443 | 38f991c | 권한 범위 표시를 버튼 밖으로 떼고 '권한 새로 조회' 버튼에 진행 표시와 완료 메시지를 달았다 | the header shows the access scope apart from a refresh button that confirms when it finishes: 실패 locator('.workspace-scope') element(s) not found |
| D-137 | #443 | 38f991c | 권한 범위 표시를 버튼 밖으로 떼고 '권한 새로 조회' 버튼에 진행 표시와 완료 메시지를 달았다 | the header shows the access scope apart from a refresh button that confirms when it finishes: 실패 locator('.workspace-scope') element(s) not found |
| D-167 | #443 | 38f991c | 권한 범위 표시를 버튼 밖으로 떼고 '권한 새로 조회' 버튼에 진행 표시와 완료 메시지를 달았다 | the header shows the access scope apart from a refresh button that confirms when it finishes: 실패 locator('.workspace-scope') element(s) not found |
| D-229 | #443 | 38f991c | 권한 범위 표시를 버튼 밖으로 떼고 '권한 새로 조회' 버튼에 진행 표시와 완료 메시지를 달았다 | the header shows the access scope apart from a refresh button that confirms when it finishes: 실패 locator('.workspace-scope') element(s) not found |
| X-062 | #443 | 38f991c | 권한 범위 표시를 버튼 밖으로 떼고 '권한 새로 조회' 버튼에 진행 표시와 완료 메시지를 달았다 | the header shows the access scope apart from a refresh button that confirms when it finishes: 실패 locator('.workspace-scope') element(s) not found |
| G-081 | #443 | b99d2ef | 렌더되지 않는 DevStateSwitch와 쓰이지 않는 Layout 토큰을 지우고 README shell 설명을 맞췄다 | 없음(화면 동작이 바뀌지 않는 죽은 코드 정리라 실패하는 시험이 없다. git grep -n 'DevStateSwitch\|headerPadding' -- frontend 결과 없음, b99d2ef의 10절 lint, b) |
| D-036 | #443 | 6730d65 | globals.css의 파랑 리터럴을 var(--blue), var(--focus), 테마 연보라 값으로 바꿨다 | 없음(보이는 색만 바뀌어 시험으로 잡을 수 없다. evidence/D-009 before, after 캡처(login, signup, pending, organizations, admin-organizations, adm) |
| D-053 | #443 | 6730d65 | globals.css의 파랑 리터럴을 var(--blue), var(--focus), 테마 연보라 값으로 바꿨다 | 없음(보이는 색만 바뀌어 시험으로 잡을 수 없다. evidence/D-009 before, after 캡처(login, signup, pending, organizations, admin-organizations, adm) |
| D-079 | #443 | 6730d65 | globals.css의 파랑 리터럴을 var(--blue), var(--focus), 테마 연보라 값으로 바꿨다 | 없음(보이는 색만 바뀌어 시험으로 잡을 수 없다. evidence/D-009 before, after 캡처(login, signup, pending, organizations, admin-organizations, adm) |
| D-098 | #443 | 6730d65 | globals.css의 파랑 리터럴을 var(--blue), var(--focus), 테마 연보라 값으로 바꿨다 | 없음(보이는 색만 바뀌어 시험으로 잡을 수 없다. evidence/D-009 before, after 캡처(login, signup, pending, organizations, admin-organizations, adm) |
| D-169 | #443 | 6730d65 | globals.css의 파랑 리터럴을 var(--blue), var(--focus), 테마 연보라 값으로 바꿨다 | 없음(보이는 색만 바뀌어 시험으로 잡을 수 없다. evidence/D-009 before, after 캡처(login, signup, pending, organizations, admin-organizations, adm) |
| D-260 | #443 | 6730d65 | globals.css의 파랑 리터럴을 var(--blue), var(--focus), 테마 연보라 값으로 바꿨다 | 없음(보이는 색만 바뀌어 시험으로 잡을 수 없다. evidence/D-009 before, after 캡처(login, signup, pending, organizations, admin-organizations, adm) |
| D-075 | #443 | f82e677 | 제목 크기 변수 --screen-title-size를 두고 두 제목 컴포넌트가 함께 쓴다 | screen titles use the same size whichever header component draws them: 실패 Expected "28px" Received "34px" (1440px 사용자 관리 대 고객) |
| G-079 | #443 | f82e677 | 제목 크기 변수 --screen-title-size를 두고 두 제목 컴포넌트가 함께 쓴다 | screen titles use the same size whichever header component draws them: 실패 Expected "28px" Received "34px" (1440px 사용자 관리 대 고객) |
| X-070 | #443 | f82e677 | 제목 크기 변수 --screen-title-size를 두고 두 제목 컴포넌트가 함께 쓴다 | screen titles use the same size whichever header component draws them: 실패 Expected "28px" Received "34px" (1440px 사용자 관리 대 고객) |
| X-017 | #443 | cd1a0fa | 문단, 입력 안내, 경고 안 링크에 밑줄을 되살렸다 | a link inside a sentence keeps its underline: 실패 Expected "underline" Received "none" |
| D-107 | #443 | a8024e0 | 소개 화면 예시 묶음을 role=group과 aria-labelledby로 이름 있는 그룹으로 만들었다 | the home trace example is a named group: 실패 getByRole('group', { name: '한 고객, 한 청구 월' }) element(s) not found |
| D-165 | #443 | 45fde17 | 업무 대화상자 네 사본의 본문을 padding 8, 좌우 margin -8로 바꿔 포커스 테두리 여유를 두었다 | a focused field in a workspace dialog keeps its whole focus ring inside the dialog body: 실패 Expected >= 7 Received 0 (본문 안쪽 왼쪽 여유) |
| G-046 | #443 | 47e14c8 | 역할 라벨 정본 ORGANIZATION_ROLE_LABELS를 두고 헤더가 그것을 쓴다 | organization roles have one label definition that matches the member list: 실패 actual undefined, expected { owner: '대표관리자', admin: '관리자', member: '일반 사용자' } |
| U1-B1 | #444 | 235ebf1 | auth.real.spec.ts의 운영자 첫 화면 기대를 /admin으로 맞춘다 | real Spring: operator without organization can manage the platform: 실패 Expected pattern: /\/admin\/organizations$/ Received string: "http://localhost:33147/admi |
| Ck2-001 | #444 | 02b14f4 | 웹훅 요청에 User-Agent 헤더를 싣는다 | 요청에_수신_서버가_발신자를_알아볼_User_Agent를_싣는다: 실패 Expecting actual "POST /hook HTTP/1.1 Host: 127.0.0.1:63777 Content-Type: application/json ..." to contain "User-Agent:  |
| Ck2-002 | #444 | c5fb0e0 | 웹훅 수신 주소의 DNS 조회를 트랜잭션 밖에서 한다 | 수신_주소의_DNS_조회는_트랜잭션_밖에서_해_DB_커넥션을_쥐지_않는다: 실패 Expecting actual [true, true] to contain exactly (and in same order) [false, false] |
| Ck2-003 | #444 | f0aa95b | 밀린 웹훅 발송을 지표로 내고 10분 넘게 밀리면 경보한다 | 보낼_때가_지난_대기_전송의_건수와_가장_오래_밀린_시간을_지표로_낸다: 실패 error: cannot find symbol method measure() (적체를 세어 지표로 내는 경로가 없다) |
| Ck2-004 | #444 | ed25d3e | 웹훅 전송 실패의 예외를 로그에 남긴다 | 전송_중_예상하지_못한_예외는_원인과_함께_ERROR로_남긴다: 실패 Expecting actual "" to contain "ERROR" |
| D-044 | #444 | 1fc88c3 | 웹훅 발송 내역이 통보 종류, 발송 상태, 실패 사유를 이름으로 보인다 | webhook delivery history names event types, delivery states and failure reasons: 실패 locator('.workspace-desktop-table').getByText('결제 실패', { exact: true }) elem |
| D-088 | #444 | 1fc88c3 | 웹훅 발송 내역이 통보 종류, 발송 상태, 실패 사유를 이름으로 보인다 | webhook delivery history names event types, delivery states and failure reasons: 실패 locator('.workspace-desktop-table').getByText('결제 실패', { exact: true }) elem |
| D-195 | #444 | 1fc88c3 | 웹훅 발송 내역이 통보 종류, 발송 상태, 실패 사유를 이름으로 보인다 | webhook delivery history names event types, delivery states and failure reasons: 실패 locator('.workspace-desktop-table').getByText('결제 실패', { exact: true }) elem |
| D-252 | #444 | 1fc88c3 | 웹훅 발송 내역이 통보 종류, 발송 상태, 실패 사유를 이름으로 보인다 | webhook delivery history names event types, delivery states and failure reasons: 실패 locator('.workspace-desktop-table').getByText('결제 실패', { exact: true }) elem |
| X-028 | #444 | 1fc88c3 | 웹훅 발송 내역이 통보 종류, 발송 상태, 실패 사유를 이름으로 보인다 | webhook delivery history names event types, delivery states and failure reasons: 실패 locator('.workspace-desktop-table').getByText('결제 실패', { exact: true }) elem |
| Ck2-006 | #444 | 37556b4 | 웹훅 전송 기록 목록을 받치는 색인을 둔다 | 전송기록_목록을_엔드포인트별_최신순으로_읽는_색인이_있다: 실패 EmptyResultDataAccessException: Incorrect result size: expected 1, actual 0 |
| Ck2-008 | #444 | a4a3660 | openapi.yaml이 웹훅 통보 종류의 최소 개수와 받는 값을 적는다 | 웹훅_엔드포인트_요청의_통보_종류는_하나_이상이고_서버가_받는_값만_적는다: 실패 [CreateWebhookEndpointRequest] expected: 1 but was: 0 |
| Ck2-013 | #444 | 40089ce | SecretMask.lastFour를 결과가 드러나는 mask로 바꾼다 | SecretMaskTest: 실패 error: cannot find symbol method mask(String) (같은 동작을 결과가 드러나는 이름으로 부를 길이 없다) |
| D-035 | #444 | 1fc88c3 | 웹훅 발송 내역이 통보 종류, 발송 상태, 실패 사유를 이름으로 보인다 | webhook delivery history names event types, delivery states and failure reasons: 실패 locator('.workspace-desktop-table').getByText('결제 실패', { exact: true }) elem |
| D-157 | #444 | 1fc88c3 | 웹훅 발송 내역이 통보 종류, 발송 상태, 실패 사유를 이름으로 보인다 | webhook delivery history names event types, delivery states and failure reasons: 실패 locator('.workspace-desktop-table').getByText('결제 실패', { exact: true }) elem |
| C444-008 | #444 | 8008c44 | 콘솔에서 웹훅 수신 주소를 수정해도 기존 설명을 지우지 않는다 | saving a webhook endpoint keeps its existing description: 실패 Expected "운영 알림 수신기" Received "설명 없이 보냄" (PUT 본문에 description이 없다) |
| D-226 | #444 | ce5041c | 운영 현황 예약 작업 표가 과금 제외 사용량 통보 작업을 이름으로 보인다 | every scheduled job the operations API lists has a label on the operations screen: 실패 unbilled_usage_report has no label in JOB_LABELS |
| G-152 | #444 | 815f525 | backend README에 웹훅 발송과 암호화 키, 운영 현황을 적는다 | 없음(문서 변경이라 시험으로 실패를 볼 수 없다. 적은 값을 WebhookDeliveryRepository.java 21-22, 43, 71-75행(재시도 간격, 6회, 2분 선점, 종결 조건), WebhookDelive) |
| U14-B1 | #444 | f67bc41 | 마지막 시도의 선점이 만료돼 실패로 끝난 웹훅도 실패 지표와 경보에 센다 | 마지막_시도_중_선점이_만료되어_실패로_끝난_전송도_실패_지표에_센다: 실패 Expecting actual 0.0 to be greater than or equal to 1.0 |
| U5-B1 | #444 | c0a9f4a | 하루 주기 작업 미실행 경보가 배포 뒤 멈춘 색의 오늘 성공도 본다 | 없음(Grafana 경보 규칙을 돌리는 저장소 시험이 없어 고치기 전 실패를 시험으로 볼 수 없다. prom/prometheus:v3의 promtool test rules로 두 색 시리즈(1시간에 오늘 성공을 낸 뒤 1시) |
| X-014 | #446 | 48fd833 | 사업자등록번호를 하이픈과 공백을 빼고 보내고 틀리면 우리말로 알린다 | a business registration number keeps only its ten digits and names the field when it is not ten digits: 실패 SyntaxError: does not provide an export named 'readBu |
| D-040 | #446 | efabb7c | 단가 입력을 0으로 미리 채우지 않고 0원 단가는 저장 전에 묻는다 | a price that charges nothing is recognized so the editor can ask before saving it: 실패 ERR_MODULE_NOT_FOUND Cannot find module src/lib/price-rate.ts (단가 창의 단가 입력 |
| D-180 | #446 | efabb7c | 단가 입력을 0으로 미리 채우지 않고 0원 단가는 저장 전에 묻는다 | a price that charges nothing is recognized so the editor can ask before saving it: 실패 ERR_MODULE_NOT_FOUND Cannot find module src/lib/price-rate.ts (단가 창의 단가 입력 |
| X-005 | #446 | efabb7c | 단가 입력을 0으로 미리 채우지 않고 0원 단가는 저장 전에 묻는다 | a price that charges nothing is recognized so the editor can ask before saving it: 실패 ERR_MODULE_NOT_FOUND Cannot find module src/lib/price-rate.ts (단가 창의 단가 입력 |
| D-109 | #446 | 61d5216 | 미터 저장 실패를 대화상자 맨 위에 보이고 포커스하며 화면 문구로 알린다 | changing the calculation of a meter with events says what can still change instead of API field names: 실패 SyntaxError: does not provide an export named 'billabl |
| Ck3-446-02 | #446 | d5ae200 | 단가 종료 월이 시작 월보다 뒤가 아니면 요금제와 같은 validation_error를 낸다 | 종료_월이_시작_월보다_뒤가_아니면_요금제와_같은_validation_error다: 실패 expected: VALIDATION_ERROR but was: INVALID_PRICE_RATE |
| Ck3-446-04 | #446 | b22fd8d | 조합별 단가 DELETE 설명을 이번 달 공통 단가를 끝내는 실제 동작에 맞춘다 | 없음(문서 변경이라 시험으로 실패를 볼 수 없다. 적은 동작을 PriceRateService.java 159-191행(공통 단가, 이번 달 적용 중, 이번 달 시작은 삭제, 그 전은 close(current))과 Audi) |
| Ck3-446-05 | #446 | ae50ce0 | 요금제 이력 조회 API가 생략한 파라미터의 뜻과 400, 404를 적는다 | 요금제_이력_조회는_생략한_파라미터의_뜻과_400_404를_적는다: 실패 Expecting not blank but was: "" (description 없음) |
| Ck3-446-06 | #446 | a7a789e | 요금 모델 검사를 서비스 한곳에 두고 오류 필드를 요청의 model로 낸다 | 요금_모델에_맞지_않는_단가는_요청에_있는_model_필드로_거절된다: 실패 expected: "model" but was: "validPricing" |
| D-120 | #446 | 8804e65 | 작성 중인 미터 폼을 Esc, 바깥 클릭, 취소로 닫기 전에 묻는다 | a meter form with input asks before Escape throws it away: 실패 Expected "작성 중인 미터를 버리고 닫을까요?" Received "" (확인 없이 닫힘) |
| X-091 | #446 | 1574c86 | 미터 폼의 숫자 입력에서 영어 이름의 증감 버튼을 뺀다 | meter number inputs do not expose English stepper buttons: 실패 Expected 0 Received 2 (Increase Value, Decrease Value) |
| D-119 | #446 | efabb7c | 단가 입력을 0으로 미리 채우지 않고 0원 단가는 저장 전에 묻는다 | a price that charges nothing is recognized so the editor can ask before saving it: 실패 ERR_MODULE_NOT_FOUND Cannot find module src/lib/price-rate.ts (단가 창의 단가 입력 |
| C448-001 | #448 | 1164c20 | 운영자 전용 경로의 요청 감사를 운영자 세션이 고른 도입사에 남기지 않는다 | 도입사에_소속된_운영자의_운영자_전용_요청은_그_도입사의_감사에_남지_않는다: 실패 감사 행 organization_id가 null이 아니라 그 도입사 id였다 |

## 정할 것

| id | 층 | 질문 |
| --- | --- | --- |
| X-012 | #443 | 모든 경로가 preload하는 2MB Pretendard 전체 가변 글꼴을 (가) 같은 릴리스의 subset woff2로 바꿔 드문 음절은 대체 글꼴로 그릴지, (나) unicode-range로 나눈 동적 subset 조각을 들일지, (다) 지금 파일을 둘지 정해야 한다(외부에서 받은 글꼴 파일을 저장소에 넣는 일 포함). |
| D-071 | #443 | 모든 경로가 preload하는 2MB Pretendard 전체 가변 글꼴을 (가) 같은 릴리스의 subset woff2로 바꿔 드문 음절은 대체 글꼴로 그릴지, (나) unicode-range로 나눈 동적 subset 조각을 들일지, (다) 지금 파일을 둘지 정해야 한다(외부에서 받은 글꼴 파일을 저장소에 넣는 일 포함). |
| G-075 | #443 | 모든 경로가 preload하는 2MB Pretendard 전체 가변 글꼴을 (가) 같은 릴리스의 subset woff2로 바꿔 드문 음절은 대체 글꼴로 그릴지, (나) unicode-range로 나눈 동적 subset 조각을 들일지, (다) 지금 파일을 둘지 정해야 한다(외부에서 받은 글꼴 파일을 저장소에 넣는 일 포함). |
| G-085 | #443 | 모든 경로가 preload하는 2MB Pretendard 전체 가변 글꼴을 (가) 같은 릴리스의 subset woff2로 바꿔 드문 음절은 대체 글꼴로 그릴지, (나) unicode-range로 나눈 동적 subset 조각을 들일지, (다) 지금 파일을 둘지 정해야 한다(외부에서 받은 글꼴 파일을 저장소에 넣는 일 포함). |
| D-011 | #443 | 767px 이하 콘솔 헤더를 줄이려고 시연 안내, 권한 새로 조회, 로그아웃(#453부터 업무 환경 선택 포함)을 메뉴 Drawer로 옮길지, 헤더 sticky를 풀지, 지금 배치를 유지할지 정해야 한다. |
| D-065 | #443 | 767px 이하 콘솔 헤더를 줄이려고 시연 안내, 권한 새로 조회, 로그아웃(#453부터 업무 환경 선택 포함)을 메뉴 Drawer로 옮길지, 헤더 sticky를 풀지, 지금 배치를 유지할지 정해야 한다. |
| G-072 | #443 | 색과 글꼴 값을 TS 모듈 하나에 모아 AppProviders 테마와 :root CSS 변수를 함께 만들고 globals.css, HomePage.module.css는 var()만 쓰게 바꿀지(새 파일과 AppProviders.tsx, layout.tsx, globals.css, HomePage.module.css에 걸친 구조 변경, 박성종 님 f671a4f 디자인), 지금 복제를 둘지 정한다. |
| G-080 | #443 | WorkspaceHeader의 kicker를 화면에 보이게 할지(globals.css:170 삭제, f671a4f 디자인 되돌림) 아니면 kicker prop과 호출부 인자, .workspace-kicker 두 규칙을 지울지(이 층 4개 파일, 정점 12개 호출 파일)를 D-237과 함께 정한다. |
| C443-002 | #443 | 모든 경로가 preload하는 2MB Pretendard 전체 가변 글꼴을 (가) 같은 릴리스의 subset woff2로 바꿔 드문 음절은 대체 글꼴로 그릴지, (나) unicode-range로 나눈 동적 subset 조각을 들일지, (다) 지금 파일을 둘지 정해야 한다(외부에서 받은 글꼴 파일을 저장소에 넣는 일 포함). |
| D-039 | #443 | 767px 이하 콘솔 헤더를 줄이려고 시연 안내, 권한 새로 조회, 로그아웃(#453부터 업무 환경 선택 포함)을 메뉴 Drawer로 옮길지, 헤더 sticky를 풀지, 지금 배치를 유지할지 정해야 한다. |
| D-055 | #443 | 767px 이하 콘솔 헤더를 줄이려고 시연 안내, 권한 새로 조회, 로그아웃(#453부터 업무 환경 선택 포함)을 메뉴 Drawer로 옮길지, 헤더 sticky를 풀지, 지금 배치를 유지할지 정해야 한다. |
| D-244 | #443 | 색 토큰을 역할 이름(--color-primary, --color-text-secondary 등)으로 다시 짓고 TS 상수 하나에서 antd 테마와 CSS 변수를 함께 만들며 테스트 환경 안내 상자를 한 컴포넌트로 묶을지(#443, #445, #446, #449 파일에 걸친 구조 변경) 정한다. |
| D-022 | #443 | 워드마크를 공용 컴포넌트 하나로 묶을 때 글꼴을 이미 번들한 Pretendard로 통일할지 브랜드 글꼴을 따로 번들할지, 그리고 로그인, 가입, 도입사 선택, 플랫폼 운영 화면에도 막대 아이콘을 붙일지 정해야 한다. |
| G-084 | #443 | 로고 마크를 공용 BrandMark 컴포넌트 하나로 합쳐 소개 화면과 콘솔 셸이 크기만 달리 쓰게 할지, 합친다면 소개 화면(6px, 27px)과 콘솔(5px, 23px) 중 어느 비율을 기준으로 할지 정해야 한다. |
| G-078 | #443 | LinkButton과 ScopedLink의 href 변환을 공용 훅으로 합치고 ConsoleShell의 workspaceHref 중첩 호출을 inheritFilters prop으로 바꾸는 세 파일 정리(#442 NavMenu 23행의 같은 패턴 포함 여부까지)를 #443에서 할지 정한다. |
| D-237 | #443 | 화면 제목을 ScreenHeader 하나로 합쳐 WorkspaceHeader의 kicker prop과 admin 화면의 .page-heading을 없애고, ActionPanel(#442)에 버튼 type을 두어 제목 줄 생성 행동을 primary로 통일할지 정한다. |
| Ck2-007 | #444 | Q9와 상관없는 없는 delivery_id의 404 webhook_delivery_not_found 분리를 먼저 고치고, 비활성 엔드포인트를 재시도할 때 webhook_endpoint_disabled를 돌려줄지는 Q9(발송 일시 중지 의미) 결정에 맞춰 정할지. 새 오류 code 추가는 공개 계약 변경이다. (Q9 발송 일시 중지와 함께 정한다) |
| D-045 | #444 | 웹훅 목록에 수신 주소별 발송 상태(마지막 발송 결과나 최근 실패 건수)를 보일지, 보인다면 WebhookEndpointResponse에 필드를 더할지 목록 화면이 수신 주소마다 발송 내역 1건을 따로 조회할지 정한다. |
| C444-002 | #444 | 도입사 하나가 전역 웹훅 발송 큐를 막지 못하게 하려면 무엇을 둘지 정한다. 후보는 시험 통보와 재시도의 도입사별 호출 한도나 엔드포인트별 대기 상한(새 409 오류 코드와 openapi.yaml 변경), 발송 루프의 도입사별 공정 소비와 병렬 발송, 또는 둘 다이고 그 한도 값도 함께 정한다. |
| G-140 | #444 | 웹훅 발송 루프를 한 실행 안에서 병렬로 보낼지(전체 동시 수 상한, 엔드포인트별 동시 1건), 연속 실패 엔드포인트를 뒤로 미룰지를 C444-002와 함께 정한다. 한 실행에 한 건씩 처리한다고 단언하는 기존 WebhookDeliveryServiceTest 기대를 바꿀지도 함께 정한다. |
| Ck2-005 | #444 | 운영 현황 failed_webhook_count를 최근 24시간(last_attempted_at) failed로 바꿀지, 삭제되지 않은 엔드포인트의 failed만 셀지, 지금처럼 현재 failed 전체로 둘지 정한다(비활성 엔드포인트의 endpoint_disabled 실패를 뺄지는 Q9와 함께 정한다). (Ck2-010과 같은 결정) |
| Ck2-009 | #444 | #444 SDK에 미리 들어온 위층 계약(organizationId 헤더, WebhookEvent.environment, POST 본문과 canRetry 기본 true, text 응답, PATCH)을 처음 쓰는 층(#445, #450, #451, #453)으로 옮길지, #442 프론트 transport처럼 미리 담아 둔 채 스택 순서대로 머지할지 정한다. |
| Ck2-010 | #444 | 운영 현황 failed_webhook_count를 최근 24시간(last_attempted_at) failed로 바꿀지, 삭제되지 않은 엔드포인트의 failed만 셀지, 지금처럼 현재 failed 전체로 둘지 정한다(비활성 엔드포인트의 endpoint_disabled 실패를 뺄지는 Q9와 함께 정한다). |
| Ck2-011 | #444 | failure_reason 정본으로 WebhookFailureReason enum을 두고 WebhookOutcome, WebhookClient, WebhookDeliveryService, WebhookDeliveryRepository를 함께 바꿀지(운영 파일 다섯), 아니면 저장소의 문자열과 CHECK 관례를 유지하고 V28에서 webhook_delivery.failure_reason에도 같은 CHECK를 거는 것만 할지 정한다. |
| Ck2-012 | #444 | #444가 발행하지 않는 PAYMENT_* 셋과 UNBILLED_USAGE_DETECTED를 #444에서 빼 발행 코드가 있는 #445, #450으로 옮길지(스택 재분할, #445는 담당 밖), 아니면 #444에 구독 계약을 미리 두는 지금 분할을 유지하고 PR 본문에만 적을지 정한다. |
| Ck2-015 | #444 | frontend와 demo가 같이 쓰는 이름 규칙을 한 파일로 모을지(모은다면 frontend, demo, 저장소 루트 중 어디에 두고 CI 경로 필터와 #452 llm-service Docker 빌드 문맥을 어떻게 맞출지), 아니면 사본을 두고 naming.md에 적고 동일성 시험만 더할지 정한다. |
| G-151 | #444 | 예약 작업 이름을 scheduling 패키지 enum 하나로 모아 #444, #445, #450에 걸쳐 바꾸고 운영 화면을 등록된 작업 목록 기준으로 바꿀지, 아니면 운영 코드는 그대로 두고 등록 이름, 운영 화면 VALUES, 경보 정규식이 일치하는지 보는 시험만 더할지 정한다. |
| Ck3-446-03 | #446 | line_type 5종과 model 3종을 엔티티 상수로 모으고 요청과 응답 DTO 다섯 곳에 @Schema(allowableValues)를 달아 openapi.yaml에 값 목록을 내는 변경(엔티티 2개, 서비스 3개, DTO 5개, openapi.yaml)을 #446에서 할지, 미터 aggregation처럼 문자열로 둘지. |
| Ck3-446-07 | #446 | 단가 감사 기록의 target_id를 버전 UUID로 유지하고 배포 DB에 남은 미터 code 행과의 차이를 README 설명이나 감사 검색(detail.billable_metric_code)으로 메울지, 미터 code로 되돌리고 버전 UUID를 detail에 둘지 정한다. |
| Ck3-446-08 | #446 | #446에서 운영 코드도 시험도 부르지 않는 DraftInvoiceLine, InvoiceLineResponse, BillableMetricPricePriceRate의 보조 생성자를 DTO 파일 셋에 걸쳐 한 번에 지울지(유지보수 분류 두 파일 상한의 예외), 아니면 지적 위치인 DraftInvoiceResponse만 지울지 정한다. |
| Ck3-446-09 | #446 | OrganizationService.find(UUID)가 없으면 예외를 던져 naming.md의 get 뜻인데, 공급자 조회와 수정을 SupplierService(get, update)로 떼고 operationId findSupplier를 getSupplier로 바꿀지 정한다. |
| G-092 | #448 | Q3: 요청 감사가 수집 밖의 주체 없는 실패 요청(익명 401, 방화벽 400)과 429까지 지울 수 없는 audit_log에 계속 남길지, 남긴다면 보존 기간(occurred_at 파티션과 V25 트리거 변경)과 플랫폼 조회의 전체 count를 어떻게 할지. |
| G-095 | #448 | 도입사 감사 조회 GET /v1/audit-logs를 #442의 API 키 목록처럼 로그인 세션 전용(@MemberReadAccess)으로 좁힐지, 15692f85 시험이 단정한 대로 read API 키에도 열어 둘지 정한다. |
| C448-002 | #448 | Q3: 주체 없는 /v1 요청(익명 401, 잘못된 Bearer 401)을 지울 수 없는 audit_log에 계속 남길지, 뺄지, 아니면 남기되 감사 필터 앞에 IP 호출 제한이나 occurred_at 기준 보존 기간을 둘지. |
| G-169 | #448 | Q3: 수집 밖 경로의 401과 429(API 키 한도, 정점 #454 인증 제한)를 지울 수 없는 audit_log에 계속 남길지, 빼고 지표나 요청 로그로만 볼지, 남긴다면 audit_log 보존 기간을 둘지. |
| Ck1-448-04 | #448 | 운영자 감사 조회 GET /v1/operations/audit-logs에 선택 인수(도입사 id)를 더하고 /admin/audit 화면에 도입사 필터를 둘지, 둔다면 작업 공간 범위로 이미 쓰이는 organization_id와 겹치지 않는 조회 키 이름을 무엇으로 할지 정한다. |
| NT-inho-001 | #443 | 헤더 안내 토글과 안내 영역의 '시연'이라는 낱말을 도입사 관리자용 업무 안내 문구로 바꿀지 정해야 한다. |
| NT-inho-003 | #444 | 웹훅 failure_reason에 tls_error, internal_error 같은 값을 더해 도입사에게도 원인을 나눠 보일지(V28 CHECK, 전송 기록 API 값, 콘솔 라벨 변경), 지금처럼 connection_error 하나로 두고 운영자 로그로만 가를지 Ck2-011과 함께 정한다. |

## 막힌 것

| id | 층 | 까닭 |
| --- | --- | --- |
| Ck2-014 | #444 | not-owner #445: 이 줄을 #444에서 지우기만 하면 위층 반영 때 #445 TossPaymentsConfig가 쓰는 RestClient.Builder 자동 설정 빈과 org.springframework.boot.http.client 클래스가 사라져 #445 컴파일과 기동이 깨진다. 줄을 #445 build.gradle.kts로 옮기는 일은 T-yang 담당 층을 같이 바꿔야 끝난다. |
| Ck3-446-10 | #446 | needs-new-migration: price_rate에 부분 조건이 없는 도입사 인덱스가 있어야 한다. 필요한 SQL은 CREATE INDEX price_rate_organization_index ON price_rate (organization_id, billable_metric_code); 이다. V36 안에 넣어 고치는 길은 do-not-touch Ck3-446-01에 걸린다. |
| C446-001 | #446 | sync-conflict #446: backend/src/test/java/com/meterengine/global/OpenApiDocumentTest.java, frontend/e2e/auth.contract.spec.ts |
| G-110 | #446 | not-owner #449: 고치려면 #446 V41__create_invoice_view_links.sql의 invoice_view_link 표와 감사 제약을 #449 V44로 옮겨야 해 양성지 님 층을 같이 바꿔야 한다(T-inho brief: 판정이 맞으면 not-owner #449). |
| C447-011 | #447 | not-owner #447: 양성지 님 층의 시험이라 판정도 수정도 하지 않는다. C446-013 수정을 #447로 반영할 때 함께 맞춘다. |

## 새 지적

| id | 층 | 상태 | 내용 |
| --- | --- | --- | --- |
| NT-inho-001 | #443 | needs-decision | 실제 도입사 관리자에게 보이는 헤더 토글과 안내 영역이 '시연'이라는 낱말을 쓴다 |
| NT-inho-002 | #444 | already-fixed | 반영된 #442 workspace-operations.test.mjs가 #444 head에서 실패한다(restoreWebhookEndpoint 분기 없음) |
| NT-inho-003 | #444 | needs-decision | 도입사에게 보이는 웹훅 실패 사유가 인증서 오류와 우리 쪽 예외까지 connection_error 하나다 |

## 돌리지 못한 검사

- fix/s456-443-t-inho 0dfdbd3 screen: 3 passed (7.2s); 바로잡음: 이 실행은 17:09에 3ec2f28 빌드로 떠 남아 있던 프론트(pid 36188)를 상대로 돌아 이 sha 빌드를 확인하지 못했다. 이 sha의 실화면 확인은 같은 sha의 계약 명세 통과 기록과 다음 push 검사로 대신한다

## 위층 반영 때 볼 것

- X-042 (#443): #451 (console)/overview/page.tsx 22행의 순수납액 div에 className="is-settled"를 붙여야 개요의 초록 강조가 남는다. #444 admin/page.tsx는 손댈 것이 없고 반영되면 마지막 칸(발송 실패 통보)의 초록 배경이 사라진다.
- G-073 (#443): #452 29a78d0이 README 29, 135, 140행을 고쳐 위층 반영 때 겹친다. 겹치는 줄은 #452 문장을 택하되 136행 '시스템 글꼴' 문구는 #452 C452-010 몫이다. 31-32행과 112행(개발 모드 스위치, antd Layout 틀)은 G-081에서 고친다.
- G-086 (#443): 위층의 고객용 경로(#445 payment-method-registrations, #449 invoice-links)도 이 루트 오류 화면으로 떨어지고 '로그인 화면으로' 링크를 보인다. 고객용 화면에 맞는 별도 오류 경계나 문구는 그 화면이 있는 층에서 정한다.
- D-153 (#443): ConsoleShell.tsx의 header ref와 ResizeObserver 줄은 위층 반영 때 위층 ConsoleShell(정점 46행 header)에도 그대로 들어가야 한다. 위층에서 헤더 요소를 바꿨으면 ref를 같은 header에 단다.
- X-047 (#443): ConsoleShell.tsx의 header ref와 ResizeObserver 줄은 위층 반영 때 위층 ConsoleShell(정점 46행 header)에도 그대로 들어가야 한다. 위층에서 헤더 요소를 바꿨으면 ref를 같은 header에 단다.
- D-191 (#443): #453 permissions.real.spec.ts 284, 379행 @ 0fe7d2a가 합친 버튼 이름 '조회 전용, 권한 새로 조회'를 찾는다. 반영 때 범위 표시(.workspace-scope '조회 전용')와 버튼 '권한 새로 조회'로 나눠 맞춘다. 위층 ConsoleShell 53행에는 EnvironmentSwitch가 앞에 더 있다.
- D-083 (#443): 문제가 크게 보이는 줄은 위층 화면이다(#448 AuditScreen.tsx, #451 customers/[id]/page.tsx 40, 46행, PricingWorkspace.tsx 105행). 반영 뒤 그 화면에서 확인한다. 고객 상세 하단의 '고객 목록으로'와 '고객 보관' 분리는 #451 D-166 몫이다.
- X-076 (#443): 문제가 크게 보이는 줄은 위층 화면이다(#448 AuditScreen.tsx, #451 customers/[id]/page.tsx 40, 46행, PricingWorkspace.tsx 105행). 반영 뒤 그 화면에서 확인한다. 고객 상세 하단의 '고객 목록으로'와 '고객 보관' 분리는 #451 D-166 몫이다.
- D-082 (#443): 이 목록을 쓰는 화면은 #444 integrations, #450 UsageQuality, #451 overview다. 반영 뒤 그 화면 1440, 390 캡처로 링크 크기와 높이를 확인한다.
- X-073 (#443): 이 목록을 쓰는 화면은 #444 integrations, #450 UsageQuality, #451 overview다. 반영 뒤 그 화면 1440, 390 캡처로 링크 크기와 높이를 확인한다.
- X-078 (#443): 카드 등록 화면 registration.module.css 8, 12행(#445 4ace811)의 context h2(최대 30px)도 panel h1(최대 28px)보다 커서, #445에서 h2를 h1보다 작게 맞춘다.
- D-080 (#443): #451 (console)/overview/page.tsx 22행의 순수납액 div에 className="is-settled"를 붙여야 개요의 초록 강조가 남는다. #444 admin/page.tsx는 손댈 것이 없고 반영되면 마지막 칸(발송 실패 통보)의 초록 배경이 사라진다.
- D-084 (#443): 뒤집힘이 보이는 화면은 #445 payments/[id] 100-102행과 #449 같은 파일 113-115행이다. 결제 시도, 취소 내역 표의 열 순서(결과와 금액을 첫 열로)는 그 층 몫이다.
- D-097 (#443): 비활성 상태가 실제로 보이는 곳은 #449 InvoiceViewLinks.tsx 72, 90행이다. 초대 화면 두 버튼을 antd primary로 바꾸는 일은 #442 d4f1714 마크업(Server Component라 client 제출 버튼 파일이 필요)이라 #442 몫이다.
- D-240 (#443): 비활성 상태가 실제로 보이는 곳은 #449 InvoiceViewLinks.tsx 72, 90행이다. 초대 화면 두 버튼을 antd primary로 바꾸는 일은 #442 d4f1714 마크업(Server Component라 client 제출 버튼 파일이 필요)이라 #442 몫이다.
- D-081 (#443): 간격이 크게 보이는 운영 현황(#444 80a3ef1)과 감사 기록(#448 15692f8) 운영자 화면은 반영 뒤 그 층에서 확인한다.
- G-087 (#443): #452 29a78d0이 README 29, 135, 140행을 고쳐 위층 반영 때 겹친다. 겹치는 줄은 #452 문장을 택하되 136행 '시스템 글꼴' 문구는 #452 C452-010 몫이다. 31-32행과 112행(개발 모드 스위치, antd Layout 틀)은 G-081에서 고친다.
- G-083 (#443): ConsoleShell.tsx의 header ref와 ResizeObserver 줄은 위층 반영 때 위층 ConsoleShell(정점 46행 header)에도 그대로 들어가야 한다. 위층에서 헤더 요소를 바꿨으면 ref를 같은 header에 단다.
- D-021 (#443): #451 events/page.tsx 38행 수집 로그 탭 이름은 X-071이 맡는다. #444 integrations/[id]/page.tsx 17행 뒤로 가기 '연동 설정으로'는 'API 키와 통보로'로 위층에서 맞춘다. #450 UsageQuality '처리 결과 통보 연결'은 동작 링크라 그대로 둔다.
- D-145 (#443): #451 events/page.tsx 38행 수집 로그 탭 이름은 X-071이 맡는다. #444 integrations/[id]/page.tsx 17행 뒤로 가기 '연동 설정으로'는 'API 키와 통보로'로 위층에서 맞춘다. #450 UsageQuality '처리 결과 통보 연결'은 동작 링크라 그대로 둔다.
- D-220 (#443): #451 events/page.tsx 38행 수집 로그 탭 이름은 X-071이 맡는다. #444 integrations/[id]/page.tsx 17행 뒤로 가기 '연동 설정으로'는 'API 키와 통보로'로 위층에서 맞춘다. #450 UsageQuality '처리 결과 통보 연결'은 동작 링크라 그대로 둔다.
- G-077 (#443): WorkspaceView.tsx STATUS_LABELS의 owner, admin, member는 셋째 파일이라 그대로 두었고 값은 같다. 이 정의로 바꿀지는 후속에서 정한다. '전용' 접미는 셸 안 scopeLabel 한 곳에서 만든다(38f991c).
- D-020 (#443): #453 permissions.real.spec.ts 284, 379행 @ 0fe7d2a가 합친 버튼 이름 '조회 전용, 권한 새로 조회'를 찾는다. 반영 때 범위 표시(.workspace-scope '조회 전용')와 버튼 '권한 새로 조회'로 나눠 맞춘다. 위층 ConsoleShell 53행에는 EnvironmentSwitch가 앞에 더 있다.
- D-038 (#443): #453 permissions.real.spec.ts 284, 379행 @ 0fe7d2a가 합친 버튼 이름 '조회 전용, 권한 새로 조회'를 찾는다. 반영 때 범위 표시(.workspace-scope '조회 전용')와 버튼 '권한 새로 조회'로 나눠 맞춘다. 위층 ConsoleShell 53행에는 EnvironmentSwitch가 앞에 더 있다.
- D-058 (#443): #453 permissions.real.spec.ts 284, 379행 @ 0fe7d2a가 합친 버튼 이름 '조회 전용, 권한 새로 조회'를 찾는다. 반영 때 범위 표시(.workspace-scope '조회 전용')와 버튼 '권한 새로 조회'로 나눠 맞춘다. 위층 ConsoleShell 53행에는 EnvironmentSwitch가 앞에 더 있다.
- D-137 (#443): #453 permissions.real.spec.ts 284, 379행 @ 0fe7d2a가 합친 버튼 이름 '조회 전용, 권한 새로 조회'를 찾는다. 반영 때 범위 표시(.workspace-scope '조회 전용')와 버튼 '권한 새로 조회'로 나눠 맞춘다. 위층 ConsoleShell 53행에는 EnvironmentSwitch가 앞에 더 있다.
- D-167 (#443): #453 permissions.real.spec.ts 284, 379행 @ 0fe7d2a가 합친 버튼 이름 '조회 전용, 권한 새로 조회'를 찾는다. 반영 때 범위 표시(.workspace-scope '조회 전용')와 버튼 '권한 새로 조회'로 나눠 맞춘다. 위층 ConsoleShell 53행에는 EnvironmentSwitch가 앞에 더 있다.
- D-229 (#443): #453 permissions.real.spec.ts 284, 379행 @ 0fe7d2a가 합친 버튼 이름 '조회 전용, 권한 새로 조회'를 찾는다. 반영 때 범위 표시(.workspace-scope '조회 전용')와 버튼 '권한 새로 조회'로 나눠 맞춘다. 위층 ConsoleShell 53행에는 EnvironmentSwitch가 앞에 더 있다.
- X-062 (#443): #453 permissions.real.spec.ts 284, 379행 @ 0fe7d2a가 합친 버튼 이름 '조회 전용, 권한 새로 조회'를 찾는다. 반영 때 범위 표시(.workspace-scope '조회 전용')와 버튼 '권한 새로 조회'로 나눠 맞춘다. 위층 ConsoleShell 53행에는 EnvironmentSwitch가 앞에 더 있다.
- D-165 (#443): #446부터 있는 PricingWorkspace.tsx(c27b229 114행)의 대화상자 본문도 같은 padding이라, 그 층에서 padding 8, marginInline -8로 맞춘다.
- G-046 (#443): 웹훅 pending이 '결과 확인 중'으로 보이는 부분은 #444 integrations/[id]/page.tsx 26행 statusLabels에서 시작하고 그 문구를 고정한 e2e는 #453 workspace.contract.spec.ts 122행이다. #443 수정 범위 밖이라 위층에서 맞춘다.
- Ck2-002 (#444): #447 2ec5d85가 update에 보관 확인(archivedAt이면 409)을, restore()를 더했다. 반영 때 update의 보관 확인은 트랜잭션 밖 get() 자리에 두고, restore()도 validate(DNS 조회)를 transactionOperations.execute 밖에서 먼저 부르고 countAndLock, restore, 감사 기록만 안에서 하도록 맞춘다. 생성자에 TransactionOperations가 늘어 #447 이후의 WebhookEndpointServiceTest 생성자 호출에도 TransactionOperations.withoutTransaction()을 넘긴다.
- Ck2-003 (#444): WebhookDeliveryRepository.backlog() 조회는 도입사와 환경을 가리지 않는 전역 집계라 #453 23b1844의 environment_scope_id 조건을 더하지 않는다. alerting.yml 끝의 webhook-delivery-backlog 규칙과 deploy/README.md 경보 표의 '웹훅 발송 적체' 행은 위층에서 같은 자리에 줄이 더해졌으면 둘 다 둔다.
- Ck2-006 (#444): #453부터 findByEndpoint와 countByEndpoint가 environment_scope_id 조건을 더한다(V48, 조율 담당 몫 파일). 이 색인의 앞부분(organization_id, webhook_endpoint_id)으로 범위를 좁힌 뒤 environment_scope_id는 걸러 읽는다. 환경까지 넣은 색인이 필요하면 V48 쪽 결정으로 넘긴다. V28 체크섬이 바뀌어 이 파일을 적용한 로컬 볼륨은 다시 만들어야 기동한다.
- Ck2-008 (#444): openapi.yaml의 CreateWebhookEndpointRequest, UpdateWebhookEndpointRequest event_types에 enum과 minItems 1이 생긴다. 위층 반영 때 openapi.yaml이 충돌하면 그 층에서 ./gradlew build로 다시 만든다.
- Ck2-013 (#444): 이름을 바꿔 위층 반영 merge 뒤 #445부터 컴파일이 깨진다. 반영 때 #445 f382376의 TossPaymentsBilling.java 13행, UpdateTossPaymentsCredentialRequest.java 26행, BillingKeyService.java의 SecretMask.lastFour 두 곳(정점 230, 248행)을 SecretMask.mask로 바꾼다. 동작은 같다.
- C444-008 (#444): workspace-actions.ts의 updateWebhookEndpoint 줄은 위층(#451 X-050, X-093이 보는 같은 파일)에서도 같은 내용이다. 반영 때 그 줄이 충돌하면 description 필드를 남긴다. integrations/[id]/page.tsx 수정 폼의 숨은 description 필드도 같다.
- D-226 (#444): 새 시험 scheduled-job-labels.test.mjs는 OperationsService의 작업 목록과 WorkspaceView의 JOB_LABELS를 맞대 본다. #445부터 정점까지 작업 목록은 이 층과 같다. 반영 때 JOB_LABELS 줄이 충돌하면 unbilled_usage_report 항목을 남겨야 위층에서도 이 시험이 통과한다.
- G-152 (#444): 위층(#450 notification, #453 23b1844 CRYPTO_ENCRYPTION_KEY 표 행)이 같은 README의 구조 표와 설정 절을 바꿨다. 반영 때 구조 표의 webhook, dashboard 행이 겹치면 위층 행(webhook, notification 합친 행)을 남기고 이 층의 global.crypto 행과 의존 방향 문장을 더한다. 설정 절의 CRYPTO_ENCRYPTION_KEY는 위층 표 행과 이 층 문장을 하나로 합친다(위층은 토스 키와 빌링키, 이 층은 웹훅 비밀값과 secret_unavailable). #450부터는 meterengine.webhook.enabled=false가 미청구 통보 작업도 멈추는지 그 층 기준으로 맞춘다.
- U14-B1 (#444): #453 23b1844가 WebhookDeliveryRepository.claim과 UPDATE 문에 environment_scope_id를 더했다. 반영 때 claim 앞의 lease_expired UPDATE는 expireLeases()로 옮긴 채 두고(환경 조건 없이 전역으로 끝내는 동작은 같다), 정점 deploy/README.md 495행의 경보 표 문장을 이 층 문장으로 맞춘다.
- U5-B1 (#444): alerting.yml scheduled-job-missed-day 식과 meterengine-billing.json 마지막 성공 패널 식이 바뀐다. 위층에서 같은 줄이 충돌하면 max_over_time(...[26h]) 형태를 남긴다. 코드로 게이지를 다시 읽는 방안(ScheduledJobRunService)은 넣지 않았다.
- X-014 (#446): workspace-actions.ts의 updateSupplier 줄과 settings/page.tsx 공급자 정보 필드는 위층에서 같은 내용이다. 반영 때 충돌하면 readBusinessRegistrationNumber 호출과 hint를 남긴다. 위층 mvp.real.spec.ts와 environment-cycle.real.spec.ts는 getByLabel(/^사업자등록번호/)와 숫자 10자리만 써서 영향이 없다.
- D-040 (#446): #451 e5fca11이 단가 창 검사를 submit()으로 옮겼다(정점 139-171행). 반영 때 단가 입력의 placeholder와 빈 처음 값을 남기고, 0원 확인은 submit()의 action 호출 직전에 둔다. 단가 라벨에 필수 표시를 더하지 않아 정점 mvp.real.spec.ts 90행의 getByLabel 정확 일치는 그대로 맞는다.
- D-180 (#446): #451 e5fca11이 단가 창 검사를 submit()으로 옮겼다(정점 139-171행). 반영 때 단가 입력의 placeholder와 빈 처음 값을 남기고, 0원 확인은 submit()의 action 호출 직전에 둔다. 단가 라벨에 필수 표시를 더하지 않아 정점 mvp.real.spec.ts 90행의 getByLabel 정확 일치는 그대로 맞는다.
- X-005 (#446): #451 e5fca11이 단가 창 검사를 submit()으로 옮겼다(정점 139-171행). 반영 때 단가 입력의 placeholder와 빈 처음 값을 남기고, 0원 확인은 submit()의 action 호출 직전에 둔다. 단가 라벨에 필수 표시를 더하지 않아 정점 mvp.real.spec.ts 90행의 getByLabel 정확 일치는 그대로 맞는다.
- D-109 (#446): #451 e5fca11이 같은 대화상자의 submit과 actions.ts를 바꿨다. 반영 때 실패 안내는 폼 위 failureRef 자리에, 수정 모드 안내는 이름 입력 위에 두고, toFailureState의 billableMetricFailureMessage 호출을 남긴다.
- Ck3-446-02 (#446): 반영 때 PriceRateService의 effective_until 검사 줄이 충돌하면 validation_error 쪽을 남기고, PriceRateController 400 설명과 openapi.yaml은 그 층에서 ./gradlew build로 다시 만든다.
- Ck3-446-04 (#446): 위층 반영 때 이 설명이 충돌하면 #447 2ec5d85의 보관 동작 설명을 택하고 openapi.yaml은 그 층에서 다시 만든다.
- Ck3-446-05 (#446): 반영 때 listPricePlans 시그니처가 충돌하면 위층의 status 파라미터와 @EnvironmentReady를 살리고 이 층의 설명, 파라미터 설명, 400과 404를 더한다. status 파라미터 설명과 그 400 경우도 같이 적는다. openapi.yaml은 그 층에서 다시 만든다.
- Ck3-446-06 (#446): 정점에서는 서비스 검사가 107행으로 옮겨져 있다. 반영 때 PriceRateService.create 첫 줄의 isValidPricing 검사를 남기고 뒤쪽의 invalid("model") 줄은 지운 상태를 택한다. DTO의 @AssertTrue를 지운 것도 남긴다.
- D-120 (#446): 반영 때 onCancel과 취소 버튼이 close()를 부르는 것을 남긴다.
- D-119 (#446): #451 e5fca11이 단가 창 검사를 submit()으로 옮겼다(정점 139-171행). 반영 때 단가 입력의 placeholder와 빈 처음 값을 남기고, 0원 확인은 submit()의 action 호출 직전에 둔다. 단가 라벨에 필수 표시를 더하지 않아 정점 mvp.real.spec.ts 90행의 getByLabel 정확 일치는 그대로 맞는다.
- C448-001 (#448): #450 8bb17c3이 RequestAuditFilter에 REQUEST_ID_ATTRIBUTE를, #453 23b1844가 요청 감사에 environment_id를 더했다. 반영 때 record 호출의 attributedActor를 남기고, 도입사를 비운 운영자 행의 environment_id도 비는지 그 층에서 확인한다.

## fixed-above로 둔 것

- C446-002 (#446): fixed-above #451 e5fca11: 정점 billable-metric-prices/page.tsx가 /pricing으로 redirect해 옛 Section을 쓰는 곳이 없다. 이 층 head에서는 아직 고장이라 층마다 고칠지는 배포 단위 결정 뒤 조율 담당이 정한다
- C446-006 (#446): fixed-above #447 2ec5d85: VersionedPricingIntegrationTest의 최신_단가_보관, 최신_요금제_보관 시험(정점 178-209행)이 겹친 달의 최신 버전 선택을 잡는다. 이 층에는 그 시험이 없다
- C446-010 (#446): fixed-above #451 e5fca11: 정점 pricing/page.tsx 37행이 선택한 미터 하나의 단가 이력만 부른다. 이 층 head는 미터마다 부른다

## 마지막 실화면 점검

| 층 | sha | 통과한 명세 | 화면 결과 |
| --- | --- | --- | --- |
| #443 | b99d2ef | 1/1 | 고친 화면 66건 after 대비 콘솔 오류, 넘침, axe 증가 없음 |
| #444 | c0a9f4a | 1/1 | 고친 화면 4건 after 대비 콘솔 오류, 넘침, axe 증가 없음 |

## 사용량

사용량 도구가 없어 수치를 남기지 못했다(사람이 /usage로 지켜본다). #443 한 층에 판정 Workflow 1회(에이전트 44개, 하위 에이전트 토큰 약 406만)와 커밋 33개 각각의 10절 검사, 마지막 실화면 점검을 썼다. 남은 7개 층은 항목이 280건이라 #443과 비슷한 비율이면 이보다 약 3배가 더 든다고 본다(추정).

## 메모

- 사용량 도구(mcp__ccd_session_mgmt__get_usage)가 없다. 사람이 /usage로 지켜보고 5시간이나 주간 70% 즈음이면 멈추라고 알려 준다.
- 2026-10-28T17:07+09:00 사람 지시로 push 방식을 바꿨다. 이 시각부터 맡은 feat 브랜치에 직접 push하지 않고, 층마다 origin/feat/<브랜치>를 base로 만든 fix/s456-<PR 번호>-t-inho 후속 브랜치에 push하며, 그 층 첫 push 때 base가 feat 브랜치인 PR을 연다(Draft 아님, merge하지 않음). origin/feat/<브랜치>가 움직이면 후속 브랜치에 merge한다. 바꾸기 전에 feat 브랜치에 push한 커밋은 없다. state.json branches에는 후속 브랜치를 적는다.
- 후속 브랜치에서 고친 항목은 report.json의 branch를 후속 브랜치로 적고 layer는 원래 PR 번호를 둔다. check-report.mjs는 후속 브랜치를 스택 목록에 두지 않아 후속 브랜치 push마다 '이 세션 커밋을 읽지 못했다' 오류를 낸다. 넘기기 도구가 바뀔 때까지 이 오류만 빼고, 같은 기준(push한 sha의 kind별 통과 기록, 커밋과 항목, push 기록 대응)을 .s456-work의 보조 검사로 확인한 뒤 보고를 push한다.
- 2026-10-28T17:55+09:00 사람 지시: 이 뒤 커밋 메시지에 Co-Authored-By 줄을 넣지 않고 새로 여는 PR 본문 끝에 Generated with Claude Code 줄을 넣지 않는다. 이미 push한 3ec2f28, 0dfdbd3과 #465 본문은 그대로 둔다.
- PR #465에 조율 담당 댓글(2026-10-28 17:53 KST): #443 작업을 마치고 다른 층으로 넘어갈 때 조율 담당이 #465를 feat/product-design에 merge하고, 그 전까지 이 브랜치에 push하라는 안내와 0fc4894를 받으라는 요청. 0fc4894는 93491f9로 받았다.
- 2026-10-28 #444 시작에서 멈춤: 바꾸지 않은 #444 head(f3855f4)에서 frontend-unit이 실패한다(NT-inho-002). common-brief 10절, 12절대로 전체를 멈췄다. #444 U1-B1 커밋 235ebf1은 fix/s456-444-t-inho에 로컬로만 있고 push하지 않았다(frontend-unit 말고는 통과).
- 2026-10-28T21:36:06+09:00 재시작: 조율 담당이 NT-inho-002를 4222fbd로 풀었다(origin/feat/webhook-jobs 178e19e). 바꾸지 않은 178e19e에서 frontend(lint, build), frontend-unit(13 통과), 계약 명세(53 통과)가 통과해 #444를 이어 간다. 시작 점검을 다시 돌려 모두 통과했다(push 확인은 feat/webhook-jobs dry-run).
- 2026-10-28T21:36:06+09:00 PR #465는 조율 담당이 feat/product-design에 merge했다(85de660, 2026-10-28T12:27:53Z). origin의 fix/s456-443-t-inho는 지워졌다. #443 push 33건은 85de660을 통해 origin에 있다. 보조 검사는 report.json fix_merges의 머지 커밋으로 이 push들을 확인한다.
- 2026-10-28T21:39:52+09:00 #465 머지와 fix/s456-443-t-inho 삭제 뒤 check-report.mjs는 #443 항목 커밋과 push가 origin/fix/s456-443-t-inho에 없다, feat/product-design의 이 세션 커밋을 올린 push가 없다는 오류를 낸다. 보조 검사가 이 오류를 줄마다 따져, 그 커밋이 머지 커밋 85de660 안에 있고 85de660이 origin/feat/product-design에 있으며 fix/s456-443-t-inho push로 올린 것일 때만 넘긴다. 그 밖의 오류가 하나라도 있으면 보고를 push하지 않는다.
- 2026-10-28T21:49:39+09:00 #444 판정: 판정 Workflow(29 에이전트, f3855f4 기준) 결과를 메인 세션이 178e19e에서 다시 대조해 적었다. #443 수정(e26db2c, 18be9f0)이 178e19e로 들어와 D-188, D-093, D-234, X-041은 not-owner 대신 already-fixed가 됐다. Ck2-005와 Ck2-010은 같은 원인인데 판정이 갈려, 박성종 님이 정한 failed_webhook_count 의미를 바꾸는 일이라 둘 다 needs-decision으로 맞췄다. Ck2-013은 판정 에이전트가 needs-decision을 권했지만 #444에서 바뀌는 파일이 SecretMask.java 하나라 6절 유지보수 기준대로 고치고 #445 호출부는 propagation_note에 적는다.
- 2026-10-28T21:55:14+09:00 #444 중간 push의 screen 검사(auth 실화면, 계약 명세)는 층에서 띄운 실화면 환경을 쓴다. 백엔드를 바꾼 커밋 뒤에는 backend-restart로 그 HEAD의 백엔드를 다시 띄우고, V__를 고친 커밋 뒤에는 DB부터 다시 만든다. Ck2-001 push(02b14f4)의 auth 실화면 검사는 de7565b에서 띄운 백엔드로 돌렸다(바뀐 곳은 웹훅 발송 헤더뿐이라 인증 화면과 관계없다).
- 2026-10-28T23:03:41+09:00 #444 마지막 실화면 점검 통과: c0a9f4a에서 DB부터 새로 띄워 auth.real 3개 통과(뺀 시험 없음), 고친 화면 4건 증가 없음. #444에서 끝 상태가 없는 것은 G-144(T-inho brief대로 #450 차례에 고친다)뿐이다.
- 2026-10-28T23:28:54+09:00 #444, #446에서 fixed로 적은 항목의 judged_at.top_sha가 보조 스크립트 기본값(0fe7d2a)으로 들어간 것을 실제로 대조한 정점 8dd028a로 고쳤다(20건). 이 항목들을 판정하고 정점과 대조할 때 origin/feat/rds-auto-stop은 8dd028a였다(2026-10-29 들어 65d4150으로 움직였다). 이 뒤 fixed 기록은 그때의 정점 sha를 쓴다.
- 2026-10-29T00:15:14+09:00 #446 demo 검사: 10절 표가 pnpm 검사를 #447부터로 적어, #446에서는 demo kind를 python3 -m unittest discover로 돌린다. 이 층 demo/package.json에는 build 스크립트가 없어 e4da2fa에서 pnpm -r build가 실패했다(이 변경과 무관, 기록은 checks에 fail로 남김). 같은 커밋에서 corepack pnpm -r --if-present typecheck, lint, test는 모두 통과했다.
- 2026-10-29T00:16:15+09:00 PR #466은 조율 담당이 feat/webhook-jobs에 merge했다(be40088, 2026-10-28T15:14:43Z). origin의 fix/s456-444-t-inho는 지워졌다. #444 push는 be40088을 통해 origin에 있고, 보조 검사는 fix_merges의 머지 커밋으로 확인한다.
- 2026-10-29T00:18:26+09:00 #446 동기화 충돌: origin/feat/pricing-aggregation이 f91912f(#466 반영, 70357f5, ad9f7fa 포함)로 움직여 fix/s456-446-t-inho에 merge하니 OpenApiDocumentTest.java와 auth.contract.spec.ts가 충돌했다. 두 파일 모두 #444와 #446에서 같은 기준 시험 앞에 새 시험을 더한 자리라, 반영할 때 양쪽 시험을 모두 남기면 된다. 9절대로 충돌을 풀지 않고 merge를 취소했고, push하지 못한 C446-001 커밋 e4da2fa를 evidence/sync-conflict/446/0001-fix-ApiClient-verify.patch로 남겼다. PR #468(fix/s456-446-t-inho -> feat/pricing-aggregation)도 같은 두 파일에서 충돌한다. #446의 남은 항목은 충돌이 풀린 뒤 이어 고치고, 그때까지 #448로 넘어간다.
