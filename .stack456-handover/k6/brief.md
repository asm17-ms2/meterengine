# 묶음 k6: 프론트 화면별: 콘솔 페이지, 인증, 요금, 청구, 인보이스 링크, 결제수단 등록 등

- 자료: `.stack456-handover/k6/findings.json`. 지적 239건이고 앞선 검증에서 확인된 것 11건, 검증 전 228건이다. 틀렸다고 본 2건은 `already_refuted`에 있다.
- PR: 지적마다 `pr`과 `branch`가 있다. 프론트 디자인 지적(`D-`, `X-`)은 PR이 비어 있으니 직접 정한다.
- 파일 범위: `frontend/` 중 k5 범위(`frontend/src/app/globals.css`, `frontend/src/app/layout.tsx`, `frontend/src/app/page.tsx`, `frontend/src/proxy.ts`, `frontend/src/lib/`, `frontend/src/components/shell/`, `frontend/src/components/screen/`, `frontend/src/components/workspace/`)를 뺀 나머지.
- 수정 브랜치: `claude/s456-k6-<PR 번호>`. 보고 브랜치: `claude/s456-k6-report`.

## 이 묶음에서 먼저 볼 것

- `D-` 지적은 디자인 검토자 13명의 원본이고 중복을 합치지 않았다. 검증 전에 원인이 같은 것부터 묶는다. `X-`는 보조 디자인 검토자 11명의 결과를 한 번 합친 것이다.
- 화면 지적은 그 줄을 처음 들인 PR에 고친다. 여러 PR이 같은 파일을 바꿨으면 각 브랜치 head의 파일을 비교해 문제의 줄이 처음 나타난 PR을 고른다.
- 고칠 곳이 k5 범위의 공통 컴포넌트이면 고치지 않고 `out_of_scope`에 적는다.
- 프론트 검사: `cd frontend && corepack pnpm install --frozen-lockfile`, lint, typecheck, `node --test e2e/*.test.mjs`, 대역 서버로 도는 Playwright 계약 테스트(`playwright.*.config.ts`). 브라우저를 받을 수 없으면 보고에 적는다.
