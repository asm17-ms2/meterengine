# T-yang: 양성지 님 (#442 #445 #447 #449 #452 #453)

- 맡은 6개 층의 남은 지적을 판정하고, 맞는 것은 그 층 PR 브랜치에 고쳐 push한다.
- 265항목(묶인 id까지 328개)이다. 판정 128, 수정 137, high 12건이다.
- 아래 "건드리지 않을 id"는 박성종 님(조율 담당) 몫이다. 판정도 수정도 하지 않는다. "범위를 좁힌 항목"은 적힌 범위만 고친다.

## 맡은 브랜치

push는 이 6개와 `claude/s456-team-T-yang-report`에만 한다.

| 층 | 브랜치 | 넘길 때 head | 항목 | 판정 | 수정 | high |
| --- | --- | --- | --- | --- | --- | --- |
| #442 | feat/account-permissions | 72e8718 | 122 | 79 | 43 | 4 |
| #445 | feat/billing-payments | 54bb1d5 | 48 | 14 | 34 | 2 |
| #447 | feat/resource-archive | 8babc3d | 35 | 20 | 15 | 3 |
| #449 | feat/customer-invoices | 2fe98d1 | 17 | 7 | 10 | 2 |
| #452 | feat/llm-service-demo | 2aa2be2 | 17 | 3 | 14 | 0 |
| #453 | feat/environment-isolation | f014e03 | 26 | 5 | 21 | 1 |

## 작업 순서

1. 층은 아래부터 #442, #445, #447, #449, #452, #453 순으로 하나씩 끝낸다. 고친 커밋은 검사를 통과하는 대로 push하고, 층의 마지막 항목을 끝내면 그 층 마지막 실화면 점검(common-brief 15절)을 한 뒤 다음 층으로 간다.
2. 층 안에서는 action이 judge인 항목을 모두 판정한 뒤 고친다. 판정도 수정도 high, medium, low 순이다.
3. group은 판정을 함께 하는 단위이고 끝 상태는 id마다 따로다(common-brief 6절 8번). "#442 유지보수"(8건)와 "#442 배포와 문서"(4건)는 원인이 서로 달라 id마다 판정한다. "g445-mode"는 #445 차례에 판정하고, 그중 #449 C449-002는 #449 차례에 고친다.
4. 고칠 때는 partial이 있는 항목을 먼저 한다: #447 C447-002, #453 G-103, C453-004, C453-003, C444-012, G-133.
   - C447-002와 G-103의 partial.diff는 넘길 때 head(8babc3d, f014e03)에 `git apply --check`로 붙는 것을 확인했다.
   - C453-003, C453-004의 `diff_same_as: G-103`은 G-103의 diff 하나에 두 항목의 수정이 들어 있다는 뜻이다.
   - C444-012, G-133은 diff 없이 partial.left만 있다.

```bash
I=G-103   # 또는 C447-002
jq -r --arg id $I '.findings[] | select(.id == $id).partial.diff' .stack456-team/T-yang/findings.json > .s456-work/$I.diff
git apply --check .s456-work/$I.diff && git apply .s456-work/$I.diff
```

5. where의 줄 번호는 넘길 때 head 기준으로 맞췄다. where_basis가 review_time인 12건은 그 head에서 같은 줄을 찾지 못한 것이라 코드를 다시 찾는다.

## 범위를 좁힌 항목

- #442 G-039(+G-064), C442-010, G-007, G-030, G-096, G-160과 #452 C452-003의 compose 정리 부분(콘솔 API 키와 도입사 변수)
  - 고치는 것: 프론트와 백엔드가 그 키와 변수를 요구하지 않게 하는 코드와 시험, `deploy/compose.prod.yml`에서 그 변수 줄과 필수 표시(`:?`) 지우기, 그에 맞는 README 줄.
  - 하지 않는 것: SSM 파라미터(`frontend-api-key` 등) 삭제, 이미 만든 콘솔 키 폐기 절차, `measurements/` 아래 terraform 변경. main으로 되돌리는 배포는 main compose가 그 파라미터와 키를 요구해서 롤백 범위(Q5)에 걸린다. 이 부분만 남으면 needs-decision(Q5)으로 둔다.
