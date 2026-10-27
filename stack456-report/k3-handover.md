# k3 넘기기

넘기기 지시로 진행 중이던 판정, 추가 검토, 수정을 멈추고 정리했다. 판정 결과와 근거는 `k3.json`, 요약은 `k3.md`에 있다.

## 끝낸 것

- 기존 지적 75건 중 63건 판정: confirmed 56, already-fixed 6, uncertain 1, refuted 0.
- 추가 검토 새 지적 12건 검증: confirmed 10(Nk3-001~006, 008~011), refuted 2(Nk3-007, Nk3-012).
- push한 수정 브랜치와 마지막 커밋:
  - `claude/s456-k3-446`: `0cab3a4` test: 지난 월 요금제 등록과 삭제 거절을 검증한다 (C446-004). 작성 에이전트가 보고 전에 멈춰, 넘기기 때 검사를 다시 돌려 통과를 확인하고 push했다. 커밋 리뷰는 하지 않았다.
  - `claude/s456-k3-447`: `02bb1af` fix: 주입처 없이 항상 승인하는 가짜 결제 게이트웨이를 다시 지운다 (C447-013, G-115). 커밋 리뷰 통과.
  - `claude/s456-k3-451`: `96875c6` fix: 대시보드가 보관한 고객과 미터와 결제 키를 세지 않게 한다 (C451-001, G-108, G-125). behavior_change. 커밋 리뷰는 중단돼 끝나지 않았다.
  - `claude/s456-k3-450`: 커밋 없음, push하지 않았다.

## 남은 것

### 판정하지 못한 기존 지적 12건

- 이벤트 검색: C450-006, C450-007, C450-012, G-119, G-132, G-170
- 미청구 알림과 진단: C450-009, G-146, G-116, G-117, G-124, G-176

### 하다 만 수정

- C447-002 (`claude/s456-k3-447`): 수정 에이전트가 PriceRate.stops와 PricePlan.stops의 마지막 조건을 `effectiveUntil.compareTo(month) > 0`으로 바꾸고 VersionedPricingIntegrationTest에 검사 두 개를 더하던 중 멈췄다. 테스트를 돌렸는지 확인하지 못해 커밋하지 않았다. 워크트리에 남은 변경은 아래와 같다.

