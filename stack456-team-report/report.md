미완료 355건, 결과: 끝 상태 17/372건 (fixed 0, refuted 1, already-fixed 0(그중 fixed-above 0), needs-decision 16, blocked 0(그중 no-evidence 0))
push: 없음
정할 것: 16건 (X-012, D-071, G-075, G-085, D-011, D-065, G-072, G-080, C443-002, D-039, D-055, D-244, D-022, G-084, G-078, D-237). 로컬 DB 다시 만들기: 없음

상태: running, 갱신 2026-10-28T17:09:26+09:00

## 층별 상태

| 층 | 브랜치 | 전체 | fixed | refuted | already-fixed | needs-decision | blocked | 미완료 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| #443 | feat/product-design | 92 | 0 | 1 | 0 | 16 | 0 | 75 |
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

## 막힌 것

| id | 층 | 까닭 |
| --- | --- | --- |

## 새 지적

없음

## 돌리지 못한 검사

없음

## 위층 반영 때 볼 것

없음

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
