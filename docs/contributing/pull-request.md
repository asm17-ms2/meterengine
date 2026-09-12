# PR

- 브랜치와 커밋 규칙은 `git-workflow.md`. 이 파일은 PR을 나누고, 쌓고, 리뷰하고, 머지하는 규칙이다.

## 머지 조건

- 작성자 외 1명 이상의 approve. 셀프머지는 하지 않는다. main 룰셋이 강제한다.
  - "보호 경로"를 건드린 PR은 approve 둘. CODEOWNERS가 `protected-reviewers` 팀 전원에게 리뷰를 요청한다.
- main은 항상 빌드와 테스트가 통과하는 상태다. CI가 실패하면 머지하지 않는다.
- 필수 체크는 main 룰셋의 Required status checks에 등록한다.
  - `ci.yml`의 `backend`, `frontend`.
  - `rfc-checklist.yml`의 `rfc-checklist`. `rfc` 라벨이 붙은 PR에서만 돌고 체크 안 된 항목이 있으면 실패한다.
  - `protected-paths-approval.yml`의 `protected-paths-approval`. 보호 경로를 건드린 PR에서 approve가 둘 미만이면 실패한다.
- 무엇을 RFC PR, 규칙 PR, `proposal` PR로 올리는지는 `governance.md` "제안".
  - Draft로 열지 않는다. 제안 PR은 `label:proposal`, RFC PR은 `label:rfc`로 거른다.

## PR 크기와 쪼개기

- 대원칙은 사람이 리뷰하기 쉬운 단위다. 줄 수는 그것을 지키게 하는 부가 규칙이다.
- 하한은 없다. 1줄 PR도 낸다.
- 상한은 300줄이다. 사람이 읽고 판단해야 하는 추가된 줄을 센다. 코드, 테스트, 문서 모두.
  - 빼고 센다: 생성물(`backend/openapi.yaml`, 잠금 파일), 삭제된 줄, 규칙에 따른 기계적 변경(개명, 주석 삭제, 파일 이동).
  - 순수 삭제와 일괄 개명은 상한을 받지 않는다. 지우는 근거와 따른 규칙은 PR 본문에 적는다.
  - 생성물이 diff에 올라왔는지는 리뷰에서 본다. CI가 검사하지 않는다.
- 상한 아래여도 한 번에 읽기 힘들면 나눈다. 삭제가 추가보다 큰 재작성은 더 잘게 나눈다.
- 테스트는 기능과 같은 PR에 둔다. 다음 PR로 미루지 않는다.
  - 테스트만 있는 PR은 이미 머지된 코드의 테스트를 보강할 때만.
  - 쪼갠 조각을 위해 테스트를 만들지 않는다. 뒤 조각이 들어오면 필요 없어질 테스트는 그 동작을 단언할 수 있는 조각에 둔다.
- 초과: `backend/`, `.github/`, `deploy/`는 초과하지 않는다. `frontend/`, `demo/`는 작성자 판단으로 넘길 수 있고 본문에 이유를 적는다.
- PR 하나에 서브태스크 하나. 300줄을 넘길 것 같으면 티켓부터 쪼갠다.

### 티켓 없이 가는 작업

- 자잘한 작업(오타, 죽은 링크, 포맷, 버전 올리기)은 티켓 없이 PR로 올린다.
  - 기준: 나중에 Jira에서 되짚을 일이 있으면 티켓, 없으면 그냥 PR. 애매하면 티켓.
  - 티켓이 없어도 왜 했는지는 PR 본문에 적는다.
  - 브랜치명은 `<type>/<설명>`. 예: `docs/fix-readme-link`.

### 쪼개는 축

- 위에서부터 적용한다.
  1. 동작별: 엔드포인트나 화면 동작 하나 + 그 동작이 처음 쓰는 DTO/예외 + 그 동작의 테스트.
  2. 층별: 동작이 하나뿐인데 크면 모델 / 서비스 / 컨트롤러.
  3. 경로별: 그래도 넘으면 성공 경로 / 검증 실패 / 동시성.
- 바닥은 따로 읽어도 뜻이 통하는 조각이다. DTO만 있는 PR, 검증 로직만 떼낸 PR은 호출되는 곳이 없어 판단할 수 없다.
  - 스택 안의 조각은 스택을 순서대로 읽어 이해되면 된다.
- 예: 고객 CRUD 1593줄을 동작별로 자른 값(테스트 포함).

