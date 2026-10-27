# 스택 #456 k2 넘기기

정리 요청을 받아 새 판정과 새 수정을 멈추고 정리했다. 기준 head는 `stack456-report/k2.json`의 `base_heads`다.

## 끝낸 것

- 판정을 마친 id: 42건 (confirmed 37, refuted 3, already-fixed 2). 판정과 근거는 `k2.json`의 `results`에 있다.
- 수정 브랜치: `claude/s456-k2-444` (base `feat/webhook-jobs` 5800476), 마지막 커밋 `a886852`.
  - 58e7078, 6b31a03: C444-003 전송 기록 N+1 제거와 V28 시도 이력 색인
  - ee2147b: C444-007 192.0.0.0/16 과차단
  - 98fa686: C444-005 운영 전용 WebhookClient 경로 테스트
  - b1983b3: C444-006, C444-010, C444-011, C444-012 웹훅 통합 테스트 공백
  - 93de12e: C444-009, G-153 local 프로파일 암호화 키
  - 2eae64c: C444-013 암호화 기동 테스트의 환경 변수 격리
  - 931921f: G-138 성공 이력 없는 작업의 게이지 0 등록 제거
  - a886852: G-137, G-133, G-150, G-143 웹훅 실패 집계, 항목 예외 격리, 경보에서 endpoint_disabled 제외
- `claude/s456-k2-445`, `claude/s456-k2-449`는 만들지 않았다.

## 남은 것

- 판정하지 못한 id: C444-000, C444-002, C445-000, C445-001, C445-002, C445-005, C449-000, C449-002, C449-004, C449-006, G-104, G-112, G-136, G-140, G-157, G-167
  - g-mig 묶음(C444-000, C445-000, C449-000, G-104, G-136, G-167): 19:39 번호 정렬로 해결됐는지만 보면 된다. 두 관점 검증이 끝나기 전에 멈췄다.
  - g445-mode 묶음(C445-001, C445-002, C445-005, C449-002, 보안): test 모드 API 키가 live 결제 자원을 건드리는지. 최상단 #453이 막는지 꼭 본다. 두 관점 검증 중에 멈췄다.
  - C444-002, G-140(웹훅 전역 큐 차단), C449-004, C449-006, G-112(공개 인보이스), G-157(배포 compose 공개 origin, high): 검증을 시작하지 못했다.
