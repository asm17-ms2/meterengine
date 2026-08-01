# CONTRIBUTING

브랜치/PR 규칙의 정본은 Notion의 [개발 워크플로 (브랜치/PR 규칙)](https://app.notion.com/p/3ac0899b32b881148d49e3b341bd5034) 페이지다. 이 파일은 그 요약이며, 규칙이 바뀌면 두 곳을 함께 갱신한다.

## 확정된 규칙

- main에는 직접 push할 수 없다 (GitHub 브랜치 보호로 강제). 모든 변경은 브랜치를 만들어 PR로 올린다
- docs/의 문서도 코드와 같은 PR 흐름을 따른다. ADR의 상태 전이와 기각 처리는 `docs/adr/README.md`, 문서 분류와 위치는 `docs/문서-관리-규칙.md`를 따른다
- work/는 개인 작업 공간이라 PR 대상이 아니다
- 브랜치 네이밍: `<type>/MS2-<이슈번호>-<설명>` 형식. type은 feat, fix, docs, refactor, test, ci, chore 중 하나. 예: `docs/MS2-31-branch-strategy`, `feat/MS2-40-event-ingest`. 브랜치명에 이슈 키가 들어가 Jira가 브랜치/PR을 이슈에 자동 연결한다
- 머지 방식: squash merge. PR 하나가 main 커밋 하나로 남아 이슈 단위 추적이 쉽고, 브랜치 안 커밋 정리에 힘 쓰지 않아도 된다. main 룰셋의 Allowed merge methods를 squash만 허용으로 설정해 강제한다. squash로 원본 커밋과의 연결이 끊기는 문제는 아래 "머지 후 브랜치 삭제"와 "선행 브랜치 위 작업" 규칙이 안전장치다
- 고정 브랜치 도입 시 전환 계획: 이후 테스트서버 운영 등으로 develop/staging 같은 고정 브랜치가 생기면, develop 룰셋은 squash만, main 룰셋은 merge commit만 허용으로 전환한다. 오래 사는 브랜치를 main과 반복 머지할 때 squash를 쓰면 히스토리가 꼬이기 때문이다. 시점은 릴리스/태그 규칙 논의와 함께 정한다
- 머지 후 브랜치 삭제: 머지된 브랜치는 삭제하고, 이어지는 작업은 main에서 새 브랜치를 딴다. GitHub 저장소 설정(Automatically delete head branches)으로 자동화한다. 로컬 브랜치는 각자 `git fetch --prune`으로 정리한다
- PR 리뷰: 작성자 본인 외 1명 이상 승인 후 머지한다. 셀프머지는 하지 않는다. main 룰셋의 Required approvals(1명)로 강제하며, 승인 후 새 커밋을 올리면 기존 승인은 무효화된다(Dismiss stale approvals)
- main 상태: main은 항상 빌드/테스트가 통과하는 상태를 유지한다. 깨진 코드나 반쯤 만든 기능은 브랜치에만 둔다. CI 실패 시 머지를 금지한다. 필수 체크 항목은 CI 워크플로(`.github/workflows/ci.yml`)의 `backend`, `frontend`, `docs`, `erd` 네 job이다 (MS2-31에서 지정). main 룰셋의 Required status checks에 네 job을 등록해 강제한다
- 선행 브랜치 위 작업: 브랜치 A가 리뷰 대기 중일 때 A 위에서 B를 이어 작업할 수 있다. A가 squash 머지되면 B의 히스토리에 A의 원본 커밋이 남아 diff가 섞이므로, B의 PR을 올리기 전에 main 위로 재배치한다
  - 2단 (A 위에 B): `git fetch origin` 후 `git rebase --onto origin/main A B`
  - 3단 이상 (A 위에 B, B 위에 C, ...): 최상단 브랜치에서 `git rebase --onto origin/main A --update-refs` (git 2.38+). 중간 브랜치 ref까지 한 번에 재배치된다
- 릴리스/태그 규칙: MVP 전까지 보류. 배포가 생기는 시점에 다시 논의한다
