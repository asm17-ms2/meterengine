# 스택 #456 k4 넘기기

사용자 지시로 진행 중이던 작업을 멈추고 정리했다. 판정 근거와 수정 계획은 `k4.json`의 `results[].fix_plan`, `new_findings[].fix_plan`에 있다.

## 끝낸 것

- 기존 지적 45건을 모두 판정했다: confirmed 41, already-fixed 2(C453-000, C453-003), refuted 2(C453-010, G-134).
- 추가 검토 #455를 마쳤다: 새 지적 8건(Nk4-001부터 Nk4-008), confirmed 7, refuted 1(Nk4-006).
- 수정 브랜치 `claude/s456-k4-455`: 마지막 커밋 `f891345` (fix: 측정용 RDS의 04:00 자동 중지 스케줄을 백스톱으로 남긴다). C455-000, G-154, G-158, C455-001, G-164, C455-004를 닫는다.
- `claude/s456-k4-452`, `claude/s456-k4-453`은 만들지 않았다(push한 커밋 없음).

## 남은 것

### 판정하지 못한 id

- 기존 지적은 없다.
- 추가 검토 #452, #453은 찾기 단계 도중 멈춰 새 지적을 하나도 기록하지 못했다. 처음부터 다시 한다. #453은 frontend/ 밖 파일만 본다.

### 하다 만 수정

- #453 G-103, C453-004, C453-003: `claude/s456-k4-453` 작업 트리에 커밋하지 않은 변경이 있었다. `./gradlew build`가 `spotlessJavaCheck`(PaymentEnvironmentIntegrationTest.java 형식)에서 실패해 테스트 단계까지 가지 못했으므로 커밋하지 않았다. 아래 diff가 멈춘 지점이다. 이어받으면 적용 뒤 `./gradlew spotlessApply`, `./gradlew build`로 확인하고 diff를 다시 검토한다. 내용: TossPaymentsCredentialService에 새 결제와 등록용 조회를 두고, 등록 링크 조회와 빌링키 발급, available(), 재시도 자격, approveScoped에서 쓴다. historical()에는 archived_at 조건을 넣지 않는다.

### 시작하지 않은 수정

- #452(16건 모두 confirmed): C452-000과 G-141(아웃박스 영구 거절 무한 재전송), C452-001과 G-186(standalone 묶음에 .env와 SQLite), C452-002(브리지 이름 조회 제거로 기존 고객 중복), C452-004, C452-005, C452-012, C452-013(테스트가 회귀를 못 잡음), C452-006, C452-014(E2E 재실행과 macOS 경로), C452-007(웹훅 수신 기록 무제한), C452-008, C452-009(Lumen 화면 깜빡임과 문구), C452-011(README), C452-015(FontProvider 연결이 #453에 있음).
- #453 confirmed 중 남은 것: C453-001과 G-106(대사가 활동 없는 은퇴 키 버전까지 돌고 첫 실패에서 멈춤), C453-002, C453-005, G-094, G-130, G-093(도입사 공통 감사가 요청 환경에 묶임, 다른 도입사 대상이면 FK 500), C453-006과 G-145(demo/web 단일 대상 환경 웹훅과 문서), C453-008(취소 확인 테스트), G-175(README와 설명의 external_id 고유 범위), G-135(IS NOT DISTINCT FROM 조건이 인덱스를 못 씀).
- #455 남은 것: C455-002(복구 블록의 $rds_plan_dir), C455-003(sdk fake 인자 검사), C455-005(CI job), G-162(root 환경 목록 변수와 SUT 지우기 절차), G-163(sut-start.sh가 실행 중인 중지 흐름을 기다림), Nk4-001, Nk4-002, Nk4-003, Nk4-004, Nk4-007, Nk4-008. Nk4-005는 f891345 뒤 남은 중복만 본다.
- `claude/s456-k4-455`의 독립 리뷰를 하지 못했다.

## 이어받을 때 알아야 할 것

### 판정이나 방향을 바꿀 수 있는 것

