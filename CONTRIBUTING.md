# CONTRIBUTING

브랜치/PR 규칙의 정본은 Notion의 [개발 워크플로 (브랜치/PR 규칙)](https://app.notion.com/p/3ac0899b32b881148d49e3b341bd5034) 페이지다. 이 파일은 그 요약이며, 규칙이 바뀌면 두 곳을 함께 갱신한다.

## 확정된 규칙

- main에는 직접 push할 수 없다 (GitHub 브랜치 보호로 강제). 모든 변경은 브랜치를 만들어 PR로 올린다
- docs/의 문서(ADR, OpenAPI, ERD)도 코드와 같은 PR 흐름을 따른다. ADR은 "제안" 상태로 PR을 올리고, 합의되면 같은 PR에서 상태를 "승인"으로 바꾸고 결정일을 기입한 뒤 머지한다. "제안" 상태로 머지하지 않는다 (docs/adr/README.md 참조)
- work/는 개인 작업 공간이라 PR 대상이 아니다

## 미합의 (MS2-31에서 정한다)

- 브랜치 네이밍 규칙
- 머지 방식 (squash / merge commit / rebase)
- PR 리뷰 승인 수, CI 필수 체크 항목
- 릴리스/태그 규칙