```diff
diff --git a/backend/src/main/java/com/meterengine/pricing/entity/PricePlan.java b/backend/src/main/java/com/meterengine/pricing/entity/PricePlan.java
index 6709ec7..d721d6f 100644
--- a/backend/src/main/java/com/meterengine/pricing/entity/PricePlan.java
+++ b/backend/src/main/java/com/meterengine/pricing/entity/PricePlan.java
@@ -66,7 +66,7 @@ public class PricePlan implements Persistable<UUID> {
     return archivedFrom != null
         && archivedFrom.compareTo(month) <= 0
         && effectiveFrom.compareTo(archivedFrom) < 0
-        && (effectiveUntil == null || effectiveUntil.compareTo(archivedFrom) > 0);
+        && (effectiveUntil == null || effectiveUntil.compareTo(month) > 0);
   }
 
   @Transient private boolean isNew = true;
diff --git a/backend/src/main/java/com/meterengine/pricing/entity/PriceRate.java b/backend/src/main/java/com/meterengine/pricing/entity/PriceRate.java
index 242b703..b8c6e14 100644
--- a/backend/src/main/java/com/meterengine/pricing/entity/PriceRate.java
+++ b/backend/src/main/java/com/meterengine/pricing/entity/PriceRate.java
@@ -79,7 +79,7 @@ public class PriceRate implements Persistable<UUID> {
     return archivedFrom != null
         && archivedFrom.compareTo(month) <= 0
         && effectiveFrom.compareTo(archivedFrom) < 0
-        && (effectiveUntil == null || effectiveUntil.compareTo(archivedFrom) > 0);
+        && (effectiveUntil == null || effectiveUntil.compareTo(month) > 0);
   }
 
   @Transient private boolean isNew = true;
diff --git a/backend/src/test/java/com/meterengine/pricing/VersionedPricingIntegrationTest.java b/backend/src/test/java/com/meterengine/pricing/VersionedPricingIntegrationTest.java
index 34e4d7a..3de0d29 100644
--- a/backend/src/test/java/com/meterengine/pricing/VersionedPricingIntegrationTest.java
+++ b/backend/src/test/java/com/meterengine/pricing/VersionedPricingIntegrationTest.java
@@ -185,6 +185,41 @@ class VersionedPricingIntegrationTest {
     assertThat(amount(month.plusMonths(1), "100")).isEqualTo(1000);
   }
 
+  @Test
+  void 기한부_단가_보관은_원래_종료월부터_구단가로_돌아간다() {
+    legacyRate();
+    var promotion = rate(null, month, month.plusMonths(2), "per_unit", new BigDecimal("20"), null);
+    priceRateService.delete(organizationId, "tokens", promotion.id());
+    assertThat(amount(month, "100")).isEqualTo(2000);
+    assertThat(amount(month.plusMonths(1), "100")).isZero();
+    assertThat(amount(month.plusMonths(2), "100")).isEqualTo(1000);
+    assertThat(
+            billableMetricPriceService
+                .list(organizationId, month.plusMonths(2), null)
+                .billableMetricPrices()
+                .getFirst()
+                .priceRates()
+                .getFirst()
+                .unitPrice())
+        .isEqualByComparingTo("10");
+  }
+
+  @Test
+  void 기한부_요금제_보관은_원래_종료월부터_구요금제로_돌아간다() {
+    legacyRate();
+    jdbcTemplate.update(
+        "INSERT INTO price_plan (organization_id,effective_from,base_fee,minimum_amount) VALUES (?, ?, 100, 0)",
+        organizationId,
+        month.minusMonths(1).toString());
+    var promotion =
+        pricePlanService.create(
+            organizationId, new CreatePricePlanRequest(null, month, month.plusMonths(2), 200L, 0L));
+    pricePlanService.delete(organizationId, promotion.id());
+    assertThat(amount(month, "100")).isEqualTo(1200);
+    assertThat(amount(month.plusMonths(1), "100")).isEqualTo(1000);
+    assertThat(amount(month.plusMonths(2), "100")).isEqualTo(1100);
+  }
+
   @Test
   void 누진_구간은_각_라인에서_절사하고_경계를_겹치지_않는다() {
     rate(null, month, null, "graduated", null, tiers());
```

### 수정 대기 (판정은 confirmed, 아직 손대지 않음)

수정 계획은 `k3.json`의 각 항목 reason과 notes에 있다. 아래 순서는 수정 워크플로에 넣었던 순서다.

- #446: C446-006, C446-011, C446-012, C446-005, C446-013, C446-003, G-107, G-174, C446-001, C446-008
- #447: C447-002(위 하다 만 수정), C447-005, C447-006, C447-007, C447-010, C447-003, C447-004, G-105, C447-008, C447-011, G-147
- #450: C450-000(G-129, G-168), G-161, C450-002, C450-003, C450-005, G-127, G-165, C450-010
- #451: C451-004, C451-009, C451-006, G-122, C451-003(G-120), G-118, G-121, G-126, G-123, C451-010, G-111(G-171)
- 새 지적 confirmed 10건(Nk3-001~006, 008~011)도 수정 전이다.

### 추가 검토에서 검증하지 못한 새 지적 후보 42건

검토 에이전트가 낸 원문 후보이고 판정 전이다. 다시 올리기 전에 공통 지침 2번대로 검증해야 한다. #450은 유지보수성 관점만 끝났고 운영 관점 검토와 중복 정리를 하지 못했다.