- C455-000 계열은 PR 작성자가 문서화한 전환 설계(첫 성공 때 04:00 RDS 스케줄을 끔)를 되돌리는 수정이다(behavior_change). 두 관점 모두 04:00 스케줄을 백스톱으로 남기는 방향을 권했고 전환의 근거는 PR 본문, 커밋, Jira 어디에도 없었다. 팀이 전환 유지를 고르면 f891345를 되돌리고, 대신 sut-stop.sh 문구(C455-001, G-164), SUT README의 RDS 스케줄 끄고 켜는 명령(G-158), 사전 DISABLED를 성공으로 보지 않는 분기가 필요하다.
- severity를 낮춘 것: G-154 high에서 medium, G-103 high에서 medium(자동 재시도 청구는 f58e1b5로 이미 막힘), G-158 medium에서 low, G-186 medium에서 low, G-093 medium에서 low(콘솔은 운영자 요청에 환경 헤더를 싣지 않음).
- G-130의 AuditLogResponse environment 필드 추가는 API 계약 변경이라 하지 않을 계획이었다. 하려면 openapi.yaml을 다시 만든다.
- G-135는 쿼리 조건만 environment_scope_id로 바꾸고 V48의 중복 인덱스는 체크섬 위험 때문에 건드리지 않을 계획이었다.
- C453-002의 ApiKeyService 부분은 틀렸다. API 키 감사는 환경별이 맞다. 감사는 target_type이 아니라 action 기준으로 나눈다(supplier_updated는 환경별 데이터).

### 다른 묶음과 겹치는 곳

- G-175(backend/README.md): k1 G-022, G-034, k2 G-152, k3 G-129, G-161, G-168, C450-003과 같은 README를 다룬다.
- C455-005: CI가 demo/ 테스트도 돌리지 않는 같은 공백이 #452부터 있다.
- C452-007과 C453-006은 demo/web의 같은 웹훅 handler 파일을 고친다. 브라우저 요청 차단과 본문 상한은 대상 선택 앞에 둔다.
- C452-002는 #453의 BridgeConfig.scope 변경(API 키 해시)이 업그레이드 직후 캐시를 비워 모든 기존 사용자에게 터진다.

### 실행 환경

- Java 25는 apt `openjdk-25-jdk-headless`로 깔았다. Maven Central이 429를 돌려줘 `~/.gradle/init.d/central-mirror.gradle`에 Google 미러(maven-central.storage-download.googleapis.com)를 두었다. 레포에는 넣지 않았다.
- Docker 데몬은 `dockerd`를 직접 띄웠다. Testcontainers가 2분 안에 postgres:18을 받지 못해 처음 빌드가 실패하므로 `docker pull postgres:18`을 먼저 한다.
- Node 24(.node-version)와 Terraform 1.17.0은 공식 배포본을 받아 /opt/tools에 두었다.
- rds-auto-stop 잠금 파일에는 한 플랫폼의 h1 해시만 있어 Linux에서 `init -lockfile=readonly` 뒤 validate가 실패한다(Nk4-002). 로컬 검사는 readonly 없이 임시 복사본에서 init 한다.

### 돌리지 못한 검사

- `cd backend && ./gradlew build (claude/s456-k4-453 미커밋 변경)`: fail. spotlessJavaCheck 실패(PaymentEnvironmentIntegrationTest.java 형식). 테스트 단계까지 가지 못해 커밋하지 않았다
- `demo 수정 검사 (pnpm test, lint, typecheck)`: not-run. #452 수정을 시작하지 않았다
- `llm-service E2E (playwright real spec)`: not-run. 살아 있는 백엔드와 시드가 필요해 돌리지 않았다

## 멈춘 #453 수정 diff

<details><summary>claude/s456-k4-453 미커밋 변경 (origin/feat/environment-isolation f040e96 기준)</summary>

