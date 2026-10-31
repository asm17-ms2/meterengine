# 워크플로

- 이 디렉터리에서 도는 것은 아래 표.

| 워크플로 | 언제 | 무엇을 |
| --- | --- | --- |
| `ci.yml` | PR, merge queue, main push | backend 빌드/테스트, frontend lint, 단위 시험, 빌드, 계약 명세(auth, 카드 등록, 업무 격리), demo 정적 검사와 시험(pnpm, Python), deploy 시험. main 룰셋의 필수 체크다. 바뀐 쪽만 돌고, 안 바뀐 쪽은 skip되어 통과로 친다. `rds-auto-stop` job은 `measurements/terraform/modules/rds-auto-stop/`나 `measurements/terraform/rds-auto-stop/`가 바뀔 때만 흐름 정의의 JSONata 시험과 Terraform fmt, mock 시험을 돌린다. AWS 자원을 만들거나 부르지 않는다 |
| `cd.yml` | main 머지에서 `backend/`, `frontend/`, `deploy/`, `cd.yml` 중 하나라도 바뀌었을 때 | 이미지를 굽고 ECR에 올린 뒤 서버 배포 명령을 보낸다. 현재 실행 ID와 두 digest 미전달로 서버에서 배포를 거절한다 |
| `claude-code-review.yml` | PR과 push | Claude 리뷰. 인라인 코멘트로 달리고 머지를 막지 않는다 |
| `claude.yml` | `@claude` 호출 | 이슈나 PR 코멘트에 응답한다 |
| `aws-access-check.yml` | 수동 | OIDC 신뢰 관계와 ECR 접근이 살아 있는지 본다 |
| `terraform-checks.yml` | PR, main push | AWS 자격 없이 Terraform fmt/validate/mock과 배포 Python 시험 |
| `terraform.yml` | main 수동 실행 | 선택한 대상의 private saved plan, 인프라 Environment 승인, 같은 run의 고정 버전 apply |

## Terraform 인프라 승인

인프라 역할 bootstrap, 입력 설정과 실행 명령은 [배포 안내](../../deploy/README.md#terraform-automation-bootstrap)를 따른다. 실제 GitHub 설정, IAM bootstrap과 첫 원격 실행은 별도 승인 대상이다.

실제 trust 활성화 전에 `staging-infrastructure`, `production-infrastructure`에 native required reviewers와 main만 허용하는 custom deployment branch policy를 별도 승인 후 설정하고 읽어 확인한다. 앱 배포용 `staging`과 구분한다. 저장소 OIDC subject template은 바꾸지 않으며 불변 owner/repository ID를 포함한 기존 subject를 쓴다.

plan은 main ref subject, apply는 해당 infrastructure Environment subject만 허용한다. Environment subject에는 branch가 없으므로 main 제한은 native branch policy와 workflow가 함께 보장해야 한다. reviewer나 branch 규칙이 없으면 AWS 자격 획득 전에 중단하고, 승인 후에도 다시 검사한다. PR 검증에는 AWS 자격, id-token 쓰기와 Environment secrets를 주지 않는다.

같은 대상의 run은 취소하지 않고 직렬화한다. apply는 plan job의 metadata, 현재 run/attempt와 source SHA, lockfile, Terraform 버전을 자격 획득 전에 확인한다. 새 attempt에서 이전 plan만 재사용할 수 없으며 실패하면 새 plan과 새 승인이 필요하다.

Terraform 원문 로그, plan과 state는 공개 로그나 artifact에 올리지 않는다. 승인자는 plan job summary의 허용된 변경, source SHA, SHA256과 고정 VersionId를 확인한 뒤 해당 Environment에서 승인한다. 원본 진단은 bucket preflight를 통과한 뒤 대상 private S3 prefix에만 저장한다. 자격 획득 전 실패와 bucket preflight 실패는 S3 진단을 쓰지 않는다.
