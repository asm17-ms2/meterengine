# 스택 #456 오케스트레이션 넘기기

- 박성종 님 대신 스택 #456(PR #442~#455)의 후속 PR을 그 층 feat 브랜치에 merge하고, #455까지 위층으로 올리고, 검사 뒤 push하고, PR 댓글과 Slack으로 알린다.
- 문인호 님과 양성지 님 중 한 분이 맡는다. 자기 층 작업 세션(T-inho, T-yang)과 다른 clone에서 따로 돈다.
- 스택 PR을 main에 merge하는 일은 이 문서에 넣지 않았다. 박성종 님과 따로 정한다.

## 1. 지금 상태 (2026-10-29 07:56 KST)

| 층 | 브랜치 | head |
| --- | --- | --- |
| #442 | feat/account-permissions | 21e8ab71 |
| #443 | feat/product-design | 6e603740 |
| #444 | feat/webhook-jobs | be400889 |
| #445 | feat/billing-payments | 53ac6d9c |
| #446 | feat/pricing-aggregation | b80e8075 |
| #447 | feat/resource-archive | 6d91d415 |
| #448 | feat/request-audit | 82df9c48 |
| #449 | feat/customer-invoices | dc1836fc |
| #450 | feat/event-ingestion | 6c3a1186 |
| #451 | feat/billing-queries | d80e06d0 |
| #452 | feat/llm-service-demo | f86f51b2 |
| #453 | feat/environment-isolation | f481c86d |
| #454 | feat/auth-rate-limit | 37304e36 |
| #455 | feat/rds-auto-stop | 8c5c2738 |

- 반영한 후속 PR: #465(#443), #466(#444), #467(#445), #468(#446).
- 남은 후속 PR: #469(문인호 님, fix/s456-448-t-inho -> feat/request-audit). T-inho가 #448 마지막 실화면 점검을 통과하면 merge한다.
- 두 팀원 세션은 `archive-filters.real.spec.ts` 실패로 멈춰 있다. common-brief 10절 표에 더했으니(b334d369) 다시 붙여 넣으면 이어 간다. T-yang의 #447 수정 21건은 아직 push 전이다.
- 할 일: T-yang의 #447 후속 PR을 반영한 뒤 `archive-filters.real.spec.ts`를 #447에서 지우고 #451에 같은 파일로 둔다. #448~#450은 이 파일을 바꾸지 않아서, #447의 삭제가 위로 merge되면 #451에서도 지워진다. #451 merge에서 되살린다.
- 담당자가 정할 것: #467 댓글의 p3 세 가지(양성지 님), #468과 #469 댓글의 p4(문인호 님).

## 2. 언제 merge하나