| 조각 | 줄 |
| --- | --- |
| 엔티티 + 리포지토리 + 스키마 테스트 | 79 |
| GET 목록 + 응답 DTO + 테스트 | 200 |
| POST 등록 + 요청 DTO + 테스트 | 200 |
| PUT 수정 + 테스트 | 180 |
| DELETE + 예외 + 동시성 테스트 | 300 |

## 스택 PR

- 독립 PR이 먼저다. 따로 머지돼도 되는 작업은 각각 main에서 브랜치를 딴다.
- 스택은 아래 중 하나일 때 쓴다.
  - 조각이 따로 머지되면 반만 동작하는 상태가 main에 남는다.
  - 뒤 PR이 앞 PR 없이 성립하지 않는다.
  - 순서대로 읽어야 이해된다. 예: 파일 이동과 내용 변경을 나눈 경우.
  - 한 작업을 읽기 쉽게 나눴다. 따로 머지돼도 되지만 한 작업임을 스택으로 드러낸다.
- 한 작업이 아니면 한 스택에 묶지 않는다.
- 스택은 직선이다. 선행 브랜치 위에 쌓고, 한 부모에 여러 자식은 아래 "가지"다.
- 어느 PR에서 머지하든 그 아래 머지되지 않은 PR이 함께 들어간다. 각 PR에 approve 조건이 그대로 걸린다.

### 가지

- 한 부모에 여러 자식이 기대고 자식끼리 독립이면 스택으로 늘어세우지 않는다.
- 자식마다 `gh pr create --base <부모 브랜치>`로 일반 PR을 낸다. 리뷰어는 PR마다 자동 배정된다.
- 부모가 머지되기 전에 자식을 머지하지 않는다.
- 부모는 `--delete-branch` 없이 머지한다. CLI가 브랜치를 먼저 지우면 자식 PR이 닫힌다.
- 부모가 머지되면 GitHub가 자식의 base를 main으로 바꾼다. 자식 PR에서 Update with rebase를 누른 뒤 리뷰한다.
  - 거절되면 부모가 커밋 여럿이었던 경우다. Update with merge commit을 누르거나 `git rebase --onto origin/main <부모 브랜치> <자식 브랜치>`.
- 독립 PR인데 다른 PR 뒤에 머지돼야 하면 본문 "결과와 검증"에 "#N 머지 뒤에 머지한다"고 적는다.

### 머지 순서

