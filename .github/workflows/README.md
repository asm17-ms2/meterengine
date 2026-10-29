# 워크플로

- 이 디렉터리에서 도는 것은 아래 표.

| 워크플로 | 언제 | 무엇을 |
| --- | --- | --- |
| `ci.yml` | PR, merge queue, main push | backend 빌드/테스트, frontend lint, 단위 시험, 빌드, 계약 명세(auth, 카드 등록, 업무 격리), demo 정적 검사와 시험(pnpm, Python), deploy 시험, rds-auto-stop 모듈의 terraform mock 시험과 ASL 시험. main 룰셋의 필수 체크다. 바뀐 쪽만 돌고, 안 바뀐 쪽은 skip되어 통과로 친다 |
| `cd.yml` | main 머지에서 `backend/`, `frontend/`, `deploy/`, `cd.yml` 중 하나라도 바뀌었을 때 | 이미지를 굽고 ECR에 올린 뒤 배포한다 |
| `claude-code-review.yml` | PR과 push | Claude 리뷰. 인라인 코멘트로 달리고 머지를 막지 않는다 |
| `claude.yml` | `@claude` 호출 | 이슈나 PR 코멘트에 응답한다 |
| `aws-access-check.yml` | 수동 | OIDC 신뢰 관계와 ECR 접근이 살아 있는지 본다 |
