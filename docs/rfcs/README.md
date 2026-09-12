# RFC

방향 결정을 제안하고 기록하는 폴더다. 제안부터 토의와 승인까지 전부 PR에 남긴다.

## 언제 쓰나

- 되돌리면 외부 계약, 이미 쌓인 데이터, 그 위에 선 코드 전체(프레임워크, 라이브러리, 저장소, 레이어 규칙)가 같이 움직이는 결정에만 쓴다. 예: 이벤트 스키마의 모양, JPA 대신 jOOQ.
- 결정 하나에 한 장이다. 따로 바꿀 수 있는 항목은 규칙이라 `docs/contributing/`에, 값은 `docs/policies/`에 쓴다.
- 가르는 기준은 `docs/contributing/governance.md`. 애매하면 규칙이나 정책 PR로 올리고 리뷰에서 "RFC급" 지적이 나오면 승격한다.

## 쓰는 법

1. `template.md`를 복사해 `NNN-short-english-title.md`로 만든다. 제목은 영어 소문자와 하이픈만.
   - NNN은 아래 표의 다음 세 자리 번호. 재사용하지 않는다. 표에 draft로 한 줄을 추가한다.
2. PR을 연다. 제목은 `RFC-NNN: 제목`, 라벨은 `rfc`.
   - 웹에서 열 때는 아래 링크로 열어야 RFC 템플릿과 라벨이 붙는다.

   ```
   https://github.com/asm17-ms2/meterengine/compare/main...<브랜치>?quick_pull=1&template=rfc.md&labels=rfc
   ```
3. 리뷰어는 줄 댓글로 질문과 반론을 단다. 작성자는 유효한 반론을 "검토한 선택지"에 옮겨 적는다.
4. 작성자를 뺀 전원이 Approve하면 작성자가 머지한다.
   - `status`를 `accepted`로, `date`를 그날로 바꾸고 아래 표를 맞춘다.
   - PR 본문의 체크리스트를 모두 체크하고 squash merge한다. 체크 안 된 항목이 있으면 `rfc-checklist`가 실패한다.
   - "당장은 이대로 가도 될 것 같다"는 판단 유보도 Approve다.
   - 채택하지 않기로 하면 같은 정족수로 `status`를 `rejected`로 바꾸고 기록으로 머지한다.
5. 결정을 뒤집을 때는 새 RFC를 쓴다. `docs/contributing/governance.md` "RFC를 뒤집을 때".

## 상태

| 상태 | 의미 |
|---|---|
| draft | PR 열림, 토의 중 |
| accepted | merge됨, 유효한 결정 |
| rejected | 토의 끝에 채택 안 함 (기록용으로 merge) |
| superseded | 이후 RFC로 대체됨 |

## 목록

- 도메인은 frontmatter `domain`. process거나 백엔드 최상위 패키지명(`customer`, `invoice`, `global`)이다. 그 결정을 바꾸면 무너지는 도메인을 전부 적는다.

| 번호 | 제목 | 도메인 | 상태 | 날짜 |
|---|---|---|---|---|
| [000](000-documentation-and-decision-process.md) | 문서와 결정 프로세스 도입 | process | accepted | 2026-09-02 |
| [001](001-naming-convention.md) | 이름 규칙을 표준 관례에 맞춰 정한다 | process | accepted | 2026-09-04 |
| [002](002-comment-cleanup.md) | 주석 전수 정리와 함수 위 한 줄 설명 허용 | process | accepted | 2026-09-04 |
| [003](003-rule-management.md) | RFC는 되돌리면 계약, 데이터, 기술 선택이 같이 움직이는 결정에만 쓴다 | process | accepted | 2026-09-05 |
| [004](004-error-handling.md) | 오류 응답을 problem+json 대신 code와 message를 든 자체 스키마로 낸다 | global | accepted | 2026-09-07 |
