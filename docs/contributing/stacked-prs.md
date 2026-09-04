# 스택 PR

**독립 PR이 먼저다.** 따로 머지돼도 상관없으면 각각 main에서 브랜치를 따 독립 PR로 낸다. 스택은 아래 중 하나일 때 쓴다.

- 조각이 따로 머지되면 반만 동작하는 상태가 main에 남는다. 한 덩어리로 갔어야 할 작업이 300줄 상한 때문에 쪼개진 경우
- 뒤 PR이 앞 PR 없이는 성립하지 않는다. 줄 수와 무관하다
- 한 기능을 순서대로 읽어야 이해된다. 파일 이동과 내용 변경을 나눈 경우

세 번째는 "그게 편해서"로 넓어지기 쉽다. 순서를 바꿔 읽어도 이해되면 독립 PR이다.

스택은 선행 브랜치 위에 쌓고 몇 단이든 된다. 어느 PR에서 머지하든 그 아래 머지되지 않은 PR이 함께 들어가고, 남은 자식들은 base가 자동으로 다시 잡힌다. 스택 안의 각 PR에도 "본인 외 1명 승인"이 그대로 걸린다.

## 머지 순서

**호출 경로가 열리는지로 가른다.**

- **내부 구조만 늘리는 조각** (엔티티, 리포지토리, 내부 서비스): 담당자 재량. 하나씩 머지하든 최상단에서 한 번에 밀든 상관없다. 아무도 호출하지 않아 밖에서 보이는 것이 달라지지 않는다
- **컨트롤러나 화면이 들어가는 조각**: 한 번에 머지한다. 뒤에 올 검증이나 오류 처리가 아직 없으면 반만 동작하는 API가 노출된다

각 PR은 자기 브랜치에서 필수 체크를 통과해야 머지되므로 main은 항상 통과 상태다.

- 승인되지 않은 부모를 끌고 들어가는 자식 머지는 요청하지 않는다. 머지는 웹에서 하든 `gh stack merge`로 하든 상관없다. main 룰셋에 bypass가 없어 어느 경로든 승인과 필수 체크가 강제된다
- 머지 큐는 쓰지 않는다. 큐 모드는 각 PR을 아래부터 개별로 평가해 main이 중간 상태를 거치므로 스택의 원자성이 깨진다. 도입을 논의할 때 이 항목을 같이 본다

## gh stack

**GitHub 네이티브 스택 PR 기능만 쓴다. 수동 git으로 스택을 만들거나 정리하지 않는다.** 2026-08 기준 public preview라 익숙한 수동 rebase로 되돌아가기 쉽고, 그러면 스택으로 인식되지 않는 PR이 생기거나 히스토리가 어긋난다.

| 하려는 일 | 쓰지 않는다 | 대신 쓴다 |
| --- | --- | --- |
| 선행 브랜치 위에 새 작업 시작 | `git checkout -b`로 그냥 따기 | `gh stack init` |
| 있는 스택 위에 브랜치 추가 | `git checkout -b` | `gh stack add` |
| 이미 있는 스택에 합류 | 브랜치 이름으로 checkout | `gh stack checkout <PR번호>` |
| PR 올리기 | `gh pr create --base <부모브랜치>` (diff만 맞고 스택으로 인식되지 않는다) | `gh stack submit` |
| 부모가 갱신됐을 때 맞추기 | `git rebase --onto`, `git rebase --update-refs`, `git rebase -i` | `gh stack sync`. push 없이 로컬만 맞추려면 `gh stack rebase` |
| 부모가 머지된 뒤 | 손으로 base 바꾸기, 손으로 rebase | 아무것도 하지 않는다. GitHub가 base를 다시 잡는다. 로컬만 `gh stack sync` |
| 스택 브랜치 push | `git push --force` | `gh stack submit` |
| 구조 확인 | `git log --graph`로 추정 | `gh stack view` |
| 이미 올린 PR을 스택으로 묶기 | base만 바꾸기 | `gh stack link` 또는 웹에서 묶기 |

확장이 없으면 `gh extension install github/gh-stack`으로 설치한다. 설치나 명령이 실패하면 수동 git으로 우회하지 말고 멈추고 묻는다. 모르는 하위 명령은 추측하지 말고 `gh stack --help`나 아래 문서를 본다.

- 개념과 머지 동작: https://github.github.com/gh-stack/introduction/overview/
- CLI 레퍼런스: https://github.github.com/gh-stack/reference/cli/
- GitHub 공식 문서: https://docs.github.com/ko/pull-requests/get-started/about-stacked-prs
