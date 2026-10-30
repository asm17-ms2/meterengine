결과: 끝 상태 372/372건 (fixed 332, refuted 4, already-fixed 29(그중 fixed-above 3), needs-decision 7, blocked 0(그중 no-evidence 0))
push: fix/s456-443-t-inho 33번(마지막 b99d2ef), fix/s456-444-t-inho 14번(마지막 c0a9f4a), fix/s456-446-t-inho 9번(마지막 a7a789e), fix/s456-448-t-inho 9번(마지막 cea0a76), fix/s456-t-inho-real-spec-baseline 1번(마지막 3373654), fix/s456-t-inho-events-query 1번(마지막 e4c8c17), fix/s456-t-inho-billing-api 1번(마지막 a0ec597), fix/s456-t-inho-webhook-audit-ops 1번(마지막 6039fad), fix/s456-t-inho-auth-security 1번(마지막 abc82c5), fix/s456-t-inho-demo-infra 1번(마지막 aac9c37), fix/s456-t-inho-list-screens 1번(마지막 58be815), fix/s456-t-inho-work-screens 2번(마지막 df26abc), fix/s456-t-inho-shell-design 1번(마지막 7fb47e4)
정할 것: 11건 (X-012, D-071, G-075, G-085, C443-002, Ck3-451-02, Nk3-011, NT-inho-001, NT-inho-004, NT-inho-008, NT-inho-010). 로컬 DB 다시 만들기: fix/s456-444-t-inho V28__create_webhook_tables.sql

상태: running, 갱신 2026-10-30T10:18:48+09:00

## 층별 상태

