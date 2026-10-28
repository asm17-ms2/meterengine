# 스택 #456 팀 작업 넘기기

- 스택 #456의 남은 지적을 층별로 나눠, 문인호 님(T-inho)과 양성지 님(T-yang)이 각자 맡은 층의 PR 브랜치에서 판정하고 고친다.
- 사람은 `common-brief.md` 1절대로 준비하고 아래에서 자기 패키지의 글을 Claude Code에 붙여 넣는다. 세션이 도는 동안 할 일은 맨 아래 절에 있고, 나머지는 세션이 `common-brief.md`대로 한다.
- 결과는 `claude/s456-team-<패키지>-report` 브랜치에 쌓인다. 2026-10-29부터 후속 PR 반영과 위층 전파는 두 분 중 한 분이 `orchestration.md`대로 맡는다.

## 읽는 순서

1. 이 파일: 준비 순서와 붙여 넣기 글
2. `common-brief.md`: 실행 지침. 사람은 1절만 읽으면 된다. clone 전에는 https://github.com/asm17-ms2/meterengine/tree/claude/s456-team-handover/.stack456-team 에서 연다.
3. `<패키지>/brief.md`: 맡은 층, 작업 순서, 건드리지 않을 id
4. `<패키지>/findings.json`: 항목 원문
5. `tools/`: 필요할 때만. `playwright.live.config.ts`(실화면 명세 실행), `screens.mjs`(화면 캡처와 axe), `check-report.mjs`(보고 완결성 검사)

## 붙여 넣기 글

글에는 두 가지를 미리 정해 두었다. 붙여 넣기 전에 바꿔도 된다.

- 커밋과 push 사전 승인: 팀 AGENTS.md가 요구하는 승인을 이 글로 미리 한다. 층 push마다 직접 승인하려면 "커밋과 push는 미리 승인한다"로 시작하는 문장을 지운다.
- 주간 상한 70%: 주간 사용량이 이 값에 닿기 전에 세션이 멈춘다. 본인 작업에 쓸 몫을 더 남기려면 숫자를 낮춘다. 70보다 높여도 70으로 본다.

### 문인호 님 (T-inho): #443, #444, #446, #448, #450, #451, #454, #455

```
ultracode. 스택 #456 팀 작업 T-inho(문인호 님, #443 #444 #446 #448 #450 #451 #454 #455)를 시작한다. 이 clone 루트의 .stack456-team/common-brief.md를 끝까지 읽고 그대로 따른다. 먼저 2절 시작 점검(모델 claude-opus-5-5, effort xhigh와 Ultracode는 사람이 맞췄다, Workflow 도구, Docker, Java 25, python3, corepack pnpm, gh push 권한)을 모두 돌리고, 하나라도 실패하면 점검 보고를 쓰고 멈춘다. 통과하면 .stack456-team/T-inho/brief.md와 findings.json대로 판정하고 고친다. 맡은 feat 브랜치와 claude/s456-team-T-inho-report에만 push하고 위층으로 옮기는 merge는 하지 않는다. 보고 브랜치가 이미 있으면 그 state.json에서 이어 간다. 커밋과 push는 미리 승인한다: 맡은 feat 브랜치와 claude/s456-team-T-inho-report에, common-brief 8절 형식의 메시지로, 10절 검사를 통과한 커밋에 한한다. 그 밖의 커밋과 push는 하지 않는다. 주간 상한 70%.
```

### 양성지 님 (T-yang): #442, #445, #447, #449, #452, #453

```
ultracode. 스택 #456 팀 작업 T-yang(양성지 님, #442 #445 #447 #449 #452 #453)을 시작한다. 이 clone 루트의 .stack456-team/common-brief.md를 끝까지 읽고 그대로 따른다. 먼저 2절 시작 점검(모델 claude-opus-5-5, effort xhigh와 Ultracode는 사람이 맞췄다, Workflow 도구, Docker, Java 25, python3, corepack pnpm, gh push 권한)을 모두 돌리고, 하나라도 실패하면 점검 보고를 쓰고 멈춘다. 통과하면 .stack456-team/T-yang/brief.md와 findings.json대로 판정하고 고친다. 맡은 feat 브랜치와 claude/s456-team-T-yang-report에만 push하고 위층으로 옮기는 merge는 하지 않는다. 보고 브랜치가 이미 있으면 그 state.json에서 이어 간다. 커밋과 push는 미리 승인한다: 맡은 feat 브랜치와 claude/s456-team-T-yang-report에, common-brief 8절 형식의 메시지로, 10절 검사를 통과한 커밋에 한한다. 그 밖의 커밋과 push는 하지 않는다. 주간 상한 70%.
```

### 오케스트레이션 (두 분 중 한 분)

자기 층 작업 세션과 다른 clone에서 붙여 넣는다.

```
스택 #456 오케스트레이션을 이어받는다. 이 clone 루트에서 git fetch origin 뒤 git archive origin/claude/s456-team-handover .stack456-team | tar -x 로 자료를 꺼내고 .stack456-team/orchestration.md를 끝까지 읽고 그대로 따른다. 커밋과 push는 미리 승인한다: orchestration.md 3절대로 만든 feat 브랜치의 반영 merge 커밋과 의미 충돌 수정 커밋 가운데 3절 4번 검사를 통과한 것, 그리고 4절의 common-brief 10절 표 변경에 한한다. 3절 8번의 PR 댓글과 Slack 스레드 알림도 미리 승인한다. 그 밖의 커밋, push, 게시는 하지 않는다. 주간 사용량이 70%에 닿으면 새 반영을 시작하지 않고 멈춘다.
```

## 도는 동안

- 세션이 끝날 때까지 컴퓨터를 깨워 둔다(`common-brief.md` 1절 4번의 `caffeinate -dims`).
- 시작 점검 보고에 사용량 도구가 없다고 나오면(CLI 등) 사람이 Claude Code의 `/usage`로 사용량을 지켜본다. 5시간이나 주간 사용률이 70%(주간 상한을 낮췄으면 그 값) 즈음이면 세션에 "사용량 70%라 멈춰"라고 보낸다.
- 세션은 사용량이 70%에 닿기 전이나 `common-brief.md` 12절 조건에서 멈춘다. 멈추면 까닭을 고친 뒤(사용량 초기화 기다리기, Docker 켜기 등) 같은 글을 같은 clone에서 다시 붙여 넣는다. 세션은 보고 브랜치의 state.json에서 이어 간다.
