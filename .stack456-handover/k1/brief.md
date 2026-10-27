# 묶음 k1: 백엔드 계정, 권한, 요청 감사, 호출 제한

- 자료: `.stack456-handover/k1/findings.json`. 지적 80건이고 앞선 검증에서 확인된 것 10건, 검증 전 70건이다. 틀렸다고 본 5건은 `already_refuted`에 있다.
- PR: #442 `feat/account-permissions`, #443 `feat/product-design`, #448 `feat/request-audit`, #454 `feat/auth-rate-limit`
- 파일 범위: `frontend/` 밖의 파일. 프론트 파일 지적은 k5, k6가 맡는다.
- 추가 검토: #448, #454. 공통 지침 3번대로 한다.
- 수정 브랜치: `claude/s456-k1-<PR 번호>`. 보고 브랜치: `claude/s456-k1-report`.

## 이 묶음에서 먼저 볼 것

- #448에는 문인호 님 PR #459(이벤트 수집 요청을 요청 감사에서 뺀다), #454에는 #461(운영 백엔드가 Caddy를 신뢰 프록시로 알아보게 한다)이 머지됐다. 관련 지적은 `already-fixed`인지 먼저 본다.
- #442의 확인된 지적 10건 중 마이그레이션 번호 건은 note대로 확인만 한다.
