# 묶음 k5: 프론트 공통: 셸, 화면 틀, 작업 공간 컴포넌트, 전역 스타일

- 자료: `.stack456-handover/k5/findings.json`. 지적 238건이고 앞선 검증에서 확인된 것 18건, 검증 전 220건이다.
- PR: 지적마다 `pr`과 `branch`가 있다. 프론트 디자인 지적(`D-`, `X-`)은 PR이 비어 있으니 직접 정한다.
- 파일 범위: `frontend/src/app/globals.css`, `frontend/src/app/layout.tsx`, `frontend/src/app/page.tsx`, `frontend/src/proxy.ts`, `frontend/src/lib/`, `frontend/src/components/shell/`, `frontend/src/components/screen/`, `frontend/src/components/workspace/`. 그 밖의 `frontend/` 파일은 k6가 맡는다.
- 수정 브랜치: `claude/s456-k5-<PR 번호>`. 보고 브랜치: `claude/s456-k5-report`.

## 이 묶음에서 먼저 볼 것

- `D-` 지적은 디자인 검토자 13명의 원본이고 중복을 합치지 않았다. 검증 전에 원인이 같은 것부터 묶는다. `X-`는 보조 디자인 검토자 11명의 결과를 한 번 합친 것이다.
- 화면 지적은 그 줄을 처음 들인 PR에 고친다. 여러 PR이 같은 파일을 바꿨으면 각 브랜치 head의 파일을 비교해 문제의 줄이 처음 나타난 PR을 고른다.
- 공통 컴포넌트를 고치면 쓰는 화면이 함께 바뀐다. 바뀌는 화면을 보고에 적는다.
- 프론트 검사: `cd frontend && corepack pnpm install --frozen-lockfile`, lint, typecheck, `node --test e2e/*.test.mjs`, 대역 서버로 도는 Playwright 계약 테스트(`playwright.*.config.ts`). 브라우저를 받을 수 없으면 보고에 적는다.