```diff
diff --git a/backend/src/main/java/com/meterengine/payment/service/PaymentAttemptService.java b/backend/src/main/java/com/meterengine/payment/service/PaymentAttemptService.java
index 00aee31..67c8ab1 100644
--- a/backend/src/main/java/com/meterengine/payment/service/PaymentAttemptService.java
+++ b/backend/src/main/java/com/meterengine/payment/service/PaymentAttemptService.java
@@ -189,11 +189,11 @@ public class PaymentAttemptService {
     String mode = pendingAttempt.getMode();
     Optional<TossPaymentsCredential> credential =
         pendingAttempt.getCredentialUpdatedAt() != null
-            ? tossPaymentsCredentialService.historical(
+            ? tossPaymentsCredentialService.chargeable(
                 organizationId, mode, pendingAttempt.getCredentialUpdatedAt())
             : pendingAttempt.getEnvironmentId() == null
                 ? tossPaymentsCredentialService.active(organizationId, mode)
-                : tossPaymentsCredentialService.historical(
+                : tossPaymentsCredentialService.chargeable(
                     organizationId, mode, pendingAttempt.getRequestedAt());
     if (credential.isEmpty()) {
       return failWithoutToss(pendingAttempt, ErrorCode.TOSS_PAYMENTS_CREDENTIAL_MISSING);
diff --git a/backend/src/main/java/com/meterengine/payment/service/PaymentMethodRegistrationService.java b/backend/src/main/java/com/meterengine/payment/service/PaymentMethodRegistrationService.java
index 08c132f..cfc36a9 100644
--- a/backend/src/main/java/com/meterengine/payment/service/PaymentMethodRegistrationService.java
+++ b/backend/src/main/java/com/meterengine/payment/service/PaymentMethodRegistrationService.java
@@ -125,26 +125,22 @@ public class PaymentMethodRegistrationService {
                 java.util.List.of(registration.getCustomerId()))
             .stream()
             .anyMatch(customer -> customer.getArchivedAt() == null)
-        && (registration.getEnvironmentId() == null
-                ? tossPaymentsCredentialService.active(
-                    registration.getOrganizationId(), registration.getMode())
-                : historicalCredential(registration))
-            .isPresent();
+        && chargeableCredential(registration).isPresent();
   }
 
   TossPaymentsCredential findCredential(PaymentMethodRegistration registration) {
-    return historicalCredential(registration)
+    return chargeableCredential(registration)
         .orElseThrow(() -> new ConflictException(ErrorCode.TOSS_PAYMENTS_CREDENTIAL_MISSING));
   }
 
-  private java.util.Optional<TossPaymentsCredential> historicalCredential(
+  private java.util.Optional<TossPaymentsCredential> chargeableCredential(
       PaymentMethodRegistration registration) {
     return PaymentEnvironments.call(
         registration.getOrganizationId(),
         registration.getEnvironmentId(),
         registration.getMode(),
         () ->
-            tossPaymentsCredentialService.historical(
+            tossPaymentsCredentialService.chargeable(
                 registration.getOrganizationId(),
                 registration.getMode(),
                 registration.getCredentialUpdatedAt() == null
diff --git a/backend/src/main/java/com/meterengine/payment/service/PaymentRetryEligibilityService.java b/backend/src/main/java/com/meterengine/payment/service/PaymentRetryEligibilityService.java
index 72fcffd..44ca5e3 100644
--- a/backend/src/main/java/com/meterengine/payment/service/PaymentRetryEligibilityService.java
+++ b/backend/src/main/java/com/meterengine/payment/service/PaymentRetryEligibilityService.java
@@ -75,7 +75,7 @@ public class PaymentRetryEligibilityService {
         BusinessEnvironmentContext.environmentId() == null
                 && failed.getCredentialUpdatedAt() == null
             ? tossPaymentsCredentialService.active(organizationId, mode)
-            : tossPaymentsCredentialService.historical(
+            : tossPaymentsCredentialService.chargeable(
                 organizationId,
                 mode,
                 failed.getCredentialUpdatedAt() == null
diff --git a/backend/src/main/java/com/meterengine/payment/service/TossPaymentsCredentialService.java b/backend/src/main/java/com/meterengine/payment/service/TossPaymentsCredentialService.java
index eeec54c..61adc1b 100644
--- a/backend/src/main/java/com/meterengine/payment/service/TossPaymentsCredentialService.java
+++ b/backend/src/main/java/com/meterengine/payment/service/TossPaymentsCredentialService.java
@@ -94,6 +94,13 @@ public class TossPaymentsCredentialService {
     return paymentArchiveRepository.historical(organizationId, mode, requestedAt);
   }
 
+  // 새 결제와 카드 등록용이다. 현재 키를 보관했으면 고정한 버전이 있어도 비운다.
+  Optional<TossPaymentsCredential> chargeable(
+      UUID organizationId, String mode, OffsetDateTime pinnedAt) {
+    if (active(organizationId, mode).isEmpty()) return Optional.empty();
+    return historical(organizationId, mode, pinnedAt);
+  }
+
   Optional<TossPaymentsCredential> active(UUID organizationId, String mode) {
     return find(organizationId, mode).filter(credential -> credential.getArchivedAt() == null);
   }
diff --git a/backend/src/test/java/com/meterengine/invoiceview/InvoiceViewLinkEnvironmentIntegrationTest.java b/backend/src/test/java/com/meterengine/invoiceview/InvoiceViewLinkEnvironmentIntegrationTest.java
index c2386af..6163ba2 100644
--- a/backend/src/test/java/com/meterengine/invoiceview/InvoiceViewLinkEnvironmentIntegrationTest.java
+++ b/backend/src/test/java/com/meterengine/invoiceview/InvoiceViewLinkEnvironmentIntegrationTest.java
@@ -5,6 +5,7 @@ import static org.mockito.ArgumentMatchers.any;
 import static org.mockito.ArgumentMatchers.anyString;
 import static org.mockito.ArgumentMatchers.eq;
 import static org.mockito.ArgumentMatchers.isNull;
+import static org.mockito.Mockito.never;
 import static org.mockito.Mockito.verify;
 import static org.mockito.Mockito.when;
 import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
@@ -187,6 +188,36 @@ class InvoiceViewLinkEnvironmentIntegrationTest {
     assertThat(BusinessEnvironmentContext.current()).isNull();
   }
 
+  @Test
+  void PG키를_보관하면_키_버전을_고정한_실패도_공개링크에서_재시도_가능으로_보이지_않는다() {
+    Payable test = payable("test");
+    failed(test, "test");
+    Link link = createLink("test", test.invoice());
+    assertThat(lookup("test", link.token()))
+        .hasStatusOk()
+        .bodyJson()
+        .extractingPath("$.payment_retry.allowed")
+        .isEqualTo(true);
+    assertThat(
+            mvc.delete()
+                .uri("/v1/toss-payments-credentials/test")
+                .header("Authorization", session)
+                .header("X-MeterEngine-Environment", "test")
+                .exchange())
+        .hasStatus(204);
+    MvcTestResult after = lookup("test", link.token());
+    assertThat(after)
+        .hasStatusOk()
+        .bodyJson()
+        .extractingPath("$.payment_retry.allowed")
+        .isEqualTo(false);
+    assertThat(after)
+        .bodyJson()
+        .extractingPath("$.payment_retry.reason")
+        .isEqualTo("credentials_missing");
+    verify(toss, never()).approveBilling(anyString(), anyString(), any(), anyString(), isNull());
+  }
+
   private Payable payable(String mode) {
     UUID env = environment(mode);
     UUID customer =
diff --git a/backend/src/test/java/com/meterengine/payment/PaymentEnvironmentIntegrationTest.java b/backend/src/test/java/com/meterengine/payment/PaymentEnvironmentIntegrationTest.java
index a5d0e26..b34e22f 100644
--- a/backend/src/test/java/com/meterengine/payment/PaymentEnvironmentIntegrationTest.java
+++ b/backend/src/test/java/com/meterengine/payment/PaymentEnvironmentIntegrationTest.java
@@ -236,6 +236,60 @@ class PaymentEnvironmentIntegrationTest {
         .containsEntry("environment_id", environment("test"));
   }
 
+  @Test
+  void PG키를_보관하면_발급한_등록링크도_조회와_빌링키_발급을_거절하고_토스를_부르지_않는다() {
+    Payable payable = invoice("test");
+    in(
+        "test",
+        () ->
+            credentials.saveAndFlush(
+                new TossPaymentsCredential(
+                    organizationId, "test", "test_ck_original_environment", TEST_SECRET, KEY_TIME)));
+    MvcTestResult created =
+        post("test", "/v1/customers/" + payable.customer() + "/payment-method-registrations", null);
+    assertThat(created).hasStatus(201);
+    String token = JsonPath.read(body(created), "$.token");
+    assertThat(
+            mvc.delete()
+                .uri("/v1/toss-payments-credentials/test")
+                .header("Authorization", session)
+                .header("X-MeterEngine-Environment", "test")
+                .exchange())
+        .hasStatus(204);
+    assertThat(mvc.get().uri("/v1/payment-method-registrations/{token}", token).exchange())
+        .hasStatus(409)
+        .bodyJson()
+        .extractingPath("$.code")
+        .isEqualTo("toss_payments_credential_missing");
+    assertThat(
+            mvc.post()
+                .uri("/v1/payment-method-registrations/{token}/billing-keys", token)
+                .contentType(MediaType.APPLICATION_JSON)
+                .content(
+                    "{\"auth_key\":\"auth-key\",\"customer_key\":\""
+                        + payable.customer()
+                        + "\",\"consent_document_version\":\"2026-10-01\"}")
+                .exchange())
+        .hasStatus(409)
+        .bodyJson()
+        .extractingPath("$.code")
+        .isEqualTo("toss_payments_credential_missing");
+    verify(toss, never()).issueBillingKey(anyString(), anyString(), anyString());
+    verify(toss, never()).deleteBillingKey(anyString(), anyString());
+    assertThat(
+            jdbcTemplate.queryForObject(
+                "SELECT count(*) FROM billing_key WHERE customer_id=?",
+                Integer.class,
+                payable.customer()))
+        .isZero();
+    assertThat(
+            jdbcTemplate.queryForObject(
+                "SELECT used_at FROM payment_method_registration WHERE customer_id=?",
+                OffsetDateTime.class,
+                payable.customer()))
+        .isNull();
+  }
+
   @Test
   void 새_PG키로_등록한_카드를_저장해도_옛_카드는_발급했던_키로_삭제한다() {
     Payable payable = payable("test");
diff --git a/backend/src/test/java/com/meterengine/payment/PaymentMethodRegistrationIntegrationTest.java b/backend/src/test/java/com/meterengine/payment/PaymentMethodRegistrationIntegrationTest.java
index 3b2c795..d5d8de4 100644
--- a/backend/src/test/java/com/meterengine/payment/PaymentMethodRegistrationIntegrationTest.java
+++ b/backend/src/test/java/com/meterengine/payment/PaymentMethodRegistrationIntegrationTest.java
@@ -162,6 +162,30 @@ class PaymentMethodRegistrationIntegrationTest {
     assertThat(countRegistrations(customerId)).isZero();
   }
 
+  @Test
+  void 키를_보관하면_발급한_링크도_조회와_발급이_409이고_토스를_부르지_않는다() {
+    UUID organizationId = insertOrganization();
+    UUID customerId = insertCustomer(organizationId);
+    insertCredential(organizationId, "test");
+    String token = createToken(organizationId, customerId);
+    assertThat(
+            withOrganization(
+                    mvc.delete().uri("/v1/toss-payments-credentials/{mode}", "test"),
+                    organizationId)
+                .exchange())
+        .hasStatus(204);
+
+    assertError(getRegistration(token), 409, ErrorCode.TOSS_PAYMENTS_CREDENTIAL_MISSING);
+    assertError(
+        createBillingKey(token, AUTH_KEY, customerId.toString(), CONSENT_DOCUMENT_VERSION),
+        409,
+        ErrorCode.TOSS_PAYMENTS_CREDENTIAL_MISSING);
+
+    verifyNoInteractions(tossPaymentsClient);
+    assertThat(countBillingKeys(customerId)).isZero();
+    assertThat(registrationRow(customerId).get("used_at")).isNull();
+  }
+
   @Test
   void 공개_조회는_X_Organization_Id_없이_200이고_token은_싣지_않는다() {
     UUID organizationId = insertOrganization();
diff --git a/backend/src/test/java/com/meterengine/payment/service/PaymentAttemptServiceTest.java b/backend/src/test/java/com/meterengine/payment/service/PaymentAttemptServiceTest.java
index b9c62d4..151f55f 100644
--- a/backend/src/test/java/com/meterengine/payment/service/PaymentAttemptServiceTest.java
+++ b/backend/src/test/java/com/meterengine/payment/service/PaymentAttemptServiceTest.java
@@ -76,6 +76,31 @@ class PaymentAttemptServiceTest {
     assertFailedWithoutToss(response, invoice, ErrorCode.TOSS_PAYMENTS_CREDENTIAL_MISSING);
   }
 
+  @Test
+  void 승인_직전에_키를_보관했으면_고정한_키_버전이_있어도_토스를_부르지_않고_실패시킨다() {
+    Invoice invoice = invoice();
+    insertCredential(invoice.getOrganizationId());
+    insertBillingKey(invoice);
+    PaymentAttempt attempt =
+        paymentAttemptRepository.saveAndFlush(
+            PaymentAttempt.pending(
+                    UUID.randomUUID(),
+                    invoice.getOrganizationId(),
+                    invoice.getCustomerId(),
+                    invoice.getId(),
+                    "test",
+                    invoice.getTotalAmount(),
+                    "2026-08 사용료")
+                .pinCredential(OffsetDateTime.parse("2026-10-01T00:00:00Z")));
+    jdbcTemplate.update(
+        "UPDATE toss_payments_credential SET archived_at = now() WHERE organization_id = ?",
+        invoice.getOrganizationId());
+
+    PaymentAttemptResponse response = paymentAttemptService.approve(attempt, null);
+
+    assertFailedWithoutToss(response, invoice, ErrorCode.TOSS_PAYMENTS_CREDENTIAL_MISSING);
+  }
+
   @Test
   @ExtendWith(OutputCaptureExtension.class)
   void 토스가_승인했는데_시도가_이미_NOT_FOUND_PAYMENT로_failed면_그대로_두고_error_로그를_남긴다(CapturedOutput output) {
```

</details>