- 후속 PR 작성자의 세션이 그 층을 떠났을 때다. 보고 브랜치 `claude/s456-team-<T-inho|T-yang>-report`의 `stack456-team-report/state.json`에서 `current.branch`가 다른 층이거나 `status`가 `done`이면 떠난 것이다. 애매하면 본인에게 묻는다.
- 후속 PR의 CI가 통과하고, PR이 지금 feat head를 포함해야 한다. 포함하지 않으면 작성자에게 feat를 받아 달라고 댓글을 단다.
- 리뷰 승인은 기다리지 않는다(#465 선례).

## 3. 절차

clone 하나에 층마다 worktree를 두면 빠르다(`git worktree add --detach ../s456/<층> origin/feat/<브랜치>`). Docker, Java 25, python3, corepack pnpm, gh가 있어야 한다.

1. 반영 merge. 메시지 파일은 아래 두 줄이다.

   ```
   fix: <작성자> 님의 #<층> 후속 수정(#<후속 PR>)을 반영한다

   - #<후속 PR>의 수정을 가져온다.
   ```

   ```bash
   git fetch origin
   git switch --detach origin/feat/<그 층 브랜치>
   git merge --no-ff -F <메시지 파일> origin/fix/s456-<층>-<t-inho|t-yang>
   ```

   위층마다 차례로, 바로 아래 층의 새 head를 같은 메시지로 merge한다.

   ```bash
   git switch --detach origin/feat/<위층 브랜치>
   git merge --no-ff -F <메시지 파일> <아래 층 새 head>
   ```

2. 충돌은 양쪽 의도를 다 살린다. 위층 README는 위층 글을 두고, 후속 PR이 더한 내용 가운데 그 층에서도 참인 것만 더한다. 층마다 `python3 .stack456-team/tools/diff_of_diffs.py <반영한 층의 옛 head> <반영한 층의 새 head> <층>=<옛 head>=<새 head>`로 후속 PR의 변경이 그대로 들어갔는지 보고, 표시된 파일을 읽어 확인한다.
3. 백엔드가 바뀐 층은 openapi를 다시 만든다. `backend/openapi.yaml`이 바뀌면 그 merge 커밋에 `git commit --amend --no-edit`로 넣는다.

   ```bash
   cd backend && ./gradlew -q test --tests 'com.meterengine.global.OpenApiDocumentTest' copyOpenApiSnapshot
   ```

4. 새 head마다 `bash .stack456-team/tools/check-layer.sh <그 층 worktree>`. 모두 통과해야 한다. 계약 명세는 포트가 고정이라 한 번에 한 층씩 돌린다.
5. 위층 시험이 깨지거나 위층 동작이 나빠졌으면(의미 충돌) 그 일이 시작되는 가장 낮은 층에 작은 커밋을 더하고(common-brief 8절 형식, 시험만이면 `test:`), 그 위층을 다시 merge한다. 위층이 후속 PR의 새 규칙을 따르지 않을 뿐 전과 같이 동작하면 고치지 않고 담당자에게 알린다.
6. push. 위층부터, origin head가 반영 전 head와 같고 fast-forward일 때만 올린다. force push, rebase, `gh stack sync`와 `gh stack rebase`는 쓰지 않는다. 이미 올린 커밋을 다시 쓰게 된다.

   ```bash
   bash .stack456-team/tools/push-stack.sh 455:rds-auto-stop:<옛>:<새> 454:auth-rate-limit:<옛>:<새> ...
   ```

7. 후속 PR이 merged로 닫혔는지 본다. head가 feat에 들어가면 GitHub이 닫는다.
8. 알린다. PR 댓글은 `[claude]`로 시작하고 상대를 @멘션한다(문인호 님 @moonino, 양성지 님 @skytre0). 무게는 뱅크샐러드 p1~p5(`p3:`처럼 소문자).
   - 반영한 후속 PR: merge했다는 것, 손으로 합친 곳, 담당자가 정할 것.
   - 진행 중인 다른 후속 PR: base가 움직였으니 받아 달라는 것과 충돌 여부. `git merge-tree --write-tree --name-only origin/fix/<그 브랜치> origin/feat/<그 층>`에 파일 이름이 나오면 충돌이다.
   - Slack: 아래 스레드에 한 번.

## 4. 멈춘 팀원 세션 풀기

- 보고 브랜치 state.json의 `status`가 `stopped`면 `stop_reason`과 `resume_hint`를 본다.
- 바꾸지 않은 head에서도 실패하는 시험이면 원인을 확인한다. 그 층과 위층에서 실화면 명세를 돌려 언제부터 실패했는지 본다(common-brief 11절).
- 고치지 않고 넘길 실패는 common-brief 10절 표에 한 줄을 더해 `claude/s456-team-handover`에 push한다. 팀원 세션은 다시 붙여 넣으면 이 브랜치를 새로 꺼내 읽는다. 고칠 실패면 그 층에서 3절대로 고친다.
- 그다음 본인에게 다시 붙여 넣어 달라고 알린다.

## 5. 틀리기 쉬운 것

- 반영은 `--no-ff` merge 커밋으로 한다. squash와 rebase는 후속 PR을 merged로 닫지 못하고, 이미 올린 커밋을 다시 쓴다.
- #466이 V28 마이그레이션을 그 자리에서 고쳤다. 스택을 띄운 적 있는 DB(로컬 볼륨, 데모, 시험 SUT)는 Flyway 체크섬이 맞지 않아 다시 만든다.
- CI는 프론트 lint와 build, 백엔드 build만 돈다. 프론트 단위 시험, 계약 명세, 카드 등록 계약 명세, 실화면 명세는 check-layer.sh로 직접 돌린다.
- 반영 전부터 실패하던 것: `playwright.workspace.config.ts`의 :130, :159(#453~#455). 이 설정은 check-layer.sh에 넣지 않았다. 실화면 명세의 이미 실패하는 시험은 common-brief 10절 표에 있다.
- 커밋과 push 전에 팀 AGENTS.md "커밋 / PR 사전 승인"을 따른다. 붙여 넣기 글에서 미리 승인할 수 있다.

## 6. 자료

- Slack 스레드: https://asm17-ms2.slack.com/archives/C0BKUTTTC9J/p1793156616259919
- 박성종 님 쪽 반영 기록은 PR 댓글과 이 스레드에 있다.