- 내부 구조만 늘리는 조각(엔티티, 리포지토리, 내부 서비스)은 전부 승인된 뒤 최상단에서 한 번에 머지한다. 앞 조각만 먼저 넣을 이유가 있으면 하나씩 머지해도 된다.
- 컨트롤러나 화면이 들어가는 조각은 한 번에 머지한다. 뒤에 올 검증이나 오류 처리가 없으면 반만 동작하는 API가 노출된다.
- GitHub 네이티브 스택 PR 기능만 쓴다. 수동 git으로 스택을 만들거나 정리하지 않는다.
  - 2026-08 기준 public preview라 익숙한 수동 rebase로 되돌아가기 쉽다. 그러면 스택으로 인식되지 않는 PR이 생기거나 히스토리가 어긋난다.
  - 확장이 없으면 `gh extension install github/gh-stack`. 설치나 명령이 실패하면 수동 git으로 우회하지 말고 멈추고 물어본다.
  - 문서: [개념](https://github.github.com/gh-stack/introduction/overview/), [CLI](https://github.github.com/gh-stack/reference/cli/), [GitHub 공식](https://docs.github.com/ko/pull-requests/get-started/about-stacked-prs).

| 하려는 일 | 쓰지 않는다 | 대신 쓴다 |
| --- | --- | --- |
| 선행 브랜치 위에 새 작업 시작 | `git checkout -b`로 그냥 따기 | `gh stack init` |
| 이미 있는 스택에 합류 | 브랜치 이름으로 checkout | `gh stack checkout <PR번호>` |
| PR 올리기 (스택) | `gh pr create --base <부모브랜치>` (스택으로 인식되지 않는다) | `gh stack submit` |
| 부모가 갱신됐을 때 맞추기 | `git rebase --onto`, `git rebase --update-refs`, `git rebase -i` | `gh stack sync` |
| 부모가 머지된 뒤 | 손으로 base 바꾸기, 손으로 rebase | 아무것도 하지 않는다. 로컬만 `gh stack sync` |
| 스택 브랜치 push | `git push --force` | `gh stack submit` |
| 구조 확인 | `git log --graph`로 추정 | `gh stack view` |
| 이미 올린 PR을 스택으로 묶기 | base만 바꾸기 | `gh stack link` 또는 웹에서 묶기 |
| 스택의 리뷰어 | 자동 배정에 맡기기 | 첫 조각에 배정된 사람으로 조각마다 `gh pr edit <번호> --remove-reviewer <자동 배정> --add-reviewer <첫 조각의 사람>` |
| 한 부모에 여러 자식 | 직선으로 늘어세우기 | 위 "가지" |

- 승인되지 않은 부모를 끌고 들어가는 자식 머지는 요청하지 않는다. 머지는 웹이든 `gh stack merge`든 같다.
- 머지 큐는 쓰지 않는다. 켜면 main이 스택의 중간 상태를 거친다.

## 리뷰어 배정

- 리뷰어는 org 팀 `reviewers`에서 자동 배정된 한 명이다. `.github/CODEOWNERS`가 모든 경로를 그 팀에 맡긴다.
- 배정된 사람이 읽고 approve나 changes requested로 답한다. 볼 수 없으면 다른 사람으로 바꾼다.
- 배정되지 않은 사람은 코멘트만 달고 approve하지 않는다.
- 보호 경로를 건드린 PR은 approve가 둘이다. CODEOWNERS의 보호 경로 행이 팀 `protected-reviewers`(auto assignment 끔)를 적고 있어 GitHub가 팀 전원에게 요청한다.
- 판단이 갈릴 것 같으면 작성자가 두 번째 리뷰어를 지정할 수 있다. 지정된 사람은 모두 approve한다.
- 스택은 리뷰어가 한 명이다. 첫 조각에 배정된 사람으로 나머지 조각을 맞추고, 스택을 한 번에 읽고 조각마다 approve한다.
- 정족수가 전원인 PR(RFC, 절이 생기거나 없어지는 규칙 개정)은 배정과 무관하게 전원이 본다.

### 보호 경로

| 경로 | 무엇 | 왜 되돌리기 비싼가 |
| --- | --- | --- |
| `backend/src/main/resources/db/migration/` | Flyway 마이그레이션 | 적용된 DB에 체크섬이 박힌다. 고치면 기동이 실패한다 |
| `backend/src/main/resources/application.properties` | DB 연결, 프로파일, 외부 설정 | 잘못되면 서버가 안 뜨거나 엉뚱한 DB를 본다 |
| `backend/build.gradle.kts`, `backend/settings.gradle.kts`, `backend/gradle/` | 의존성, 버전 카탈로그, Gradle 버전 | 하나를 올리면 다른 것이 어긋난다. 전원의 로컬 환경이 같이 바뀐다 |
| `deploy/` | 운영 배포 설정과 스크립트 | 잘못 나가면 서비스가 내려간다 |
| `docker-compose.yml` | 로컬 환경 정의 | 깨지면 전원이 같이 멈춘다 |
| `docs/rfcs/` | 방향 결정 기록 | 정족수가 전원이다 |
| `docs/policies/` | 정책 값 | 코드 동작을 정한다 |
| `docs/contributing/`, `CONTRIBUTING.md`, `CLAUDE.md`, `docs/README.md` | 규칙과 정본 표 | 틀린 규칙대로 쌓이면 되돌릴 것이 코드가 된다 |
| `.github/` | CI, CODEOWNERS, PR 템플릿 | 머지 조건 자체를 바꾼다 |

- 정규식은 워크플로의 `PROTECTED`에, 리뷰 요청용 경로는 `.github/CODEOWNERS`에 있다. 표를 바꾸면 둘 다 바꾼다.
- 넣지 않은 것: `backend/openapi.yaml`. 생성물이라 컨트롤러나 DTO를 건드리면 같이 바뀐다.

## 리뷰 방법

- 모든 PR은 GitHub의 Files changed만 보고 승인할 수 있어야 한다.
  - 작성자가 규칙대로 했다고 보고 가볍게 훑어 approve한다. 로컬에 받거나 파일을 찾아다니지 않는다.
  - 기억과 다른 부분이 보이면 그때 로컬에 받아 점검하고, 그랬으면 코멘트에 적는다.
  - 코멘트는 판단이 갈리는 곳에만 단다. 놓친 것은 다음 PR에서 드러나면 그때 고친다.
- 문서 이관과 양식 변환은 합의한 규칙에서 빠진 것이 없는지만 훑는다. 문장 대조는 작성자와 도구가 한다.
- 리팩터와 기계적 변경(개명, 주석 삭제)은 본문의 왜와 기존 테스트가 손대지 않은 채 통과하는 것으로 판정한다. 이동 자체는 줄마다 읽지 않는다.
- 배정된 리뷰어만 approve한다.
