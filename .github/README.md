# .github

워크플로, CODEOWNERS, PR 템플릿.

- `workflows/`에서 도는 것은 아래 표.

| 워크플로 | 언제 | 무엇을 |
| --- | --- | --- |
| `ci.yml` | PR과 main push | backend 빌드/테스트, frontend lint/빌드. main 룰셋의 필수 체크다 |
| `rfc-checklist.yml` | `rfc` 라벨이 붙은 PR | PR 본문의 체크리스트에 체크 안 된 항목이 있으면 실패한다. main 룰셋의 필수 체크다 |
| `protected-paths-approval.yml` | PR 열림, push, 리뷰 제출 | 보호 경로를 건드린 PR에 approve가 둘 미만이면 실패한다. main 룰셋의 필수 체크다. 경로 목록은 `docs/contributing/pull-request.md` "보호 경로" |
| `cd.yml` | main 머지 | 이미지를 굽고 ECR에 올린 뒤 배포한다 |
| `claude-code-review.yml` | PR과 push | Claude 리뷰. 인라인 코멘트로 달리고 머지를 막지 않는다 |
| `claude.yml` | `@claude` 호출 | 이슈나 PR 코멘트에 응답한다 |
| `aws-access-check.yml` | 수동 | OIDC 신뢰 관계와 ECR 접근이 살아 있는지 본다 |
