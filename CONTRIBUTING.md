# CONTRIBUTING

팀 규칙의 입구다. 확정된 규칙을 한 줄씩 적고, 규칙 본문은 `docs/contributing/`의 주제별 파일에, 왜 그렇게 정했는지는 `docs/rfcs/`에 있다. 규칙이 바뀌면 규칙 파일을 고치고 여기는 그 파일을 가리킨다.

## 확정된 규칙

- main에는 직접 push할 수 없다. 모든 변경은 브랜치를 만들어 PR로 올린다 (GitHub 브랜치 보호로 강제)
- work/는 개인 작업 공간이라 PR 대상이 아니다
- 브랜치 이름: `<type>/MS2-<이슈번호>-<설명>`. type은 feat, fix, docs, refactor, test, ci, chore. 예: `docs/MS2-31-branch-strategy`. 이슈 키로 Jira가 브랜치와 PR을 이슈에 자동 연결한다. 티켓이 없으면 `<type>/<설명>` (`docs/contributing/pull-requests.md` "티켓 없이 가는 작업")
- 커밋 메시지: 제목은 `<type>: <요약> (MS2-xxx)`이고 티켓이 없으면 키를 뺀다. RFC PR은 PR 제목과 커밋 제목이 `RFC-NNN: 제목 (MS2-xxx)`. 결정 내용을 바꾸지 않는 정정(오탈자, 상태값)은 일반 양식. 본문은 왜 / 변경 사항 / 결과와 검증 절. 사소한 변경(오타, 링크, 버전 올리기)은 제목 한 줄로 끝내도 된다. 절 제목에 `#`을 쓰지 않는다 (편집기 커밋에서 git이 주석으로 지운다)
- 커밋 개수: PR 하나에 커밋 하나. squash merge라 main에는 어차피 하나로 남는다. 나눠야 하면 PR 본문에 이유를 적는다
- 머지 방식: squash merge (main 룰셋 Allowed merge methods로 강제). develop/staging 같은 고정 브랜치가 생기면 develop은 squash만, main은 merge commit만으로 전환한다. 오래 사는 브랜치를 반복 머지할 때 squash는 히스토리가 꼬인다. 시점은 릴리스/태그 규칙 논의와 함께 정한다
- 머지 후 브랜치 삭제 (GitHub 저장소 설정 Automatically delete head branches로 자동화). 이어지는 작업은 main에서 새로 딴다. 머지되지 않은 선행 작업에 의존하면 그 위에 쌓는다 (`docs/contributing/stacked-prs.md`). 로컬은 각자 `git fetch --prune`
- PR 리뷰: 작성자 본인 외 1명 이상 승인 후 머지. 셀프머지는 하지 않는다 (main 룰셋 Required approvals로 강제)
- 제안: 리팩터나 아키텍처 변경처럼 방향을 바꾸는 것은 RFC PR로 올려 합의한 뒤 작업한다 (쓰는 법은 `docs/rfcs/README.md`). RFC PR의 정족수는 작성자를 제외한 전원이다. 정책(`docs/policies/`) 변경은 코드와 같은 PR로 올리고 일반 PR과 같은 절차로 리뷰한다. RFC를 쓰기엔 가벼운데 합의가 안 된 변경은 Draft PR로 올리고 본문 첫 줄에 합의가 필요한 점을 적는다. 반대가 없으면 ready로 돌리고 평소대로 리뷰한다
- main 상태: main은 항상 빌드/테스트가 통과하는 상태를 유지한다. 깨진 코드나 반쯤 만든 기능은 브랜치에만 둔다. CI 실패 시 머지를 금지한다. 필수 체크 항목은 CI 워크플로(`.github/workflows/ci.yml`)의 `backend`, `frontend` job이다. main 룰셋의 Required status checks에 그 job을 등록해 강제한다
- 선행 브랜치 위 작업: B가 A 없이 성립하지 않으면 A 위에 쌓고, 따로 머지돼도 되면 독립 PR로 낸다. GitHub 네이티브 스택 기능만 쓴다 (`docs/contributing/stacked-prs.md`)
- 릴리스/태그: 버전 태그는 두지 않는다. 배포 단위는 커밋이고 이름은 git SHA다 (ECR 이미지 태그). 롤백은 이전 SHA 재배포다. 외부에 버전 번호가 필요해지면 그때 논의한다

## 문서의 정본

문서를 어디에 두고 무엇을 정본으로 삼을지는 이 절이 정한다. 층을 가르는 질문과 바꾸는 절차는 `docs/contributing/decisions.md`에 있다. 가르는 기준은 하나다. **이력이 남아야 하는 결정과 규칙은 레포, 흘러가도 되는 것은 Miro.**

| 종류 | 정본 |
| --- | --- |
| 방향을 정하는 결정 | `docs/rfcs/` (RFC-000, 쓰는 법은 그 README) |
| 방향 안의 세부 규칙(정책) | `docs/policies/` (도메인당 파일 하나, 쓰는 법은 그 README) |
| 스프린트 범위(이번에 하기로 한 것과 안 하기로 한 것) | Jira(MS2 프로젝트)의 스프린트 |
| 회의록, 일정, 브레인스토밍 초안 | Miro |
| API 계약 | `backend/openapi.yaml` (컨트롤러와 DTO에서 자동 생성) |
| 팀 규칙(브랜치, 커밋, PR, 코드와 문서 작성) | 이 파일의 "확정된 규칙"과 `docs/contributing/` (쓰는 법은 `docs/contributing/decisions.md`) |
| 각 디렉터리의 실행법, 구조, 그 안에서 내린 판단 | 그 디렉터리의 `README.md` |

한 내용을 두 곳에 쓰지 않는다. 정본이 아닌 곳에서 언급해야 하면 정본을 가리킨다. 표에 없는 종류의 문서는 만들기 전에 어디에 둘지 먼저 합의한다.

## 규칙 문서

규칙 본문은 `docs/contributing/`에 주제당 파일 하나로 있다. 규칙을 만들거나 고치는 절차는 `decisions.md`에 있다.

| 파일 | 내용 |
| --- | --- |
| [decisions.md](docs/contributing/decisions.md) | 문서의 층과 가르는 기준, RFC와 proposal과 정책 PR, 규칙 신설과 개정 |
| [pull-requests.md](docs/contributing/pull-requests.md) | PR 크기 상한, 티켓 없이 가는 작업, 쪼개는 축 |
| [stacked-prs.md](docs/contributing/stacked-prs.md) | 스택 PR을 쓰는 조건, 머지 순서, gh stack 명령 |
| [naming.md](docs/contributing/naming.md) | 백엔드, 프론트엔드, API, DB 이름 규칙 |
| [comments.md](docs/contributing/comments.md) | 주석과 javadoc |
| [documentation.md](docs/contributing/documentation.md) | 시간이 지나면 틀리는 값, Jira 키, 개수 표현, README 점검 |
