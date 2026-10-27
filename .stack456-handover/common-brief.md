# 스택 #456 클라우드 검토 공통 지침

- 맡은 묶음의 지적을 현재 head에서 다시 확인하고, 맞는 것은 고쳐 검사를 통과하는 대로 바로 커밋해 `claude/s456-<묶음>-<PR 번호>` 브랜치로 push한다. 모아 두지 않는다.
- 판정과 로그는 `claude/s456-<묶음>-report` 브랜치에 PR 하나를 끝낼 때마다 push한다. 세션 한도가 다 되어 가거나 일을 마치면 아래 "넘기기"대로 정리해 push한다. 로컬 조율 세션은 이 브랜치를 1시간마다 확인해 이어받는다.
- `feat/*`와 `main`에는 push하지 않고 GitHub에 댓글, 리뷰, PR을 만들지 않는다. 스택 브랜치로 합치는 일과 PR 댓글은 로컬 조율 세션이 한다.

## 대상

저장소는 `asm17-ms2/meterengine`이고 스택 #456은 아래 14개 PR이다. 각 PR은 바로 앞 PR 브랜치를 base로 한다.

| PR | 브랜치 | 내용 |
|---|---|---|
| #442 | feat/account-permissions | 계정과 사용자별 접근 권한 |
| #443 | feat/product-design | 서비스 진입부터 콘솔까지 공통 디자인 |
| #444 | feat/webhook-jobs | 웹훅 전달과 예약 작업 실행 기반 |
| #445 | feat/billing-payments | 카드 등록부터 결제 결과 복구 |
| #446 | feat/pricing-aggregation | 요금제와 확장 집계 |
| #447 | feat/resource-archive | 과금 자원 보관 |
| #448 | feat/request-audit | 사용자와 API 키 요청 감사 |
| #449 | feat/customer-invoices | 고객 인보이스 조회와 결제 재시도 |
| #450 | feat/event-ingestion | 배치 수집과 이벤트 매핑 진단 |
| #451 | feat/billing-queries | 기간 필터와 내보내기 |
| #452 | feat/llm-service-demo | 도입사 서비스 예제(Lumen) |
| #453 | feat/environment-isolation | 테스트와 실제 환경 데이터 분리 |
| #454 | feat/auth-rate-limit | 공개 인증 요청 호출 제한 |
| #455 | feat/rds-auto-stop | 측정용 RDS 자동 중지 |

자료는 브랜치 `claude/s456-handover`의 `.stack456-handover/`에 있다.

```bash
git fetch origin claude/s456-handover
git show origin/claude/s456-handover:.stack456-handover/<묶음>/findings.json > /tmp/findings.json
```

`findings.json`의 `findings`가 할 일이다.

- `review_status`: `confirmed`는 앞선 검증에서 맞다고 본 것, `confirmed-partial`은 검증 관점 일부만 끝난 것, `unverified`는 검증 전이다. 셋 다 현재 head에서 다시 확인한다.
- `prior_verdicts`: 앞선 검증의 판정과 근거다.
- `note`: 마이그레이션 번호 순서 지적에 붙어 있다. 2026-10-27 19:39에 번호를 PR 순서로 다시 매겼으니 해결됐는지만 확인하고 번호는 건드리지 않는다.
- `already_refuted`: 앞선 검증에서 틀렸다고 본 지적이다. 다시 올리지 않는다.

줄 번호는 2026-10-27 18:15 head 기준이다. 그 뒤 마이그레이션 번호 정렬, 인증 E2E 복구, Lumen 문서 보완, 문인호 님 수정 PR #458~#461 머지와 위층 반영이 들어갔다. 지적 위치는 현재 head에서 다시 찾는다.

제품 사실: API 키는 `me_<mode>_` 뒤 base62 32자이고 SHA-256으로 저장하며 Bearer로 보낸다. `X-Organization-Id` 헤더는 없앴다. 결제 모드의 정본은 `payment_setting.mode`다. 예약 작업은 ShedLock 없이 `scheduled_job_run`의 UNIQUE(job_name, scheduled_at)로 중복을 막는다. 결제는 토스페이먼츠다. 전액 취소된 결제를 다시 취소하면 409 `payment_attempt_fully_canceled`다.

## 순서

1. 실행 환경을 확인한다: `java -version`(25 필요), docker, node, `corepack pnpm`. 돌릴 수 없는 검사는 보고에 적는다.
2. 지적마다 현재 head의 코드, 호출부, 설정, 테스트, PR 본문을 읽고 먼저 반박을 시도한다.
   - 판정은 `confirmed`, `refuted`, `already-fixed`, `uncertain` 중 하나다. 실패 경로를 코드로 짚을 수 있을 때만 `confirmed`다.
   - severity가 high, critical이거나 category가 security이면 두 관점으로 따로 본다. 하나는 재현 경로, 다른 하나는 이 제품에서 실제로 해로운지와 다른 PR에서 이미 막는지다.
   - 원인이 같은 지적은 하나로 묶고 묶은 id를 남긴다. 다른 묶음 자료와 겹쳐 보이면 보고에 적기만 한다.
   - 하위 에이전트를 써도 된다. 모델은 Opus까지만 쓴다.
