# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 이 저장소에 대해

- MeterEngine 모노레포. 사용량 기반 과금 플랫폼이다.
- `backend/`: 미터링 엔진 API 서버. `frontend/`: 관리자 화면. `demo/`: 시연용 CLI와 OTel 브리지. `work/`: 개인 작업 공간.
- 기술 스택은 백엔드 Java 25 + Spring Boot 4 + Gradle, 프론트엔드 Next.js, 저장소 PostgreSQL. 세부는 각 README.
- 이슈의 최신 상태는 Jira(MS2 프로젝트)에서 확인한다. 문서를 쓸 때 미정 범위를 확정된 것처럼 쓰지 않는다.

## 개인 작업 공간 (work/)

- `/work`는 .gitignore로 제외된 개인 공간이다. 각자 만들어 자유롭게 쓰고, 개인 레포로 백업해도 된다.
- 커밋 대상이 아닌 개인 산출물(메모, 조사, 스크래치, 보고서)은 팀 레포 트리에 만들지 않고 work/ 아래에 만든다.

## 커밋 / PR 사전 승인

- 커밋, push, PR 생성은 사용자에게 보고하고 승인을 받은 뒤에만 실행한다.
  - 실행 전에 대상 파일, 커밋 메시지, PR 제목과 본문 요지를 보고하고 명시적 승인을 기다린다.
  - 계획 승인은 커밋과 PR 실행 승인이 아니다. 실행 직전에 다시 확인한다.
  - 로컬 파일 편집, 브랜치 생성과 전환, 조회는 대상이 아니다.

## 규칙의 정본

- 팀 규칙의 입구는 CONTRIBUTING.md이고 규칙은 `docs/contributing/`에 있다. 규칙을 여기 베끼지 않는다.
  - 커밋 메시지: `git-workflow.md`. PR 크기, 스택, 리뷰: `pull-request.md`. 문서 양식과 README 점검: `documents.md`. 주석: `comments.md`.
  - 무엇의 정본이 어디인지는 `governance.md` "정본". 새 문서를 만들기 전에 그 표를 보고 없으면 먼저 물어본다.
- PR을 올리기 전에 `documents.md` "README 점검"을 본다.
- 아래는 Claude가 특히 틀리기 쉬운 것만 짚는다.

## 팀과 협업 도구

- 팀: 박성종(팀 리드), 문인호, 양성지. 멘토: 장시현, 강민준, 남상수 (2026 AI·SW 마에스트로 17기).
- Jira/Confluence: https://asm17-ms2.atlassian.net (프로젝트 키 MS2).
- Miro: 회의록, 일정, 브레인스토밍 초안.
- Notion: 쓰지 않는다. 무료 플랜으로 얼려 두었고 내용을 옮기지 않는다 (`docs/contributing/governance.md` "노션"). 옛 기록을 볼 때만 https://app.notion.com/p/MS2-3af0899b32b881f199ede2a87ac32a30 을 연다.
- GitHub org: https://github.com/asm17-ms2 (meterengine, meterengine-demo, asm-crawling).

## 개발 방법론

- 빅뱅 설계를 하지 않는다. 얇은 수직 슬라이스 단위로 개발한다. 슬라이스 하나는 최소 폭으로 끝-대-끝을 관통한다.
- PR은 사람이 리뷰하기 쉬운 단위가 대원칙이고 300줄 상한은 부가 규칙이다. 넘거나 읽기 힘들면 동작 단위로 조각내 스택 PR로 쌓는다.
- 리팩터는 왜 하는지만 사람이 정하고 구체적인 이동은 도구에 맡긴다. 판정은 본문의 왜와 기존 테스트 통과다.
- 문서를 옮기거나 양식을 바꿀 때 Claude는 중대한 내용이 빠지지 않았는지 확인한다. 사소한 누락도 사용자에게 보고하고 확인한다.
  - 리뷰 규칙이 가벼운 것은 사람의 부담을 줄이려는 것이지 Claude의 검증을 줄이는 것이 아니다.

## 스택 PR

- 독립 PR이 먼저다. 스택 조건과 명령 대응표는 `docs/contributing/pull-request.md` "스택 PR".
- GitHub 네이티브 스택 PR 기능만 쓴다. 수동 git으로 스택을 만들거나 정리하지 않는다.
  - 2026-08 기준 public preview라 학습 데이터에 없다. `git checkout -b`, `git rebase --onto`, `git push --force` 대신 `gh stack init`, `gh stack sync`, `gh stack submit`.
  - 설치나 명령이 실패하면 수동 git으로 우회하지 말고 멈추고 물어본다. 모르는 하위 명령은 `gh stack --help`.

## 주석

- 주석을 쓰지 않는다. javadoc도 주석이다. 예외는 선언 위의 한 줄 설명뿐이고 그것도 의무가 아니다. 형식은 `docs/contributing/comments.md`.
- 주석을 붙이고 싶어지면 이름이나 분리를 고친다.
- 규칙 이전의 주석은 레포 전체를 한 PR로 지운다. 옮기지 않고, 긴 주석을 한 줄로 줄이지 않는다.
- **`V__` 마이그레이션은 주석만 고쳐도 체크섬이 바뀌어 적용된 DB의 기동이 실패한다.** 배포 서버의 DB와 로컬 볼륨의 체크섬을 같이 맞춘다. `R__`는 다시 실행된다. CI는 빈 DB라 둘 다 잡지 못한다.
- **주변 코드의 주석 밀도는 본보기가 아니다.**.

## OpenAPI 생성물

- **컨트롤러나 DTO를 건드린 PR은 `backend/openapi.yaml`을 같이 커밋한다.** `./gradlew build`가 다시 만든다. `git status`에 떴으면 넣는다.
  - CI는 검사하지 않는다. 빠뜨리면 프론트엔드가 낡은 계약을 읽는다.

## 출력 형식

- 문서 양식과 금지 문자는 `docs/contributing/documents.md`. 응답에도 같다.
- 한영 병기는 고유 제품명 첫 등장 시에만.
- AI가 쓴 티가 나는 문구를 쓰지 않는다.
