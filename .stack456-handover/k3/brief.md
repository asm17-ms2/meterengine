# 묶음 k3: 백엔드 요금과 집계, 자원 보관, 수집, 조회와 내보내기

- 자료: `.stack456-handover/k3/findings.json`. 지적 75건이고 앞선 검증에서 확인된 것 0건, 검증 전 75건이다.
- PR: #446 `feat/pricing-aggregation`, #447 `feat/resource-archive`, #450 `feat/event-ingestion`, #451 `feat/billing-queries`
- 파일 범위: `frontend/` 밖의 파일. 프론트 파일 지적은 k5, k6가 맡는다.
- 추가 검토: #446, #447, #450, #451. 공통 지침 3번대로 한다.
- 수정 브랜치: `claude/s456-k3-<PR 번호>`. 보고 브랜치: `claude/s456-k3-report`.

## 이 묶음에서 먼저 볼 것

- #450에는 문인호 님 PR #460(이벤트 제약 검증을 나누고 수집 거절 집계와 배치 저장 순서를 고친다)이 머지됐다. 관련 지적은 `already-fixed`인지 먼저 본다.