- #446 (검증 전) `backend/src/main/resources/db/migration/V36__version_price_rates.sql:1` [medium, rollout-safety] #446 배포 뒤 이전 SHA로 롤백하면 옛 R__seed가 다시 돌다가 ON CONFLICT 대상이 없어 백엔드가 뜨지 않는다
- #446 (검증 전) `backend/src/main/java/com/meterengine/pricing/service/PricePlanService.java:66` [low, api-consistency] 같은 effective_until 규칙을 어겼을 때 단가는 invalid_price_rate, 요금제는 validation_error를 돌려준다
- #446 (검증 전) `backend/src/main/java/com/meterengine/invoice/service/InvoiceEmailService.java:70` [low, type-design] line_type과 model 값이 enum이나 상수 없이 문자열로 흩어져 있고, openapi.yaml에도 값 목록이 없다
- #446 (검증 전) `backend/src/main/java/com/meterengine/pricing/controller/PriceRateController.java:88` [low, openapi-drift] 조합별 단가 DELETE의 동작은 바뀌었는데, API 설명은 여전히 조합의 단가를 지운다고 말한다
- #446 (검증 전) `backend/src/main/java/com/meterengine/pricing/controller/PricePlanController.java:48` [low, api-design] 새 가격 조회 API는 customer_id와 month를 생략했을 때의 의미가 기존 API와 다른데 문서에 없고, 4xx 응답 문서도 빠졌다
- #446 (검증 전) `backend/src/main/java/com/meterengine/pricing/dto/CreatePriceRateRequest.java:38` [low, api-consistency] 요금 모델 검증이 DTO와 서비스에 두 번 있어서, HTTP 오류는 요청에 없는 필드 validPricing으로 나가고 서비스 쪽 검사는 실행되지 않는다
- #446 (검증 전) `backend/src/main/java/com/meterengine/pricing/service/PriceRateService.java:130` [low, data-contract] 단가 감사 기록의 target_id가 미터 code에서 버전 UUID로 바뀌어, 같은 target_type 안에 두 가지 의미가 섞인다
- #446 (검증 전) `backend/src/main/java/com/meterengine/invoice/dto/DraftInvoiceResponse.java:61` [low, dead-code] 운영 코드가 부르지 않는 보조 생성자들이 line_type, 분모, 시작 월 기본값을 조용히 채운다
- #446 (검증 전) `backend/src/main/java/com/meterengine/organization/service/OrganizationService.java:69` [low, naming] 공급자 조회와 수정을 OrganizationService의 find, update로 넣어서 이름 규칙에 어긋나고 기존 update와 뜻이 겹친다
- #446 (검증 전) `backend/src/main/resources/db/migration/V36__version_price_rates.sql:43` [low, performance] V36이 price_rate_pk를 지우고 부분 인덱스만 남겨 단가 조회가 모든 도입사의 단가 행을 순차 스캔한다
- #447 (검증 전) `backend/src/main/resources/db/migration/V43__archive_business_resources.sql:22` [medium, rollout] V43이 단가 유일 인덱스 술어를 바꿔 #446 SHA로 롤백하면 #446의 R__seed가 다시 돌다 실패해 백엔드가 뜨지 않는다
- #447 (검증 전) `backend/src/main/java/com/meterengine/pricing/entity/PriceRate.java:72` [low, duplication] 보관 효력 규칙이 단가, 요금제, 계산 경로마다 복사돼 있어 한쪽만 고치면 청구와 가격 목록이 갈린다
- #447 (검증 전) `backend/src/main/java/com/meterengine/payment/repository/PaymentArchiveRepository.java:58` [low, layering] 저장소가 이력 행을 응답 DTO로 직접 만들어 목록 문서의 KST 시각과 정렬 약속을 어기고, 교체 이력을 보관으로 보인다
- #447 (검증 전) `backend/src/main/resources/db/migration/V43__archive_business_resources.sql:3` [low, data-integrity] 새 월 열에 #446이 둔 YYYY-MM 형식 제약이 없고, 보관 시각과 보관 월이 함께 채워져야 한다는 제약도 없다
- #447 (검증 전) `backend/src/main/java/com/meterengine/metric/entity/BillableMetric.java:65` [low, naming] 같은 '보관 효력 시작 월'을 미터는 inactive_from, 단가와 요금제는 archived_from으로 부르고, 미터 쪽 값은 판정에 쓰이지 않는다
- #447 (검증 전) `backend/src/main/java/com/meterengine/customer/repository/CustomerRepository.java:39` [low, dead-code] 새로 더한 조회와 삭제 가드의 잔재가 호출처 없이 남았고, 실제로 호출되는 목록 메서드에는 읽기 트랜잭션이 빠졌다
- #447 (검증 전) `backend/src/main/resources/db/migration/V42__add_customer_external_id.sql:1` [low, scope] #450의 외부 고객 ID 스키마와 응답 필드가 값을 쓰는 경로 없이 #447에 들어 있다
- #447 (검증 전) `backend/src/main/java/com/meterengine/customer/service/CustomerService.java:65` [low, naming] 되돌릴 수 있는 보관을 서비스에서 delete로 불러, 이름 규칙과 #442의 revoke 선례를 어긴다
- #447 (검증 전) `backend/src/main/java/com/meterengine/payment/service/PaymentCancellationService.java:259` [low, duplication] 결제별 토스 키 버전 선택이 네 곳에 복사돼 있어, 규칙을 바꾸면 확인과 취소가 다른 키를 쓸 수 있다
- #447 (검증 전) `backend/src/main/java/com/meterengine/customer/controller/CustomerController.java:59` [low, api-design] 새 status 파라미터들이 openapi에 허용값 없이 string으로만 나간다
- #447 (검증 전) `backend/README.md:252` [low, docs] README 구조 절이 없어진 삭제 가드 의존과 삭제 동작을 그대로 적고 있다
- #447 (검증 전) `backend/src/main/java/com/meterengine/payment/service/TossPaymentsCredentialService.java:90` [low, rollout] 이력 표가 빈 채로 생겨 #447 배포 전에 토스 키를 바꾼 도입사는 그 전 결제를 취소할 수 없고 사유가 키 없음으로 남는다
- #447 (검증 전) `backend/src/main/java/com/meterengine/payment/repository/PaymentArchiveRepository.java:27` [low, locking] organization 행을 FOR UPDATE로 잠가 카드 등록 완료, 키 저장, 카드 보관 동안 그 도입사의 이벤트 수집이 멈춘다
- #447 (검증 전) `backend/src/main/java/com/meterengine/metric/service/BillableMetricService.java:114` [low, locking] 미터 수정이 도입사 행 잠금을 쥔 채 색인 없는 event 전체 스캔을 돌려 그동안 카드 등록 완료와 가격 변경이 멈춘다
- #451 (검증 전) `backend/src/main/java/com/meterengine/idempotency/service/IdempotencyService.java:90` [medium, data-retention] idempotent_request가 고객 등록 응답(이름, billing_email)을 만료 없이 영구히 남기고, 같은 키로 다시 보내면 옛 응답을 계속 돌려준다
- #451 (검증 전) `backend/src/main/java/com/meterengine/customer/controller/CustomerController.java:174` [low, api-design] PATCH /v1/customers/{id}와 /v1/billable-metrics/{code}는 If-Match만 필수인 PUT의 복제이고, PATCH인데 전체 교체라서 본문에 없는 billing_email을 지운다
- #451 (검증 전) `backend/src/main/java/com/meterengine/query/repository/WorkspaceQueryRepository.java:306` [low, type-design] 조회 자원을 문자열로 고르는 분기가 네 곳에 있고 쓰는 키도 서로 달라서, 월 목록은 모르는 값이 와도 이벤트 월을 조용히 돌려준다
- #451 (검증 전) `backend/src/main/java/com/meterengine/query/service/SearchCsvService.java:69` [low, duplication] CSV 수식 이스케이프, 인용, BOM, 응답 헤더가 EventCsvService와 SearchCsvService에 두 벌로 들어왔다
- #451 (검증 전) `backend/src/main/java/com/meterengine/customer/dto/CustomerResponse.java:19` [low, dead-code] version 자리에 0을 넣는 CustomerResponse, BillableMetricResponse 보조 생성자가 쓰는 곳 없이 추가됐다
- #450 (중복 정리와 검증 전) `backend/src/main/java/com/meterengine/notification/service/UnbilledUsageReportService.java:48` [medium, divergence] 미청구 일일 통보가 같은 요금 계산에서 음수 사용량만 꺼내고 단가 없는 사용량은 버려, 이벤트 진단과 미청구 판정이 갈린다
- #450 (중복 정리와 검증 전) `backend/src/main/java/com/meterengine/event/service/EventRejectionService.java:31` [medium, type-design] 거절 사유와 매핑 문제 사유가 문자열 목록으로 여러 곳에 따로 있어, 한 곳만 늘리면 한 요청의 거절 기록 전체가 사라지거나 total이 행 수와 어긋난다
- #450 (중복 정리와 검증 전) `backend/src/main/java/com/meterengine/event/repository/EventRejectionRepository.java:35` [medium, duplication] 요청 행위자와 요청 ID를 구하는 코드가 PR 안에 세 벌이고, 기존 AuditActor와 달리 새 주체 종류를 조용히 user_account로 기록한다
- #450 (중복 정리와 검증 전) `backend/src/main/java/com/meterengine/global/security/UsagePriceDiagnostics.java:9` [medium, layering] 요금 진단 계약 UsagePriceDiagnostics를 인증 패키지 global.security에 두어 수집 담당 리뷰와 도메인 경계 테스트를 우회한다
- #450 (중복 정리와 검증 전) `backend/src/main/java/com/meterengine/event/dto/ListEventsResponse.java:37` [low, naming] 이벤트와 거절 기록의 request_id를 '감사 ID'라고 부르지만 수집 요청은 감사 기록과 요청 로그 어디에도 이 값을 남기지 않는다
- #450 (중복 정리와 검증 전) `backend/src/main/java/com/meterengine/event/dto/UnbilledEventsResponse.java:23` [low, api-design] 같은 음수 수량 집계를 /v1/unbilled-events와 웹훅은 event_count로, /v1/event-issues/search는 negative_event_metric_count로 부르고, 앞쪽 이름은 실제 이벤트 수가 아니다
- #450 (중복 정리와 검증 전) `backend/src/main/java/com/meterengine/event/controller/EventController.java:145` [low, api-design] 모르는 조회 조건을 400으로 막는 규칙이 이벤트 엔드포인트마다 다르고, 허용 이름 목록을 @RequestParam과 따로 손으로 적는다
- #450 (중복 정리와 검증 전) `backend/src/main/java/com/meterengine/event/dto/IngestEventBatchRequest.java:11` [low, openapi-drift] openapi.yaml이 배치 요청 events를 minItems 0으로 내고, 결과 result는 값 목록 없는 문자열이다
- #450 (중복 정리와 검증 전) `backend/src/main/java/com/meterengine/event/service/EventBatchService.java:193` [low, duplication] 배치 경로가 IngestEventRequest의 JSON 필드 이름과 해석을 손으로 다시 써서 단건 경로의 GlobalExceptionHandler 규칙과 갈릴 수 있다
- #450 (중복 정리와 검증 전) `backend/src/main/java/com/meterengine/global/error/ErrorCode.java:25` [low, dead-code] 이 PR에서 아무도 던지지 않는 오류 code 네 개를 더해 openapi.yaml의 ErrorResponse.code 목록으로 공개한다
- #450 (중복 정리와 검증 전) `backend/src/main/java/com/meterengine/customer/api/CustomerDirectory.java:12` [low, naming] 공개 계약 CustomerDirectory.active와 check가 이름과 달리 고객 행에 FOR SHARE 잠금을 건다
- #450 (중복 정리와 검증 전) `backend/src/main/java/com/meterengine/event/repository/EventRepository.java:96` [low, dead-code] 호출하는 곳이 없는 옛 시그니처와 호환 생성자를 남겨, 검증을 건너뛰는 두 번째 진입점이 생겼다
- #450 (중복 정리와 검증 전) `backend/src/main/java/com/meterengine/event/service/UnbilledEventService.java:42` [low, duplication] 월 구간과 집계 cutoff 계산을 BillableMetricUsageService.aggregationEnd와 따로 두 번 구현하고 Asia/Seoul을 직접 만든다