- #442 G-187: 판정만 한다. 맞으면 needs-decision으로 두고 question은 "CI에 프론트 단위 시험, 계약 명세, demo, deploy, terraform 잡을 더할지(팀 전체에 거는 제약이라 Discussion 합의가 필요하다)"로 쓴다.
- #442 C442-005(+C449-007, C451-005, C453-013): `frontend/package.json`에 test:unit 한 줄까지만 더한다. ci.yml은 건드리지 않는다. 같은 줄을 #453 G-189가 더하니 글자를 맞추고, #449, #453에서 따로 고치지 않는다.
- #453 G-133: 웹훅과 결제 지표, 운영 카운트에 environment 태그나 환경 조건을 붙이는 백엔드 코드와 시험까지 한다. `deploy/grafana/`의 경보(alerting.yml)와 대시보드는 바꾸지 않고, 그것이 남으면 needs-decision("test 환경 실패를 운영 경보와 대시보드에서 뺄지")으로 둔다.
- #442 G-019(V26), #453 G-135(V48), #447 Ck3-447-04(V43): 판정은 한다. 고치려면 common-brief 8절의 마이그레이션 파일을 바꿔야 하므로 맞으면 blocked(`do-not-touch`)다. #447 Ck3-447-07(V42)은 막지 않는다.
- #453 U10-B1, U10-B2와 #449 G-157, #452 C452-003: 양성지 님 커밋(f58e1b5, 7ce9d36)의 후속이다. 그 의도를 바꿔야 하면 양성지 님이 대화창에서 정한다.

## 층을 건너 걸리는 항목

- #442 C442-004: 정점에서는 #451 프론트 검사와 #453 백엔드 403이 막는다. common-brief 7절 4번의 fixed-above인지 먼저 본다. 같은 `client.ts` tenantRequest를 박성종 님이 G-062로 고칠 수 있으니 고치기 전에 그 파일의 커밋을 본다(common-brief 4절).
- #442 G-065: 같은 `transport.ts`를 박성종 님이 H2-S1(#453)로 고칠 수 있다. C442-004와 같이 본다.
- #442 G-042: 더할 검사가 #444~#450 head의 workspace-actions.ts에서 실패한다. #442만 고치고 위층 실패는 propagation_note에 적는다.
- #445 D-149, D-217, D-255, X-051: #442 공용 부품(WorkspaceView 상태 라벨, ActionPanel, WorkspaceError, lib/api 타입)을 같이 바꿔야 할 수 있다. 바꿀 코드가 #445부터 쓰이면 #445에서 고친다.
- #445 C445-001, C445-002, C445-005와 #449 C449-002: test 모드 키가 live 결제 자원에 닿는 문제다. 정점 #453이 막는지 먼저 본다.
- #445 C445-004: #445에서 고객 필터와 정렬을 끄면 #450(문인호 님 층)에서 이벤트 정렬, 검색, 기간을 다시 켜야 한다. #450은 고치지 않고 propagation_note에 적는다.
- #447 C447-002, U19-B1, Nk3-006, Ck3-447-02: 같은 보관 효력 규칙(PriceRate, PricePlan, ChargeService)과 VersionedPricingIntegrationTest를 다룬다. C447-002의 diff를 적용한 뒤 함께 본다.
- #447 C447-009: 고칠 readListQuery 호출이 위층 화면에도 있다. #447에서 고치고 위층 화면은 propagation_note에 적는다.
- #447 Nk3-004, Ck3-447-09, Ck3-447-12와 #453 G-103, C453-004, C453-003, U10-B1, U10-B2: 같은 토스 키 조회 코드(TossPaymentsCredentialService, 결제별 키 버전 선택)다. #453의 diff는 #453 head에 적용하고, #447에서 같은 파일을 고쳤으면 propagation_note에 적는다.
- #449 G-157과 #452 C452-003: 같은 METERENGINE_PUBLIC_ORIGIN 전달 문제다. 7ce9d36으로 풀렸는지 먼저 본다.
- #452와 #453: C452-002는 #453의 BridgeConfig.scope 변경 뒤 모든 기존 사용자에게 드러난다. C452-007은 #453 C453-006과 같은 demo/web 웹훅 handler를 고친다(브라우저 요청 차단과 본문 상한을 대상 선택 앞에 둔다). C452-015는 #453에 연결이 있어 fixed-above인지 먼저 본다.
- #453 C444-012, G-133: 원래 #444(문인호 님 층) 지적이다. #444에서 끝낸 b1983b3, a886852는 손대지 않고 partial.left만 #453에서 한다.

## 건드리지 않을 id

결정 대기이거나 조율 담당 몫이다. 아래 "하지 않는 변경"을 해야 고쳐지는 항목은 blocked(`do-not-touch <id>`)로 둔다. 같은 파일의 다른 수정은 된다. Q 번호의 질문 원문은 findings.json `decisions_pending`에 있다. common-brief 8절의 마이그레이션 파일 목록도 함께 지킨다.