- confirmed인데 고치지 않은 것:
  - C444-001 (#444, medium, out-of-scope-frontend): 콘솔의 '발송 일시 중지'가 대기 중 전송을 실패로 끝내고 중지 동안의 이벤트를 영구히 버린다
  - C445-003 (#445, medium, in-scope): 이미 미청구로 확인된 NOT_FOUND_PAYMENT 실패가 7일 뒤 그 인보이스의 수동 결제를 영구히 막는다
  - C445-006 (#445, low, in-scope): 같은 인보이스에 결제 요청이 동시에 오면 진 쪽이 문서의 409 대신 500을 받는다
  - C445-007 (#445, low, in-scope, C445-006에 묶임): 동시 결제 요청 시험이 500을 통과로 보아 인보이스 잠금이 빠지는 회귀를 잡지 못한다
  - C445-008 (#445, low, in-scope): 동시 부분 취소 시험이 500도 통과로 보아 시도 행 잠금이 빠지는 회귀를 잡지 못한다
  - C445-009 (#445, low, in-scope): 같은 고객의 카드 등록 두 건이 동시에 끝나면 500이 나거나 새로 발급한 빌링키가 토스에 남는다
  - C445-010 (#445, low, in-scope): 웹훅 API 설명이 실제로 없는 '일회용 카드 등록 토큰 인증'을 적어 openapi.yaml에도 실렸다
  - C445-011 (#445, low, in-scope): 결제 요청 API의 409 목록에 payment_mode_mismatch가 빠졌다
  - C445-012 (#445, low, in-scope): 시크릿 키에 콜론이나 ISO-8859-1 밖 문자가 있으면 Basic 인증 헤더를 만들다 예외가 나 400 대신 500이 된다
  - C445-013 (#445, low, in-scope): 짝이 없는 완료 취소를 토스 단건 조회 없이 바로 missing_in_toss_payments로 기록한다
  - C445-015 (#445, low, in-scope): exhausted 시험 이름은 '상한에 이르는 실패에서만 한 번'인데 코드는 상한을 넘긴 실패도 매번 센다
  - C445-016 (#445, low, in-scope): '재시도 두 개가 겹쳐도' 시험이 insertRetryAttempt의 재확인 분기에 닿지 않는다
  - C445-017 (#445, low, in-scope): skippedCount, processedCount 단언이 공유 DB에 남은 다른 후보로도 채워져 자기 후보의 집계를 확인하지 못한다
  - C445-018 (#445, low, in-scope): 새로 더한 인보이스 단건 조회 API의 도입사 격리를 시험하지 않았다
  - C449-008 (#449, low, in-scope): 자동 재시도와 고객 재시도 경합 테스트가 NOT_FOUND 실패만 다뤄, 일반 거절 실패에서 공통 잠금 없이 경합해 500이 나는 경로를 놓친다
  - G-097 (#445, medium, in-scope): 정지된 도입사의 카드 등록 링크와 자동 재결제는 계속 동작해 공개 경로마다 정지 처리가 다르다
  - G-098 (#445, medium, in-scope): 카드 등록 링크 발급에 admin 키가 필요해, 예제 서비스가 PG 키와 결제 모드를 바꿀 수 있는 키를 웹 서버에 둔다
  - G-102 (#445, low, in-scope): 고객 카드 등록과 교체가 도입사 감사 기록에 남지 않는다
  - G-142 (#444, low, in-scope, G-152에 묶임): WEBHOOK_ENABLED=false는 발송만 조용히 끄고 경보도 남기지 않으며, 일일 미청구 통보까지 함께 끈다
  - G-144 (#444, low, out-of-scope-other): 구독 엔드포인트가 없어도 webhook_event를 저장하고 삭제를 막아, #450의 일일 통보가 영구히 쌓인다
  - G-151 (#444, low, in-scope): 예약 작업 이름을 여러 PR이 문자열로 따로 적는다
  - G-152 (#444, low, in-scope): backend README에 웹훅 운영 정보가 빠졌다
  - 각 id의 수정 방안은 검증 결과 원문에 있다. 이어받을 때 아래 "자료 위치"의 journal에서 fix_plan을 읽는다.
- 하다 만 수정: 없다. 커밋하지 않은 변경은 남기지 않았다.
- 추가 검토를 못 한 PR:
  - #444: 유지보수성 15건, 운영 6건 후보가 나왔지만 검증 전이다. `k2.json`의 `unverified_review_candidates`(Ck2-001~015)에 원문이 있다.
  - #445: 운영 관점 앞 절반(클라이언트, 설정, 컨트롤러, DTO, 마이그레이션)만 나왔다(Ck2-016, Ck2-017, 검증 전). 유지보수성 전체와 서비스, 저장소, 엔티티 운영 검토는 못 했다.
  - #449: 유지보수성과 운영 검토 모두 못 했다.
- 새 지적 id(Nk2-*)는 검증을 거친 것이 없어 붙이지 않았다. 후보는 Ck2-*로 구분했다.

## 이어받을 때 알아야 할 것

- 판정을 바꿀 수 있는 의심
  - C444-001은 백엔드가 아니라 콘솔 문구(일시 중지) 문제로 판정해 k5, k6로 넘겼다. 백엔드로 일시 중지 의미를 살리기로 정하면 b1983b3의 비활성화 테스트 기대값을 바꿔야 한다.
  - 6b31a03은 아직 main에 없는 V28을 직접 고쳐 체크섬이 바뀐다. 19:39 뒤 V28을 적용한 로컬 볼륨은 다시 만들어야 한다. 원치 않으면 이 커밋만 빼고 색인은 최상단 새 V__로 옮긴다.
  - a886852는 동작이 바뀐다: deliver()의 processedCount가 시도를 기록한 건수가 되고, failedCount는 항목 예외만 센다. meterengine.webhook.delivery에 reason 태그가 생기고 alerting.yml의 webhook-delivery-failed가 endpoint_disabled를 뺀다.
  - 931921f도 동작이 바뀐다: 성공 이력이 없는 작업은 첫 성공 전까지 마지막 성공 시리즈가 없다(main 계약).
  - C444-012의 근본 원인 중 #453 WebhookEnvironmentIntegrationTest가 대기 전송을 남기는 부분은 k2 밖이라 고치지 않았다.
- 다른 묶음과 겹치는 파일
  - frontend/src/app/(console)/integrations 화면 문구: C444-001과 k6 D-045.
  - METERENGINE_PUBLIC_ORIGIN(G-157)은 k5 C449-001과 같은 원인으로 보인다(검증 전).
  - WebhookDeliveryRepository, WebhookIntegrationTest는 #453이 environment_scope_id를 더하며 고친다. 위층 병합 때 충돌을 풀어야 한다.
  - application-local.properties 두 줄은 #445와 글자까지 같아 충돌하지 않는다.
- 돌리지 못한 검사
  - `claude/s456-k2-444` 전체 `./gradlew build`(바뀐 패키지 테스트, 이름 규칙, spotless, checkstyle만 돌림).
  - Grafana에서 바뀐 경보 식 평가, 로컬 bootRun 실제 기동.
  - 프론트 검사(수정 없음).
- 환경: dockerd를 직접 띄우고 postgres:18을 mirror.gcr.io에서 받았다. Maven Central 429 때문에 ~/.gradle/init.d에 Google 미러를 두었다(저장소에는 없음). Testcontainers ryuk는 껐다.

## 자료 위치(이 세션 컨테이너 안, 세션이 끝나면 사라진다)

- 검증 결과 원문: 워크플로 journal(wf_4a1ec601-c8a, wf_282a50f9-5f6, wf_93949afd-47d). 판정과 근거는 이미 `k2.json`에 옮겼다.
