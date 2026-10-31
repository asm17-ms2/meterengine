package com.meterengine.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meterengine.TestcontainersConfiguration;
import com.meterengine.invoice.entity.Invoice;
import com.meterengine.invoice.repository.InvoiceRepository;
import com.meterengine.payment.client.TossPaymentsClient;
import com.meterengine.payment.client.TossPaymentsPayment;
import com.meterengine.payment.client.TossPaymentsResult;
import com.meterengine.payment.dto.PaymentJobResult;
import com.meterengine.payment.dto.UpdateTossPaymentsCredentialRequest;
import com.meterengine.payment.entity.TossPaymentsCredential;
import com.meterengine.payment.repository.TossPaymentsCredentialRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PaymentAttemptConfirmationServiceTest {

  private static final String CLIENT_KEY = "test_ck_D5GePWvyJnrK0W0k6q8gLzN97Eoq";

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private TossPaymentsCredentialRepository tossPaymentsCredentialRepository;
  @Autowired private MeterRegistry meterRegistry;
  @Autowired private PaymentAttemptConfirmationService paymentAttemptConfirmationService;
  @Autowired private TossPaymentsCredentialService tossPaymentsCredentialService;

  @MockitoBean private TossPaymentsClient tossPaymentsClient;

  private int invoiceCount;

  // 다른 도입사 행의 토스 조회에 결과 모름으로 답한다.
  @BeforeEach
  void stubOtherRows() {
    when(tossPaymentsClient.findPaymentByOrderId(anyString(), anyString()))
        .thenReturn(new TossPaymentsResult.Unknown<>("다른 테스트의 행"));
    when(tossPaymentsClient.findPayment(anyString(), anyString()))
        .thenReturn(new TossPaymentsResult.Unknown<>("다른 테스트의 행"));
  }

  @Test
  void 토스_조회가_DONE이면_pending_시도를_done으로_바꾼다() {
    Organization organization = organization();
    UUID attemptId = pendingAttempt(organization, Duration.ofMinutes(3));
    when(tossPaymentsClient.findPaymentByOrderId(organization.secretKey(), attemptId.toString()))
        .thenReturn(found(attemptId, paymentKey(attemptId), "DONE"));
    double doneBefore = attemptCount("done");

    PaymentJobResult result = paymentAttemptConfirmationService.confirm();

    assertThat(attemptRow(attemptId))
        .containsEntry("status", "done")
        .containsEntry("payment_key", paymentKey(attemptId));
    assertThat(result.processedCount()).isEqualTo(1);
    assertThat(result.failedCount()).isZero();
    assertThat(attemptCount("done")).isEqualTo(doneBefore + 1);
  }

  @Test
  void 확정_지연이_지나지_않은_pending_시도는_조회하지_않는다() {
    Organization organization = organization();
    UUID attemptId = pendingAttempt(organization, Duration.ofSeconds(30));

    paymentAttemptConfirmationService.confirm();

    verify(tossPaymentsClient, never())
        .findPaymentByOrderId(eq(organization.secretKey()), anyString());
    assertThat(attemptRow(attemptId)).containsEntry("status", "pending");
  }

  @Test
  void 토스에_결제가_없으면_없음_실패_지연_전에는_그대로_두고_지난_뒤에는_NOT_FOUND_PAYMENT로_실패시킨다() {
    Organization organization = organization();
    UUID recentAttemptId = pendingAttempt(organization, Duration.ofMinutes(3));
    UUID oldAttemptId = pendingAttempt(organization, Duration.ofMinutes(11));
    for (UUID attemptId : List.of(recentAttemptId, oldAttemptId)) {
      when(tossPaymentsClient.findPaymentByOrderId(organization.secretKey(), attemptId.toString()))
          .thenReturn(new TossPaymentsResult.Rejected<>(404, "NOT_FOUND_PAYMENT", "없는 결제"));
    }
    double failedBefore = attemptCount("failed");

    PaymentJobResult result = paymentAttemptConfirmationService.confirm();

    assertThat(attemptRow(recentAttemptId)).containsEntry("status", "pending");
    assertThat(attemptRow(oldAttemptId))
        .containsEntry("status", "failed")
        .containsEntry("failure_code", "NOT_FOUND_PAYMENT")
        .containsEntry("failure_message", "토스페이먼츠에 결제 기록이 없습니다");
    assertThat(result.processedCount()).isEqualTo(1);
    assertThat(attemptCount("failed")).isEqualTo(failedBefore + 1);
  }

  @Test
  void 키_이력_없이_현재_키만_시도_뒤에_바뀌었으면_토스를_부르지_않고_pending으로_둔다() {
    Organization organization = organization();
    UUID attemptId = pendingAttempt(organization, Duration.ofMinutes(11));
    jdbcTemplate.update(
        "UPDATE toss_payments_credential SET updated_at = now() WHERE organization_id = ?",
        organization.organizationId());

    PaymentJobResult result = paymentAttemptConfirmationService.confirm();

    assertThat(attemptRow(attemptId)).containsEntry("status", "pending");
    assertThat(result.processedCount()).isZero();
    verify(tossPaymentsClient, never())
        .findPaymentByOrderId(eq(organization.secretKey()), anyString());
  }

  @Test
  void 키를_바꾼_뒤에도_바꾸기_전에_요청한_결제와_취소는_이전_키로_확인한다() {
    Organization organization = organization();
    UUID pendingAttemptId = pendingAttempt(organization, Duration.ofMinutes(11));
    UUID doneAttemptId = doneAttempt(organization);
    UUID cancellationId = pendingCancellation(organization, doneAttemptId, Duration.ofMinutes(3));
    String transactionKey = "tk_" + cancellationId;
    String newSecretKey = "test_sk_" + UUID.randomUUID().toString().replace("-", "");
    when(tossPaymentsClient.findPaymentByOrderId(eq(newSecretKey), anyString()))
        .thenReturn(new TossPaymentsResult.Rejected<>(404, "NOT_FOUND_PAYMENT", "없는 결제"));
    tossPaymentsCredentialService.update(
        organization.organizationId(),
        "test",
        new UpdateTossPaymentsCredentialRequest(CLIENT_KEY, newSecretKey));
    when(tossPaymentsClient.findPaymentByOrderId(
            organization.secretKey(), pendingAttemptId.toString()))
        .thenReturn(new TossPaymentsResult.Rejected<>(404, "NOT_FOUND_PAYMENT", "없는 결제"));
    when(tossPaymentsClient.findPayment(organization.secretKey(), paymentKey(doneAttemptId)))
        .thenReturn(
            new TossPaymentsResult.Success<>(
                new TossPaymentsPayment(
                    paymentKey(doneAttemptId),
                    doneAttemptId.toString(),
                    "PARTIAL_CANCELED",
                    13200,
                    8200,
                    List.of(
                        new TossPaymentsPayment.Cancel(
                            transactionKey, 5000, OffsetDateTime.now().minusMinutes(3), "DONE")))));

    paymentAttemptConfirmationService.confirm();

    assertThat(attemptRow(pendingAttemptId))
        .containsEntry("status", "failed")
        .containsEntry("failure_code", "NOT_FOUND_PAYMENT");
    assertThat(cancellationRow(cancellationId))
        .containsEntry("status", "done")
        .containsEntry("transaction_key", transactionKey);
    verify(tossPaymentsClient, never())
        .findPaymentByOrderId(newSecretKey, pendingAttemptId.toString());
    verify(tossPaymentsClient, never()).findPayment(eq(newSecretKey), anyString());
  }

  @Test
  void 결제_모드의_키가_없으면_토스를_부르지_않고_pending으로_둔다() {
    Organization organization = organization();
    UUID attemptId = pendingAttempt(organization, Duration.ofMinutes(11));
    jdbcTemplate.update(
        "DELETE FROM toss_payments_credential WHERE organization_id = ?",
        organization.organizationId());

    PaymentJobResult result = paymentAttemptConfirmationService.confirm();

    assertThat(attemptRow(attemptId)).containsEntry("status", "pending");
    assertThat(result.processedCount()).isZero();
    assertThat(result.failedCount()).isZero();
    verify(tossPaymentsClient, never())
        .findPaymentByOrderId(eq(organization.secretKey()), anyString());
  }

  @Test
  void 토스_조회가_ABORTED면_그_상태를_실패_코드로_실패시킨다() {
    Organization organization = organization();
    UUID attemptId = pendingAttempt(organization, Duration.ofMinutes(3));
    when(tossPaymentsClient.findPaymentByOrderId(organization.secretKey(), attemptId.toString()))
        .thenReturn(found(attemptId, null, "ABORTED"));

    paymentAttemptConfirmationService.confirm();

    assertThat(attemptRow(attemptId))
        .containsEntry("status", "failed")
        .containsEntry("failure_code", "ABORTED");
  }

  @Test
  void 조회_결과를_모르면_pending으로_둔다() {
    Organization organization = organization();
    UUID attemptId = pendingAttempt(organization, Duration.ofMinutes(30));

    PaymentJobResult result = paymentAttemptConfirmationService.confirm();

    assertThat(attemptRow(attemptId)).containsEntry("status", "pending");
    assertThat(result.processedCount()).isZero();
    assertThat(result.failedCount()).isZero();
    assertThat(result.skippedCount()).isPositive();
  }

  @Test
  void 한_시도에서_예외가_나도_다음_시도를_처리하고_failedCount는_1이다() {
    Organization organization = organization();
    UUID brokenAttemptId = pendingAttempt(organization, Duration.ofMinutes(5));
    UUID nextAttemptId = pendingAttempt(organization, Duration.ofMinutes(4));
    when(tossPaymentsClient.findPaymentByOrderId(
            organization.secretKey(), brokenAttemptId.toString()))
        .thenThrow(new IllegalStateException("조회 중 예외"));
    when(tossPaymentsClient.findPaymentByOrderId(
            organization.secretKey(), nextAttemptId.toString()))
        .thenReturn(found(nextAttemptId, paymentKey(nextAttemptId), "DONE"));

    PaymentJobResult result = paymentAttemptConfirmationService.confirm();

    assertThat(result.failedCount()).isEqualTo(1);
    assertThat(result.processedCount()).isEqualTo(1);
    assertThat(attemptRow(brokenAttemptId)).containsEntry("status", "pending");
    assertThat(attemptRow(nextAttemptId)).containsEntry("status", "done");
  }

  @Test
  void 웹훅이_먼저_끝낸_시도는_확정이_다시_바꾸지_않는다() {
    Organization organization = organization();
    UUID attemptId = pendingAttempt(organization, Duration.ofMinutes(3));
    String webhookPaymentKey = "tgen_webhook_" + attemptId;
    when(tossPaymentsClient.findPaymentByOrderId(organization.secretKey(), attemptId.toString()))
        .thenAnswer(
            invocation -> {
              jdbcTemplate.update(
                  "UPDATE payment_attempt SET status = 'done', payment_key = ?,"
                      + " completed_at = now() WHERE id = ?",
                  webhookPaymentKey,
                  attemptId);
              return found(attemptId, paymentKey(attemptId), "DONE");
            });
    double doneBefore = attemptCount("done");

    PaymentJobResult result = paymentAttemptConfirmationService.confirm();

    assertThat(attemptRow(attemptId))
        .containsEntry("status", "done")
        .containsEntry("payment_key", webhookPaymentKey);
    assertThat(result.processedCount()).isZero();
    assertThat(attemptCount("done")).isEqualTo(doneBefore);
  }

  @Test
  @ExtendWith(OutputCaptureExtension.class)
  void 토스_조회가_DONE인데_시도가_이미_failed로_바뀌었으면_그대로_두고_error_로그를_남긴다(CapturedOutput output) {
    Organization organization = organization();
    UUID attemptId = pendingAttempt(organization, Duration.ofMinutes(3));
    when(tossPaymentsClient.findPaymentByOrderId(organization.secretKey(), attemptId.toString()))
        .thenAnswer(
            invocation -> {
              jdbcTemplate.update(
                  "UPDATE payment_attempt SET status = 'failed',"
                      + " failure_code = 'NOT_FOUND_PAYMENT', failure_message = '결제 기록 없음',"
                      + " completed_at = now() WHERE id = ?",
                  attemptId);
              return found(attemptId, paymentKey(attemptId), "DONE");
            });
    double doneBefore = attemptCount("done");

    PaymentJobResult result = paymentAttemptConfirmationService.confirm();

    assertThat(attemptRow(attemptId))
        .containsEntry("status", "failed")
        .containsEntry("failure_code", "NOT_FOUND_PAYMENT")
        .containsEntry("payment_key", null);
    assertThat(attemptCount("done")).isEqualTo(doneBefore);
    assertThat(output.getAll())
        .containsPattern(
            "ERROR.*toss payments approved a payment attempt already marked failed: attempt_id="
                + attemptId
                + ", organization_id="
                + organization.organizationId()
                + ", mode=test, failure_code=NOT_FOUND_PAYMENT")
        .doesNotContain(organization.secretKey());
    assertThat(result.failedCount()).isZero();
  }

  @Test
  void 요청한_지_7일이_넘은_pending_시도는_토스에_묻지_않고_게이지는_계속_센다() {
    Organization organization = organization();
    UUID staleAttemptId = pendingAttempt(organization, Duration.ofDays(7).plusMinutes(1));
    UUID recentAttemptId = pendingAttempt(organization, Duration.ofDays(7).minusMinutes(1));

    paymentAttemptConfirmationService.confirm();

    verify(tossPaymentsClient, never())
        .findPaymentByOrderId(organization.secretKey(), staleAttemptId.toString());
    verify(tossPaymentsClient)
        .findPaymentByOrderId(organization.secretKey(), recentAttemptId.toString());
    assertThat(attemptRow(staleAttemptId)).containsEntry("status", "pending");
    double stalePending = meterRegistry.get("meterengine.payment.stale.pending").gauge().value();
    assertThat(stalePending)
        .isGreaterThanOrEqualTo(2)
        .isBetween(
            (double) pendingOlderThan(Duration.ofSeconds(125)),
            (double) pendingOlderThan(Duration.ofSeconds(115)));
  }

  @Test
  void 요청한_지_7일이_넘은_pending_취소는_토스에_묻지_않는다() {
    Organization organization = organization();
    UUID staleAttemptId = doneAttempt(organization);
    UUID recentAttemptId = doneAttempt(organization);
    UUID staleCancellationId =
        pendingCancellation(organization, staleAttemptId, Duration.ofDays(7).plusMinutes(1));
    pendingCancellation(organization, recentAttemptId, Duration.ofDays(7).minusMinutes(1));

    paymentAttemptConfirmationService.confirm();

    verify(tossPaymentsClient, never())
        .findPayment(organization.secretKey(), paymentKey(staleAttemptId));
    verify(tossPaymentsClient).findPayment(organization.secretKey(), paymentKey(recentAttemptId));
    assertThat(cancellationRow(staleCancellationId)).containsEntry("status", "pending");
  }

  @Test
  void 확정_지연이_지난_pending_취소도_게이지에_센다() {
    Organization organization = organization();
    UUID attemptId = doneAttempt(organization);
    pendingCancellation(organization, attemptId, Duration.ofMinutes(3));

    paymentAttemptConfirmationService.confirm();

    double stalePending = meterRegistry.get("meterengine.payment.stale.pending").gauge().value();
    assertThat(stalePending)
        .isGreaterThan((double) pendingAttemptsOlderThan(Duration.ofSeconds(115)))
        .isBetween(
            (double) pendingOlderThan(Duration.ofSeconds(125)),
            (double) pendingOlderThan(Duration.ofSeconds(115)));
  }

  @Test
  void 끝나고_남은_오래된_pending_시도_수를_게이지에_둔다() {
    Organization organization = organization();
    pendingAttempt(organization, Duration.ofMinutes(3));
    pendingAttempt(organization, Duration.ofMinutes(30));

    paymentAttemptConfirmationService.confirm();

    double stalePending = meterRegistry.get("meterengine.payment.stale.pending").gauge().value();
    assertThat(stalePending)
        .isGreaterThanOrEqualTo(2)
        .isBetween(
            (double) pendingOlderThan(Duration.ofSeconds(125)),
            (double) pendingOlderThan(Duration.ofSeconds(115)));
  }

  @Test
  void 토스_결제에_맞는_취소_원소가_있으면_pending_취소를_done으로_바꾼다() {
    Organization organization = organization();
    UUID attemptId = doneAttempt(organization);
    UUID cancellationId = pendingCancellation(organization, attemptId, Duration.ofMinutes(3));
    String transactionKey = "tk_" + cancellationId;
    when(tossPaymentsClient.findPayment(organization.secretKey(), paymentKey(attemptId)))
        .thenReturn(
            new TossPaymentsResult.Success<>(
                new TossPaymentsPayment(
                    paymentKey(attemptId),
                    attemptId.toString(),
                    "PARTIAL_CANCELED",
                    13200,
                    8200,
                    List.of(
                        new TossPaymentsPayment.Cancel(
                            transactionKey, 5000, OffsetDateTime.now().minusMinutes(3), "DONE")))));
    double doneBefore = cancellationCount("done");

    PaymentJobResult result = paymentAttemptConfirmationService.confirm();

    assertThat(cancellationRow(cancellationId))
        .containsEntry("status", "done")
        .containsEntry("transaction_key", transactionKey);
    assertThat(result.processedCount()).isEqualTo(1);
    assertThat(cancellationCount("done")).isEqualTo(doneBefore + 1);
  }

  @Test
  void 맞는_취소_원소가_없으면_없음_실패_지연_전에는_그대로_두고_지난_뒤에는_NOT_FOUND_CANCELLATION으로_실패시킨다() {
    Organization organization = organization();
    UUID recentAttemptId = doneAttempt(organization);
    UUID oldAttemptId = doneAttempt(organization);
    UUID recentCancellationId =
        pendingCancellation(organization, recentAttemptId, Duration.ofMinutes(3));
    UUID oldCancellationId =
        pendingCancellation(organization, oldAttemptId, Duration.ofMinutes(11));
    for (UUID attemptId : List.of(recentAttemptId, oldAttemptId)) {
      when(tossPaymentsClient.findPayment(organization.secretKey(), paymentKey(attemptId)))
          .thenReturn(found(attemptId, paymentKey(attemptId), "DONE"));
    }

    PaymentJobResult result = paymentAttemptConfirmationService.confirm();

    assertThat(cancellationRow(recentCancellationId)).containsEntry("status", "pending");
    assertThat(cancellationRow(oldCancellationId))
        .containsEntry("status", "failed")
        .containsEntry("failure_code", "NOT_FOUND_CANCELLATION")
        .containsEntry("failure_message", "토스페이먼츠에 취소 기록이 없습니다");
    assertThat(result.processedCount()).isEqualTo(1);
  }

  @Test
  void 맞는_취소_원소가_없어도_토스가_done_취소_합보다_더_환불했으면_없음_실패_지연이_지나도_pending으로_둔다() {
    Organization organization = organization();
    UUID attemptId = doneAttempt(organization);
    UUID cancellationId = pendingCancellation(organization, attemptId, Duration.ofMinutes(11));
    double failedBefore = cancellationCount("failed");
    when(tossPaymentsClient.findPayment(organization.secretKey(), paymentKey(attemptId)))
        .thenReturn(
            new TossPaymentsResult.Success<>(
                new TossPaymentsPayment(
                    paymentKey(attemptId),
                    attemptId.toString(),
                    "PARTIAL_CANCELED",
                    13200,
                    8200,
                    List.of(
                        new TossPaymentsPayment.Cancel(
                            "tk_" + cancellationId,
                            5000,
                            OffsetDateTime.now().minusMinutes(30),
                            "DONE")))));

    PaymentJobResult result = paymentAttemptConfirmationService.confirm();

    assertThat(cancellationRow(cancellationId))
        .containsEntry("status", "pending")
        .containsEntry("failure_code", null);
    assertThat(result.processedCount()).isZero();
    assertThat(cancellationCount("failed")).isEqualTo(failedBefore);
  }

  @Test
  void 취소_확정에서_조회_결과를_모르면_pending으로_둔다() {
    Organization organization = organization();
    UUID attemptId = doneAttempt(organization);
    UUID cancellationId = pendingCancellation(organization, attemptId, Duration.ofMinutes(11));

    PaymentJobResult result = paymentAttemptConfirmationService.confirm();

    assertThat(cancellationRow(cancellationId)).containsEntry("status", "pending");
    assertThat(result.processedCount()).isZero();
    assertThat(result.failedCount()).isZero();
  }

  @Test
  void 취소_확정에서_조회한_결제가_다른_주문이면_pending으로_둔다() {
    Organization organization = organization();
    UUID attemptId = doneAttempt(organization);
    UUID cancellationId = pendingCancellation(organization, attemptId, Duration.ofMinutes(11));
    when(tossPaymentsClient.findPayment(organization.secretKey(), paymentKey(attemptId)))
        .thenReturn(
            new TossPaymentsResult.Success<>(
                new TossPaymentsPayment(
                    paymentKey(attemptId),
                    UUID.randomUUID().toString(),
                    "PARTIAL_CANCELED",
                    13200,
                    8200,
                    List.of(
                        new TossPaymentsPayment.Cancel(
                            "tk_" + cancellationId,
                            5000,
                            OffsetDateTime.now().minusMinutes(11),
                            "DONE")))));

    PaymentJobResult result = paymentAttemptConfirmationService.confirm();

    assertThat(cancellationRow(cancellationId)).containsEntry("status", "pending");
    assertThat(result.processedCount()).isZero();
  }

  private static TossPaymentsResult<TossPaymentsPayment> found(
      UUID attemptId, String paymentKey, String status) {
    return new TossPaymentsResult.Success<>(
        new TossPaymentsPayment(paymentKey, attemptId.toString(), status, 13200, 13200, List.of()));
  }

  private static String paymentKey(UUID attemptId) {
    return "tgen_" + attemptId;
  }

  private Map<String, Object> attemptRow(UUID attemptId) {
    return jdbcTemplate.queryForMap("SELECT * FROM payment_attempt WHERE id = ?", attemptId);
  }

  private Map<String, Object> cancellationRow(UUID cancellationId) {
    return jdbcTemplate.queryForMap(
        "SELECT * FROM payment_cancellation WHERE id = ?", cancellationId);
  }

  private long pendingOlderThan(Duration age) {
    return pendingAttemptsOlderThan(age) + pendingRowsOlderThan("payment_cancellation", age);
  }

  private long pendingAttemptsOlderThan(Duration age) {
    return pendingRowsOlderThan("payment_attempt", age);
  }

  private long pendingRowsOlderThan(String table, Duration age) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM "
            + table
            + " WHERE status = 'pending' AND requested_at < now() - make_interval(secs => ?)",
        Long.class,
        age.toSeconds());
  }

  private double attemptCount(String result) {
    return meterRegistry
        .get("meterengine.payment.attempt")
        .tags("mode", "test", "result", result)
        .counter()
        .count();
  }

  private double cancellationCount(String result) {
    return meterRegistry
        .get("meterengine.payment.cancellation")
        .tags("mode", "test", "result", result)
        .counter()
        .count();
  }

  private UUID pendingAttempt(Organization organization, Duration age) {
    UUID attemptId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO payment_attempt
          (id, organization_id, customer_id, invoice_id, mode, amount, order_name, status,
           requested_at)
        VALUES (?, ?, ?, ?, 'test', 13200, '2026-08 사용료', 'pending',
                now() - make_interval(secs => ?))
        """,
        attemptId,
        organization.organizationId(),
        organization.customerId(),
        invoice(organization),
        age.toSeconds());
    return attemptId;
  }

  private UUID doneAttempt(Organization organization) {
    UUID attemptId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO payment_attempt
          (id, organization_id, customer_id, invoice_id, mode, amount, order_name, status,
           payment_key, requested_at, completed_at)
        VALUES (?, ?, ?, ?, 'test', 13200, '2026-08 사용료', 'done', ?,
                now() - interval '1 hour', now() - interval '1 hour')
        """,
        attemptId,
        organization.organizationId(),
        organization.customerId(),
        invoice(organization),
        paymentKey(attemptId));
    return attemptId;
  }

  private UUID pendingCancellation(Organization organization, UUID attemptId, Duration age) {
    UUID cancellationId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO payment_cancellation
          (id, organization_id, payment_attempt_id, mode, amount, reason, status, requested_at)
        VALUES (?, ?, ?, 'test', 5000, '고객 요청', 'pending', now() - make_interval(secs => ?))
        """,
        cancellationId,
        organization.organizationId(),
        attemptId,
        age.toSeconds());
    return cancellationId;
  }

  private UUID invoice(Organization organization) {
    return invoiceRepository
        .save(
            new Invoice(
                UUID.randomUUID(),
                organization.organizationId(),
                organization.customerId(),
                "확정 고객",
                "2026-%02d".formatted(++invoiceCount),
                12000,
                1200,
                OffsetDateTime.parse("2026-09-01T04:00:00Z"),
                OffsetDateTime.parse("2026-09-01T05:00:00Z")))
        .getId();
  }

  private Organization organization() {
    UUID organizationId =
        jdbcTemplate.queryForObject(
            "INSERT INTO organization (name) VALUES ('확정 도입사') RETURNING id", UUID.class);
    UUID customerId =
        jdbcTemplate.queryForObject(
            "INSERT INTO customer (organization_id, name) VALUES (?, '확정 고객') RETURNING id",
            UUID.class,
            organizationId);
    String secretKey = "test_sk_" + UUID.randomUUID().toString().replace("-", "");
    tossPaymentsCredentialRepository.saveAndFlush(
        new TossPaymentsCredential(
            organizationId,
            "test",
            CLIENT_KEY,
            secretKey,
            OffsetDateTime.parse("2026-10-01T00:00:00Z")));
    return new Organization(organizationId, customerId, secretKey);
  }

  private record Organization(UUID organizationId, UUID customerId, String secretKey) {}
}