| 층 | 브랜치 | 전체 | fixed | refuted | already-fixed | needs-decision | blocked | 미완료 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| #443 | fix/s456-443-t-inho | 92 | 85 | 1 | 1 | 5 | 0 | 0 |
| #444 | fix/s456-444-t-inho | 40 | 33 | 0 | 7 | 0 | 0 | 0 |
| #446 | fix/s456-446-t-inho | 50 | 43 | 0 | 7 | 0 | 0 | 0 |
| #448 | fix/s456-t-inho-webhook-audit-ops | 24 | 22 | 1 | 1 | 0 | 0 | 0 |
| #450 | fix/s456-t-inho-events-query | 38 | 36 | 0 | 2 | 0 | 0 | 0 |
| #451 | feat/billing-queries | 96 | 87 | 1 | 6 | 2 | 0 | 0 |
| #454 | feat/auth-rate-limit | 19 | 14 | 1 | 4 | 0 | 0 | 0 |
| #455 | fix/s456-t-inho-demo-infra | 12 | 11 | 0 | 1 | 0 | 0 | 0 |
| #447 | fix/s456-t-inho-billing-api | 1 | 1 | 0 | 0 | 0 | 0 | 0 |

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
| D-011 | #443 | 455ced3, 079b1fa, ad80761, 811994d, c91c6f9 | 폰 헤더에 업무 환경만 남기고 계정 조작을 메뉴 서랍으로 옮긴다 | on a phone the sticky header keeps only the brand and environment and the menu drawer holds the account controls: 실패 (헤더에 계정 조작이 보임) |
| D-065 | #443 | 455ced3, 079b1fa, ad80761, 811994d, c91c6f9 | 폰 헤더에 업무 환경만 남기고 계정 조작을 메뉴 서랍으로 옮긴다 | on a phone the sticky header keeps only the brand and environment and the menu drawer holds the account controls: 실패 (헤더에 계정 조작이 보임) |
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
| G-072 | #443 | 9fdea12, c91c6f9 | globals.css의 토큰과 같은 색 리터럴을 var()로 바꾼다 | globals.css uses its own color tokens instead of repeating the same literal values: 실패 (토큰과 같은 리터럴 21줄) |
| L-001 | #443 | f07a01f | 도입사 이름을 span으로 감싸 말줄임을 걸고 아이콘이 줄지 않게 했다 | a long organization name is cut with an ellipsis and keeps the header icon inside the link: 실패 Expected <= 300.71875 Received 317.90625 (320px, 아이콘 오른쪽 끝) |
| D-083 | #443 | 8abe196 | .workspace-actions를 세로 가운데로 맞추고 필드 줄은 버튼만 입력 아래선에 맞췄다 | 없음(보이는 정렬만 바뀌어 시험으로 잡을 수 없다. evidence/D-083 before, after 캡처(members, admin-users, customers)) |
| X-076 | #443 | 8abe196 | .workspace-actions를 세로 가운데로 맞추고 필드 줄은 버튼만 입력 아래선에 맞췄다 | 없음(보이는 정렬만 바뀌어 시험으로 잡을 수 없다. evidence/D-083 before, after 캡처(members, admin-users, customers)) |
| D-082 | #443 | 3901315 | .setup-list 조치 링크를 기본 글자 크기와 44px 높이로 맞췄다 | 없음(git grep -n setup-list HEAD -- frontend/src/**/*.tsx: 이 층에는 쓰는 화면이 없다; git grep -n setup-list origin/feat/rds-auto-stop:) |
| X-073 | #443 | 3901315 | .setup-list 조치 링크를 기본 글자 크기와 44px 높이로 맞췄다 | 없음(git grep -n setup-list HEAD -- frontend/src/**/*.tsx: 이 층에는 쓰는 화면이 없다; git grep -n setup-list origin/feat/rds-auto-stop:) |
| X-078 | #443 | b6d0822 | 옆 패널 h2를 clamp(22px, 1.8vw, 26px)로 낮춰 과업 제목보다 작게 했다 | the login side panel title is smaller than the task title: 실패 Expected < 34 Received 42 (1440px 패널 h2) |
| G-080 | #443 | ff14477 | 늘 숨겨진 kicker prop과 호출부 인자, 규칙을 지운다 | workspace headers no longer carry a kicker that is always hidden: 실패 (kicker 인자 15곳과 숨김 규칙) |
| D-080 | #443 | e26db2c | 지표 띠 초록 배경을 마지막 칸이 아니라 .is-settled 칸에만 건다 | 없음(git grep -n finance-flow HEAD -- frontend/src: 이 층에는 globals.css 정의만 있고 쓰는 화면이 없다; git grep -n finance-flow origin/feat/) |
| D-084 | #443 | d4129fb | 767px 이하 WorkspaceTable 레코드 제목을 14px로 낮췄다 | 없음(보이는 크기만 바뀌어 시험으로 잡을 수 없다. evidence/D-084 before, after 캡처(members, integrations 390). integrations PNG는 키 정보가 보일 수 있어 보고) |
| D-097 | #443 | c002716 | .month-apply에 hover, 누름, 비활성 상태를 주었다 | 없음(보이는 상태만 바뀌어 저장소 시험으로 두지 않았다. /login에 .month-apply 버튼을 넣어 계산된 스타일을 쟀다(.s456-work/probe-month.mjs). 고치기 전 hover와 disabled가) |
| D-240 | #443 | c002716 | .month-apply에 hover, 누름, 비활성 상태를 주었다 | 없음(보이는 상태만 바뀌어 저장소 시험으로 두지 않았다. /login에 .month-apply 버튼을 넣어 계산된 스타일을 쟀다(.s456-work/probe-month.mjs). 고치기 전 hover와 disabled가) |
| D-081 | #443 | 18be9f0 | .operator-content를 flex column, gap 28px로 두었다 | 없음(보이는 간격만 바뀌어 시험으로 잡을 수 없다. evidence/D-081 before, after 캡처(admin-organizations, admin-users 1440, 390)) |
| G-076 | #443 | 4455641 | 어디에서도 쓰지 않는 선택자(.console-content, .tenant-*, .workspace-grid-wide, .setup-state)를 지웠다 | 없음(쓰지 않는 CSS 삭제라 실패하는 시험이 없다. 지운 선택자는 층 head와 정점 b8abfdf frontend/src(globals.css 제외)에서 git grep 0건. 삭제 전후 19개 라우트 화소 비교에서 ) |
| C443-001 | #443 | 55ce723 | 스켈레톤을 aria-hidden div로 감싸고 상태 영역에서 aria-busy, aria-label을 뺐다 | 없음(개발 서버 /customers?state=loading의 상태 영역 접근성 트리(.s456-work/verify-skeleton.mjs): 고치기 전 status 이름 '데이터를 불러오고 있습니다' 안에 list와 ) |
| G-087 | #443 | cc3de53 | README의 / 설명, 스타일 규칙, 테마, 글꼴 줄과 화면 표를 코드에 맞췄다 | 없음(문서 변경: 고친 README 줄을 layout.tsx 8-14행, AppProviders.tsx 10-19행, page.tsx, HomePage.module.css와 대조; 글꼴 버전: PretendardVaria) |
| D-039 | #443 | 455ced3, 079b1fa, ad80761, 811994d, c91c6f9 | 폰 헤더에 업무 환경만 남기고 계정 조작을 메뉴 서랍으로 옮긴다 | on a phone the sticky header keeps only the brand and environment and the menu drawer holds the account controls: 실패 (헤더에 계정 조작이 보임) |
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
| D-244 | #443 | 9fdea12, c91c6f9 | globals.css의 토큰과 같은 색 리터럴을 var()로 바꾼다 | globals.css uses its own color tokens instead of repeating the same literal values: 실패 (토큰과 같은 리터럴 21줄) |
| D-022 | #443 | 5e49f8f | 로고 마크를 BrandMark 하나로 묶고 소개 화면은 크기만 키운다 | the console and the introduction page draw the logo mark from one component that only changes its size: 실패 (마크 사본 두 곳) |
| G-084 | #443 | 5e49f8f | 로고 마크를 BrandMark 하나로 묶고 소개 화면은 크기만 키운다 | the console and the introduction page draw the logo mark from one component that only changes its size: 실패 (마크 사본 두 곳) |
| G-078 | #443 | 77dd660 | LinkButton과 ScopedLink의 주소 변환을 useScopedHref 하나로 합친다 | link buttons and scoped links resolve workspace addresses through one shared hook: 실패 (변환 코드 복사본) |
| D-075 | #443 | f82e677 | 제목 크기 변수 --screen-title-size를 두고 두 제목 컴포넌트가 함께 쓴다 | screen titles use the same size whichever header component draws them: 실패 Expected "28px" Received "34px" (1440px 사용자 관리 대 고객) |
| D-237 | #443 | d1d409a | 운영자 목록 제목을 28px로 맞추고 고객 등록을 primary로 둔다 | operator list titles use the shared screen title size: 실패 (34px) |
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
| Ck2-007 | #444 | 401978e | main의 retry()는 지금도 엔드포인트가 있는지만 본 뒤 UPDATE 건수가 0이면 409 webhook_delivery_not_retryable을 던집니다. UPDATE 조건에는 전송 id, status='failed', 엔드포인트 enable | WebhookIntegrationTest.없는_전송의_재시도는_404이고_실패하지_않은_전송의_재시도는_409다: 실패 (ConflictException이 났다) |
| D-044 | #444 | 1fc88c3 | 웹훅 발송 내역이 통보 종류, 발송 상태, 실패 사유를 이름으로 보인다 | webhook delivery history names event types, delivery states and failure reasons: 실패 locator('.workspace-desktop-table').getByText('결제 실패', { exact: true }) elem |
| D-088 | #444 | 1fc88c3 | 웹훅 발송 내역이 통보 종류, 발송 상태, 실패 사유를 이름으로 보인다 | webhook delivery history names event types, delivery states and failure reasons: 실패 locator('.workspace-desktop-table').getByText('결제 실패', { exact: true }) elem |
| D-195 | #444 | 1fc88c3 | 웹훅 발송 내역이 통보 종류, 발송 상태, 실패 사유를 이름으로 보인다 | webhook delivery history names event types, delivery states and failure reasons: 실패 locator('.workspace-desktop-table').getByText('결제 실패', { exact: true }) elem |
| D-252 | #444 | 1fc88c3 | 웹훅 발송 내역이 통보 종류, 발송 상태, 실패 사유를 이름으로 보인다 | webhook delivery history names event types, delivery states and failure reasons: 실패 locator('.workspace-desktop-table').getByText('결제 실패', { exact: true }) elem |
| X-028 | #444 | 1fc88c3 | 웹훅 발송 내역이 통보 종류, 발송 상태, 실패 사유를 이름으로 보인다 | webhook delivery history names event types, delivery states and failure reasons: 실패 locator('.workspace-desktop-table').getByText('결제 실패', { exact: true }) elem |
| D-045 | #444 | 82b2aee, bc4767c | main의 웹훅 목록(page.tsx:45)은 여전히 archived_at과 enabled만 보고 문구를 정해서, 발송이 계속 실패하는 수신 주소도 '발송 중'으로 나온다. 넘길 때 head 81651ed의 page.tsx:36과 판단이 같고, 목록  | auth.contract.spec.ts a webhook receiver whose latest delivery is failing is flagged in the list: 실패 (최근 발송 실패 문구 없음) |
| C444-002 | #444 | 4a3dd20, 8600976 | 보안 지적 수정 | WebhookIntegrationTest.한_도입사의_전송이_밀려_있어도_다른_도입사의_전송이_다음_차례에_나간다: 실패 (밀린 도입사 전송이 먼저 모두 나감) |
| G-140 | #444 | 7c90dba | main에서도 deliver는 claim, send, complete를 한 건씩 직렬로 돌고, 15초 실행 예산은 101행 루프 조건에서 claim 전에만 본다. WebhookClient.send는 Future.get으로 최대 15초 막히므로 응답 없 | 없음(문서 변경. WebhookClient 요청 15초, WebhookDeliveryService 실행 15초와 100건을 코드에서 확인) |
| Ck2-005 | #444 | 95c8ae9 | main의 OperationsService.java:52는 넘길 때 head와 같은 줄로, 기간과 엔드포인트 조건 없이 webhook_delivery의 failed를 모두 센다. 이것은 실수가 아니라 박성종 님이 80a3ef1f(main 828ea61 | WebhookIntegrationTest.운영_현황의_웹훅_실패_수는_보관한_엔드포인트의_전송을_세지_않는다: 실패 (expected: 1L but was: 2L) |
| Ck2-006 | #444 | 37556b4 | 웹훅 전송 기록 목록을 받치는 색인을 둔다 | 전송기록_목록을_엔드포인트별_최신순으로_읽는_색인이_있다: 실패 EmptyResultDataAccessException: Incorrect result size: expected 1, actual 0 |
| Ck2-008 | #444 | a4a3660 | openapi.yaml이 웹훅 통보 종류의 최소 개수와 받는 값을 적는다 | 웹훅_엔드포인트_요청의_통보_종류는_하나_이상이고_서버가_받는_값만_적는다: 실패 [CreateWebhookEndpointRequest] expected: 1 but was: 0 |
| Ck2-010 | #444 | 95c8ae9 | main의 OperationsService.java:52는 넘길 때 head와 한 글자도 다르지 않아서, 결제 실패는 최근 24시간만 세고 웹훅 실패는 지금 failed인 전송을 기간 없이 모두 셉니다. 지적의 '영영 빠지지 않는다'는 조금 과장입니다 | WebhookIntegrationTest.운영_현황의_웹훅_실패_수는_보관한_엔드포인트의_전송을_세지_않는다: 실패 (expected: 1L but was: 2L) |
| Ck2-011 | #444 | eb1780b | 웹훅 전송 실패 사유 CHECK를 V52로 더한다(결정: 문자열과 CHECK 관례 유지) | SchemaConstraintTest.웹훅_전송의_실패_사유도_시도_기록과_같은_값만_받는다: 실패 expected 1, actual 0 |
| Ck2-013 | #444 | 40089ce | SecretMask.lastFour를 결과가 드러나는 mask로 바꾼다 | SecretMaskTest: 실패 error: cannot find symbol method mask(String) (같은 동작을 결과가 드러나는 이름으로 부를 길이 없다) |
| Ck2-015 | #444 | 67d55c9, aac9c37 | main에서도 demo/eslint-naming-rules.mjs 1-40행은 frontend/eslint.config.mjs 6-45행과 export 한 단어만 빼고 글자까지 같고, 넘길 때 head(81651ed)와 내용이 그대로라 고쳐지지 않았다 | naming-rules-sync.test.mjs(변이: 데모 enumMember 형식 변경): 실패 확인 |
| D-035 | #444 | 1fc88c3 | 웹훅 발송 내역이 통보 종류, 발송 상태, 실패 사유를 이름으로 보인다 | webhook delivery history names event types, delivery states and failure reasons: 실패 locator('.workspace-desktop-table').getByText('결제 실패', { exact: true }) elem |
| D-157 | #444 | 1fc88c3 | 웹훅 발송 내역이 통보 종류, 발송 상태, 실패 사유를 이름으로 보인다 | webhook delivery history names event types, delivery states and failure reasons: 실패 locator('.workspace-desktop-table').getByText('결제 실패', { exact: true }) elem |
| C444-008 | #444 | 8008c44 | 콘솔에서 웹훅 수신 주소를 수정해도 기존 설명을 지우지 않는다 | saving a webhook endpoint keeps its existing description: 실패 Expected "운영 알림 수신기" Received "설명 없이 보냄" (PUT 본문에 description이 없다) |
| D-226 | #444 | ce5041c | 운영 현황 예약 작업 표가 과금 제외 사용량 통보 작업을 이름으로 보인다 | every scheduled job the operations API lists has a label on the operations screen: 실패 unbilled_usage_report has no label in JOB_LABELS |
| G-144 | #444 | 7b270fe, 3831102 | 구독한 수신 주소가 없으면 미청구 통보를 남기지 않는다 | UnbilledUsageNotificationIntegrationTest.미청구_통보를_구독한_수신_주소가_없으면_웹훅_원장에_남기지_않는다: 실패 Expecting value to be false but was true |
| G-151 | #444 | 3decc91, a0ec597 | 예약 작업 이름이 세 곳에서 같은지 시험한다(결정: 운영 코드 유지) | ScheduledJobNamesIntegrationTest(변이, 경보 이름 오타): 실패 |
| G-152 | #444 | 815f525 | backend README에 웹훅 발송과 암호화 키, 운영 현황을 적는다 | 없음(문서 변경이라 시험으로 실패를 볼 수 없다. 적은 값을 WebhookDeliveryRepository.java 21-22, 43, 71-75행(재시도 간격, 6회, 2분 선점, 종결 조건), WebhookDelive) |
| U14-B1 | #444 | f67bc41 | 마지막 시도의 선점이 만료돼 실패로 끝난 웹훅도 실패 지표와 경보에 센다 | 마지막_시도_중_선점이_만료되어_실패로_끝난_전송도_실패_지표에_센다: 실패 Expecting actual 0.0 to be greater than or equal to 1.0 |
| U5-B1 | #444 | c0a9f4a | 하루 주기 작업 미실행 경보가 배포 뒤 멈춘 색의 오늘 성공도 본다 | 없음(Grafana 경보 규칙을 돌리는 저장소 시험이 없어 고치기 전 실패를 시험으로 볼 수 없다. prom/prometheus:v3의 promtool test rules로 두 색 시리즈(1시간에 오늘 성공을 낸 뒤 1시) |
| X-014 | #446 | 48fd833 | 사업자등록번호를 하이픈과 공백을 빼고 보내고 틀리면 우리말로 알린다 | a business registration number keeps only its ten digits and names the field when it is not ten digits: 실패 SyntaxError: does not provide an export named 'readBu |
| D-040 | #446 | efabb7c | 단가 입력을 0으로 미리 채우지 않고 0원 단가는 저장 전에 묻는다 | a price that charges nothing is recognized so the editor can ask before saving it: 실패 ERR_MODULE_NOT_FOUND Cannot find module src/lib/price-rate.ts (단가 창의 단가 입력 |
| D-180 | #446 | efabb7c | 단가 입력을 0으로 미리 채우지 않고 0원 단가는 저장 전에 묻는다 | a price that charges nothing is recognized so the editor can ask before saving it: 실패 ERR_MODULE_NOT_FOUND Cannot find module src/lib/price-rate.ts (단가 창의 단가 입력 |
| X-005 | #446 | efabb7c | 단가 입력을 0으로 미리 채우지 않고 0원 단가는 저장 전에 묻는다 | a price that charges nothing is recognized so the editor can ask before saving it: 실패 ERR_MODULE_NOT_FOUND Cannot find module src/lib/price-rate.ts (단가 창의 단가 입력 |
| D-109 | #446 | 61d5216 | 미터 저장 실패를 대화상자 맨 위에 보이고 포커스하며 화면 문구로 알린다 | changing the calculation of a meter with events says what can still change instead of API field names: 실패 SyntaxError: does not provide an export named 'billabl |
| Ck3-446-02 | #446 | d5ae200 | 단가 종료 월이 시작 월보다 뒤가 아니면 요금제와 같은 validation_error를 낸다 | 종료_월이_시작_월보다_뒤가_아니면_요금제와_같은_validation_error다: 실패 expected: VALIDATION_ERROR but was: INVALID_PRICE_RATE |
| Ck3-446-03 | #446 | 85e8d8d | 단가 모델과 라인 종류 값 목록을 문서화한다(결정: 상수 통합 없음) | OpenApiDocumentTest.단가_모델과_청구_라인_종류는_서버가_쓰는_값_목록을_적는다: 실패 enum 없음 |
| Ck3-446-04 | #446 | b22fd8d | 조합별 단가 DELETE 설명을 이번 달 공통 단가를 끝내는 실제 동작에 맞춘다 | 없음(문서 변경이라 시험으로 실패를 볼 수 없다. 적은 동작을 PriceRateService.java 159-191행(공통 단가, 이번 달 적용 중, 이번 달 시작은 삭제, 그 전은 close(current))과 Audi) |
| Ck3-446-05 | #446 | ae50ce0 | 요금제 이력 조회 API가 생략한 파라미터의 뜻과 400, 404를 적는다 | 요금제_이력_조회는_생략한_파라미터의_뜻과_400_404를_적는다: 실패 Expecting not blank but was: "" (description 없음) |
| Ck3-446-06 | #446 | a7a789e | 요금 모델 검사를 서비스 한곳에 두고 오류 필드를 요청의 model로 낸다 | 요금_모델에_맞지_않는_단가는_요청에_있는_model_필드로_거절된다: 실패 expected: "model" but was: "validPricing" |
| Ck3-446-07 | #446 | ec82ba5 | 단가 감사 target_id 뜻을 README에 적는다(결정: 버전 UUID 유지) | 없음(README와 PriceRateService 감사 줄 대조) |
| Ck3-446-08 | #446 | 318a9e9 | 청구 응답 DTO의 보조 생성자를 지운다(결정) | 없음(컴파일, OpenApiDocumentTest: 통과, openapi.yaml 변화 없음) |
| Ck3-446-09 | #446 | db6a775 | 공급자 조회 find가 예외를 던진다고 적는다(결정 변경: 이름 유지) | 없음(컴파일 통과, naming.md와 이름 규칙 시험 대조) |
| Ck3-446-10 | #446 | 18f7a85 | 단가 조회 인덱스를 V50으로 더한다 | SchemaConstraintTest.단가에는_조건_없는_도입사와_미터_인덱스가_있다: 실패 expected 1, actual 0 |
| D-120 | #446 | 8804e65 | 작성 중인 미터 폼을 Esc, 바깥 클릭, 취소로 닫기 전에 묻는다 | a meter form with input asks before Escape throws it away: 실패 Expected "작성 중인 미터를 버리고 닫을까요?" Received "" (확인 없이 닫힘) |
| X-091 | #446 | 1574c86 | 미터 폼의 숫자 입력에서 영어 이름의 증감 버튼을 뺀다 | meter number inputs do not expose English stepper buttons: 실패 Expected 0 Received 2 (Increase Value, Decrease Value) |
| D-119 | #446 | efabb7c | 단가 입력을 0으로 미리 채우지 않고 0원 단가는 저장 전에 묻는다 | a price that charges nothing is recognized so the editor can ask before saving it: 실패 ERR_MODULE_NOT_FOUND Cannot find module src/lib/price-rate.ts (단가 창의 단가 입력 |
| D-070 | #446 | 80d8745 | main에서도 미터 목록 section은 workspace-panel과 styles.meterRail을 같이 단다. globals.css의 .workspace-panel이 display:flex와 flex-direction:column을 주는데, pr | work-copy.test.mjs the pricing meter rail stays a wrapping row of chips at 900px and below: 실패 |
| H3-B2 | #446 | 3be4f3a | 시드 재실행이 percentile을 비운다 | SeedDataTest.백분위로_바꾼_시드_미터도_시드를_다시_실행하면_백분위가_비워진_합계로_돌아온다: 실패 DataIntegrityViolationException |
| Nk3-002 | #446 | 85e8d8d | 단가 응답 tiers를 배열로 문서화한다 | OpenApiDocumentTest.단가_응답의_tiers는_구간_객체의_배열이다: 실패 expected array but was |
| X-018 | #446 | 28c0fda | main에서도 요금제 변경 이력 표(84행)와 단가 변경 이력 표(92행)는 Table locale.emptyText에 문자열을 바로 넣는다. 설치된 antd 6.6.7의 table/style/empty.js는 이 자리(tr.ant-table-plac | work-copy.test.mjs pricing history empty states use the readable Empty component instead of the faint default text: 실패 |
| C446-005 | #446 | 349319e | 반올림 시험에 사사오입 판별 자리를 더한다 | 없음(MetricAggregationIntegrationTest: 통과; round를 HALF_EVEN으로 바꾼 변이: 실패) |
| C446-008 | #446 | d1b3464 | main에서도 서버는 기본료와 최소 사용료 라인의 billable_metric_code를 null로 보낸다. ChargeService.surcharge가 DimensionCharge의 첫 인자에 null을 넣고, DraftInvoiceService와  | demo/sdk typecheck(meter-engine-client.test.ts 기본료 라인 타입 시험): 실패 (TS2322 null을 string에 넣을 수 없음) |
| C446-009 | #446 | 3879ac3, 331761e | 반박하려고 요금 설계 화면이나 API 어딘가에서 기본료와 최소 사용료 절사를 막거나 안내하는지 찾아봤지만 나오지 않았다. main의 ChargeService.apply는 여전히 최소 사용료와 기본료에 trim(amount - amount % 10)을  | plan-amount.test.mjs plan amounts show the billed amount when billing drops the part under 10 won: 실패 (모듈 없음) |
| C446-011 | #446 | 628658f | 올림 음수 시험이 올림 미터를 쓴다 | 없음(ChargeServiceTest: 통과; 음수 판정을 변환 수량으로 바꾼 변이: 실패) |
| C446-012 | #446 | 7347579 | 다른 도입사 단가 ID 삭제의 경계를 시험한다 | 없음(VersionedPricingIntegrationTest: 통과; 도입사 비교를 뺀 변이: 실패) |
| C446-013 | #446 | 088d775 | 이름만 바꿀 때 이벤트 확인을 건너뛰는지 실제 메서드로 검증한다 | 없음(BillableMetricServiceTest: 통과; changesBeyondName을 늘 true로 바꾼 변이: 실패) |
| D-115 | #446 | 99c34ff | main의 InvoicesTable.tsx를 보면 데스크톱 인보이스 행은 인보이스 번호(153행)와 수납 화면으로 가는 '수납과 고객 조회 링크'(155행)를 보여 준다. 같은 groups를 그리는 모바일 카드(228행)에는 고객명, 금액, 월과 확정 | list-copy.test.mjs mobile invoice cards show the invoice number and the same payment link as the desktop table: 실패 (모바일 카드에 없음) |
| D-142 | #446 | d419f7a | main의 UsageTable.tsx 164행 모바일 카드는 넘길 때 head(c27b229 162행)와 같은 블록이 그대로 남아 있다. kind가 meter인 항목의 차원 행 라벨은 모두 '차원별 집계값'으로 고정되고, 행을 가르는 차원 값은 숫자  | list-copy.test.mjs mobile usage cards label each dimension row with its dimension values under one group heading: 실패 (같은 라벨 반복) |
| D-146 | #446 | b977412 | main의 pricing.module.css 2행에 .tierEditorRow .workspace-field{font-size:12px}가, 1행 480px 미디어에 .tierEditorRow>.ant-btn{grid-column:3}이 넘길 때 he | work-copy.test.mjs the pricing CSS module does not rely on global class selectors that the module hashes away: 실패 |
| D-230 | #446 | 0b1b15b | main에서도 단가 화면이 같은 개념을 서로 다른 말로 적는다. 단일 단가는 PricingWorkspace.tsx:110에서 '{금액}원 / 사용량 1', 구간 단가는 112행에서 '{금액}원 / 1'이고, 입력 라벨(195행)과 오류 라벨(134행) | work-copy.test.mjs unit prices say they are per billed quantity, not per raw usage: 실패 |
| G-107 | #446 | c93b2d3 | 요금제 절사 규칙을 API 설명에 적는다 | 없음(VersionedPricingIntegrationTest.기본료와_최소_사용료는_청구할_때_10원_미만을_버린다: 통과; OpenApiDocumentTest: 통과) |
| G-174 | #446 | 191dc8d | CHECK 제약 이름을 V51로 규칙에 맞춘다 | SchemaConstraintTest.집계와_수량_분수_제약은_테이블_대상_종류_순서의_이름을_쓴다: 실패 새 이름 없음 |
| Nk3-001 | #446 | 42b996d | sum 전용 우회 경로를 지운다 | BillableMetricUsageServiceTest.고객의_수량은_집계_결과의_고객_합계다: 실패 expected: 800 but was: 0 |
| Nk3-003 | #446 | 5588b4f | 호출처 없는 옛 기간 판정 메서드를 지운다 | 없음(git grep 호출 없음, pricing 시험: 통과) |
| X-036 | #446 | 3f80123 | 반박을 시도했지만 main에서 막는 줄을 찾지 못했다. ExpandControls는 화면 폭과 관계없이 두 버튼을 그리고 CollapseProvider의 mode만 바꾸는데, 이 상태를 읽는 곳은 데스크톱 Table의 expandedRowKeys를 만 | auth.contract.spec.ts phones do not show expand-all controls that only act on the desktop table: 실패 (Received: 1) |
| X-052 | #446 | ceaabfd, db91503 | main의 PricingWorkspace.tsx 29행 MONEY는 여전히 maximumFractionDigits 8이고, 단가 이력 표(96행), 단일 단가(110행), 구간 단가(112행)가 이것으로 단가를 보여 준다. 단가 입력은 step any | price-rate.test.mjs unit prices the screen can show exactly are accepted and finer ones are sent to the API instead: 실패 (export 없음) |
| X-067 | #446 | 58c8297 | main에서도 InvoiceFinalizeDialog.tsx 44-45행이 대상이 0곳일 때 버튼을 비활성으로 만들고 사유는 title 속성으로만 넘기며, InvoiceFinalizeButton.tsx 18행은 그 값을 antd Button의 titl | work-copy.test.mjs a disabled month finalization shows its reason as text the button describes, not only a tooltip: 실패 |
| X-077 | #446 | 28c0fda | main의 PricingWorkspace.tsx 86, 87행 요금제 변경 이력 표의 기본료, 최소 사용료 열과 96행 단가 변경 이력 표의 단가 열에 align이 없습니다. 그래서 antd 기본값인 왼쪽 정렬로 그려지고, 값이 꾸밈 없는 문자열이라  | work-copy.test.mjs pricing history money columns are right aligned numbers: 실패 |
| X-102 | #446 | 625706d | main의 CustomerLabel.tsx 8행은 여전히 이름과 UUID를 <Typography.Text ellipsis> 한 줄로 감싸고 tooltip이나 title을 주지 않는다. 설치된 antd 6.6.7은 ellipsis가 true면 useTo | list-copy.test.mjs a truncated customer label offers the full name and id: 실패 (툴팁 없음) |
| C447-011 | #447 | 088d775 | 이름만 바꿀 때 이벤트 확인을 건너뛰는지 실제 메서드로 검증한다 | 없음(BillableMetricServiceTest: 통과; changesBeyondName을 늘 true로 바꾼 변이: 실패) |
| G-092 | #448 | 794b0f8 | 수집 요청을 감사에서 빼는 코드는 넘길 때 head에 이미 있었고 main에서도 RequestAuditFilter.java:63-66에 그대로 있다. 그래서 수집량만큼 행이 는다는 원래 시나리오는 main에서 성립하지 않는다. 남는 것은 수집 밖의 모 | AuditQueryAndRequestIntegrationTest.주체_없는_401은_요청_감사에_남기지_않는다: 실패 (expected: 0L but was: 1L) |
| C448-001 | #448 | 1164c20 | 운영자 전용 경로의 요청 감사를 운영자 세션이 고른 도입사에 남기지 않는다 | 도입사에_소속된_운영자의_운영자_전용_요청은_그_도입사의_감사에_남지_않는다: 실패 감사 행 organization_id가 null이 아니라 그 도입사 id였다 |
| G-095 | #448 | e956665 | 보안 지적 수정 | AuditQueryAndRequestIntegrationTest.감사조회는_로그인_세션만_할_수_있고_read_API_키는_403이다: 실패 (expected: 403 but was: 200) |
| C448-002 | #448 | 794b0f8 | 보안 지적 수정 | AuditQueryAndRequestIntegrationTest.주체_없는_401은_요청_감사에_남기지_않는다: 실패 (expected: 0L but was: 1L) |
| G-169 | #448 | 794b0f8 | 수집 요청은 넘길 때 head와 main 모두 RequestAuditFilter.java:63-66에서 상태와 관계없이 감사에서 빠진다. 그래서 수집 요청마다 커밋이 하나 더 늘어 test-storage 기준선이 흔들린다는 부분은 main에서 성립하지 | AuditQueryAndRequestIntegrationTest.주체_없는_401은_요청_감사에_남기지_않는다: 실패 (expected: 0L but was: 1L) |
| X-024 | #448 | 80a3ad0 | 감사 기록 요약 줄에 실행한 사람을 보이고 변경 이력 보기는 사용자와 초대 변경만 연다 | an audit entry names the person who acted and falls back to the actor kind: 실패 ERR_MODULE_NOT_FOUND src/lib/audit.ts (요약 줄은 실행 주체 유형만 보였다) |
| Ck1-448-01 | #448 | 1003ba5 | 감사 조회의 기간 조건이 저장할 수 없는 연도면 500 대신 400을 낸다 | 감사조회의_잘못된_필터와_기간은_400이다: 실패 [HTTP status code] expected: 400 but was: 500 (to=+300000-01-01T00:00:00Z) |
| Ck1-448-03 | #448 | d0edd0f | README에 감사 조회 API와 감사 화면, X-Request-ID를 적는다 | 없음(문서 변경이라 시험으로 실패를 볼 수 없다. AuditLogController의 두 GET 매핑, RequestAuditFilter의 X-Request-ID 헤더와 수집 제외, NavMenu.tsx와 Operator) |
| Ck1-448-04 | #448 | 262352b, 6039fad | main에서도 지적은 사실이다. 운영자 감사 조회는 도입사 인수가 없고 서비스에 organizationId로 null을 넘기며(AuditLogController 116행), 검색어 q가 보는 열은 action, target_type, target_id | AuditQueryAndRequestIntegrationTest.운영자_감사조회의_검색어로_도입사_id를_찾는다: 실패 (expected: 2 but was: 0) |
| D-152 | #448 | 80a3ad0 | 감사 기록 요약 줄에 실행한 사람을 보이고 변경 이력 보기는 사용자와 초대 변경만 연다 | an audit entry names the person who acted and falls back to the actor kind: 실패 ERR_MODULE_NOT_FOUND src/lib/audit.ts (요약 줄은 실행 주체 유형만 보였다) |
| GA-B1 | #448 | b0bf0b1 | 요청 감사가 퍼센트 인코딩한 경로도 디코딩해 /v1 요청으로 본다 | 퍼센트_인코딩한_수집_경로는_수집으로_보지_않아_감사기록을_남긴다: 실패 Wanted but not invoked |
| X-007 | #448 | 6d30dca | 감사 기록 화면에 빠진 행동 이름 15개를 더하고 남기지 않는 행동은 조건에서 뺀다 | every audit action the backend records has a label on the audit screen: 실패 SyntaxError: does not provide an export named 'AUDIT_ACTIONS' (실화면 요약 줄에 payment_sett |
| Nk1-002 | #448 | df3caf7 | v1 경로 변수가 요청 감사 대상인지 대상에서 뺀 이름인지 시험으로 강제한다 | 없음(이 층 라우트 변수(code, delivery_id, id, mode, organization_id, token)는 모두 분류돼 있어 고치기 전에 실패하는 시험을 만들 수 없다. 새 RequestAuditTarget) |
| Nk1-003 | #448 | c2991ef | 요청 감사가 감사 행을 AuditLogService로 남기고 익명 행위자를 AuditActor에서 만든다 | 없음(동작이 같은 리팩터링이라 고치기 전에 실패하는 시험을 쓸 수 없다. ./gradlew test --tests 'com.meterengine.audit.*' --tests 'com.meterengine.global.s) |
| X-045 | #448 | cea0a76 | 감사 기록을 한 목록으로 촘촘하게 보이고 거절과 오류 요청은 붉은 태그로 표시한다 | the audit list marks rejected requests and keeps entries compact: 실패 Expected <= 80 Received 120.84375 (항목 간격), 거절 태그 0개 |
| Ck3-450-01 | #450 | 7b270fe, 3831102 | 미청구 통보에 단가 없는 사용량을 싣는다 | UnbilledUsageNotificationIntegrationTest.단가_없는_사용량만_있어도_통보에_싣는다: 실패 Expecting value to be true but was false |
| Ck3-450-02 | #450 | 68afb08, c2d367a | 사유 목록끼리와 DB CHECK가 같은지 시험한다(결정: 문자열 유지) | 없음(EventReasonListTest: 통과; 서비스 목록에만 사유를 더한 변이: 실패; 시험 자리 이동 뒤 EventRejectionServiceTest, EventIssueIntegrationTest: 통과) |
| Ck3-450-03 | #450 | fa9fdc9 | 행위자 종류를 PrincipalKind 전체 switch로 정한다(결정: 시그니처 유지) | 없음(EventRejectionIntegrationTest, audit 시험: 통과(동작 변화 없음)) |
| Ck3-450-04 | #450 | 821ac9f | 요금 진단 계약을 event.api로 옮긴다(결정) | 없음(DomainBoundaryTest, EventIssueIntegrationTest: 통과) |
| D-193 | #450 | f3afea4 | main의 이벤트 매핑 진단 표는 넘길 때 head와 똑같이 8개 열을 그리고, '확인할 내용' 열이 '문제' 바로 뒤에 있다. #451과 #453이 이 파일을 고쳤지만 열 목록은 바뀌지 않았다. 표는 scroll x를 max-content로 두고 첫 | event-issue-hints.test.mjs each issue reason's hint is shown once in order of first appearance: 실패 (모듈 없음) |
| G-116 | #450 | 7b270fe, 3831102 | 지난달 몫을 따로 통보한다 | UnbilledUsageNotificationIntegrationTest.지난달_사용량은_지난달_통보로_따로_발행한다: 실패 Expecting value to be true but was false |
| G-117 | #450 | 645b7bb | 확정한 달의 단가 없는 사용량에 확정 안내를 준다(결정: reason 값 유지) | EventIssueIntegrationTest.확정한_달의_단가_없는_사용량은_단가_등록_대신_확정_안내를_준다: 실패 단가를 등록하세요 to contain 확정 |
| G-119 | #450 | a727083 | README에 페이지 기준, 진단 비용, 거절 기록 보존을 적는다(결정) | 없음(README와 코드 대조로 확인) |
| G-132 | #450 | d07ecde | 이벤트 조회와 거절 검색이 환경 인덱스를 탄다 | 없음(event, query, metric, notification 통합 시험(환경 격리 시험 포함): 통과; 성능 변화는 시험으로 잡지 않고 인덱스를 쓸 수 있는 조건(environment_scope_id = ?, en) |
| G-170 | #450 | d07ecde | 이벤트 조회와 거절 검색이 환경 인덱스를 탄다 | 없음(event, query, metric, notification 통합 시험(환경 격리 시험 포함): 통과; 성능 변화는 시험으로 잡지 않고 인덱스를 쓸 수 있는 조건(environment_scope_id = ?, en) |
| Ck3-450-06 | #450 | ea3cce0 | 미청구 요약 event_count를 서로 다른 이벤트 수로 센다 | UnbilledEventIntegrationTest.같은_유형에_미터가_둘이어도_음수와_숫자_아닌_이벤트는_한_건으로_센다: 실패 expected: 1L but was: 2L |
| Ck3-450-07 | #450 | 09e06ae | API 설명에 엔드포인트별 규칙과 summary 키를 적는다(결정: 공개 계약 유지) | 없음(OpenApiDocumentTest: 통과; 설명 문장과 코드 대조로 확인) |
| Ck3-450-08 | #450 | c55b3d3 | 배치 수집 API 문서가 한 건 이상과 결과 세 값을 적는다 | OpenApiDocumentTest.배치_수집_요청은_한_건_이상이고_결과는_서버가_내는_세_값만_적는다: 실패 expected: 1 but was: 0 |
| Ck3-450-09 | #450 | 6a870a0, 4caedb4 | 배치 오류 필드 이름을 DTO의 JSON 이름에서 읽는다 | 없음(EventBatchServiceTest.검증_오류_필드는_단건_요청과_같은_JSON_이름이다: 통과(바꾸기 전과 뒤); checkstyle 이름 규칙에 맞춘 @Test 전환 뒤 통과) |
| Ck3-450-11 | #450 | 06655a9 | 고객 조회 계약이 잠금을 건다고 적는다(결정: 이름 유지, 한 낱말 규칙) | 없음(컴파일 통과, naming.md와 MemberNamingConventionTest 규칙 대조) |
| Ck3-450-12 | #450 | 1704e89 | 검증을 건너뛰는 옛 저장소 오버로드를 지운다 | 없음(git grep으로 기간 인자 findPage, count 호출 없음 확인; event, query 시험: 통과) |
| Ck3-450-13 | #450 | 1e010c1 | 과금 달 계산을 global 도우미로 모은다(결정) | 없음(BillingMonthTest: 통과; global, event, metric, notification 시험과 DomainBoundaryTest: 통과) |
| D-076 | #450 | f3afea4 | action_hint는 백엔드가 reason 하나만 보고 고르는 고정 문장 세 개라서, 바로 옆 '문제' 열이 같으면 '확인할 내용'도 항상 같은 문장이다. main의 표는 이 열을 그대로 두어 같은 문장을 줄마다 되풀이한다. 두 코드 줄(프런트 RE | event-issue-hints.test.mjs each issue reason's hint is shown once in order of first appearance: 실패 (모듈 없음) |
| D-092 | #450 | f3afea4 | D-076과 원인이 같다. reason으로 정해지는 고정 hint 문장을 '확인할 내용' 열로 줄마다 되풀이해 표 폭을 키운다. 덧붙인 지적인 요약 문장 이름도 main에 남아 있다. 46행 요약은 '미터 없음, 수량 확인, 단가 없음'이라 쓰고, 1 | event-issue-hints.test.mjs each issue reason's hint is shown once in order of first appearance: 실패 (모듈 없음) |
| D-056 | #450 | f3afea4 | main의 이벤트 매핑 진단 화면은 행마다 action_hint로 '미터를 정의하거나', '단가를 등록하세요'라고 안내하지만, 표에서 링크가 걸린 곳은 원본 이벤트(/events)로 가는 전송 ID 열 하나뿐이다. 요약 줄과 미래 이벤트, 음수 조합  | event-issue-hints.test.mjs the diagnosis table drops the repeated hint column and links to where each problem is fixed: 실패 (링크 없음) |
| D-100 | #450 | f3afea4 | main의 이벤트 매핑 진단 화면은 미래 이벤트 건수와 음수 이벤트-미터 조합 건수를 보여 준 뒤 '위 문제 건수와 별도로 확인합니다'로 끝나고, 어디서 확인하는지 링크가 없다. 같은 개념을 보여 주는 사용량 화면의 UsageQuality는 미래 시각 | event-issue-hints.test.mjs the diagnosis table drops the repeated hint column and links to where each problem is fixed: 실패 (링크 없음) |
| D-099 | #450 | 08fd6a3 | main에서도 고객 ID 입력란의 안내가 placeholder 한 줄뿐이라, 값을 넣으면 사라지고 UUID 형식이라는 말도 없습니다. 원문의 실패 시나리오는 조금 다릅니다. 고객명을 넣으면 0건이 아니라 백엔드가 customer_id를 UUID로 받기 | list-copy.test.mjs the rejection search keeps its customer id guidance visible below the field: 실패 (placeholder 안내) |
| G-182 | #450 | a727083 | README에 페이지 기준, 진단 비용, 거절 기록 보존을 적는다(결정) | 없음(README와 코드 대조로 확인) |
| C450-009 | #450 | 210baa2 | 미청구 통보 실패 로그에 예외를 남긴다 | UnbilledUsageNotificationServiceTest.통보와_환경_조회가_실패하면_예외_종류와_메시지를_로그에_남긴다: 실패 출력에 java.lang.IllegalStateException: environments down이 없음 |
| G-124 | #450 | d459a67 | 세 집계 가능 정의가 같은지 시험한다(결정: 구조 유지) | 없음(EligibleEventDefinitionIntegrationTest: 통과; 문제 진단 조건을 바꾼 변이: 실패) |
| G-146 | #450 | 210baa2 | 미청구 통보 실패 로그에 예외를 남긴다 | UnbilledUsageNotificationServiceTest.통보와_환경_조회가_실패하면_예외_종류와_메시지를_로그에_남긴다: 실패 출력에 java.lang.IllegalStateException: environments down이 없음 |
| G-176 | #450 | a727083 | README에 페이지 기준, 진단 비용, 거절 기록 보존을 적는다(결정) | 없음(README와 코드 대조로 확인) |
| C450-006 | #450 | eb6350c, 4caedb4 | DB 시각 범위를 넘는 기간을 400으로 막는다 | EventListIntegrationTest.DB_시각_범위를_넘는_기간은_500이_아니라_400이다: 실패 expected: 400 but was: 500 |
| C450-007 | #450 | eb6350c, 39b29bd, 4caedb4 | DB가 받지 못할 속성 조건을 400으로 막는다 | EventListIntegrationTest.DB가_받지_못할_속성_조건은_500이_아니라_400이다: 실패 expected: 400 but was: 500 |
| C450-012 | #450 | cfb0e6f | 검색어 % 시험이 실제로 % 한 글자를 보낸다 | 없음(EventIssueIntegrationTest, EventRejectionIntegrationTest: 통과; position을 이스케이프 없는 LIKE로 바꾼 변이: 실패 expected: 0 but was: 3) |
| D-254 | #450 | 2bff105 | main에서도 UsageQuality의 '미터와 연결되지 않은 유형' 링크는 /billable-metrics로, '집계할 수 없는 속성' 링크는 /events?month로만 간다. 넘길 때 head와 줄 내용이 같고, #451은 import 한 줄만  | usage-quality.test.mjs ingestion exceptions link to the diagnosis filtered by their reason and month: 실패 (모듈 없음) |
| C450-002 | #450 | db6bbd5 | 보안 지적 수정 | CustomerExternalIdIntegrationTest.조회_권한_없는_수집_키로_다시_등록하면_기존_고객의_이름과_이메일을_돌려주지_않는다: 실패 |
| C450-003 | #450 | db6bbd5 | 고객 등록의 보관 409를 문서화하고 시험한다 | OpenApiDocumentTest.고객_등록은_보관한_외부_ID의_409와_조회_권한_없는_재등록의_빈_값을_문서화한다: 실패 |
| C450-010 | #450 | 1b33c58, 4e083bc | main의 _send_batches는 넘길 때 head와 같은 코드이고, build_body_text가 properties 원문을 그대로 이어 붙여 봉투 하나를 만든다. csvio.py는 빈 칸을 빈 문자열로 읽고 예측 단계는 문제를 표시만 한 채 그 | csvdemo.test_send_cmd.test_속성이_깨진_행만_단건으로_보내고_나머지는_배치로_저장한다: 실패 (세 행 모두 rejected) |
| G-127 | #450 | cdb63ae | 월 목록에서 네 자리 연도로 열 수 없는 달을 뺀다 | EventMonthsIntegrationTest.네_자리_연도로_열_수_없는_달은_월_목록에서_뺀다: 실패 actual ["55835-03", "2026-10"] |
| G-165 | #450 | 7357259 | main의 저장단 대시보드에서 수집 API 패널 넷(초당 요청 수, 평균 처리 시간, p99와 p50, 5xx 오류율)의 식이 모두 uri="/v1/events",method="POST"로 고정되어 있다. #450이 이 파일을 바꿨지만 중복률 분모 한 | test_dashboards.test_ingestion_panels_cover_single_and_batch_ingestion_per_uri: 실패 (uri 조건이 /v1/events뿐) |
| Ck3-451-01 | #451 | 4f705cd | main에서도 고객 등록 응답(이름, billing_email)을 idempotent_request.response_body에 기한 없이 저장한다. 지우는 코드가 없고 created_at 열을 읽는 곳도 없어서, 넘길 때 head와 같은 상태로 남아  | OpenApiDocumentTest.멱등_생성의_재시도_키는_최초_응답을_기한_없이_보관한다고_적는다: 실패 (설명 없음) |
| D-186 | #451 | c651e79 | CSV 내보내기 버튼이 카드 폭으로 늘어나지 않는다 | workspace.contract.spec.ts 조건 전체 CSV 내보내기는 보조 버튼이라 카드 폭으로 늘어나지 않는다: 실패 버튼 폭이 카드 절반 이상 |
| X-038 | #451 | c651e79 | CSV 내보내기 버튼이 카드 폭으로 늘어나지 않는다 | workspace.contract.spec.ts 조건 전체 CSV 내보내기는 보조 버튼이라 카드 폭으로 늘어나지 않는다: 실패 버튼 폭이 카드 절반 이상 |
| D-176 | #451 | c198bc1, 9d2a57f | main의 RoutedEventDetail은 넘길 때 head와 한 글자도 다르지 않고, 이 파일을 바꾼 커밋은 #451(f53222e7) 하나뿐이라 고쳐지지 않았다. 드로어를 늘 isOpen으로 그리고, 닫기와 Esc는 router.replace로  | list-copy.test.mjs mobile event links open the drawer in place and closing it returns focus to the row link: 실패 (일반 a 태그, 포커스 복귀 없음) |
| D-185 | #451 | d81a162, 9d2a57f | main에서도 globals.css 124행과 153행이 '.workspace-field input'을 자손 선택자로 걸어서, ResourceSearchControls 107행에서 .workspace-field 안에 놓인 MonthPicker(antd | auth.contract.spec.ts filter row controls on a list screen share one height: 실패 (월 선택기 60px) |
| X-037 | #451 | d81a162, 9d2a57f | 원인은 D-185와 같습니다. main의 globals.css 124행과 153행에 있는 '.workspace-field input' 자손 규칙이 MonthPicker 안쪽 input에 min-height 44px를 넣어서 조회 기간 칸이 약 60px | auth.contract.spec.ts filter row controls on a list screen share one height: 실패 (월 선택기 60px) |
| D-001 | #451 | e373a55 | main의 globals.css 311행에 .resource-advanced summary { min-height: 44px; display: flex; align-items: center; }가 그대로 남아 있다. 이 줄은 #443(be67d816) | auth.contract.spec.ts list screens keep the disclosure marker on the advanced filter summary: 실패 (display flex) |
| D-025 | #451 | e373a55 | D-001과 원인이 같다. main의 globals.css 311행 .resource-advanced summary가 display: flex를 걸어 summary의 기본 list-item 표시를 덮어쓰므로 펼침 삼각형이 사라진다. 이 줄은 #443( | auth.contract.spec.ts list screens keep the disclosure marker on the advanced filter summary: 실패 (display flex) |
| D-047 | #451 | e373a55 | D-001과 원인이 같다. main의 globals.css 311행이 .resource-advanced summary를 flex로 바꿔 기본 펼침 삼각형이 그려지지 않는다. 이벤트 화면의 .event-advanced summary(254행)는 disp | auth.contract.spec.ts list screens keep the disclosure marker on the advanced filter summary: 실패 (display flex) |
| D-067 | #451 | e373a55 | D-001과 원인이 같다. main의 globals.css 311행 .resource-advanced summary { min-height: 44px; display: flex; align-items: center; }가 남아 있어 ::marker 삼 | auth.contract.spec.ts list screens keep the disclosure marker on the advanced filter summary: 실패 (display flex) |
| D-133 | #451 | e373a55 | 지적 위치는 ResourceSearchControls.tsx 113행이지만 원인은 D-001과 같은 globals.css 311행의 display: flex다. main의 113~114행 summary에는 글자만 있고 펼침 아이콘이 없으며, CSS가  | auth.contract.spec.ts list screens keep the disclosure marker on the advanced filter summary: 실패 (display flex) |
| D-147 | #451 | e373a55 | D-001과 원인이 같다. main의 globals.css 311행이 summary를 flex로 바꿔 브라우저 기본 펼침 표시가 없어진다. 같은 패턴인 이벤트 화면은 254행 규칙만 받아 삼각형이 남는다. 이 줄은 #443 이후 바뀌지 않았다. 화면  | auth.contract.spec.ts list screens keep the disclosure marker on the advanced filter summary: 실패 (display flex) |
| D-177 | #451 | e373a55 | D-001과 원인이 같다. main의 globals.css 311행 .resource-advanced summary가 display: flex라 Chromium에서 ::marker가 그려지지 않는다. 수집 로그는 display를 바꾸지 않아 삼각형이  | auth.contract.spec.ts list screens keep the disclosure marker on the advanced filter summary: 실패 (display flex) |
| X-023 | #451 | e373a55 | D-001과 원인이 같다. main의 globals.css 311행이 남아 있어 /customers의 summary는 flex, /events의 summary는 기본 list-item이 된다. .audit-summary(286~289행)는 마커를 지운 | auth.contract.spec.ts list screens keep the disclosure marker on the advanced filter summary: 실패 (display flex) |
| X-039 | #451 | 809c459, d967c92 | main에서도 ResourceSearchControls의 '조건 적용' 버튼이 type="primary"로 남아 있다. 고객 화면의 '고객 등록'은 ActionPanel 트리거인데 ActionPanel은 default와 link만 고를 수 있어, /c | auth.contract.spec.ts list filters submit with a default 조건 적용 button and reset with 조건 초기화: 실패 (ant-btn-primary) |
| X-053 | #451 | a2a9125, 9d2a57f | main에서도 조회 조건 폼의 apply()는 최소 금액이 최대 금액보다 크거나 확정 시각 시작이 종료보다 늦어도 막지 않고 그대로 주소를 바꾼다. 서버 SearchParameters가 이 경우 400을 던지고, InvoicesSection은 Erro | resource-query.test.mjs reversed amount and finalized time ranges are caught before the request: 실패 (export 없음) |
| Ck3-451-03 | #451 | cdb63ae | 모르는 자원의 월 목록을 거부한다 | WorkspaceQueryIntegrationTest.모르는_자원의_월_목록은_이벤트_월로_대신하지_않고_거부한다: 실패 Expecting code to raise a throwable |
| Ck3-451-04 | #451 | d7f3266 | CSV 처리를 global 도우미로 모은다(결정) | 없음(EventExportIntegrationTest, WorkspaceQueryIntegrationTest CSV 시험: 통과(동작 변화 없음)) |
| Ck3-451-05 | #451 | 4f63c98 | 고객과 미터 응답의 보조 생성자를 지운다 | 없음(컴파일, OpenApiDocumentTest: 통과, openapi.yaml 변화 없음) |
| D-139 | #451 | 46b9621, 9d2a57f | main에서도 고객 검색 함수가 넘길 때 head와 한 글자도 다르지 않아 지적이 그대로 남아 있다. 서버 액션은 백엔드가 잠깐 응답하지 않으면 requireSession이 401/403이 아닌 실패에서 예외를 던지므로 클라이언트 쪽 await가 re | workspace.contract.spec.ts 격리 계약: 고객 선택지는 입력을 멈춘 뒤 한 번만 찾고 빈 검색어로 열 때는 다시 받지 않는다: 실패 (추가 요청) |
| X-106 | #451 | 46b9621, 9d2a57f | main의 CustomerFilter.tsx는 넘길 때 head(17cc788)와 한 글자도 다르지 않고, 마지막으로 바꾼 커밋도 #451(f53222e7)이라 이후 squash나 후속 PR에서 고쳐지지 않았다. 78행 onSearch가 searchC | workspace.contract.spec.ts 격리 계약: 고객 선택지는 입력을 멈춘 뒤 한 번만 찾고 빈 검색어로 열 때는 다시 받지 않는다: 실패 (추가 요청) |
| D-062 | #451 | 320745a, e4c8c17 | CSV 파일 이름에 조회 월과 업무 환경을 넣는다 | export-filename.test.mjs: 실패 ERR_MODULE_NOT_FOUND |
| D-144 | #451 | 320745a, e4c8c17 | CSV 파일 이름에 조회 월과 업무 환경을 넣는다 | export-filename.test.mjs: 실패 ERR_MODULE_NOT_FOUND |
| X-096 | #451 | 320745a, e4c8c17 | CSV 파일 이름에 조회 월과 업무 환경을 넣는다 | export-filename.test.mjs: 실패 ERR_MODULE_NOT_FOUND |
| D-019 | #451 | c651e79 | CSV 내보내기 버튼이 카드 폭으로 늘어나지 않는다 | workspace.contract.spec.ts 조건 전체 CSV 내보내기는 보조 버튼이라 카드 폭으로 늘어나지 않는다: 실패 버튼 폭이 카드 절반 이상 |
| D-072 | #451 | c651e79 | CSV 내보내기 버튼이 카드 폭으로 늘어나지 않는다 | workspace.contract.spec.ts 조건 전체 CSV 내보내기는 보조 버튼이라 카드 폭으로 늘어나지 않는다: 실패 버튼 폭이 카드 절반 이상 |
| D-135 | #451 | c651e79 | CSV 내보내기 버튼이 카드 폭으로 늘어나지 않는다 | workspace.contract.spec.ts 조건 전체 CSV 내보내기는 보조 버튼이라 카드 폭으로 늘어나지 않는다: 실패 버튼 폭이 카드 절반 이상 |
| D-160 | #451 | c651e79 | CSV 내보내기 버튼이 카드 폭으로 늘어나지 않는다 | workspace.contract.spec.ts 조건 전체 CSV 내보내기는 보조 버튼이라 카드 폭으로 늘어나지 않는다: 실패 버튼 폭이 카드 절반 이상 |
| D-116 | #451 | f4080ce | #451(f53222e7)이 수집 로그 표 머리글만 '전송 ID', '발생 시각 (KST)', '수신 시각 (KST)', '이벤트 유형'으로 바꿨고, 상세 서랍은 그대로 두었다. main의 서랍은 넘길 때 head(17cc788)와 같은 코드라 아직  | list-copy.test.mjs the event detail drawer uses the table's Korean labels and says the raw JSON is in UTC: 실패 (영어 키 라벨) |
| D-199 | #451 | f4080ce | D-116과 원인이 같다. #451이 표 머리글은 한국어로 바꾸고 서랍에는 '수집 요청', '전송 주체' 같은 한국어 라벨을 더했지만, 기존 transaction_id, type, occurred_at, received_at 라벨은 영어 키로 남겼다. | list-copy.test.mjs the event detail drawer uses the table's Korean labels and says the raw JSON is in UTC: 실패 (영어 키 라벨) |
| D-242 | #451 | 809c459, d967c92 | main(af15d7f5)에서 git grep을 돌려 보면 넘길 때 head(17cc788)와 같은 문구가 그대로 남아 있어, 어느 squash에서도 고쳐지지 않았다. 목록 조건을 제출하는 같은 동작이 세 갈래로 나뉜다. ResourceSearchCo | auth.contract.spec.ts list filters submit with a default 조건 적용 button and reset with 조건 초기화: 실패 (ant-btn-primary) |
| D-112 | #451 | 4a2e1c5 | #451(f53222e7)이 미터 목록 capabilities를 q: true, sort: true로 바꿔서 main에서 rows는 서버가 검색어로 걸러 준 현재 페이지다. 검색 결과가 없으면 visibleRows가 곧 rows라서 rows.lengt | list-copy.test.mjs the meter search empty state counts all meters and the sort note follows the applied sort: 실패 (행 수 기준) |
| D-124 | #451 | c198bc1, 9d2a57f | main의 EventsTable.tsx 114행 모바일 레코드는 아직 Next Link가 아닌 그냥 <a href={detailHref(row)}>이고, 클릭 처리도 없다. 그래서 휴대폰 폭(globals.css 218행에서 data-mobile-re | list-copy.test.mjs mobile event links open the drawer in place and closing it returns focus to the row link: 실패 (일반 a 태그, 포커스 복귀 없음) |
| D-134 | #451 | d81a162, 9d2a57f | main의 ResourceSearchControls 107행도 MonthPicker를 div.workspace-field 안에 두기 때문에 globals.css 153행의 min-height 44px가 DatePicker 안쪽 input에 걸립니다.  | auth.contract.spec.ts filter row controls on a list screen share one height: 실패 (월 선택기 60px) |
| D-159 | #451 | d81a162, 9d2a57f | 원인은 D-185와 같습니다. main globals.css 153행의 '.workspace-field input' min-height 44px가 DatePicker 안쪽 input에도 걸려서 월 선택 칸이 약 60px로 커집니다. 지적은 antd S | auth.contract.spec.ts filter row controls on a list screen share one height: 실패 (월 선택기 60px) |
| D-201 | #451 | e373a55 | D-001과 원인이 같다. main의 globals.css 311행 display: flex 때문에 .resource-advanced summary의 기본 펼침 화살표가 없어진다. 이벤트 화면 .event-advanced(254행)는 화살표가 남아 같 | auth.contract.spec.ts list screens keep the disclosure marker on the advanced filter summary: 실패 (display flex) |
| D-203 | #451 | 809c459, d967c92 | 필터 적용과 초기화 부분은 D-263과 원인이 같고 main에서도 남아 있다. 이벤트 화면 안에서도 같은 초기화 주소를 필터 줄은 '필터 초기화', 빈 결과 안내는 '검색 조건 초기화'로 부르고, 감사 기록은 '조건 초기화'다. 모달 보조 버튼 부분은 | auth.contract.spec.ts list filters submit with a default 조건 적용 button and reset with 조건 초기화: 실패 (ant-btn-primary) |
| D-263 | #451 | 809c459, d967c92 | main에서도 조회 조건을 제출하는 버튼 문구가 화면마다 다르다. 목록 화면은 '조건 적용', 이벤트는 '이 조건으로 조회', 감사 기록은 '조건으로 조회', 단가 이력은 '이력 조건 적용', 월 선택은 '조회', 진단과 거절 화면은 '진단 조회'와  | auth.contract.spec.ts list filters submit with a default 조건 적용 button and reset with 조건 초기화: 실패 (ant-btn-primary) |
| D-013 | #451 | 809c459, d967c92 | 앞부분(필터 제출이 primary라 화면에 primary가 둘)은 main에 그대로 남아 있고 X-039와 원인이 같아 그 수정으로 함께 풀린다. 남는 뒷부분은 같은 동작의 이름이 화면마다 다르다는 지적인데, main에서도 제출은 '조건 적용', '이 | auth.contract.spec.ts list filters submit with a default 조건 적용 button and reset with 조건 초기화: 실패 (ant-btn-primary) |
| D-073 | #451 | 809c459, d967c92 | X-039와 원인이 같다. main의 ResourceSearchControls.tsx:124 '조건 적용'이 아직 primary라 청구 화면에는 되돌릴 수 없는 '월 전체 확정'과 채운 버튼이 둘 나오고, 요금 설계도 '단가 버전 등록'과 함께 pri | auth.contract.spec.ts list filters submit with a default 조건 적용 button and reset with 조건 초기화: 실패 (ant-btn-primary) |
| D-161 | #451 | 809c459, d967c92 | X-039와 원인이 같다. main에서 '조건 적용'은 여전히 type="primary"이고 ActionPanel 트리거는 default나 link만 되므로, 고객 목록에서 생성 동작보다 필터 버튼이 더 강하게 보인다. 청구 화면에는 InvoiceFi | auth.contract.spec.ts list filters submit with a default 조건 적용 button and reset with 조건 초기화: 실패 (ant-btn-primary) |
| X-082 | #451 | 8cefcf3 | 고객 검색칸의 라벨은 '이름 또는 외부 ID로 찾기'인데 같은 줄의 placeholder는 '이름, 외부 ID, 청구 이메일'이고, 백엔드 검색 SQL도 청구 이메일까지 LIKE로 찾는다. 라벨은 #447에서 들어왔고 청구 이메일 검색은 #451에서  | auth.contract.spec.ts the customer search names every searched field and fits its hint on a phone: 실패 (새 라벨 없음) |
| D-243 | #451 | 1ffe52a | 화면마다 방식이 다른 것 자체는 의도한 설계다. 고객, 미터, 가격처럼 조건이 여럿인 목록은 README 목록 조회 절과 폼 위 안내 문구에 맞춰 '조건 적용'으로 한꺼번에 제출한다. 실화면 시험도 이 세 화면에서는 select만 바꿔서는 이동하지 않 | list-copy.test.mjs the meter screen shows the same submit-based archive filter while loading or failing: 실패 (링크 묶음) |
| C451-003 | #451 | 3c7aae1, 46cb5c9 | 인보이스 검색이 고객과 월 조건을 조회에 넘긴다 | WorkspaceQueryIntegrationTest.인보이스_검색은_고객과_월_조건을_조회에_넘긴다: 실패 Argument(s) are different |
| C451-004 | #451 | fafcdc0 | 개요 금액 집계를 상태별로 시험한다 | 없음(DashboardIntegrationTest: 통과; 수납액에서 취소액을 빼지 않는 변이: 실패) |
| D-132 | #451 | 57e66ed | 반박하려고 고객 0곳을 따로 다루는 54행 분기와 백엔드 응답을 봤지만, /v1/usage/search는 summary를 항상 보내고 customer_count는 필터를 거친 뒤의 고객 수(filtered.size())라서 고객이 하나도 없는 환경에서 | usage-empty-kind.test.mjs usage empty states lead to registration instead of a filter reset when nothing exists yet: 실패 (모듈 없음) |
| Nk3-009 | #451 | 09e06ae | API 설명에 엔드포인트별 규칙과 summary 키를 적는다(결정: 공개 계약 유지) | 없음(OpenApiDocumentTest: 통과; 설명 문장과 코드 대조로 확인) |
| X-035 | #451 | 2bff105 | main에서도 사용량 화면은 ScreenHeader, 조회 조건과 사용량 표를 담은 ScreenCard 순서로 그리고, UsageQuality(수집 확인과 정정)를 ScreenCard 뒤 맨 끝에 붙인다(page.tsx:83). 넘길 때 head 17 | usage-quality.test.mjs the usage screen shows ingestion exceptions above the long usage table: 실패 (표 아래) |
| C451-006 | #451 | 01d7cbd, 921d9d0 | 조회 경로와 겹치는 미터 code를 거절한다 | BillableMetricServiceTest.조회_경로와_겹치는_code의_미터는_만들지_않는다: 실패 Expecting code to raise a throwable |
| C451-009 | #451 | 3c7aae1 | 금액 범위 시험이 합계 기준을 가린다 | 없음(금액 필터를 공급가액으로 바꾼 변이: 실패 expected: 1 but was: 0; WorkspaceQueryIntegrationTest: 통과) |
| C451-010 | #451 | d6653bf | main에서도 createCustomer는 external_id가 있을 때만 재시도를 켠다(meter-engine-client.ts 169행). 서버 설명대로 external_id를 빼면 매번 새 고객이 생기고 SDK는 Idempotency-Key를  | 변이(재시도 조건 true): external_id 없는 createCustomer는 실패해도 다시 보내지 않는다: 실패 확인 |
| D-034 | #451 | ce5972f | 사용량 툴팁에 미터명, 고객 상세에 이름 (코드)를 보인다 | a meter shows both its name and code on the usage screen and on customer detail: 실패 (한 화면은 코드, 다른 화면은 이름만) |
| D-057 | #451 | 4ee27ae | 공급자와 결제 설정 단계에 빠진 설정을 적는다 | the overview setup step says which supplier and payment settings are missing: 실패 (빠진 설정을 밝히지 않음) |
| D-102 | #451 | 31f0e9f | 고객 상세 월 소제목과 개요 산출 시각을 공용 표기로 맞춘다 | customer detail and overview write months and times in the shared Korean format: 실패 (원시 월과 로케일 시각) |
| D-113 | #451 | cf7c788 | main에서도 인보이스 화면의 수납 상태 태그가 unpaid를 '미납', paid를 '납부 완료'로 보여 주고, 인보이스 목록의 수납 상태 필터 옵션도 같은 이름을 쓴다. 반면 수납과 취소 화면(/payments, /payments/{id})은 #44 | invoice-payment-status.test.mjs invoice screens read payment status names and colors from the shared map: 실패 (따로 적은 이름) |
| D-118 | #451 | 836eba7 | main에서도 청구 예정액과 인보이스 표 하단 라벨은 그대로 '합계'(모바일은 '공급가액 합계', '세금 포함 합계')이고, 넘길 때 head 17cc788과 main 사이에 이 네 파일은 바뀐 것이 없다. 표에 넣는 값은 서버 summary 금액으로 | list-copy.test.mjs paged tables name their totals as totals of the whole filtered set: 실패 (합계 라벨) |
| D-121 | #451 | 951fc97 | 미터 목록 둘째 줄을 등록 단가 수로 맞추고 계산식은 단가 패널로 옮긴다 | every pricing rail meter shows its rate count and the selected meter's formula sits in its price panel: 실패 (고른 미터만 계산식) |
| D-125 | #451 | ffb168f | main의 성공 모달은 넘길 때 head(17cc788)와 같은 코드로, 92행이 state.invoices 전체를 개수 제한 없이 ul로 그리고 71행 Modal에는 본문 높이 제한(styles.body maxHeight, overflowY)이 없다 | work-copy.test.mjs the finalization result lists only the first few invoices and points to the invoice list for the rest: 실패 |
| D-143 | #451 | 8ead910 | 반박으로 고객 행의 이 칸에 '집계 라인 N줄'이 보여 정렬 결과가 화면 값과 맞는다는 점을 봤지만, 헤더 이름은 '집계 수량'이고 정렬 기준은 어디에도 드러나지 않는다. main에서 사용량 목록은 서버 정렬(sort true)이라 헤더를 누르면 so | list-copy.test.mjs the usage quantity header and sort option say they sort by aggregated line count: 실패 (툴팁과 라벨 없음) |
| D-156 | #451 | cf7c788 | main에서도 인보이스 목록의 수납 상태 태그가 color 없이 렌더되어 결제 실패, 결제 전, 수납 완료가 모두 같은 기본 회색이다. 같은 수납 상태를 보이는 수납 목록은 WorkspaceView.tsx:33 규칙으로 paid는 초록, failed는 | invoice-payment-status.test.mjs invoice screens read payment status names and colors from the shared map: 실패 (따로 적은 이름) |
| D-166 | #451 | bd15060, acad05a | 고객 보관 버튼을 이동 링크 줄에서 빼 고객 작업 줄 끝에 둔다 | the customer archive action sits with the other customer actions, not in the navigation link row: 실패 (보관 버튼이 이동 링크 줄에 있음) |
| D-231 | #451 | 9c8de96 | main의 고객 화면 숫자 줄은 넘길 때 head와 한 글자도 다르지 않고, #451 뒤에 이 파일을 바꾼 main 커밋이 없다. 백엔드는 total에 보관 조건과 검색어를 적용하고 total_unfiltered에는 조직과 환경의 전체 건수(보관 포함 | customer-count.test.mjs the customer count names its scope and only mentions a search when there is one: 실패 (모듈 없음) |
| G-111 | #451 | 4e6ebc7 | main에도 지적이 그대로 남아 있다. 결제 취소는 Idempotency-Key를 UUID로만 받고 충돌하면 payment_cancellation_idempotency_conflict를 돌려주며 재생 헤더가 없다. 고객과 미터 생성이 쓰는 Idempo | OpenApiDocumentTest.결제_취소의_재시도_키는_등록_API와_다른_형식과_재생_규칙을_적는다: 실패 (400 설명에 Idempotency-Key 없음) |
| G-118 | #451 | e79f63e | 가격 검색이 그 달에 청구하는 미터만 센다(결정: status 뜻 유지) | WorkspaceQueryIntegrationTest.가격_검색은_그_달에_청구하는_미터만_목록과_누락_단가_수에_넣는다: 실패 actual ["restored", "retired"] |
| G-121 | #451 | 6198c5d | 빈 검색 CSV도 같은 열 제목을 쓴다 | WorkspaceQueryIntegrationTest.결과가_없는_검색_CSV도_결과가_있을_때와_같은_열_제목을_쓴다: 실패 but was "empty_result" |
| G-122 | #451 | 70b1df3 | 이벤트 CSV 시각을 KST 오프셋 초 단위로 쓴다 | EventExportIntegrationTest.발생과_수신_시각을_KST_오프셋이_붙은_초_단위_형식으로_쓴다: 실패 CSV에 2026-09-30T15:00Z |
| G-123 | #451 | 09e06ae | API 설명에 엔드포인트별 규칙과 summary 키를 적는다(결정: 공개 계약 유지) | 없음(OpenApiDocumentTest: 통과; 설명 문장과 코드 대조로 확인) |
| G-126 | #451 | 3568cc3, d904aaf | CSV 내보내기 원본 속성 합계를 32MB로 묶는다 | EventExportIntegrationTest.원문_속성_합계가_상한을_넘으면_큰_본문_대신_오류를_반환한다: 실패 expected: 400 but was: 200 |
| G-178 | #451 | 416fb26 | 카드 목록 API에 customer_id 조건을 더하고 고객 상세가 쓴다 | 카드_목록에_customer_id를_주면_그_고객의_카드와_보관_이력만_나온다: 실패 (다른 고객 카드 포함) |
| Nk3-008 | #451 | c93b2d3 | 개요 미수금 정의를 적는다(결정: 이름과 값 유지) | 없음(OpenApiDocumentTest: 통과, 설명과 DashboardService, WorkspaceQueryService 대조) |
| Nk3-010 | #451 | ec1bc7f | main에서도 WorkspaceQueryController에는 @ApiResponse가 하나도 없고, OpenApiConfig.documentAccess는 401, 403, 429만 자동으로 더하므로 검색, 내보내기의 400 validation_err | OpenApiDocumentTest 새 시험 6개: 실패 (400, 404, 409 응답과 재생 헤더 없음, CSV format byte) |
| X-026 | #451 | 214e22f | 없는 고객 상세에 찾을 수 없음 제목과 고객 목록 링크를 둔다 | a missing customer says it was not found and links back to the customer list without a retry: 실패 (제목과 목록 링크 없음) |
| X-027 | #451 | 3bb093e, f76f1ed, 02b3f10 | 결제 실패 항목이 실패만 거른 수납 목록으로 잇는다 | the payments list can show only the month's failed payments and go back to all of them: 실패 (상태로 거를 수 없음) |
| X-050 | #451 | 21ee4a0 | main에서도 백엔드는 같은 external_id가 이미 있으면 새 고객을 만들지 않고 기존 고객을 200으로 돌려주며 이름과 이메일을 바꾸지 않는다. 프론트의 transport.ts 성공 결과는 {ok, data}뿐이라 200과 201을 가르지 못하 | workspace-action-feedback.test.mjs re-registering an existing external customer id is reported as not created: 실패 (export 없음) |
| X-071 | #451 | 2d5a733 | main에서도 사용량 화면(usage/page.tsx:43)의 탭 줄은 '사용량 | 수집 로그 | 청구 검토로 이동', 수집 로그 화면(events/page.tsx:41)의 탭 줄은 '월별 사용량 | 수집 로그 | 서버 연동 설정'으로 갈라져 있고,  | list-copy.test.mjs usage and event screens drop the mismatched sub-tab rows and keep their cross links in the content: 실패 (탭 줄) |
| X-083 | #451 | 836eba7 | main의 InvoicesSection.tsx는 넘길 때 head(17cc788)와 한 글자도 다르지 않아 고쳐지지 않았다. #451이 앞에 넣은 65행 분기가 검색, 고객, 미터, 수납 상태, 금액, 기간, 결제 모드 조건을 모두 먼저 받기 때문에  | list-copy.test.mjs an empty invoice month points to the draft amounts instead of a reset that changes nothing: 실패 (필터 초기화 링크) |
| X-092 | #451 | ccfa250 | 버전 충돌 시 최신 정보 불러오기 버튼으로 폼을 새 값으로 다시 그린다 | a customer edit that lost a version race offers to load the latest values and then saves: 실패 (다시 불러올 버튼 없음) |
| X-093 | #451 | 8cc84f9 | 보관 뒤 성공 안내를 보이고 조회 월을 지킨 보관 목록으로 옮긴다 | archiving a customer, a member or a receiver confirms what happened: 실패 (성공 문구 없음) |
| Ck1-454-01 | #454 | 0428493, 52a27da | main의 Caddyfile은 두 log 블록(9~11줄, 48~50줄)에 같은 정규식을 세 줄씩 복사해 두었고, RequestLogFilter는 같은 경로 목록을 다른 정규식으로 따로 들고 있다. 넘길 때 head(dac784b)와 main 사이에  | test_logging.test_variant_paths_do_not_leak_tokens_into_logs: 실패 (인코딩 경로의 토큰이 로그에 남음) |
| Ck1-454-02 | #454 | 2c62eb1 | main의 운영 compose는 백엔드에 호출 제한 설정 가운데 ENABLED와 TRUSTED_PROXIES 두 개만 넘기고, 로그인과 가입의 한도 넷과 창 둘은 넘기지 않는다. deploy.sh는 Parameter Store 값을 모두 env 파일에 | test_trusted_proxies.test_every_auth_rate_limit_setting_reaches_backends_with_the_application_default: 실패 (LOGIN_IP_LIMIT 등 누락) |
| Ck1-454-04 | #454 | 8453b90 | main의 서비스는 한도 확인보다 먼저 매 요청마다 cleanExpired 트랜잭션과 consume 트랜잭션을 따로 열고, 429는 consume가 끝난 뒤에야 던진다. consume의 upsert는 이미 한도를 넘어 attempts가 limit+1로 | AuthRequestRateLimitIntegrationTest.이미_한도를_넘은_요청은_카운터_행을_다시_쓰지_않는다: 실패 (카운터 행 xmin이 바뀜) |
| C454-002 | #454 | 8933243, 66054cb | 보안 지적 수정 | AuthRequestRateLimitIntegrationTest.다른_IP의_로그인_실패는_내_IP의_계정_한도를_쓰지_않는다: 실패 (expected: 401 but was: 429) |
| C454-005 | #454 | 8933243, 66054cb | 보안 지적 수정 | AuthRequestRateLimitIntegrationTest.다른_IP의_로그인_실패는_내_IP의_계정_한도를_쓰지_않는다: 실패 (expected: 401 but was: 429) |
| C454-008 | #454 | 09a1e24 | 보안 지적 수정 | AuthRequestRateLimitIntegrationTest.계정_한도는_로그인_조회와_같은_DB_규칙으로_대소문자를_합친다: 실패 (expected: 429 but was: 401) |
| Ck1-454-05 | #454 | 9353ab3 | main에서도 로그인(POST /v1/sessions)과 가입 신청(POST /v1/signups)의 429 응답은 설명 문구에만 Retry-After를 적고 headers 정의가 없다. 실제 서버는 AuthRequestRateLimitService가 | OpenApiDocumentTest.로그인과_가입의_429는_Retry_After_헤더를_적는다: 실패 (헤더 정의 없음) |
| C454-003 | #454 | 09a1e24 | 보안 지적 수정 | AuthRequestRateLimitIntegrationTest.계정_한도는_로그인_조회와_같은_DB_규칙으로_대소문자를_합친다: 실패 (expected: 429 but was: 401) |
| C454-007 | #454 | 09a1e24 | 보안 지적 수정 | AuthRequestRateLimitIntegrationTest.계정_한도는_로그인_조회와_같은_DB_규칙으로_대소문자를_합친다: 실패 (expected: 429 but was: 401) |
| X-088 | #454 | d7e08ef | 보안 지적 수정 | auth.contract.spec.ts every page refuses framing and MIME sniffing ...: 실패 (Received: undefined) |
| G-099 | #454 | 4b6b732 | main의 초대 수락 서버 액션은 새 계정으로 수락한 뒤 POST /v1/sessions로 자동 로그인하는데, 이 호출에 clientIp를 넘기지 않는다. 같은 /v1/sessions를 부르는 loginAction과 가입 액션은 x-meterengin | auth.contract.spec.ts the automatic login after accepting an invitation sends the visitor's client address: 실패 (Received: undefined) |
| G-101 | #454 | 5a91d92, abc82c5 | main에서도 RequestLogFilter는 X-Forwarded-For 첫 값을 client_address로 찍고, 로그인과 가입 한도는 신뢰 프록시 대역에서 온 X-MeterEngine-Client-IP로 IP를 센다. 운영에서 콘솔 로그인은 프 | AuthRequestRateLimitIntegrationTest.한도에_걸린_요청은_센_클라이언트_주소와_걸린_한도를_이메일_없이_로그에_남긴다: 실패 (limit, retry_after 로그 없음) |
| U12-B1 | #454 | 56f4f94 | 보안 지적 수정 | test_logging.test_public_and_internal_proxies_overwrite_client_ip_sent_by_other_hosts: 실패 (internal=True에서 보낸 값 유지) |
| C455-002 | #455 | a0a5f21 | main의 루트 README에서 rds_plan_dir는 '계획과 적용' 절의 bash 블록(13, 14행)에서만 정의된다. '복구' 절의 감지 끄기 블록(50~54행)은 이 변수를 다시 정의하지 않고 -out="$rds_plan_dir/disable | 없음(문서 변경. 복구 절 명령이 umask와 rds_plan_dir 정의부터 시작하고 감지 끄기가 기본값 PR 절차로 바뀐 것을 확인) |
| C455-003 | #455 | 24958e0 | main의 workflow.test.mjs는 넘길 때 head(0fe7d2a)와 한 글자도 다르지 않아 지적한 빈틈이 그대로 남았다. stopDBInstance 가짜 응답은 인자를 보지 않고 늘 성공을 돌려주고, putItem 가짜 응답은 Condit | workflow.test.mjs(변이: 중지 대상 db_arn, 기록 표 db_identifier): 실패 확인 |
| C455-005 | #455 | d1e1526 | main에서도 지적이 그대로 남았다. ci.yml의 paths-filter에는 backend와 frontend 두 필터만 있고 job도 backend, frontend 둘뿐이라, measurements/ 아래만 바꾸는 PR은 두 job이 skip되어  | 없음(CI 단계를 로컬에서 순서대로 실행: npm 27개, fmt, 모듈 mock 4개, root mock 3개 통과, YAML 파싱 확인) |
| G-162 | #455 | 99fe508, aac9c37 | main에서도 지적이 그대로 남아 있다. rds-auto-stop root는 환경을 test-slo와 test-storage로 고정하고(main.tf 6행), 두 환경의 DB를 data source로 읽으며(9-12행), enabled 검증도 두 키를 | rds-auto-stop/tests/environments.tftest.hcl one_environment_can_be_removed_before_its_sut: 실패 (Invalid value for variable) |
| G-163 | #455 | c6d2042 | main(af15d7f5)의 measurements/ 아래 코드는 넘길 때 head(0fe7d2a)와 한 줄도 다르지 않아서, 지적한 실패 경로가 그대로 남아 있다. 두 환경의 sut-start.sh는 RDS가 starting이면 시작 요청을 건너뛰고 | 없음(문서 변경. 상태 기계 이름과 TimeoutSeconds 7200을 모듈에서 확인, AWS 명령은 실행하지 않음) |
| Nk4-001 | #455 | a0a5f21 | main의 measurements 폴더는 넘길 때 head 0fe7d2a와 글자 하나 다르지 않고, 04:00 백스톱 수정(f8913453)이 #455 squash로 들어왔다. 그래서 제목 뒷부분인 "되살린 04:00 스케줄을 다시 끈다"는 main에 | 없음(문서 변경. 복구 절 명령이 umask와 rds_plan_dir 정의부터 시작하고 감지 끄기가 기본값 PR 절차로 바뀐 것을 확인) |
| Nk4-002 | #455 | 8abbcab | main의 rds-auto-stop 잠금 파일에는 aws 6.69.0의 h1 해시가 qXrr 값 하나뿐이고, README 계획 절은 init -lockfile=readonly로 시작한다. readonly init은 zh 해시로 zip을 검증해 통과하지 | 없음(잠금 파일 h1 해시 1개에서 4개로, 새 잠금 파일로 init -backend=false와 mock 시험 4개 통과) |
| Nk4-003 | #455 | 1e741a9, aac9c37 | main의 measurements/terraform은 넘길 때 head 0fe7d2a와 한 글자도 다르지 않아 지적한 줄이 그대로 남아 있다. EventBridge는 900초, 3회 재시도 뒤 DLQ로 보내고, 상태 기계는 15분이 지난 이벤트를 Ig | rds-auto-stop/tests/environments.tftest.hcl both_environments_by_default: 실패 (Reference to undeclared output value) |
| Nk4-004 | #455 | e0fcdef | 반박을 먼저 시도했지만 실패했다. main의 rds-auto-stop 모듈은 넘길 때 head 0fe7d2a와 한 줄도 다르지 않고(#455 squash 하나만 이 모듈을 건드렸다) 설정 키 계약을 검사하는 곳이 여전히 없다. main.tf가 Conf | contracts.tftest.hcl scoped_automatic_restart(변이: $config.claim_tables): 실패 확인 |
| Nk4-007 | #455 | 88e3fe8 | main의 variables.tf 5행 정규식은 아직 {1,45}이고 6행 오류 문구도 45자를 약속한다. 그런데 main.tf 2행이 접두 뒤에 '-rds-auto-stop'(14자)을 붙이고, iam.tf 41행이 다시 '-workflow'(9자) | contracts.tftest.hcl name_prefix_too_long_for_iam_role_names: 실패 (Missing expected failure, IAM 이름 64자 초과) |
| Nk4-008 | #455 | a3e0439 | main의 모듈 README 로컬 검사 절차는 넘길 때 head(0fe7d2a)와 한 글자도 다르지 않고, 여전히 test-slo SUT root의 잠금 파일을 임시 폴더로 복사해 terraform test를 돌린다. 이 모듈을 쓰는 root는 mea | 없음(문서 변경. 바꾼 절차(배포 root 잠금 파일)로 mock 시험 4개 통과) |
| NT-inho-003 | #444 | 76f7215 | main에서도 WebhookClient는 TLS 인증서 오류, DNS 실패, 연결 거부(모두 IOException)와 전송 작업 안의 예상하지 못한 예외(ExecutionException)를 모두 connection_error로 돌려준다. 이 값은 전 | 없음(문서 변경. WebhookClient의 connection_error 분기와 로그 줄을 코드에서 확인) |
| NT-inho-006 | #451 | 3373654 | environment-cycle 명세가 개요 제목을 화면과 같은 formatKoreanMonth로 만들어 기대한다(PR #471) | environment-cycle.real.spec.ts: 실패 heading '이번 달 과금 운영' element(s) not found @ af15d7f5 |
| NT-inho-007 | #442 | 3373654 | mvp 명세가 API 키와 웹훅 비밀값을 복사한 뒤 닫는다(PR #471) | mvp.real.spec.ts: 실패 locator.click Timeout, ant-modal-wrap intercepts pointer events @ af15d7f5 |
| NT-inho-009 | #442 | 62a2f0f | 워크스페이스 계약 명세가 비밀값을 복사한 뒤 닫는다 | workspace.contract.spec.ts 발급 중 닫기, 키 한도: 실패 dialog toHaveCount(0) @ af15d7f5 |

