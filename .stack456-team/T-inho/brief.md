# T-inho: 문인호 님 (#443 #444 #446 #448 #450 #451 #454 #455)

- 맡은 8개 층의 남은 지적을 판정하고, 맞는 것은 그 층 PR 브랜치에 고쳐 push한다.
- 346항목(묶인 id까지 372개)이다. 판정 246, 수정 99, 담당 밖 1, high 15건이고 #443(92)과 #451(85)이 가장 많다.
- 아래 "건드리지 않을 id"는 박성종 님(조율 담당) 몫이다. 판정도 수정도 하지 않는다. "범위를 좁힌 항목"은 적힌 범위만 고친다.

## 맡은 브랜치

push는 이 8개와 `claude/s456-team-T-inho-report`에만 한다.

| 층 | 브랜치 | 넘길 때 head | 항목 | 판정 | 수정 | high |
| --- | --- | --- | --- | --- | --- | --- |
| #443 | feat/product-design | 0f1d1bf | 92 | 88 | 4 | 5 |
| #444 | feat/webhook-jobs | 81651ed | 37 | 29 | 8 | 0 |
| #446 | feat/pricing-aggregation | c27b229 | 46 | 17 | 29 | 4 |
| #448 | feat/request-audit | bc7e538 | 17 | 11 | 6 | 1 |
| #450 | feat/event-ingestion | 7b1d1e5 | 38 | 32 | 6 | 0 |
| #451 | feat/billing-queries | 17cc788 | 85 | 47 | 38 | 1 |
| #454 | feat/auth-rate-limit | dac784b | 18 | 15 | 3 | 4 |
| #455 | feat/rds-auto-stop | 0fe7d2a | 12 | 6 | 6 | 0 |

담당 밖 1건은 #447 C447-011이다. #446 C446-013과 같은 시험, 같은 원인이라 이 패키지에 두었다. #446 차례에 판정도 수정도 하지 않고 blocked(`not-owner #447`)로 둔다.

## 작업 순서