## 이어받을 때 알아야 할 것

- 기준 head가 움직였다. 판정과 수정은 `feat/pricing-aggregation` 5efe449, `feat/resource-archive` afdf8f1, `feat/event-ingestion` 457a0d8, `feat/billing-queries` 53ec3d9 기준이다. 넘기기 시점 원격 head는 각각 cba99f1, 2f2af65, ba34b24, 8141287이다. 세 수정 브랜치는 옛 head에서 갈라졌으니 합칠 때 새 head와 맞춰 보고, 남은 판정은 새 head에서 다시 찾는다.
- 판정을 바꿀 수 있는 의심:
  - 판정 에이전트가 반박을 먼저 시도했지만 63건 중 refuted가 0건이다. 각 근거는 코드 경로를 짚고 있으나 낮은 심각도의 설계 지적(G-121, G-123, G-126, G-111)은 결함보다 개선 제안에 가깝다는 반론이 가능하다.
  - C450-000은 첫 관점 high, 두 번째 관점 medium(개발 서버 하나, 운영 도입사 없음)이라 medium으로 적었다. C450-002는 medium과 low 중 low로 적었다.
  - C447-008은 기전은 맞지만 지금 피해 경로가 없어(청구 경로 필터가 막음) 방어 심층 차원의 low confirmed다. #453의 savePending 스냅숏과 approveScoped 사이 경합을 일부 줄인다는 점이 근거다.
  - G-109는 보관 고객의 기존 인보이스 수금 정책이 없어 uncertain이다.
