# 스택 #456 k5 넘기기

사용자 요청으로 검증 도중 멈추고 정리했다. 결과 파일은 같은 폴더의 `k5.json`, `k5.md`다.

## 끝낸 것

- 판정을 마친 지적: 81건(48묶음). confirmed 39묶음, refuted 8묶음, already-fixed 1묶음.
- 판정을 마친 배치: environment, home-page, workspace-labels, audit, action-panel-b, lib-tenant, lib-config-proxy, lib-api, lib-auth.
- 수정 브랜치: 없다. 수정은 하나도 시작하지 않았고 `claude/s456-k5-*` 수정 브랜치는 만들지 않았다.
- 보고 브랜치: `claude/s456-k5-report` (`origin/main`에서 만들었고 `stack456-report/k5*` 파일만 있다).

## 남은 것

### 판정하지 못한 id (157건)

원인이 같은 지적을 미리 묶어 둔 배치다. 묶음은 가설이므로 검증하며 나누거나 합친다.

- 배치 `shell-layout`
  - sidebar-overflow (sticky 사이드바가 뷰포트보다 길어 하단 메뉴에 닿지 못함): D-002, D-024, D-048, D-064, D-235, D-249, X-010
  - header-height (모바일 헤더 높이와 --workspace-header-height 불일치, 68px 리터럴): D-011, D-039, D-055, D-065, D-153, X-047, G-083
  - sticky-thead-focus (역방향 Tab 시 sticky 표 머리글 아래로 포커스가 가려짐): X-048
  - header-org-clip (320px에서 헤더 도입사 이름 잘림): X-058
  - sidebar-group-padding (사이드바 그룹 제목 시작선 불일치): X-079
  - word-break (콘솔 본문 word-break keep-all 없음): D-074, X-098
- 배치 `shell-nav-header`
  - meter-menu (미터/가격 메뉴 없음, 현재 위치 표시 없음): C442-016, D-031, D-154, D-258, X-025
  - nav-role-menu (사이드바 role=menu, ul Tab 정지): D-179, X-019
  - scope-refresh-button (헤더 '전체 관리 / 권한 새로 조회' 버튼): D-020, D-038, D-058, D-137, D-167, D-191, D-229, X-062
  - org-dropdown-look (헤더 도입사 이름 ▼): X-063
  - integrations-naming (/integrations 화면 이름 불일치): D-021, D-145, D-220
  - role-labels (헤더 역할/권한 라벨이 라벨 정의 우회): G-077
  - guide-mode (GuideMode aria-pressed와 이름 변화): D-138, D-178, X-059
  - unused-shell (쓰이지 않는 셸 컴포넌트, DevStateSwitch, Layout 토큰): G-054, G-081
- 배치 `theme-color`
  - old-blue (antd 기본 파랑 잔존): D-009, D-036, D-053, D-079, D-098, D-169, D-187, D-233, D-260, X-043
  - focus-ring (antd 포커스 링 대비 1.87:1): D-151, D-174, X-001
  - checkbox-contrast (체크박스 테두리, 링크 hover, 보조 라벨 대비): D-173, X-022
  - token-dup (색 토큰 재정의, 리터럴 복제): D-244, G-071, G-072
  - wordmark (워드마크/로고 마크 복제): D-022, G-084
- 배치 `global-css-misc`
  - reduced-motion (prefers-reduced-motion에서 animation none으로 antd Modal 고장): C443-000
  - metric-last-green (지표 띠 마지막 칸 초록): D-080, X-042
  - operator-spacing (운영자 화면 섹션 간격): D-081
  - attention-link (확인할 항목 링크 위치와 크기): D-082, X-073
  - actions-row-align (workspace-actions 정렬): D-083, X-076
  - mobile-record-title (모바일 레코드 제목 크기): D-084
  - month-apply (.month-apply hover/비활성): D-097, D-240
  - auth-side-title (로그인/카드 등록 옆 패널 제목 크기): X-078
  - kicker (.workspace-kicker 숨김): G-080
  - unused-selectors (쓰이지 않는 선택자): G-076