3. 묶음 지침의 "추가 검토" PR이 있으면 그 PR의 diff 전체에 유지보수성 검토와 운영 검토를 한다. 관점은 `.stack456-handover/prompts/maint-ops.md`에 있다. 새 지적도 2번과 같이 검증하고 id는 `N<묶음>-<번호>`로 붙인다.
4. `confirmed`만 고친다.
   - 문제의 줄을 들인 PR 브랜치의 현재 head에서 `claude/s456-<묶음>-<PR 번호>`를 만든다. 위층 브랜치로 옮기는 일은 하지 않는다.
   - 커밋은 수정 단위로 여러 개여도 된다. 메시지는 `docs/contributing/git-workflow.md`를 따른다.
   - 팀 규칙은 `AGENTS.md`와 `docs/contributing/`이다. 코드 주석은 선언 위 한 줄 설명 말고 쓰지 않는다. 컨트롤러나 DTO를 바꾸면 `./gradlew build`로 다시 만든 `backend/openapi.yaml`을 같이 커밋한다.
   - 새 `V__` 마이그레이션은 피한다. 꼭 필요하면 만들되 보고에 적는다. 번호는 통합 때 다시 정한다.
   - 고친 동작을 확인하는 테스트를 더하거나 고치고, 돌릴 수 있는 검사를 돌린다.
   - 동작이나 API 계약이 바뀌는 수정은 하되 보고에 `behavior_change: true`로 표시한다.
   - 묶음 지침의 파일 범위 밖을 고쳐야 하면 고치지 않고 보고에 적는다.
   - 프론트 화면 문구는 사용자 과업, 화면 맥락, 행동의 명확성, 접근성, 제품 용어 일관성 기준으로 고친다. antd 6 관례를 따르고 e2e 선택자를 깨지 않는다.
5. 보고 브랜치 `claude/s456-<묶음>-report`에 `stack456-report/<묶음>.json`과 `stack456-report/<묶음>.md`를 둔다. 이 브랜치는 `origin/main`에서 만들고 이 두 파일만 커밋한다. PR 하나를 끝낼 때마다 갱신해 push한다.

## 보고 형식

`<묶음>.json`:

```json
{
  "task": "k2",
  "base_heads": {"feat/webhook-jobs": "<sha>"},
  "env": {"java": "...", "docker": false, "notes": "..."},
  "results": [
    {"id": "C444-004", "merged_ids": [], "verdict": "already-fixed", "severity": "medium", "reason": "...", "evidence": "backend/...:123 @ <sha>", "fixed": false, "fix_branch": null, "fix_commits": [], "tests": [], "behavior_change": false, "notes": ""}
  ],
  "new_findings": [
    {"id": "Nk2-001", "pr": 445, "file": "...", "line": 1, "severity": "...", "category": "...", "title": "...", "detail": "...", "failure_scenario": "...", "suggested_fix": "...", "verdict": "...", "reason": "...", "fixed": false, "fix_branch": null, "fix_commits": []}
  ],
  "out_of_scope": [{"id": "...", "file": "...", "why": "..."}],
  "not_done": ["..."],
  "checks": [{"command": "...", "result": "pass|fail|not-run", "detail": "..."}]
}
```

`<묶음>.md`는 한국어로 판정별 개수, PR별로 고친 것(커밋 해시와 한 줄 설명), 고치지 못한 것과 이유, 돌리지 못한 검사를 적는다.

## 넘기기

세션 한도 경고가 보이거나 맡은 일을 마치면 새 일을 시작하지 않고 정리한다.

1. 검사를 통과한 수정은 모두 커밋해 수정 브랜치에 push한다. 끝내지 못한 수정은 커밋하지 않고 넘기기 문서에 적는다.
2. `<묶음>.json`과 `<묶음>.md`를 최신으로 고친다.
3. `stack456-report/<묶음>-handover.md`에 이어받을 사람이 바로 시작할 수 있게 적는다.
   - 끝낸 것: 판정을 마친 id 수, push한 수정 브랜치와 마지막 커밋.
   - 남은 것: 판정하지 못한 id 목록, 하다 만 수정과 멈춘 지점, 추가 검토를 못 한 PR.
   - 이어받을 때 알아야 할 것: 판정을 바꿀 수 있는 의심, 다른 묶음과 겹치는 파일, 돌리지 못한 검사.
4. 세 파일을 보고 브랜치에 커밋해 push한다.

## 하지 않는 것

- `feat/*`, `main`, 다른 사람 브랜치에 push하지 않는다. force push하지 않는다.
- GitHub에 댓글, 리뷰, PR을 만들지 않는다.
- 계정을 만들거나 토스 같은 외부 서비스를 실제로 호출하지 않는다. 키 값을 파일이나 로그에 쓰지 않는다.
- PR 크기, 300줄 상한, 커밋이나 PR 나누기는 지적하지 않는다.
- 글에는 em-dash, en-dash, 이모지, 가운뎃점, 둥근 따옴표를 쓰지 않는다.