## 정할 것

| id | 층 | 질문 |
| --- | --- | --- |
| X-012 | #443 | 정해 둔 KS X 1001 subset 가변 글꼴이 Pretendard 1.3.9에 공식 파일로 없다(npm pretendard에는 전체 가변, 굵기별 정적 subset, 가변 dynamic subset 92조각만 있고 pretendard-std 가변 글꼴은 한글이 없다. OFL 예약 이름 때문에 직접 만든 subset은 이름을 바꿔야 한다). 사용자가 PR #499에서 빼고 결정 대기로 두기로 했다. 공식 dynamic subset, 쓰는 굵기만 정적 subset, 직접 만든 subset 가운데 박성종 님과 정한 뒤 따로 올린다. |
| D-071 | #443 | 정해 둔 KS X 1001 subset 가변 글꼴이 Pretendard 1.3.9에 공식 파일로 없다(npm pretendard에는 전체 가변, 굵기별 정적 subset, 가변 dynamic subset 92조각만 있고 pretendard-std 가변 글꼴은 한글이 없다. OFL 예약 이름 때문에 직접 만든 subset은 이름을 바꿔야 한다). 사용자가 PR #499에서 빼고 결정 대기로 두기로 했다. 공식 dynamic subset, 쓰는 굵기만 정적 subset, 직접 만든 subset 가운데 박성종 님과 정한 뒤 따로 올린다. |
| G-075 | #443 | 정해 둔 KS X 1001 subset 가변 글꼴이 Pretendard 1.3.9에 공식 파일로 없다(npm pretendard에는 전체 가변, 굵기별 정적 subset, 가변 dynamic subset 92조각만 있고 pretendard-std 가변 글꼴은 한글이 없다. OFL 예약 이름 때문에 직접 만든 subset은 이름을 바꿔야 한다). 사용자가 PR #499에서 빼고 결정 대기로 두기로 했다. 공식 dynamic subset, 쓰는 굵기만 정적 subset, 직접 만든 subset 가운데 박성종 님과 정한 뒤 따로 올린다. |
| G-085 | #443 | 정해 둔 KS X 1001 subset 가변 글꼴이 Pretendard 1.3.9에 공식 파일로 없다(npm pretendard에는 전체 가변, 굵기별 정적 subset, 가변 dynamic subset 92조각만 있고 pretendard-std 가변 글꼴은 한글이 없다. OFL 예약 이름 때문에 직접 만든 subset은 이름을 바꿔야 한다). 사용자가 PR #499에서 빼고 결정 대기로 두기로 했다. 공식 dynamic subset, 쓰는 굵기만 정적 subset, 직접 만든 subset 가운데 박성종 님과 정한 뒤 따로 올린다. |
| C443-002 | #443 | 정해 둔 KS X 1001 subset 가변 글꼴이 Pretendard 1.3.9에 공식 파일로 없다(npm pretendard에는 전체 가변, 굵기별 정적 subset, 가변 dynamic subset 92조각만 있고 pretendard-std 가변 글꼴은 한글이 없다. OFL 예약 이름 때문에 직접 만든 subset은 이름을 바꿔야 한다). 사용자가 PR #499에서 빼고 결정 대기로 두기로 했다. 공식 dynamic subset, 쓰는 굵기만 정적 subset, 직접 만든 subset 가운데 박성종 님과 정한 뒤 따로 올린다. |
| Ck3-451-02 | #451 | 세션이 지금 동작을 유지하기로 정하고 근거를 PR #491 본문에 적었다(PATCH는 If-Match 필수 전체 교체로 둔다(박성종 님 #451 설계, API 설명에 이미 적힘)). 리뷰어 박성종 님이 동의하면 그대로 닫고, 바꾸자고 하면 후속 PR로 고친다. |
| Nk3-011 | #451 | 세션이 지금 동작을 유지하기로 정하고 근거를 PR #491 본문에 적었다(draft_invoice는 read API 키 호환 때문에 응답에 남긴다). 리뷰어 박성종 님이 동의하면 그대로 닫고, 바꾸자고 하면 후속 PR로 고친다. |
| NT-inho-001 | #443 | 세션이 지금 문구를 유지하기로 정하고 근거를 PR #499 본문에 적었다(시연 안내는 발표용 기능이라 '시연' 낱말을 유지한다). 리뷰어 박성종 님이 동의하면 그대로 닫고, 바꾸자고 하면 후속 PR로 고친다. |
| NT-inho-004 | #448 | 세션이 지금 동작을 유지하기로 정하고 근거를 PR #492 본문에 적었다(감사 화면 기본 보기는 지금처럼 모든 기록으로 둔다(#448 설계). 행동 조건으로 좁힌다). 리뷰어 박성종 님이 동의하면 그대로 닫고, 바꾸자고 하면 후속 PR로 고친다. |
| NT-inho-008 | #454 | 세션이 HSTS를 이번에 더하지 않기로 정하고 근거를 PR #493 본문에 적었다. 배포 도메인 기준과 max-age를 정할 때 박성종 님이 따로 결정한다. |
| NT-inho-010 | #454 | 인증 호출 제한에서 IPv6 클라이언트 주소를 접두 대역(/64 등)으로 묶어 셀지. 운영 배포에 IPv6가 들어오는지 먼저 확인해야 한다. |

