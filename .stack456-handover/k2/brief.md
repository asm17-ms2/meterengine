# 묶음 k2: 백엔드 웹훅과 예약 작업, 결제, 고객 인보이스

- 자료: `.stack456-handover/k2/findings.json`. 지적 58건이고 앞선 검증에서 확인된 것 11건, 검증 전 47건이다. 틀렸다고 본 1건은 `already_refuted`에 있다.
- PR: #444 `feat/webhook-jobs`, #445 `feat/billing-payments`, #449 `feat/customer-invoices`
- 파일 범위: `frontend/` 밖의 파일. 프론트 파일 지적은 k5, k6가 맡는다.
- 추가 검토: #444, #445, #449. 공통 지침 3번대로 한다.
- 수정 브랜치: `claude/s456-k2-<PR 번호>`. 보고 브랜치: `claude/s456-k2-report`.

## 이 묶음에서 먼저 볼 것

- #444에는 문인호 님 PR #458(예약 작업 마지막 성공 게이지가 지표 수집 때 DB를 읽지 않게 한다)이 머지됐다. C444-004는 `already-fixed`인지 먼저 본다.
- 결제는 토스 대역 서버(`frontend/e2e/toss-server.mjs`, 백엔드 테스트 대역)로만 확인한다.
