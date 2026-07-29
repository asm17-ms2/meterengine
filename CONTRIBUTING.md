# CONTRIBUTING

브랜치/PR 규칙의 정본은 Notion의 [개발 워크플로 (브랜치/PR 규칙)](https://app.notion.com/p/3ac0899b32b881148d49e3b341bd5034) 페이지다. 이 파일은 그 요약이며, 규칙이 바뀌면 두 곳을 함께 갱신한다.

## 확정된 규칙

- main에는 직접 push할 수 없다 (GitHub 브랜치 보호로 강제). 모든 변경은 브랜치를 만들어 PR로 올린다
- docs/의 문서(ADR, OpenAPI, ERD)도 코드와 같은 PR 흐름을 따른다. ADR은 "제안" 상태로 PR을 올리고, 합의되면 같은 PR에서 상태를 "승인"으로 바꾸고 결정일을 기입한 뒤 머지한다. "제안" 상태로 머지하지 않는다 (docs/adr/README.md 참조)
- work/는 개인 작업 공간이라 PR 대상이 아니다

## 제안 (MS2-31, 팀 합의 대기)

아래는 제안 상태다. 이 PR에서 합의되면 이 절을 "확정된 규칙"으로 올리고 Notion 정본을 함께 갱신한다.

- 브랜치 네이밍: `<type>/MS2-<이슈번호>-<설명>` 형식. type은 feat, fix, docs, chore 중 하나. 예: `docs/MS2-31-branch-strategy`, `feat/MS2-40-event-ingest`. 기존 브랜치 관례(docs/MS2-28-...)와 호환된다
- 머지 방식: squash merge. PR 하나가 커밋 하나로 남아 이슈 단위 추적이 쉽고, 브랜치 안 커밋 정리에 힘 쓰지 않아도 된다
- PR 리뷰: 승인 1명 이상 후 머지. CI 필수 체크 항목은 CI 도입 PR에서 지정한다
- 릴리스/태그 규칙: MVP 전까지 보류. 배포가 생기는 시점에 다시 논의한다