- 고칠 때 주의:
  - C450-000(expand-contract), G-161, G-174는 V39, V40, V45 본문을 고쳐야 한다. 모두 main 밖이지만 스택을 적용한 로컬 볼륨은 체크섬이 바뀌어 다시 만들어야 한다. G-174는 고치기 전에 위층 브랜치 전체에서 옛 제약 이름 참조를 찾아야 한다.
  - C446-003과 C446-001은 #446에서 고치면 #447(delete를 보관으로 바꿈)과 #450(같은 ApiClient 메서드 추가)으로 올릴 때 충돌한다. 위층 내용을 택하면 된다.
  - C446-007(G-113, G-172)은 인보이스 번호 정책 결정이 필요해 고치지 않기로 했다. G-110(G-173)은 #449 V44를 함께 고쳐야 해서 고치지 않았다.
  - C450-005는 단건 수집이 숫자 epoch를 받던 동작을 엄격하게 바꿀지 팀 결정이 필요하다.
- 다른 묶음과 겹치는 것:
  - G-111, G-171은 k2 G-181(#445 PaymentCancellationController)과 같은 문제다. k2 판정을 보고 한쪽에서만 고쳐야 한다.
  - C447-009의 수정 위치는 frontend/라 k5, k6 몫이다(`k3.json` out_of_scope).
  - G-110의 #449 V44는 k2 범위다.
  - C451-001의 개요 화면은 k6 D-057과 화면이 겹치지만 원인은 다르다.
- 돌리지 못한 검사: 세 수정 브랜치의 전체 `./gradlew build`(세 커밋 모두 컨트롤러, DTO, API 설명을 바꾸지 않았다), demo 검사 전부.
- 실행 환경: 기본 Java 21이라 `apt-get install -y openjdk-25-jdk-headless`로 25를 깔았다. dockerd는 세션에서 직접 띄웠다. Docker Hub와 Maven Central이 429를 내서 `mirror.gcr.io/library/postgres:18`을 받아 `postgres:18`로 태그했고, Gradle은 `~/.gradle/init.d/`에 `https://maven-central.storage-download.googleapis.com/maven2/` 미러와 gradlePluginPortal을 넣었다. Testcontainers는 `TESTCONTAINERS_RYUK_DISABLED=true`로 돌렸다. 네 PR 기준 head의 전체 build는 모두 통과했다.
