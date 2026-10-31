package com.meterengine.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.invoice.entity.Invoice;
import com.meterengine.invoice.repository.InvoiceRepository;
import com.meterengine.payment.client.TossPaymentsClient;
import com.meterengine.payment.client.TossPaymentsPayment;
import com.meterengine.payment.client.TossPaymentsResult;
import com.meterengine.payment.dto.UpdateTossPaymentsCredentialRequest;
import com.meterengine.payment.entity.TossPaymentsCredential;
import com.meterengine.payment.repository.TossPaymentsCredentialRepository;
import com.meterengine.payment.service.TossPaymentsCredentialService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class TossPaymentsWebhookIntegrationTest {

  private static final String TEST_CLIENT_KEY = "test_ck_D5GePWvyJnrK0W0k6q8gLzN97Eoq";
  private static final String TEST_SECRET_KEY = "test_sk_zXLkKEypNArWmo50nX3lmeaxYG5R";
  private static final String PAYMENT_KEY_PREFIX = "tgen_20261026101500";
  private static final String PAYMENT_STATUS_CHANGED = "PAYMENT_STATUS_CHANGED";
  private static final OffsetDateTime APPROVED_AT =
      OffsetDateTime.parse("2026-10-26T10:15:00+09:00");

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private TossPaymentsCredentialRepository tossPaymentsCredentialRepository;
  @Autowired private TossPaymentsCredentialService tossPaymentsCredentialService;
  @Autowired private MeterRegistry meterRegistry;

  @MockitoBean private TossPaymentsClient tossPaymentsClient;

  private MockMvcTester mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcTester.from(
            webApplicationContext, builder -> builder.apply(springSecurity()).build());
  }

  @Test
  void pending_시도에_재조회가_DONE이면_인증_헤더_없이_받아_done으로_바꾼다() {
    Attempt attempt = attempt("pending");
    String paymentKey = paymentKey(attempt);
    when(tossPaymentsClient.findPayment(TEST_SECRET_KEY, paymentKey))
        .thenReturn(found(attempt, paymentKey, "DONE"));
    double doneBefore = attemptCount("done");
    double appliedBefore = webhookCount(PAYMENT_STATUS_CHANGED, "applied");

    MvcTestResult result =
        receive(attempt.organizationId(), webhookBody(attempt.attemptId(), paymentKey, "DONE"));

    assertThat(result).hasStatusOk();
    assertThat(result.getResponse().getContentAsByteArray()).isEmpty();
    assertThat(attemptRow(attempt))
        .containsEntry("status", "done")
        .containsEntry("payment_key", paymentKey);
    assertThat(attemptRow(attempt).get("completed_at")).isNotNull();
    assertThat(attemptCount("done")).isEqualTo(doneBefore + 1);
    assertThat(webhookCount(PAYMENT_STATUS_CHANGED, "applied")).isEqualTo(appliedBefore + 1);
  }

  @Test
  void 본문의_status가_DONE이어도_재조회가_IN_PROGRESS면_pending으로_남는다() {
    Attempt attempt = attempt("pending");
    String paymentKey = paymentKey(attempt);
    when(tossPaymentsClient.findPayment(TEST_SECRET_KEY, paymentKey))
        .thenReturn(found(attempt, paymentKey, "IN_PROGRESS"));
    double ignoredBefore = webhookCount(PAYMENT_STATUS_CHANGED, "ignored");

    assertThat(
            receive(attempt.organizationId(), webhookBody(attempt.attemptId(), paymentKey, "DONE")))
        .hasStatusOk();

    assertThat(attemptRow(attempt))
        .containsEntry("status", "pending")
        .containsEntry("payment_key", null)
        .containsEntry("completed_at", null);
    assertThat(webhookCount(PAYMENT_STATUS_CHANGED, "ignored")).isEqualTo(ignoredBefore + 1);
  }

  @Test
  void 재조회가_ABORTED면_토스_상태를_failure_code로_남기고_failed로_바꾼다() {
    Attempt attempt = attempt("pending");
    String paymentKey = paymentKey(attempt);
    when(tossPaymentsClient.findPayment(TEST_SECRET_KEY, paymentKey))
        .thenReturn(found(attempt, paymentKey, "ABORTED"));
    double failedBefore = attemptCount("failed");

    assertThat(
            receive(
                attempt.organizationId(), webhookBody(attempt.attemptId(), paymentKey, "ABORTED")))
        .hasStatusOk();

    assertThat(attemptRow(attempt))
        .containsEntry("status", "failed")
        .containsEntry("failure_code", "ABORTED")
        .containsEntry("failure_message", "토스페이먼츠 결제가 승인되지 않고 끝났습니다");
    assertThat(attemptCount("failed")).isEqualTo(failedBefore + 1);
  }

  @Test
  void 본문에_paymentKey가_없으면_orderId로_재조회한다() {
    Attempt attempt = attempt("pending");
    String paymentKey = paymentKey(attempt);
    when(tossPaymentsClient.findPaymentByOrderId(TEST_SECRET_KEY, attempt.attemptId().toString()))
        .thenReturn(found(attempt, paymentKey, "DONE"));

    assertThat(receive(attempt.organizationId(), webhookBody(attempt.attemptId(), null, "DONE")))
        .hasStatusOk();

    assertThat(attemptRow(attempt))
        .containsEntry("status", "done")
        .containsEntry("payment_key", paymentKey);
    verify(tossPaymentsClient, never()).findPayment(anyString(), anyString());
  }

  @Test
  void 없는_orderId_다른_도입사_시도_다른_eventType_JSON_아님은_200이고_바뀌는_것이_없다() {
    Attempt attempt = attempt("pending");
    UUID otherOrganizationId = insertOrganization();
    String paymentKey = paymentKey(attempt);
    double ignoredBefore = webhookCount(PAYMENT_STATUS_CHANGED, "ignored");
    double otherIgnoredBefore = webhookCount("other", "ignored");

    List<MvcTestResult> statusChanged =
        List.of(
            receive(attempt.organizationId(), webhookBody(UUID.randomUUID(), paymentKey, "DONE")),
            receive(otherOrganizationId, webhookBody(attempt.attemptId(), paymentKey, "DONE")),
            receive(UUID.randomUUID(), webhookBody(attempt.attemptId(), paymentKey, "DONE")),
            receive(
                attempt.organizationId(),
                "{\"eventType\":\"PAYMENT_STATUS_CHANGED\",\"data\":{\"orderId\":\"not-a-uuid\"}}"),
            receive(attempt.organizationId(), "{\"eventType\":\"PAYMENT_STATUS_CHANGED\"}"));
    List<MvcTestResult> other =
        List.of(
            receive(
                attempt.organizationId(),
                webhookBody(attempt.attemptId(), paymentKey, "DONE")
                    .replace(PAYMENT_STATUS_CHANGED, "DEPOSIT_CALLBACK")),
            receive(attempt.organizationId(), "not json"),
            receive(attempt.organizationId(), "{\"eventType\":1}"),
            receive(attempt.organizationId(), "[]"),
            mvc.post()
                .uri("/v1/toss-payments-webhooks/{organization_id}", attempt.organizationId())
                .contentType(MediaType.APPLICATION_JSON)
                .exchange());

    assertThat(statusChanged).allSatisfy(result -> assertThat(result).hasStatusOk());
    assertThat(other).allSatisfy(result -> assertThat(result).hasStatusOk());
    assertThat(attemptRow(attempt))
        .containsEntry("status", "pending")
        .containsEntry("payment_key", null);
    assertThat(webhookCount(PAYMENT_STATUS_CHANGED, "ignored"))
        .isEqualTo(ignoredBefore + statusChanged.size());
    assertThat(webhookCount("other", "ignored")).isEqualTo(otherIgnoredBefore + other.size());
    verifyNoInteractions(tossPaymentsClient);
  }

  @Test
  void 시도_모드의_키가_없으면_토스를_부르지_않고_200이다() {
    Attempt attempt = attempt("pending");
    jdbcTemplate.update(
        "DELETE FROM toss_payments_credential WHERE organization_id = ?", attempt.organizationId());

    assertThat(
            receive(
                attempt.organizationId(),
                webhookBody(attempt.attemptId(), paymentKey(attempt), "DONE")))
        .hasStatusOk();

    assertThat(attemptRow(attempt)).containsEntry("status", "pending");
    verifyNoInteractions(tossPaymentsClient);
  }

  @Test
  void 키를_바꾼_뒤에_받은_웹훅도_바꾸기_전에_요청한_시도는_이전_키로_재조회해_done으로_바꾼다() {
    Attempt attempt = attempt("pending");
    String paymentKey = paymentKey(attempt);
    String newSecretKey = "test_sk_" + UUID.randomUUID().toString().replace("-", "");
    when(tossPaymentsClient.findPaymentByOrderId(eq(newSecretKey), anyString()))
        .thenReturn(new TossPaymentsResult.Rejected<>(404, "NOT_FOUND_PAYMENT", "없는 결제"));
    tossPaymentsCredentialService.update(
        attempt.organizationId(),
        "test",
        new UpdateTossPaymentsCredentialRequest(TEST_CLIENT_KEY, newSecretKey));
    when(tossPaymentsClient.findPayment(TEST_SECRET_KEY, paymentKey))
        .thenReturn(found(attempt, paymentKey, "DONE"));

    assertThat(
            receive(attempt.organizationId(), webhookBody(attempt.attemptId(), paymentKey, "DONE")))
        .hasStatusOk();

    assertThat(attemptRow(attempt))
        .containsEntry("status", "done")
        .containsEntry("payment_key", paymentKey);
    verify(tossPaymentsClient, never()).findPayment(eq(newSecretKey), anyString());
  }

  @Test
  void 재조회_결과의_orderId가_시도와_다르거나_토스가_거절하면_200이고_바뀌는_것이_없다() {
    Attempt attempt = attempt("pending");
    String paymentKey = paymentKey(attempt);
    when(tossPaymentsClient.findPayment(TEST_SECRET_KEY, paymentKey))
        .thenReturn(
            new TossPaymentsResult.Success<>(
                new TossPaymentsPayment(
                    paymentKey, UUID.randomUUID().toString(), "DONE", 13200L, 13200L, List.of())))
        .thenReturn(new TossPaymentsResult.Rejected<>(404, "NOT_FOUND_PAYMENT", "결제 없음"));
    double ignoredBefore = webhookCount(PAYMENT_STATUS_CHANGED, "ignored");

    String body = webhookBody(attempt.attemptId(), paymentKey, "DONE");
    assertThat(receive(attempt.organizationId(), body)).hasStatusOk();
    assertThat(receive(attempt.organizationId(), body)).hasStatusOk();

    assertThat(attemptRow(attempt)).containsEntry("status", "pending");
    assertThat(webhookCount(PAYMENT_STATUS_CHANGED, "ignored")).isEqualTo(ignoredBefore + 2);
  }

  @Test
  void 재조회_결과를_알_수_없으면_500_internal_server_error이고_pending으로_남는다() {
    Attempt attempt = attempt("pending");
    String paymentKey = paymentKey(attempt);
    when(tossPaymentsClient.findPayment(TEST_SECRET_KEY, paymentKey))
        .thenReturn(new TossPaymentsResult.Unknown<>("HttpTimeoutException: request timed out"));
    double unknownBefore = webhookCount(PAYMENT_STATUS_CHANGED, "unknown");

    MvcTestResult result =
        receive(attempt.organizationId(), webhookBody(attempt.attemptId(), paymentKey, "DONE"));

    assertThat(result)
        .hasStatus(500)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR.getCode());
    assertThat(attemptRow(attempt)).containsEntry("status", "pending");
    assertThat(webhookCount(PAYMENT_STATUS_CHANGED, "unknown")).isEqualTo(unknownBefore + 1);
  }

  @Test
  void 같은_웹훅을_두_번_받으면_시도_done_지표는_한_번만_오른다() {
    Attempt attempt = attempt("pending");
    String paymentKey = paymentKey(attempt);
    when(tossPaymentsClient.findPayment(TEST_SECRET_KEY, paymentKey))
        .thenReturn(found(attempt, paymentKey, "DONE"));
    double doneBefore = attemptCount("done");
    double appliedBefore = webhookCount(PAYMENT_STATUS_CHANGED, "applied");
    double ignoredBefore = webhookCount(PAYMENT_STATUS_CHANGED, "ignored");

    String body = webhookBody(attempt.attemptId(), paymentKey, "DONE");
    assertThat(receive(attempt.organizationId(), body)).hasStatusOk();
    assertThat(receive(attempt.organizationId(), body)).hasStatusOk();

    assertThat(attemptRow(attempt)).containsEntry("status", "done");
    assertThat(attemptCount("done")).isEqualTo(doneBefore + 1);
    assertThat(webhookCount(PAYMENT_STATUS_CHANGED, "applied")).isEqualTo(appliedBefore + 1);
    assertThat(webhookCount(PAYMENT_STATUS_CHANGED, "ignored")).isEqualTo(ignoredBefore + 1);
    verify(tossPaymentsClient, times(1)).findPayment(anyString(), anyString());
  }

  @Test
  void pending_취소에_저장된_paymentKey로_재조회한_cancels의_맞는_원소가_있으면_취소를_done으로_바꾼다() {
    Attempt attempt = attempt("done");
    UUID cancellationId = insertPendingCancellation(attempt, 3000L);
    when(tossPaymentsClient.findPayment(TEST_SECRET_KEY, attempt.paymentKey()))
        .thenReturn(
            canceled(
                attempt,
                new TossPaymentsPayment.Cancel(
                    "txkey-webhook", 3000L, OffsetDateTime.now(), "DONE")));
    double cancellationDoneBefore = cancellationCount("done");
    double appliedBefore = webhookCount(PAYMENT_STATUS_CHANGED, "applied");

    assertThat(
            receive(
                attempt.organizationId(),
                webhookBody(attempt.attemptId(), "tgen_forged_payment_key", "PARTIAL_CANCELED")))
        .hasStatusOk();

    assertThat(cancellationRow(cancellationId))
        .containsEntry("status", "done")
        .containsEntry("transaction_key", "txkey-webhook");
    assertThat(attemptRow(attempt)).containsEntry("status", "done");
    assertThat(cancellationCount("done")).isEqualTo(cancellationDoneBefore + 1);
    assertThat(webhookCount(PAYMENT_STATUS_CHANGED, "applied")).isEqualTo(appliedBefore + 1);
    verify(tossPaymentsClient).findPayment(TEST_SECRET_KEY, attempt.paymentKey());
    verify(tossPaymentsClient, never()).findPayment(TEST_SECRET_KEY, "tgen_forged_payment_key");
  }

  @Test
  void pending_취소에_재조회_cancels의_맞는_원소가_없으면_pending으로_남는다() {
    Attempt attempt = attempt("done");
    UUID cancellationId = insertPendingCancellation(attempt, 3000L);
    when(tossPaymentsClient.findPayment(TEST_SECRET_KEY, attempt.paymentKey()))
        .thenReturn(
            canceled(
                attempt,
                new TossPaymentsPayment.Cancel(
                    "txkey-other-amount", 2000L, OffsetDateTime.now(), "DONE")));

    assertThat(
            receive(
                attempt.organizationId(),
                webhookBody(attempt.attemptId(), attempt.paymentKey(), "PARTIAL_CANCELED")))
        .hasStatusOk();

    assertThat(cancellationRow(cancellationId))
        .containsEntry("status", "pending")
        .containsEntry("transaction_key", null);
  }

  @Test
  void 취소_요청_스레드가_토스를_기다리는_동안_웹훅이_먼저_끝내면_취소_done_지표는_한_번만_오른다() {
    Attempt attempt = attempt("done");
    TossPaymentsResult<TossPaymentsPayment> canceled =
        canceled(
            attempt,
            new TossPaymentsPayment.Cancel("txkey-race", 3000L, OffsetDateTime.now(), "DONE"));
    when(tossPaymentsClient.findPayment(TEST_SECRET_KEY, attempt.paymentKey()))
        .thenReturn(canceled);
    List<Integer> webhookStatuses = new ArrayList<>();
    when(tossPaymentsClient.cancel(anyString(), anyString(), any(), anyString()))
        .thenAnswer(
            invocation -> {
              webhookStatuses.add(
                  receive(
                          attempt.organizationId(),
                          webhookBody(
                              attempt.attemptId(), attempt.paymentKey(), "PARTIAL_CANCELED"))
                      .getResponse()
                      .getStatus());
              return canceled;
            });
    double cancellationDoneBefore = cancellationCount("done");
    double cancellationUnknownBefore = cancellationCount("unknown");
    double appliedBefore = webhookCount(PAYMENT_STATUS_CHANGED, "applied");

    MvcTestResult result =
        mvc.post()
            .uri("/v1/payment-attempts/{id}/cancellations", attempt.attemptId())
            .header(
                "Authorization",
                TestCredentials.issueMemberSession(jdbcTemplate, attempt.organizationId()))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"amount\": 3000, \"reason\": \"고객 요청\"}")
            .exchange();

    assertThat(webhookStatuses).containsExactly(200);
    assertThat(result).hasStatus(201).bodyJson().extractingPath("$.status").isEqualTo("done");
    Map<String, Object> cancellation =
        jdbcTemplate.queryForMap(
            "SELECT * FROM payment_cancellation WHERE payment_attempt_id = ?", attempt.attemptId());
    assertThat(cancellation)
        .containsEntry("status", "done")
        .containsEntry("transaction_key", "txkey-race");
    assertThat(cancellationCount("done")).isEqualTo(cancellationDoneBefore + 1);
    assertThat(cancellationCount("unknown")).isEqualTo(cancellationUnknownBefore);
    assertThat(webhookCount(PAYMENT_STATUS_CHANGED, "applied")).isEqualTo(appliedBefore + 1);
  }

  @Test
  void done_시도에_pending_취소가_없으면_토스를_부르지_않고_200이다() {
    Attempt attempt = attempt("done");
    double ignoredBefore = webhookCount(PAYMENT_STATUS_CHANGED, "ignored");

    assertThat(
            receive(
                attempt.organizationId(),
                webhookBody(attempt.attemptId(), attempt.paymentKey(), "CANCELED")))
        .hasStatusOk();

    assertThat(attemptRow(attempt)).containsEntry("status", "done");
    assertThat(webhookCount(PAYMENT_STATUS_CHANGED, "ignored")).isEqualTo(ignoredBefore + 1);
    verifyNoInteractions(tossPaymentsClient);
  }

  @Test
  void 토스를_재조회하는_동안_트랜잭션_밖이다() {
    Attempt attempt = attempt("pending");
    String paymentKey = paymentKey(attempt);
    List<Boolean> transactionActiveDuringCall = new ArrayList<>();
    when(tossPaymentsClient.findPayment(TEST_SECRET_KEY, paymentKey))
        .thenAnswer(
            invocation -> {
              transactionActiveDuringCall.add(
                  TransactionSynchronizationManager.isActualTransactionActive());
              return found(attempt, paymentKey, "DONE");
            });

    assertThat(
            receive(attempt.organizationId(), webhookBody(attempt.attemptId(), paymentKey, "DONE")))
        .hasStatusOk();

    assertThat(transactionActiveDuringCall).containsExactly(false);
    verify(tossPaymentsClient, times(1)).findPayment(anyString(), anyString());
  }

  private MvcTestResult receive(UUID organizationId, String body) {
    return mvc.post()
        .uri("/v1/toss-payments-webhooks/{organization_id}", organizationId)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body)
        .exchange();
  }

  private static String webhookBody(UUID orderId, String paymentKey, String status) {
    return """
        {"eventType":"PAYMENT_STATUS_CHANGED","createdAt":"2026-10-26T11:42:23.709772503",\
        "data":{"mId":"tvivarepublica","version":"2022-11-16",%s"orderId":"%s",\
        "orderName":"2026-08 사용료","status":"%s","totalAmount":13200,"balanceAmount":13200,\
        "method":"카드","cancels":null}}"""
        .formatted(
            paymentKey == null ? "" : "\"paymentKey\":\"%s\",".formatted(paymentKey),
            orderId,
            status);
  }

  private static TossPaymentsResult<TossPaymentsPayment> found(
      Attempt attempt, String paymentKey, String status) {
    return new TossPaymentsResult.Success<>(
        new TossPaymentsPayment(
            paymentKey, attempt.attemptId().toString(), status, 13200L, 13200L, List.of()));
  }

  private static TossPaymentsResult<TossPaymentsPayment> canceled(
      Attempt attempt, TossPaymentsPayment.Cancel cancel) {
    return new TossPaymentsResult.Success<>(
        new TossPaymentsPayment(
            attempt.paymentKey(),
            attempt.attemptId().toString(),
            "PARTIAL_CANCELED",
            13200L,
            13200L - cancel.cancelAmount(),
            List.of(cancel)));
  }

  private static String paymentKey(Attempt attempt) {
    return PAYMENT_KEY_PREFIX + attempt.attemptId().toString().substring(0, 8);
  }

  private double attemptCount(String result) {
    return meterRegistry
        .get("meterengine.payment.attempt")
        .tags("mode", "test", "result", result)
        .counters()
        .stream()
        .mapToDouble(Counter::count)
        .sum();
  }

  private double cancellationCount(String result) {
    return meterRegistry
        .get("meterengine.payment.cancellation")
        .tags("mode", "test", "result", result)
        .counter()
        .count();
  }

  private double webhookCount(String eventType, String outcome) {
    return meterRegistry
        .get("meterengine.toss.payments.webhook")
        .tags("event_type", eventType, "outcome", outcome)
        .counter()
        .count();
  }

  private Map<String, Object> attemptRow(Attempt attempt) {
    return jdbcTemplate.queryForMap(
        "SELECT * FROM payment_attempt WHERE id = ?", attempt.attemptId());
  }

  private Map<String, Object> cancellationRow(UUID cancellationId) {
    return jdbcTemplate.queryForMap(
        "SELECT * FROM payment_cancellation WHERE id = ?", cancellationId);
  }

  private Attempt attempt(String status) {
    UUID organizationId = insertOrganization();
    UUID customerId = insertCustomer(organizationId);
    insertCredential(organizationId);
    UUID invoiceId = insertInvoice(organizationId, customerId);
    UUID attemptId = UUID.randomUUID();
    boolean done = "done".equals(status);
    String paymentKey = done ? PAYMENT_KEY_PREFIX + attemptId.toString().substring(0, 8) : null;
    jdbcTemplate.update(
        """
        INSERT INTO payment_attempt
          (id, organization_id, customer_id, invoice_id, mode, amount, order_name, status,
           payment_key, requested_at, completed_at)
        SELECT ?, organization_id, customer_id, id, 'test', total_amount, '2026-08 사용료', ?,
               ?, ?, ?
        FROM invoice
        WHERE organization_id = ? AND id = ?
        """,
        attemptId,
        status,
        paymentKey,
        APPROVED_AT.minusSeconds(5),
        done ? APPROVED_AT : null,
        organizationId,
        invoiceId);
    return new Attempt(organizationId, attemptId, paymentKey);
  }

  private UUID insertPendingCancellation(Attempt attempt, long amount) {
    UUID cancellationId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO payment_cancellation
          (id, organization_id, payment_attempt_id, mode, amount, reason, status, requested_at)
        VALUES (?, ?, ?, 'test', ?, '고객 요청', 'pending', ?)
        """,
        cancellationId,
        attempt.organizationId(),
        attempt.attemptId(),
        amount,
        OffsetDateTime.now().minusSeconds(4));
    return cancellationId;
  }

  private void insertCredential(UUID organizationId) {
    tossPaymentsCredentialRepository.saveAndFlush(
        new TossPaymentsCredential(
            organizationId,
            "test",
            TEST_CLIENT_KEY,
            TEST_SECRET_KEY,
            OffsetDateTime.parse("2026-10-01T00:00:00Z")));
  }

  private UUID insertOrganization() {
    return jdbcTemplate.queryForObject(
        "INSERT INTO organization (name) VALUES ('도입사') RETURNING id", UUID.class);
  }

  private UUID insertCustomer(UUID organizationId) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO customer (organization_id, name) VALUES (?, '아크메') RETURNING id",
        UUID.class,
        organizationId);
  }

  private UUID insertInvoice(UUID organizationId, UUID customerId) {
    Invoice invoice =
        new Invoice(
            UUID.randomUUID(),
            organizationId,
            customerId,
            "아크메",
            "2026-08",
            12000,
            1200,
            OffsetDateTime.parse("2026-09-01T04:00:00Z"),
            OffsetDateTime.parse("2026-09-01T05:00:00Z"));
    return invoiceRepository.save(invoice).getId();
  }

  private record Attempt(UUID organizationId, UUID attemptId, String paymentKey) {}
}
