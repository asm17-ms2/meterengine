미완료 282건, 결과: 끝 상태 90/372건 (fixed 73, refuted 1, already-fixed 0(그중 fixed-above 0), needs-decision 16, blocked 0(그중 no-evidence 0))
push: fix/s456-443-t-inho 31번(마지막 18be9f0)
정할 것: 17건 (X-012, D-071, G-075, G-085, D-011, D-065, G-072, G-080, C443-002, D-039, D-055, D-244, D-022, G-084, G-078, D-237, NT-inho-001). 로컬 DB 다시 만들기: 없음

상태: running, 갱신 2026-10-28T19:29:35+09:00

## 층별 상태

| 층 | 브랜치 | 전체 | fixed | refuted | already-fixed | needs-decision | blocked | 미완료 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| #443 | fix/s456-443-t-inho | 92 | 73 | 1 | 0 | 16 | 0 | 2 |
| #444 | feat/webhook-jobs | 40 | 0 | 0 | 0 | 0 | 0 | 40 |
| #446 | feat/pricing-aggregation | 50 | 0 | 0 | 0 | 0 | 0 | 50 |
| #448 | feat/request-audit | 24 | 0 | 0 | 0 | 0 | 0 | 24 |
| #450 | feat/event-ingestion | 38 | 0 | 0 | 0 | 0 | 0 | 38 |
| #451 | feat/billing-queries | 96 | 0 | 0 | 0 | 0 | 0 | 96 |
| #454 | feat/auth-rate-limit | 19 | 0 | 0 | 0 | 0 | 0 | 19 |
| #455 | feat/rds-auto-stop | 12 | 0 | 0 | 0 | 0 | 0 | 12 |
| #447 | feat/resource-archive | 1 | 0 | 0 | 0 | 0 | 0 | 1 |

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
| NT-inho-001 | #443 | 헤더 안내 토글과 안내 영역의 '시연'이라는 낱말을 도입사 관리자용 업무 안내 문구로 바꿀지 정해야 한다. |

## 막힌 것

| id | 층 | 까닭 |
| --- | --- | --- |

## 새 지적

| id | 층 | 상태 | 내용 |
| --- | --- | --- | --- |
| NT-inho-001 | #443 | needs-decision | 실제 도입사 관리자에게 보이는 헤더 토글과 안내 영역이 '시연'이라는 낱말을 쓴다 |

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

## fixed-above로 둔 것

없음

## 마지막 실화면 점검

아직 없음

## 사용량

사용량 도구가 없다. 사람이 /usage로 지켜보고 상한(5시간, 주간 70%) 즈음이면 멈추라고 알려 준다.

## 메모

- 사용량 도구(mcp__ccd_session_mgmt__get_usage)가 없다. 사람이 /usage로 지켜보고 5시간이나 주간 70% 즈음이면 멈추라고 알려 준다.
- 2026-10-28T17:07+09:00 사람 지시로 push 방식을 바꿨다. 이 시각부터 맡은 feat 브랜치에 직접 push하지 않고, 층마다 origin/feat/<브랜치>를 base로 만든 fix/s456-<PR 번호>-t-inho 후속 브랜치에 push하며, 그 층 첫 push 때 base가 feat 브랜치인 PR을 연다(Draft 아님, merge하지 않음). origin/feat/<브랜치>가 움직이면 후속 브랜치에 merge한다. 바꾸기 전에 feat 브랜치에 push한 커밋은 없다. state.json branches에는 후속 브랜치를 적는다.
- 후속 브랜치에서 고친 항목은 report.json의 branch를 후속 브랜치로 적고 layer는 원래 PR 번호를 둔다. check-report.mjs는 후속 브랜치를 스택 목록에 두지 않아 후속 브랜치 push마다 '이 세션 커밋을 읽지 못했다' 오류를 낸다. 넘기기 도구가 바뀔 때까지 이 오류만 빼고, 같은 기준(push한 sha의 kind별 통과 기록, 커밋과 항목, push 기록 대응)을 .s456-work의 보조 검사로 확인한 뒤 보고를 push한다.
- 2026-10-28T17:55+09:00 사람 지시: 이 뒤 커밋 메시지에 Co-Authored-By 줄을 넣지 않고 새로 여는 PR 본문 끝에 Generated with Claude Code 줄을 넣지 않는다. 이미 push한 3ec2f28, 0dfdbd3과 #465 본문은 그대로 둔다.
