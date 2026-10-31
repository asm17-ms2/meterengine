package com.meterengine.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meterengine.SideEffectTestSupport;
import com.meterengine.payment.client.TossPaymentsClient;
import com.meterengine.payment.entity.TossPaymentsCredential;
import com.meterengine.payment.repository.TossPaymentsCredentialRepository;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "METERENGINE_DEPLOYMENT_ENVIRONMENT=staging")
class StagingPaymentIntegrationTest extends SideEffectTestSupport {
  static final AtomicInteger requests = new AtomicInteger();
  static final HttpServer RECEIVER = startReceiver();
  @Autowired TossPaymentsClient toss;
  @Autowired TossPaymentsCredentialRepository credentials;
  @Autowired com.meterengine.invoice.repository.InvoiceRepository invoices;
  @Autowired com.meterengine.payment.service.PaymentAttemptConfirmationService confirmation;
  @Autowired com.meterengine.payment.service.PaymentReconciliationService reconciliation;
  @Autowired com.meterengine.payment.service.TossPaymentsWebhookService receivedWebhook;

  @DynamicPropertySource
  static void paymentEndpoint(DynamicPropertyRegistry registry) {
    registry.add(
        "tosspayments.base-url", () -> "http://127.0.0.1:" + RECEIVER.getAddress().getPort());
  }

  @AfterAll
  static void stopReceiver() {
    RECEIVER.stop(0);
  }

  @Test
  void LIVE자격_등록은_PG검증과_저장전에_거절하고_TEST대역은_허용한다() {
    int before = requests.get();
    var live =
        request(
            "PUT",
            "/v1/toss-payments-credentials/live",
            session,
            "live",
            "{\"client_key\":\"live_ck_fixture\",\"secret_key\":\"live_sk_fixture\"}");
    assertThat(live.statusCode()).as(live.body()).isEqualTo(409);
    assertThat(requests).hasValue(before);
    assertThat(
            ownerJdbc()
                .queryForObject(
                    "SELECT count(*) FROM live.toss_payments_credential WHERE organization_id=?",
                    Long.class,
                    organizationId))
        .isZero();
    var test =
        request(
            "PUT",
            "/v1/toss-payments-credentials/test",
            session,
            "test",
            "{\"client_key\":\"test_ck_fixture\",\"secret_key\":\"test_sk_fixture\"}");
    assertThat(test.statusCode()).as(test.body()).isEqualTo(200);
    assertThat(requests).hasValue(before + 1);
  }

  @Test
  void 저장된_LIVE자격의_pending_확인과_대사와_수신웹훅은_원래_ID를_유지하고_PG를_부르지_않는다() {
    var customer = java.util.UUID.randomUUID();
    createCustomer("live", customer, "historical-live");
    var invoice = java.util.UUID.randomUUID();
    var attempt = java.util.UUID.randomUUID();
    var credentialAt = OffsetDateTime.now().minusDays(1);
    in(
        "live",
        () -> {
          credentials.saveAndFlush(
              new TossPaymentsCredential(
                  organizationId, "live", "live_ck_pending", "live_sk_pending", credentialAt));
          invoices.saveAndFlush(
              new com.meterengine.invoice.entity.Invoice(
                  invoice,
                  organizationId,
                  customer,
                  "고객",
                  "2026-08",
                  1000,
                  100,
                  OffsetDateTime.now()));
          jdbc.update(
              "INSERT INTO payment_attempt(id,organization_id,customer_id,invoice_id,mode,amount,order_name,status,requested_at,credential_updated_at) VALUES (?,?,?,?,'live',1100,'fixture','pending',now()-interval '3 minutes',?)",
              attempt,
              organizationId,
              customer,
              invoice,
              credentialAt);
        });
    int before = requests.get();
    assertThat(in("live", () -> confirmation.confirm()).failedCount()).isPositive();
    assertThat(in("live", () -> reconciliation.reconcile(OffsetDateTime.now())).failedCount())
        .isPositive();
    assertThatThrownBy(
            () ->
                receivedWebhook.receive(
                    organizationId,
                    "{\"eventType\":\"PAYMENT_STATUS_CHANGED\",\"data\":{\"orderId\":\""
                        + attempt
                        + "\",\"paymentKey\":\"fixture\"}}"))
        .isInstanceOf(IllegalStateException.class);
    assertThat(
            ownerJdbc()
                .queryForObject(
                    "SELECT status FROM live.payment_attempt WHERE id=?", String.class, attempt))
        .isEqualTo("pending");
    assertThat(
            ownerJdbc()
                .queryForObject(
                    "SELECT count(*) FROM live.payment_reconciliation_mismatch WHERE organization_id=?",
                    Long.class,
                    organizationId))
        .isZero();
    assertThat(requests).hasValue(before);
    var rejected =
        request("POST", "/v1/invoices/" + invoice + "/payment-attempts", session, "live", null);
    assertThat(rejected.statusCode()).as(rejected.body()).isEqualTo(409);
    assertThat(
            ownerJdbc()
                .queryForObject(
                    "SELECT count(*) FROM live.payment_attempt WHERE organization_id=?",
                    Long.class,
                    organizationId))
        .isEqualTo(1);
    assertThat(requests).hasValue(before);
  }

  @Test
  void 직접_PG의_GET과_발급과_삭제와_승인과_취소와_목록은_LIVE_자격으로_연결하지_않는다() {
    in(
        "live",
        () ->
            credentials.saveAndFlush(
                new TossPaymentsCredential(
                    organizationId,
                    "live",
                    "live_ck_historical",
                    "live_sk_historical",
                    OffsetDateTime.now().minusDays(1))));
    int before = requests.get();
    assertThatThrownBy(() -> toss.findPayment("live_sk_historical", "fixture"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> toss.findPaymentByOrderId("live_sk_historical", "fixture"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> toss.issueBillingKey("live_sk_historical", "fixture", "fixture"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> toss.deleteBillingKey("live_sk_historical", "fixture"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () -> toss.approveBilling("live_sk_historical", "fixture", null, "fixture", null))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> toss.cancel("live_sk_historical", "fixture", null, "fixture"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                toss.listTransactions(
                    "live_sk_historical", LocalDateTime.now(), LocalDateTime.now(), null, 1))
        .isInstanceOf(IllegalStateException.class);
    assertThat(requests).hasValue(before);
  }

  private static HttpServer startReceiver() {
    try {
      var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext(
          "/",
          exchange -> {
            requests.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            byte[] body =
                "{\"code\":\"NOT_FOUND_PAYMENT\",\"message\":\"fixture\"}"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(404, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
          });
      server.start();
      return server;
    } catch (java.io.IOException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