| 층 | id | 파일과 대상 | 하지 않는 변경 | 까닭 |
| --- | --- | --- | --- | --- |
| #442 | G-062 | `frontend/src/lib/api/client.ts` tenantRequest의 요청 헤더 | 새 프론트가 옛 백엔드와 맞도록 헤더나 요청을 바꾸는 것 | 조율(deploy.sh 전환 순서) |
| #442 | H2-B1, H2-B2, G-033 | `deploy/deploy.sh` 실패 정리, `V27__add_member_and_invitation_scopes` | deploy.sh의 모든 변경, V27 내용 | 조율(deploy.sh 실패 처리) |
| #442 | G-031, M3-B2 | `V26__organization_people_and_invitations`의 보관, 조회 전용 사용자 | 롤백 뒤 권한 회복을 막는 변경, V26 내용 | Q5 |
| #442 | G-032(+G-003), M3-B1 | V26의 role, scopes 열 기본값 | 열 기본값과 소속 보정 | Q4 |
| #442 | H1-B1(+H1-B2, M4-B2) | `V47__add_conditional_resource_updates`, JDBC 드라이버 설정 | V47 내용, `autosave` 설정 추가(지금 코드에 없다) | Q4 |
| #442 | M1-S2 | `.github/workflows/cd.yml` concurrency | cd.yml의 모든 변경 | Q4 |
| #442 | G-049 | ActionPanel, CreateOperatorButton, AttachOrganizationButton, ReviewAction의 모달 폼 | 네 컴포넌트를 합치거나 공용 모달로 빼는 리팩터. 사본마다의 버그 수정(X-000, X-004 등)은 된다 | Q7 |
| #442 | G-055(+G-070) | `frontend/src/proxy.ts`와 서버 코드의 쿠키, 내부 헤더 이름 | 이름을 공용 상수로 모으는 변경. proxy.ts의 다른 수정(X-015 등)은 된다 | Q7 |
| #442 | G-184 | `frontend/src/lib/api/invoices.ts` 인보이스 확정 요청 | 확정 요청의 시간 제한 값과 그 안내 | Q6 |
| #442 | G-047 | `frontend/src/lib/api/client.ts` serverFetch, serverSend의 `_baseUrl`, `_cacheTag` 인자 | 쓰지 않는 인자를 지우거나 호출부를 바꾸는 변경 | Q7 |
| #445 | C445-000, G-104 | `V29__add_payment_attempt_invoice_fk` | 번호 바꾸기 | 조율(번호 순서) |
| #445 | M1-B1 | `V30__add_payment_attempt_mode`의 mode 기본값 | V30 내용 | Q4 |
| #447 | Ck3-447-01, M2-S1 | `V43__archive_business_resources`의 단가 유일 인덱스, 카드와 토스 키 보관 | V43 내용, R__seed.sql | Q5, Q4 |
| #447 | G-109 | `CustomerService.java` 보관 고객의 결제 처리 | 보관 고객의 기존 인보이스 결제 재시도를 막거나 허용하는 변경 | Q11 |
| #449 | C449-000 | `V44__allow_customer_payment_retry` | 번호 바꾸기 | 조율(번호 순서) |
| #453 | H2-S1 | `frontend/src/lib/api/transport.ts` | 실패한 배포가 남긴 옛 백엔드 조합에 맞추는 변경 | 조율(deploy.sh 실패 처리) |
| #453 | H1-S2 | `WorkspaceQueryRepository.java`의 `*` 투영 | 옛 색 호환을 위한 투영 변경 | Q4 |
| #453 | M4-B3, M4-S2 | `V48__isolate_billing_payment_and_delivery_environments`의 잠금, 같은 환경 FK | V48 내용 | Q4 |
| #453 | G-131(+D-041) | `frontend/src/proxy.ts` 새 도입사 기본 환경, legacy 키 발급 | 새 도입사 기본 환경과 legacy의 test, live 키 발급 정책. X-003을 고치다 이것을 바꿔야 하면 needs-decision(Q13) | Q13 |

## 끝 상태

- findings.json의 328개 id 모두 report.json에 끝 상태가 있고 `node .stack456-team/tools/check-report.mjs T-yang`이 통과한다.
- 고친 커밋은 맡은 6개 브랜치에 push돼 있고, 위층으로 옮기는 merge는 없다.
- 커밋을 push한 층마다 마지막으로 push한 sha에서 마지막 실화면 점검(common-brief 15절, 프론트를 바꾸지 않은 층도 실제 E2E)이 통과했고, 결과가 보고 브랜치 `evidence/final-<PR>/`에 있다.