## 막힌 것

| id | 층 | 까닭 |
| --- | --- | --- |

## 새 지적

| id | 층 | 상태 | 내용 |
| --- | --- | --- | --- |
| NT-inho-001 | #443 | needs-decision | 실제 도입사 관리자에게 보이는 헤더 토글과 안내 영역이 '시연'이라는 낱말을 쓴다 |
| NT-inho-002 | #444 | already-fixed | 반영된 #442 workspace-operations.test.mjs가 #444 head에서 실패한다(restoreWebhookEndpoint 분기 없음) |
| NT-inho-003 | #444 | fixed | 도입사에게 보이는 웹훅 실패 사유가 인증서 오류와 우리 쪽 예외까지 connection_error 하나다 |
| NT-inho-004 | #448 | needs-decision | 감사 화면을 직접 열면 기본 목록이 화면 조회 때마다 쌓이는 API 요청 기록으로 채워진다 |
| NT-inho-005 | #448 | already-fixed | #448 head의 archive-filters.real.spec.ts가 고객 목록의 '관리 상태' 선택 필터를 찾지 못해 실패한다 |
| NT-inho-006 | #451 | fixed | environment-cycle 실화면 명세가 개요 h1을 '이번 달 과금 운영'으로 기대하는데 main은 선택한 달 이름을 그린다 |
| NT-inho-007 | #442 | fixed | mvp 실화면 명세가 API 키 발급 뒤 Escape로 닫는데 main은 복사 전 닫기를 확인 창으로 묻는다 |
| NT-inho-008 | #454 | needs-decision | 운영 Caddyfile에 HSTS가 없다(X-088에서 떼어 낸 결정 항목) |
| NT-inho-009 | #442 | fixed | 워크스페이스 계약 명세 두 시험이 API 키 발급 뒤 복사 없이 닫아 main에서도 실패한다 |
| NT-inho-010 | #454 | needs-decision | 인증 호출 제한의 IPv6 주소 묶음(PR #493 두 번째 리뷰에서 나옴) |

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
- X-024 (#448): #453 23b1844가 AuditScreen.tsx import 줄을 바꿨다. 반영 때 @/lib/audit import와 auditActorLabel 호출을 남긴다. 정점 people.real.spec.ts와 operator-create.real.spec.ts는 .audit-summary strong과 .audit-detail만 읽어 영향이 없다.
- Ck1-448-01 (#448): 반영 때 AuditLogQueryService.boundary의 연도 범위 검사와 AuditLogController의 from, to 설명, 200과 400 응답을 남기고 openapi.yaml은 그 층에서 다시 만든다.
- Ck1-448-03 (#448): 반영 때 backend README audit 행과 X-Request-ID 문장을 남긴다. frontend README 화면 표는 #452 29a78d0의 묶인 형식에 맞춰 /admin 행에 /admin/audit을 넣고 /audit 행을 더한다.
- D-152 (#448): #453 23b1844가 AuditScreen.tsx import 줄을 바꿨다. 반영 때 @/lib/audit import와 auditActorLabel 호출을 남긴다. 정점 people.real.spec.ts와 operator-create.real.spec.ts는 .audit-summary strong과 .audit-detail만 읽어 영향이 없다.
- GA-B1 (#448): #450부터 RequestAuditFilter가 REQUEST_ID_ATTRIBUTE를 넣는다. 반영 때 shouldNotFilter의 isApiRequest와 JsonRequestRejectedHandler의 RequestAuditFilter.isApiRequest 호출을 남긴다. isIngestion은 그대로다.
- X-007 (#448): #453 23b1844가 AuditScreen.tsx import 줄을 바꿨다. 반영 때 @/lib/audit에서 AUDIT_ACTIONS, AUDIT_ACTION_OPTIONS, AUDIT_ACTOR_TYPES, auditActorLabel을 가져오는 줄을 남기고 AuditScreen 안의 ACTIONS 블록은 지운 쪽을 택한다. 새 시험은 #449부터 정점까지의 백엔드 행동도 모두 통과한다.
- Nk1-002 (#448): #449 반영 때 이 가드 시험이 {view_link_id}로 실패한다. #449에서 RequestAuditFilter.UUID_VARIABLES의 id 앞에 view_link_id를 더해야 폐기 요청 행의 target_id가 링크 id가 되고 시험이 통과한다.
- Nk1-003 (#448): #453 반영 때 RequestAuditService는 AuditLogService.record를 부르는 모양을 남기고, 요청 감사의 environment_id를 principal에서 넘길 수 있게 AuditLogService에 environmentId를 받는 record 오버로드를 더하거나 BusinessEnvironmentContext와 principal 값이 같은지 그 층에서 맞춘다.
- X-045 (#448): #453부터 people.real.spec.ts와 operator-create.real.spec.ts가 .audit-summary strong을 행동 이름으로 찾는다. 이 수정은 strong 자리를 두고 결과만 StatusTag로 바꿨다. 반영 때 globals.css의 감사 줄과 AuditScreen의 StatusTag import를 남긴다.

## fixed-above로 둔 것

- C446-002 (#446): fixed-above #451 e5fca11: 정점 billable-metric-prices/page.tsx가 /pricing으로 redirect해 옛 Section을 쓰는 곳이 없다. 이 층 head에서는 아직 고장이라 층마다 고칠지는 배포 단위 결정 뒤 조율 담당이 정한다
- C446-006 (#446): fixed-above #447 2ec5d85: VersionedPricingIntegrationTest의 최신_단가_보관, 최신_요금제_보관 시험(정점 178-209행)이 겹친 달의 최신 버전 선택을 잡는다. 이 층에는 그 시험이 없다
- C446-010 (#446): fixed-above #451 e5fca11: 정점 pricing/page.tsx 37행이 선택한 미터 하나의 단가 이력만 부른다. 이 층 head는 미터마다 부른다

## 마지막 실화면 점검

| 층 | sha | 통과한 명세 | 화면 결과 |
| --- | --- | --- | --- |
| #443 | b99d2ef | 1/1 | 고친 화면 66건 after 대비 콘솔 오류, 넘침, axe 증가 없음 |
| #444 | c0a9f4a | 1/1 | 고친 화면 4건 after 대비 콘솔 오류, 넘침, axe 증가 없음 |
| #448 | cea0a76 | 1/2 | 명세 실패로 멈춰 화면 비교는 돌리지 않음 |

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
- 2026-10-29T00:59:24+09:00 U6-B3(Q3) 결정 때 다시 볼 동작: #448 GA-B1(b0bf0b1)로 요청 감사가 퍼센트 인코딩한 경로를 디코딩해 판정하므로, 인코딩한 수집 경로(POST /%761/events)는 수집 제외(isIngestion, 원문 비교)에 걸리지 않고 감사 행을 남긴다. 시험 퍼센트_인코딩한_수집_경로는_수집으로_보지_않아_감사기록을_남긴다가 이 동작을 단정한다.
- 2026-10-29T01:27:30+09:00 12절로 전체를 멈췄다: #448 마지막 실화면 점검에서 archive-filters.real.spec.ts가 실패했고, 바꾸지 않은 head 6431cef와 넘길 때 head bc7e538에서도 같은 단계에서 같은 실패였다(NT-inho-005). #448 항목은 모두 끝 상태이고(fixed 10, needs-decision 5, already-fixed 1, refuted 1), 고친 커밋은 fix/s456-448-t-inho(PR #469)에 모두 push했다. #446은 동기화 충돌(C446-001)로 남은 24건이 열려 있다.
- 2026-10-29 스택 #442~#455가 main(af15d7f5)에 squash로 머지됐다(후속 PR #465 #466 #468 #469 포함). 이 뒤로는 최신 origin/main을 기준으로 남은 지적을 판정하고 주제별 fix/s456-t-inho-<주제> 브랜치에서 고쳐 main 대상 PR로 올린다. 층 순서, feat 브랜치, fix/s456-<PR>-t-inho, 9절 동기화 merge, 15절 층별 마지막 실화면 점검은 쓰지 않고, 이미 있는 V__는 고치지 않는다(새 V__를 더하거나 needs-decision).
- 준비 4번 바꾸지 않은 main(af15d7f5) 기준선: backend, frontend, frontend-unit(79/79), 계약 명세(65), demo(unittest와 pnpm -r typecheck, lint, test, build), demo llm-service 명세 목록, deploy, rds-auto-stop, static이 통과했다. 실화면 명세 12개 가운데 10개가 통과했고 environment-cycle(NT-inho-006)과 mvp(NT-inho-007)가 두 번 모두 같은 단계에서 실패해 준비 4번대로 1단계 판정 전에 멈췄다.
- demo의 corepack pnpm -r build는 main에서 통과한다. build 스크립트가 없는 demo/sdk는 건너뛰고(Scope: 3 of 4) web, llm-service만 빌드하므로 not-applicable로 적지 않았다.
- 로컬 DB: 2026-10-29 docker compose down -v로 compose 프로젝트 meterengine의 meterengine_postgres-data(V20까지 적용)를 지우고 main 기준으로 다시 띄웠다. 실화면 환경 DB(meterengine-0696-e2e)도 main에서 새로 만들었다.
- 넘기기 자료 41d92a5: findings.json은 바뀌지 않았다. common-brief 10절 표에 archive-filters가 더해졌고 orchestration.md가 생겼다. 바뀐 규칙(main 기준)이 이보다 우선한다.
- 1단계 판정 준비: 판정 대상은 open_ids 193건과 needs-decision 36건, blocked 5건(항목 219개, 원인 묶음 166개)과 새 지적 needs-decision 3건이다. 시범으로 g0(X-012 묶음, 두 관점)만 main에서 다시 판정했고 다섯 건 모두 needs-decision이 유지된다. 로컬 e4da2fa(C446-001)는 쓰지 않고 main에서 다시 판정한다.
- 2026-10-29 사람 지시: environment-cycle.real.spec.ts(NT-inho-006, 개요 제목 '이번 달 과금 운영' 기대)와 mvp.real.spec.ts(NT-inho-007, API 키 발급 뒤 Escape 닫기)는 main에서 이미 실패하는 시험으로 기록하고 기준선에서 뺀다. 앞으로 검사에서 이 두 파일이 같은 이유로 실패하면 screen을 not-applicable로 적고 멈추지 않는다. 다른 이유로 실패하면 멈춘다.
- PR #471(fix/s456-t-inho-real-spec-baseline)이 NT-inho-006, NT-inho-007을 고쳤다. 이 PR이 main에 머지되기 전까지 다른 주제 브랜치의 검사에서 environment-cycle과 mvp 명세가 같은 이유로 실패하면 screen을 not-applicable로 적고 멈추지 않는다.
- 2026-10-29 1단계(main af15d7f5 기준 판정과 묶음): 대상 219항목과 새 지적 4건을 원인 묶음 169개로 나눠 묶음마다 에이전트 하나(high와 보안은 두 관점)로 판정했고 메인 세션이 evidence 줄을 main에서 열어 대조했다. 박성종 님 글(2026-10-29)대로 needs-decision도 세션이 정해 묶음에 넣고, 다른 사람 영역이나 팀 전체 결정이면 그 사람을 리뷰어로 둔다. 결정안은 각 항목 left에 있다. 묶음 목록은 evidence/main-stage1/topics.md
- 2026-10-29 사람 승인: 22개 주제를 PR 8개로 합쳐 진행한다(events-query, billing-api, webhook-audit-ops, auth-security, demo-infra, list-screens, work-screens, shell-design 순). 300줄 상한은 없고 PR 안에서 항목별로 커밋을 나눈다.
- PR #488(fix/s456-t-inho-events-query): events-query 묶음 46건과 NT-inho-009를 고쳤다. push 전 독립 리뷰가 찾은 회귀 세 건(인보이스 검색 total_unfiltered, 속성 미포함 CSV의 크기 상한, 한쪽 끝 기간 파일 이름)도 같은 PR에서 고쳤다. Ck3-450-11은 이름 규칙 시험 때문에 결정안을 설명 한 줄로 바꿨다.
