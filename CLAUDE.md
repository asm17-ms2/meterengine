# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 이 저장소에 대해

MeterEngine 제품 모노레포다. 사용량 기반 과금 플랫폼으로, raw usage event 수집 -> 미터링/집계 -> rating -> 인보이스 생성 파이프라인에 국내 PG 연동과 전자세금계산서 발행을 결합하는 것을 목표로 한다.

- `backend/`: 미터링 엔진 API 서버
- `frontend/`: 관리자 화면
- `demo/`: 데모 시연용 CLI (Python, 이벤트 전송과 청구 예정액 검증)
- `docs/`: 무엇을 둘지 미정 (아래 "문서 흐름" 참조)
- `work/`: 개인 작업 공간 (.gitignore로 제외, 아래 참조)

## 개인 작업 공간 (work/)

- `/work`는 .gitignore로 제외된 개인 공간이다. 팀원 각자 자기 work/를 만들어 자유롭게 쓴다. 개인 계정의 별도 레포로 백업해도 된다 (이 경우 work/ 안에서만 해당 레포의 git 명령을 실행한다)
- 커밋 대상이 아닌 개인 산출물(메모, 조사 자료, 스크래치 파일, 개인 보고서)은 팀 레포 트리(backend/, frontend/, demo/, docs/, 루트)에 만들지 않고 work/ 아래에 만든다. 팀 레포에는 팀이 리뷰하고 커밋할 파일만 둔다

## 커밋 / PR 사전 승인 (팀 합의)

커밋, push, PR 생성은 사용자에게 보고하고 승인을 받은 뒤에만 실행한다.

- 실행 전에 무엇을 올릴지(대상 파일, 커밋 메시지, PR 제목/본문 요지) 먼저 보고하고 명시적 승인을 기다린다
- 계획(plan) 승인은 작업 내용에 대한 승인이지 커밋/PR 실행 승인이 아니다. 계획에 커밋/PR이 포함되어 있어도 실행 직전에 다시 확인한다
- 로컬 파일 편집, 브랜치 생성/전환, 조회는 이 규칙의 대상이 아니다

## 현재 상태 (중요)

- 기술 스택 확정: 백엔드 Java 25 + Spring Boot 4 + Gradle, 프론트엔드 Next.js, 저장소 PostgreSQL 단일. 세부 구성은 `backend/README.md`와 `frontend/README.md`가 정본이다
- 브랜치 전략: main 직접 push 금지(모든 변경은 PR로), 브랜치 네이밍, squash 머지 모두 확정됐고 GitHub 브랜치 보호로 강제된다. PR 크기 상한(300줄)과 쪼개는 축, 스택 PR 절차도 같이 확정됐다. 정본은 CONTRIBUTING.md
- 문서를 쓸 때 미정 범위를 확정된 것처럼 서술하지 않는다
- 이슈의 최신 상태는 Jira(MS2 프로젝트)에서 확인한다

## 팀과 협업 도구

- 팀: 박성종(팀 리드), 문인호, 양성지 / 멘토: 장시현, 강민준, 남상수 (2026 AI·SW 마에스트로 17기)
- Jira/Confluence: https://asm17-ms2.atlassian.net (Jira 프로젝트 키 MS2, Confluence의 MS2 스페이스)
- Notion: MS2 팀 위키, https://app.notion.com/p/MS2-3af0899b32b881f199ede2a87ac32a30 (정책과 스프린트 범위의 정본, 아래 "문서 흐름" 참조)
- 팀 GitHub org: https://github.com/asm17-ms2 (meterengine, meterengine-demo, asm-crawling)

## 개발 방법론

빅뱅 설계를 하지 않는다. 얇은 수직 슬라이스 단위로 개발한다.

- 슬라이스 하나는 최소 폭으로 끝-대-끝을 관통한다
- 명세서나 정책 문서를 작성할 때 현재 슬라이스 범위와 미정 범위를 구분해 쓴다
- 슬라이스를 얇게 만드는 것과 PR을 작게 내는 것은 다른 축이다. 슬라이스가 300줄을 넘으면 동작 단위로 조각내 스택 PR로 쌓는다. 쪼개는 축과 머지 순서는 CONTRIBUTING.md "PR 크기와 쪼개기", "스택 PR"에 있다

## 스택 PR

**독립 PR이 먼저다.** 따로 머지돼도 되는 작업은 각각 main에서 따서 독립 PR로 낸다. 스택은 조각이 따로 머지되면 반만 동작할 때, 뒤 PR이 앞 PR 없이 성립하지 않을 때, 순서대로 읽어야 이해될 때 쓴다. 판단 기준은 CONTRIBUTING.md "스택 PR"에 있다.

**GitHub 네이티브 스택 PR 기능만 쓴다. 수동 git으로 스택을 만들거나 정리하지 않는다.** 2026-08 기준 public preview라 학습 데이터에 없는 기능이고, 익숙한 수동 rebase로 되돌아가면 스택으로 인식되지 않는 PR이 생기거나 히스토리가 어긋난다.