1. 층은 아래부터 #443, #444, #446, #448, #450, #451, #454, #455 순으로 하나씩 끝낸다. 고친 커밋은 검사를 통과하는 대로 push하고, 층의 마지막 항목을 끝내면 그 층 마지막 실화면 점검(common-brief 15절)을 한 뒤 다음 층으로 간다.
2. #444는 U1-B1을 가장 먼저 고친다. 그 전에는 이 층 auth.real.spec.ts의 운영자 시험이 실패한다(common-brief 10절 "이미 실패하는 시험"). #446, #448, #450, #451에서는 그 시험을 빼고 돌린다.
3. 층 안에서는 action이 judge인 항목을 모두 판정한 뒤 고친다. 판정도 수정도 high, medium, low 순이다. findings.json의 items가 이 순서로 놓여 있다(#444 U1-B1만 맨 앞).
4. judge 항목은 group이 같으면 한 원인 묶음으로 판정한다.
5. where의 줄 번호는 넘길 때 head 기준으로 맞췄다. where_basis가 review_time인 30건은 그 head에서 같은 줄을 찾지 못한 것이라 코드를 다시 찾는다.
6. H3-B2, GA-B1, X-050, X-092, X-096은 원문이 가리키는 자료가 패키지에 없다(todo에 적었다). 재현부터 한다.

## 범위를 좁힌 항목

- #448 G-092, C448-002, G-169(감사 행 증가): 판정은 한다. 고치려면 감사 대상(어떤 요청을 남길지)이나 보존 방식(audit_log 트리거, 파티션, 테이블 분리)을 바꿔야 하면 needs-decision(Q3)이다. Q3에 걸린 주제라 대화창에서 정하지 않는다.
- #448 GA-B1: 접두 판정(shouldNotFilter, JsonRequestRejectedHandler)만 고치고 isIngestion은 그대로 둔다(U6-B3, Q3).
  - fix_plan의 검증된 diff는 #450의 REQUEST_ID_ATTRIBUTE 문맥이라 bc7e538 기준으로 다시 만든다.
  - 고치면 인코딩한 수집 경로(POST /%761/events)가 새로 감사 행을 남긴다. 그 동작을 단정하는 시험을 더하고, behavior_change true와 report notes의 "U6-B3(Q3) 결정 때 다시 볼 동작"으로 적는다.
- #444 G-144: #450 미청구 통보 발행부(UnbilledUsageReportService)만 고친다.
  - #444 차례에는 남았는지만 보고 open(left "#450 차례에 고친다")으로 둔다. #450 차례에 feat/event-ingestion에 커밋하고, report 항목은 layer 444, branch feat/event-ingestion으로 적는다.
  - #444 WebhookEventRepository의 insert 조건(C444-001, Q9)을 바꿔야 끝나면 blocked(`do-not-touch C444-001`)다.
- #454 U12-B1: Caddyfile 8081 사이트에서 프론트가 아닌 연결의 헤더를 덮어쓰는 것과 그에 맞춘 deploy/README.md, deploy/tests/test_logging.py까지다. 프론트 네트워크를 옮기거나 compose.prod.yml의 proxy 네트워크와 신뢰 기본값을 바꾸지 않는다(Ck1-454-03, Q5).
- #454 X-088: next.config.ts의 프레이밍 차단, nosniff, poweredByHeader: false까지다. HSTS는 한 번 내보내면 브라우저가 max-age 동안 HTTPS를 강제해 되돌릴 수 없으니 더하지 않는다. 판정이 맞으면 HSTS는 new_findings에 needs-decision으로 따로 둔다.
- #446 Ck3-446-10(V36), G-174(V39, V40, V45): V36, V45는 고치지 않는다.
  - Ck3-446-10을 V36을 바꿔야만 고칠 수 있으면 blocked(`do-not-touch Ck3-446-01`), 새 마이그레이션이 있어야 하면 blocked(`needs-new-migration`)다.
  - G-174는 V39, V40만 고치고, V45(#450부터)의 organization_environment_* 이름은 propagation_note에 적는다.
- #455: 앞선 판정은 f891345(04:00 백스톱) 전 sha에서 했고, 줄은 0fe7d2a에서 다시 맞췄다.
  - 고친다: C455-002, C455-003(fix_plan (1), (2)만. getSchedule은 f891345가 지웠다), Nk4-002, Nk4-007, Nk4-008.
  - 다시 판정하고 맞으면 고친다: Nk4-003, Nk4-004.
  - 판정하고 맞으면 needs-decision(Q14): Nk4-001, Nk4-005, G-162. Nk4-005는 partial의 남은 것이 0fe7d2a에 없으면 already-fixed(f891345)다.
  - G-163: 판정하고 맞아도 needs-decision이다. 더할 AWS Step Functions 조회를 로컬에서는 `bash -n`으로만 확인할 수 있다. question은 그 항목 todo에 있다.
  - C455-005: 판정만 한다. 맞아도 고치지 않고 needs-decision으로 두고 question은 'rds-auto-stop 테스트(workflow.test.mjs, contracts.tftest.hcl)를 돌리는 job을 CI에 더할지'로 쓴다. CI job 추가는 팀 합의 대상이라 G-187(#442)과 함께 정한다. ci.yml과 .github/는 건드리지 않는다.

## 층을 건너 걸리는 항목

어느 경우든 그 항목의 층 head 기준으로만 고친다. 아래층에서 고친 것이 위층 브랜치에 아직 없어도 위층에서 다시 고치지 않고, 위층 반영과 충돌은 박성종 님이 푼다.

- #443과 #451의 공통 화면: #451 X-071의 사이드바 하단 링크 이름은 #443 D-021, D-220에서, #451 D-166의 `.workspace-actions` 정렬 기본값은 #443 X-076에서 고친다. X-071, D-166은 #451 몫만 고쳐 fixed로 두고 #443 부분은 propagation_note에 적는다. #451 head에는 위층 반영 전까지 #443 수정이 없으니 화면은 #451 몫만 본다. #451 D-001(`.resource-advanced`)은 쓰는 화면이 #451부터라 #451에서 고친다.
- #443 X-090과 #446 D-109, D-120, X-091: 같은 BillableMetricFormDialog다.
- #444 C444-008과 #451 X-050, X-093: 같은 workspace-actions.ts다.
- #446과 #451의 요금, 청구 화면: PricingWorkspace(#446 D-040 묶음, X-018, X-052, X-077, D-230, C446-009와 #451 D-121), 목록 표(#446 X-036, D-115, D-142와 #451 D-118, D-156, D-143), InvoiceFinalizeDialog(#446 X-067과 #451 C451-002, D-125)는 같은 파일을 두 층에서 고친다.
- #446 C446-001: #450이 같은 ApiClient 메서드를 더해 위층 반영 때 충돌한다. 위층 내용을 택하면 된다고 propagation_note에 적는다.
- #448 RequestAuditFilter(GA-B1, Nk1-002, C448-002, G-092, G-169): #459(수집 요청 감사 제외, 머지 4f8e59f)는 bc7e538에 있다. 대시보드 파일은 #448 G-159와 #450 G-165가 같이 본다.
- #454 compose.prod.yml 신뢰 프록시(high C454-001, C454-004, G-091, G-156과 U12-B1): #461(머지 9089702, 21feb94)은 dac784b에 있다. 그 뒤에도 남는지로 판정하고, 21feb94로 풀렸으면 already-fixed다. Caddyfile의 Ck1-454-01과 RequestLogFilter의 G-101은 같은 가림 규칙을 본다.

양성지 님 층과 걸리는 것은 아래다. 같은 파일을 양성지 님이 고치는 항목은 related에 적었다.

- #446 C446-003, C446-013: #447에서 맞출 내용은 propagation_note에만 적는다.
- #446 G-110: #449 V44와 함께 고쳐야 한다. 판정이 맞으면 blocked(`not-owner #449`)로 둔다.
- #444 U1-B1: #444의 auth.real.spec.ts만 고친다. #453부터는 이미 `/admin$`를 기대한다.
- #444 WebhookDeliveryService(Ck2-003, Ck2-007, G-140): #453 G-133과 같은 파일이다. #444에 들어 있는 b1983b3, a886852는 되돌리지 않는다.

## 건드리지 않을 id

결정 대기이거나 조율 담당 몫이다(findings.json의 `coordinator_owned_on_these_layers`, 질문 원문은 `decisions_pending`). 고치다 Q 번호가 붙은 id의 줄이나 동작을 바꿔야 하면 그 항목을 needs-decision으로 두고 question에 그 Q 번호를 적는다. Q 번호가 없는 id나 `deploy/deploy.sh`를 바꿔야 하면 blocked(`do-not-touch <id>`, deploy.sh면 `do-not-touch deploy/deploy.sh`)로 둔다.

| 층 | id |
| --- | --- |
| #443 | C443-003(+7), G-088 (소개 화면 문구, Q8) |
| #444 | C444-000, G-136, G-167 (마이그레이션 번호 순서). C444-001 (발송 일시 중지, Q9) |
| #446 | H3-B1(+3), Ck3-446-01 (R__seed 롤백, Q5). C446-007(+2) (인보이스 번호, Q10) |
| #448 | C448-003, U17-B1 (감사 실패 처리, Q1, Q2). U6-B1, U6-B2(+GA-S2), U6-B3 (수집 요청 감사 기준, Q3) |
| #450 | C450-000(+2), U8-B1(+GB-B1) (블루그린 중 수집, Q4). G-161 (배포 준비 한도). C450-005 (단건 시각 형식, Q12). Ck3-450-05, D-222, X-064, U17-B2 (수집 요청 감사 기준, Q3) |
| #454 | Ck1-454-03 (proxy 네트워크 롤백, Q5) |
| #455 | C455-000(+5) (04:00 백스톱, Q14) |

- 같은 파일에서 이 id 곁의 항목을 고칠 때가 있다. #446 H3-B2는 R__seed.sql에 `percentile = NULL`만 더한다. #455에서 고치는 항목은 04:00 백스톱 동작(f891345)과 README의 백스톱, 이행 순서 문장을 바꾸지 않는다.
- Q3(수집 요청 감사 기준)와 Q4(U8-B1의 main 선행 PR)에서 문인호 님과 할 협의는 박성종 님이 이 작업과 따로 한다. 세션은 대화창에서 묻거나 정하지 않는다.

## 끝 상태

- findings.json의 372개 id 모두 report.json에 끝 상태가 있고 `node .stack456-team/tools/check-report.mjs T-inho`가 통과한다.
- 고친 커밋은 맡은 8개 브랜치에 push돼 있고, 위층으로 옮기는 merge는 없다.
- 커밋을 push한 층마다 마지막으로 push한 sha에서 마지막 실화면 점검(common-brief 15절, 프론트를 바꾸지 않은 층도 실제 E2E)이 통과했고, 결과가 보고 브랜치 `evidence/final-<PR>/`에 있다.
