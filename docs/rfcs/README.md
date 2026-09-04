# RFC

MS2 팀의 설계와 정책 방향 결정을 제안하고 기록하는 폴더입니다.
"왜 이렇게 정했는지"를 나중에 찾아볼 수 있게, 제안부터 토의와 승인까지 전부 PR에 남깁니다.

## 언제 쓰나

정한 뒤 나중에 바꾸려면 다른 것(스키마, 다른 모듈, 외부 계약, 팀 규칙)이 같이 바뀌거나 데이터 이관, 외부 공지가 필요한 결정.
예) 미터 집계 방식, 이벤트 스키마, API 버저닝, 데이터 보관 정책.

인보이스 확정 대기 기간 같은 세부 규칙은 여기가 아니라 `docs/policies/`에 씁니다.
애매하면 정책 PR로 올리고, 리뷰에서 "RFC급" 지적이 나오면 RFC로 승격합니다.
회의록, 일정, 브레인스토밍 초안은 Miro에 씁니다.

## 쓰는 법

1. `template.md`를 복사해 `NNN-short-english-title.md`로 만든다. 제목은 영어 소문자와 하이픈만 쓴다. GitHub 리뷰 화면이 한글 경로 파일의 줄 댓글을 표시하지 않기 때문이다. NNN은 아래 표의 다음 세 자리 번호. 번호는 재사용하지 않는다. 아래 표에 draft로 한 줄을 추가한다.
2. 채우고 PR을 연다. 제목은 `RFC-NNN: 제목`, 라벨은 `rfc`. 웹에서 열 때는 아래 링크 형식으로 열어야 RFC 전용 PR 템플릿과 라벨이 붙는다 (GitHub 웹에는 PR 템플릿 선택창이 없다).

   ```
   https://github.com/asm17-ms2/meterengine/compare/main...<브랜치>?quick_pull=1&template=rfc.md&labels=rfc
   ```
3. 리뷰어는 줄 댓글로 질문과 반론을 단다. 작성자는 답하면서 유효한 반론을 "검토한 선택지"에 옮겨 적는다.
4. 작성자를 제외한 팀원 전원이 Approve하면 작성자가 frontmatter의 `status`를 `accepted`로, `date`를 그날로 바꾸고, 아래 표의 상태와 날짜를 맞춘 뒤 PR 본문의 체크리스트를 모두 체크하고 squash merge한다. 체크 안 된 항목이 남아 있으면 `rfc-checklist` 체크가 실패해 머지되지 않는다. "당장은 이대로 가도 될 것 같다"는 판단 유보 동의도 승인으로 치고 Approve를 누른다. 토의 끝에 채택하지 않기로 하면 같은 정족수로 `status`를 `rejected`로 바꾸고 기록으로 남기기 위해 merge한다.

결정을 뒤집을 때는 옛 파일을 지우지 않고 새 RFC를 씁니다. 새 문서에는 어느 결정을 대체하는지를(frontmatter `supersedes`), 옛 문서에는 대체됐다는 상태(`status: superseded`)와 어느 결정으로 대체됐는지를(`superseded-by`) 적습니다. 옛 문서는 새 RFC의 PR에서 같이 고칩니다.

RFC PR만 모아 보려면 PR 목록에서 `label:rfc`로 거릅니다. RFC를 쓰기엔 가벼운 제안은 라벨 `proposal`을 붙인 일반 PR로 올립니다.

## 상태

| 상태 | 의미 |
|---|---|
| draft | PR 열림, 토의 중 |
| accepted | merge됨, 유효한 결정 |
| rejected | 토의 끝에 채택 안 함 (기록용으로 merge) |
| superseded | 이후 RFC로 대체됨 |

## 목록

도메인은 frontmatter `domain`과 같은 값이다. process거나, 백엔드 패키지명과 같은 이름(customer, event, metric, pricing, invoice, payment)을 쓴다. 그 결정을 바꾸면 무너지는 도메인을 전부 적고, 순서에 뜻을 두지 않는다. 정책 파일 이름도 이를 따른다.

| 번호 | 제목 | 도메인 | 상태 | 날짜 |
|---|---|---|---|---|
| [000](000-documentation-and-decision-process.md) | 문서와 결정 프로세스 도입 | process | accepted | 2026-09-02 |
| [001](001-naming-convention.md) | 이름 규칙을 표준 관례에 맞춰 정한다 | process | accepted | 2026-09-04 |
| [002](002-comment-cleanup.md) | 주석 전수 정리와 함수 위 한 줄 설명 허용 | process | accepted | 2026-09-04 |