| 하려는 일 | 쓰지 않는다 | 대신 쓴다 |
| --- | --- | --- |
| 선행 브랜치 위에 새 작업 시작 | `git checkout -b`로 그냥 따기 | `gh stack init` |
| 이미 있는 스택에 합류 | 브랜치 이름으로 checkout | `gh stack checkout <PR번호>` |
| PR 올리기 | `gh pr create --base <부모브랜치>` (diff만 맞고 스택으로 인식되지 않는다) | `gh stack submit` |
| 부모가 갱신됐을 때 맞추기 | `git rebase --onto`, `git rebase --update-refs`, `git rebase -i` | `gh stack sync` |
| 부모가 머지된 뒤 | 손으로 base 바꾸기, 손으로 rebase | 아무것도 하지 않는다. GitHub가 base를 다시 잡는다. 로컬만 `gh stack sync` |
| 스택 브랜치 push | `git push --force` | `gh stack submit` |
| 구조 확인 | `git log --graph`로 추정 | `gh stack view` |
| 이미 올린 PR을 스택으로 묶기 | base만 바꾸기 | `gh stack link` 또는 웹에서 묶기 |

확장이 없으면 `gh extension install github/gh-stack`으로 설치한다. 설치나 명령이 실패하면 수동 git으로 우회하지 말고 멈추고 물어본다. 모르는 하위 명령은 추측하지 말고 `gh stack --help`나 아래 문서를 본다.

- 개념과 머지 동작: https://github.github.com/gh-stack/introduction/overview/
- CLI 레퍼런스: https://github.github.com/gh-stack/reference/cli/
- GitHub 공식 문서: https://docs.github.com/ko/pull-requests/get-started/about-stacked-prs

## 주석

주석을 쓰지 않는다. 코드만 읽어도 이해되게 쓰고, 코드가 맞게 도는지는 테스트로 관리한다.

- 왜 이렇게 설계했는지는 노션에, 이 변경을 왜 했는지는 커밋 메시지와 PR 본문에, "고치면 깨진다"는 테스트에 넣는다
- 주석을 붙이고 싶어지면 대개 이름이나 분리가 잘못된 것이다. 주석 대신 그쪽을 고친다
- 도구 지시문(`@SuppressWarnings`, `// eslint-disable-next-line`)과 `@Schema`/`@Operation`의 description은 대상이 아니다. description은 주석이 아니라 API 계약이다. 다만 거기에 Jira 키를 넣지 않는다 (`openapi.yaml`로 외부에 나간다)
- 지금부터 새로 쓰거나 고치는 코드에 적용한다. 기존 주석은 소급 정리하지 않고, 그 파일을 다른 이유로 편집할 때 함께 정리한다
- **주변 코드에 주석이 많아도 그것을 근거로 삼지 않는다.** 기존 파일의 주석 밀도는 규칙 이전 상태이지 따라야 할 본보기가 아니다

전체 규칙은 CONTRIBUTING.md "주석과 javadoc"에 있다.

## 문서 흐름

코드를 고칠 때 같이 고쳐야 하면 레포, 논의해서 정하는 것이면 Notion MS2 팀 위키다. 규칙의 정본은 `docs/document-rules.md`이며, 새 문서를 만들기 전에 그 파일을 보고 없는 내용이면 먼저 물어본다.

레포 안 정본은 API 계약이 `backend/openapi.yaml`, 브랜치와 PR 규칙이 `CONTRIBUTING.md`, 각 디렉터리의 실행법과 구조가 그 디렉터리 README다. `docs/` 아래에 무엇을 둘지는 아직 정하지 않았다.

PR을 올리기 전에 CONTRIBUTING.md의 "README 점검"을 본다. 슬라이스가 끝날 때마다 README가 밀리는 것을 막는 표다.

## OpenAPI 생성물 (MS2-140)

**백엔드 컨트롤러나 DTO를 건드린 PR은 `backend/openapi.yaml`을 같이 커밋한다.** `./gradlew build`가 다시 만들어 주므로, 빌드한 뒤 `git status`에 이 파일이 떴으면 커밋에 넣는다.

CI는 이 파일을 검사하지 않는다. 갱신을 빠뜨려도 아무것도 실패하지 않고, 프론트엔드가 낡은 계약을 읽게 된다. 검사를 넣지 않은 이유와 재검토 조건은 `backend/README.md`에 있다.

## 출력 형식

- 가운뎃점, 엠 대시, 이모지, 스마트 따옴표를 쓰지 않는다 (공식 고유명사 제외)
- 한영 병기는 고유 제품명 첫 등장 시에만 최소한으로
- AI가 쓴 티가 나는 문구를 지양한다
- 옆에 목록이 있으면 개수를 문장이나 제목에 적지 않는다 ("예외 핸들러 넷" -> "예외 핸들러"). 항목이 늘면 조용히 틀린다. 세는 것 자체가 정보인 자리는 예외이며, 기준은 CONTRIBUTING.md "문서의 개수 표현"에 있다