- 배치 `layout-head-font`
  - font-preload (2MB Pretendard preload): C443-002, D-071, G-075, G-085, X-012
  - doc-title (문서 제목 동일): D-012, D-090, X-016
  - not-found (404 기본 영어 페이지, skip link 대상 없음): X-054
  - skeleton-aria (TableSkeleton aria-hidden, aria-busy): C443-001
- 배치 `workspace-table`
  - action-col-cut (공용 표 작업 열 잘림): D-003, D-026, D-049, D-150, D-189, D-250, X-008
  - dup-render-442 (#442에서 표와 카드 두 번 렌더): C442-017
  - scroll-focus (가로 스크롤 영역 포커스 불가): X-021
  - heading-levels (제목 수준 건너뜀): X-057
  - empty-state (표 빈 상태 다음 행동 없음): X-072
  - load-error-kinds (없는/권한 없는 항목도 불러오지 못했습니다): D-050
- 배치 `action-panel-a`
  - focus-return (대화상자 닫으면 포커스 body): D-172, X-000
  - secret-result (비밀값 결과가 폼 아래, Esc로 확인 없이 닫힘): D-127, X-004
  - throw-error (서버 액션 예외 시 입력 사라지고 전체 오류 화면): D-128, X-015
  - cancel-idem (결제 취소 모달 다시 열면 같은 멱등 키): D-043
  - result-status (복사/발급/폐기 결과 status 영역, 폐기 뒤 포커스): X-020
- 배치 `workspace-header-links`
  - month-picker (월 선택 컨트롤 불일치, 네이티브 month): D-006, D-029, D-136, D-170, D-184, D-236, D-256, X-046
  - header-links (수납 상세 머리 링크 흩어짐): D-078
  - scoped-link-default (ScopedLink와 workspaceHref inheritFilters 기본값 반대): G-057
  - link-button-dup (LinkButton이 ScopedLink 변환 복사, ConsoleShell 두 번 적용): G-078
  - screen-title (화면 제목 컴포넌트와 크기 불일치): D-075, D-237, G-079, X-070
- 배치 `search-controls`
  - details-marker (상세 조건 summary 펼침 표시 사라짐): D-001, D-025, D-047, D-067, D-133, D-147, D-177, D-201, X-023
  - primary-filter (필터 적용 버튼 primary): D-013, D-073, D-161, X-039
  - filter-copy (필터/조회 버튼 문구 화면마다 다름): D-203, D-263
  - control-heights (조회 줄 컨트롤 높이 불일치): D-134, D-159, D-185, X-037
  - status-filter-kinds (관리 상태 필터 링크 묶음 vs select): D-243
  - reversed-range (범위 거꾸로 입력 시 400 오류): X-053
  - search-label (고객 검색 라벨에 청구 이메일 없음): X-082
- 배치 `csv-customer-filter`
  - csv-width (CSV/결제 모드 변경 버튼 전체 폭): D-019, D-072, D-135, D-160, D-186, X-038
  - csv-name (CSV 파일 이름): D-062, D-144, X-096
  - cf-failure (고객/미터 검색 실패 시 조회 중 멈춤): D-139
  - cf-uuid (200명 넘으면 UUID 표시): C451-007
  - cf-keystroke (글자마다 서버 액션): X-106

### 하다 만 수정

- 없다. confirmed 묶음 모두 `k5.json`의 `fix_plan`에 고칠 PR, 파일, 변경, 시험, 충돌 위험을 적어 두었다.

### 추가 검토를 못 한 PR

- 없다. k5는 `needs_maint_ops_review`가 비어 있다.

## 이어받을 때 알아야 할 것

### 고치기 전에 정할 것과 판정을 바꿀 수 있는 의심

- C443-003 묶음(소개 화면 "준비 중" 문구): #443에서 문단을 지우고 #453에서 정확한 문구를 넣는 안은 두 수정이 같은 줄이라 위층 반영 때 충돌한다. #443 한 곳에서 정점 기준 문구로 바꾸는 안도 있다. G-088(소개 화면이 뒤 PR 기능을 미리 안내)은 코드 수정 대신 스택 연속 머지나 #443 본문에 배포 순서를 적는 쪽을 권했다.
- G-062(새 프론트가 새 백엔드보다 먼저 떠 403): 근본 수정은 `deploy/deploy.sh`라 범위 밖이다. 두 번째 관점이 배포 순간의 짧은 구간이라며 low로 낮췄다.
- X-003 묶음(실제 환경이 테스트와 같은 모양, 고위험 D-246 포함): 첫 관점만 끝났고 두 번째 관점을 하지 못했다. 앞선 검증 2건은 confirmed medium이었다. G-131 묶음(새 도입사 기본 legacy)은 제품 결정이 필요할 수 있다.
- C442-004(다른 탭 도입사 변경): 정점에서는 #451 프론트 검사와 #453 백엔드 403으로 해소됐다(still_at_top=false). #442 수정은 `client.ts`에서 #453이 바꾼 줄과 붙어 위층 반영 때 충돌한다.
- C445-004(#445에서 백엔드가 받지 않는 정렬, 고객 필터를 켬): #445에서 끄면 #450에서 이벤트 정렬, 검색, 기간을 다시 켜는 수정이 하나 더 필요하다.
- G-042(작업 이름 문자열 세 벌): 제안한 검사 시험은 #444-#446, #447-#450 head에서 실패한다. 그 head의 `workspace-actions.ts`(k6)를 같이 맞춰야 한다.
- G-184(인보이스 확정 5초 타임아웃): main부터 있던 문제다. 스택 PR 중 어디에 고칠지 정해야 한다(계획은 #442).
- 수정이 거의 k6 파일에 있는 것: C442-012, X-092, D-034, G-049, X-009 대부분, D-152의 2-4단계(백엔드 포함), G-039 묶음(배포와 백엔드), G-067(백엔드). `out_of_scope`에 파일을 적었다.
- 들이는 코드에 비해 이득이 작아 보류를 권하는 것: G-047(쓰지 않는 인자, 여러 PR 호출부와 충돌), G-055 묶음(이름 상수화, 남는 리터럴이 k6 파일), G-049(대화상자 공통화, 범위 밖 파일 필요). 고치지 않는다면 그 이유를 결과에 남긴다.

### 다른 묶음과 겹치는 파일

- `frontend/e2e/*.spec.ts`, `frontend/e2e/workspace-contract-server.mjs`, `frontend/src/app/(console)/workspace-actions.ts`, `frontend/src/app/(console)/**/page.tsx`, `frontend/src/components/{billing,invoices,usage,events,auth,pricing}/**`는 k6 몫이다. k5 수정 계획 다수가 이 파일의 짝 수정을 요구한다.
- `frontend/src/components/shell/ConsoleShell.tsx`는 #443, #444, #445, #446, #451, #453이 모두 고쳐 아래 PR에서 고치면 위층 반영 때 충돌이 잦다. `frontend/src/lib/api/client.ts`는 #451, #453이, `frontend/src/proxy.ts`는 #445, #449, #453이 고쳤다.
- 화면 문구 공통 정본(수납 상태, 역할, 권한 이름)은 k6 화면 지적과 겹칠 수 있다.

### 실행 환경과 돌리지 못한 검사

- Java는 21만 있어 백엔드 `./gradlew build`는 돌리지 않았다(k5는 프론트 범위). docker 데몬이 없어 실제 백엔드 E2E(`*.real.spec.ts`)는 돌리지 않았다.
- 꼭대기(#455) 기준 프론트 검사는 통과했다: lint, typecheck, `node --test e2e/*.test.mjs` 46건, Playwright 계약 테스트 auth 9건, workspace 9건, payment 6건.
- Playwright 1.64.1은 chromium 1248을 찾는데 미리 깔린 것은 1243이다. `chromium-1248`, `chromium_headless_shell-1248` 이름으로 1243 폴더를 가리키는 링크를 둔 폴더를 `PLAYWRIGHT_BROWSERS_PATH`로 주면 돌아간다.
- 화면 검증에는 꼭대기 빌드를 모든 콘솔 경로에 응답하는 대역 API에 붙인 미리보기 환경을 썼다. 클라우드 세션의 임시 폴더에만 있어 이어받는 쪽에는 없다. 필요하면 `frontend/e2e/workspace-contract-server.mjs`를 본떠 다시 만든다.
