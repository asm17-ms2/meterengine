# RFC

기존 결정의 내용과 번호, 당시 이유를 보존하는 폴더다.

- 신규 아키텍처 결정은 [ADR 작성 안내](../adr/README.md)를 따른다.
- 기존 기록을 새 양식으로 일괄 다시 쓰지 않는다. 결정을 대체할 때도 ADR 작성 안내의 "대체"를 따른다.
- 진행 중인 RFC는 문서별로 전환 여부를 확인한다.

## 상태

| 상태 | 의미 |
| --- | --- |
| draft | 기존 RFC의 제안 상태 |
| accepted | 기존 RFC에서 채택한 결정 |
| rejected | 채택하지 않기로 확정한 기록 |
| superseded | 이후 결정 기록으로 대체됨 |

## 목록

- 기존 frontmatter의 `domain`, 상태와 날짜를 유지한다. 대체 시 상태와 연결 정보는 ADR 작성 안내를 따른다.

| 번호 | 제목 | 도메인 | 상태 | 날짜 |
|---|---|---|---|---|
| [000](000-documentation-and-decision-process.md) | 문서와 결정 프로세스 도입 | process | accepted | 2026-09-02 |
| [001](001-naming-convention.md) | 이름 규칙을 표준 관례에 맞춰 정한다 | process | accepted | 2026-09-04 |
| [002](002-comment-cleanup.md) | 주석 전수 정리와 함수 위 한 줄 설명 허용 | process | accepted | 2026-09-04 |
| [003](003-rule-management.md) | RFC는 되돌리면 계약, 데이터, 기술 선택이 같이 움직이는 결정에만 쓴다 | process | accepted | 2026-09-05 |
| [004](004-error-handling.md) | 오류 응답을 problem+json 대신 code와 message를 든 자체 스키마로 낸다 | global | accepted | 2026-09-07 |
