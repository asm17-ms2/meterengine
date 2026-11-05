# 브랜치, 커밋, 머지

- develop과 main에는 직접 push할 수 없다. 브랜치를 만들어 develop으로 PR을 올린다.
- main은 릴리스와 긴급 수정만 받는다. develop에서 main으로 PR을 올려 일반 머지하고, 머지되면 production에 배포된다.
- 긴급 수정은 main에서 브랜치를 따서 main으로 PR을 올린다. 머지 뒤 같은 수정을 develop에도 PR로 넣는다.
- 브랜치 이름은 `<type>/MS2-<이슈번호>-<설명>`. 티켓이 없으면 `<type>/<설명>`.
  - type은 feat, fix, docs, refactor, test, ci, chore.
- 커밋 제목은 `<type>: <요약> (MS2-xxx)`. 티켓이 없으면 `(MS2-xxx)`를 뺀다.
- 커밋 본문은 PR 템플릿(`.github/pull_request_template.md`)의 절을 따른다.
  - 사소한 변경(오타, 링크, 버전 올리기)은 제목 한 줄로 끝내도 된다.
  - 절 제목에 `#`을 쓰지 않는다. git이 주석으로 지운다.
- develop은 squash merge, main은 일반 머지. 룰셋이 강제한다.
