# CONTRIBUTING

팀 규칙의 입구다. 확정된 규칙을 한 줄씩 적고, 규칙 본문은 `docs/contributing/`의 주제별 파일에, 왜 그렇게 정했는지는 `docs/rfcs/`에 있다. 규칙이 바뀌면 규칙 파일을 고치고 여기는 그 파일을 가리킨다.

## 확정된 규칙

- main에는 직접 push할 수 없다 (GitHub 브랜치 보호로 강제). 모든 변경은 브랜치를 만들어 PR로 올린다
- work/는 개인 작업 공간이라 PR 대상이 아니다
- 브랜치 네이밍: `<type>/MS2-<이슈번호>-<설명>` 형식. type은 feat, fix, docs, refactor, test, ci, chore 중 하나. 예: `docs/MS2-31-branch-strategy`, `feat/MS2-40-event-ingest`. 브랜치명에 이슈 키가 들어가 Jira가 브랜치/PR을 이슈에 자동 연결한다. 티켓 없이 가는 잡일은 키를 빼고 `<type>/<설명>` 형식으로 쓴다 (`docs/contributing/pull-requests.md` "티켓 없이 가는 작업")
- 커밋 메시지: 제목은 `<type>: <요약> (MS2-xxx)`이고 티켓이 없으면 `(MS2-xxx)`를 뺀다. RFC PR은 PR 제목과 커밋 제목이 `RFC-NNN: 제목`이고 티켓이 있으면 `(MS2-xxx)`를 붙인다. 결정 내용을 바꾸지 않는 정정(오탈자, 상태값)은 일반 양식으로 쓴다. 본문은 왜 / 변경 사항 / 결과와 검증 절로 쓴다. type은 브랜치 네이밍과 같은 목록에서 고른다. 사소한 변경(오타, 링크, 버전 올리기)은 제목 한 줄로 끝내도 된다. 절 제목에 `#`을 쓰지 않는다. 편집기로 커밋하면 git이 `#`으로 시작하는 줄을 주석으로 지운다
- 커밋 개수: PR 하나에 커밋 하나를 최대한 유지한다. squash merge라 어차피 main에는 하나로 남고, 브랜치 안에서 커밋을 나눠도 리뷰가 나아지지 않는다. 나눠야 한다면 PR 본문에 왜 나눴는지 적는다
- 머지 방식: squash merge. PR 하나가 main 커밋 하나로 남아 이슈 단위 추적이 쉽고, 브랜치 안 커밋 정리에 힘 쓰지 않아도 된다. main 룰셋의 Allowed merge methods를 squash만 허용으로 설정해 강제한다. squash로 원본 커밋과의 연결이 끊기는 문제는 아래 "머지 후 브랜치 삭제"와 "선행 브랜치 위 작업" 규칙이 안전장치다
- 고정 브랜치 도입 시 전환 계획: 이후 테스트서버 운영 등으로 develop/staging 같은 고정 브랜치가 생기면, develop 룰셋은 squash만, main 룰셋은 merge commit만 허용으로 전환한다. 오래 사는 브랜치를 main과 반복 머지할 때 squash를 쓰면 히스토리가 꼬이기 때문이다. 시점은 릴리스/태그 규칙 논의와 함께 정한다
- 머지 후 브랜치 삭제: 머지된 브랜치는 삭제하고, 이어지는 작업은 main에서 새 브랜치를 딴다. 다만 아직 머지되지 않은 선행 작업에 의존하면 main이 아니라 그 브랜치 위에 쌓는다(`docs/contributing/stacked-prs.md`). GitHub 저장소 설정(Automatically delete head branches)으로 자동화한다. 로컬 브랜치는 각자 `git fetch --prune`으로 정리한다
- PR 리뷰: 작성자 본인 외 1명 이상 승인 후 머지한다. 셀프머지는 하지 않는다. main 룰셋의 Required approvals(1명)로 강제한다
- 제안: 리팩터나 아키텍처 변경처럼 방향을 바꾸는 것은 RFC PR로 올려 합의한 뒤 작업한다 (쓰는 법은 `docs/rfcs/README.md`). RFC PR의 정족수는 작성자를 제외한 전원이다. 정책(`docs/policies/`) 변경은 코드와 같은 PR로 올리고 일반 PR과 같은 절차로 리뷰한다. RFC를 쓰기엔 가벼운데 합의가 안 된 변경은 Draft PR로 올리고 본문 첫 줄에 합의가 필요한 점을 적는다. 반대가 없으면 ready로 돌리고 평소대로 리뷰한다
- main 상태: main은 항상 빌드/테스트가 통과하는 상태를 유지한다. 깨진 코드나 반쯤 만든 기능은 브랜치에만 둔다. CI 실패 시 머지를 금지한다. 필수 체크 항목은 CI 워크플로(`.github/workflows/ci.yml`)의 `backend`, `frontend` job이다. main 룰셋의 Required status checks에 그 job을 등록해 강제한다
- 선행 브랜치 위 작업: 브랜치 A가 리뷰 대기 중이고 B가 A 없이는 성립하지 않으면 A 위에 쌓는다. 따로 머지돼도 되면 독립 PR로 낸다. 절차는 `docs/contributing/stacked-prs.md`에 있고, GitHub 네이티브 기능만 쓴다
- 릴리스/태그 규칙: 버전 태그는 두지 않는다. 배포 단위는 커밋이고, 배포된 것을 가리키는 이름은 그 커밋의 git SHA다(ECR 이미지 태그가 SHA다). 롤백도 이전 SHA로 다시 배포하는 것이라 semver 태그 없이 성립한다. 외부에 버전 번호를 알려야 하는 일이 생기면 그때 다시 논의한다

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
