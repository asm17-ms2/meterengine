# 워크플로

- 이 디렉터리에서 도는 것은 아래 표.

| 워크플로 | 언제 | 무엇을 |
| --- | --- | --- |
| `ci.yml` | PR, merge queue, main과 develop push | backend 빌드/테스트, frontend lint, 단위 시험, 빌드, 계약 명세(auth, 카드 등록, 업무 격리), demo 정적 검사와 시험(pnpm, Python), deploy 시험. develop과 main 룰셋의 필수 체크다. 바뀐 쪽만 돌고, 안 바뀐 쪽은 skip되어 통과로 친다. `rds-auto-stop` job은 `measurements/terraform/modules/rds-auto-stop/`나 `measurements/terraform/rds-auto-stop/`가 바뀔 때만 흐름 정의의 JSONata 시험과 Terraform fmt, mock 시험을 돌린다. AWS 자원을 만들거나 부르지 않는다 |
| `cd-production.yml` | main 머지. 문서나 demo만 바뀐 머지와, `deploy/`에서 앱 서버가 읽지 않는 파일(Terraform, 시험, 문서, 공용 모니터링)만 바뀐 머지는 제외 | production에 배포한다 |
| `cd-staging.yml` | develop 머지. 문서나 demo만 바뀐 머지와, `deploy/`에서 앱 서버가 읽지 않는 파일(Terraform, 시험, 문서, 공용 모니터링)만 바뀐 머지는 제외 | staging에 배포한다 |
| `deploy.yml` | `cd-production.yml`과 `cd-staging.yml`이 부른다 | 이미지를 굽고 ECR에 올린 뒤, 환경 파일의 오토스케일링 그룹에 `deploy/rollout.sh`로 버전을 기록하고 서버를 새로 바꾼다. 최소 대수가 0이면 기록만 한다 |
| `claude-code-review.yml` | PR과 push | Claude 리뷰. 인라인 코멘트로 달리고 머지를 막지 않는다 |
| `claude.yml` | `@claude` 호출 | 이슈나 PR 코멘트에 응답한다 |
| `aws-access-check.yml` | 수동 | OIDC 신뢰 관계와 ECR 접근이 살아 있는지 본다 |
