package com.meterengine.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.meterengine.TestcontainersConfiguration;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.invoice.entity.Invoice;
import com.meterengine.invoice.repository.InvoiceRepository;
import com.meterengine.payment.client.TossPaymentsClient;
import com.meterengine.payment.client.TossPaymentsPayment;
import com.meterengine.payment.client.TossPaymentsResult;
import com.meterengine.payment.dto.PaymentAttemptResponse;
import com.meterengine.payment.entity.BillingKey;
import com.meterengine.payment.entity.PaymentAttempt;
import com.meterengine.payment.entity.TossPaymentsCredential;
import com.meterengine.payment.repository.BillingKeyRepository;
import com.meterengine.payment.repository.PaymentAttemptRepository;
import com.meterengine.payment.repository.TossPaymentsCredentialRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PaymentAttemptServiceTest {

  private static final String SECRET_KEY = "test_sk_zXLkKEypNArWmo50nX3lmeaxYG5R";
  private static final String BILLING_KEY = "approvedAfterFailureBillingKey0001";

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private PaymentAttemptRepository paymentAttemptRepository;
  @Autowired private TossPaymentsCredentialRepository tossPaymentsCredentialRepository;
  @Autowired private BillingKeyRepository billingKeyRepository;
  @Autowired private MeterRegistry meterRegistry;
  @Autowired private PaymentAttemptService paymentAttemptService;
  @Autowired private PlatformTransactionManager platformTransactionManager;

  // 토스 클라이언트를 목으로 바꿔 이 테스트를 결제 통합 테스트와 같은 컨텍스트에서 돌린다.
  @MockitoBean private TossPaymentsClient tossPaymentsClient;

  @Test
  void 승인할_때_카드가_없으면_토스를_부르지_않고_payment_method_missing으로_실패시킨다() {
    Invoice invoice = invoice();
    insertCredential(invoice.getOrganizationId());

    PaymentAttemptResponse response = paymentAttemptService.approve(pendingAttempt(invoice), null);

    assertFailedWithoutToss(response, invoice, ErrorCode.PAYMENT_METHOD_MISSING);
  }

  @Test
  void 승인할_때_키가_없으면_토스를_부르지_않고_toss_payments_credential_missing으로_실패시킨다() {
    Invoice invoice = invoice();

    PaymentAttemptResponse response = paymentAttemptService.approve(pendingAttempt(invoice), null);

    assertFailedWithoutToss(response, invoice, ErrorCode.TOSS_PAYMENTS_CREDENTIAL_MISSING);
  }

  @Test
  void 승인_직전에_키를_보관했으면_고정한_키_버전이_있어도_토스를_부르지_않고_실패시킨다() {
    Invoice invoice = invoice();
    insertCredential(invoice.getOrganizationId());
    insertBillingKey(invoice);
    PaymentAttempt attempt =
        paymentAttemptRepository.saveAndFlush(
            PaymentAttempt.pending(
                    UUID.randomUUID(),
                    invoice.getOrganizationId(),
                    invoice.getCustomerId(),
                    invoice.getId(),
                    "test",
                    invoice.getTotalAmount(),
                    "2026-08 사용료")
                .pinCredential(OffsetDateTime.parse("2026-10-01T00:00:00Z")));
    jdbcTemplate.update(
        "UPDATE toss_payments_credential SET archived_at = now() WHERE organization_id = ?",
        invoice.getOrganizationId());

    PaymentAttemptResponse response = paymentAttemptService.approve(attempt, null);

    assertFailedWithoutToss(response, invoice, ErrorCode.TOSS_PAYMENTS_CREDENTIAL_MISSING);
  }

  @Test
  @ExtendWith(OutputCaptureExtension.class)
  void 토스가_승인했는데_시도가_이미_NOT_FOUND_PAYMENT로_failed면_그대로_두고_error_로그를_남긴다(CapturedOutput output) {
    Invoice invoice = invoice();
    insertCredential(invoice.getOrganizationId());
    insertBillingKey(invoice);
    PaymentAttempt attempt = pendingAttempt(invoice);
    when(tossPaymentsClient.approveBilling(anyString(), anyString(), any(), anyString(), isNull()))
        .thenAnswer(
            invocation -> {
              jdbcTemplate.update(
                  "UPDATE payment_attempt SET status = 'failed',"
                      + " failure_code = 'NOT_FOUND_PAYMENT', failure_message = '결제 기록 없음',"
                      + " completed_at = now() WHERE id = ?",
                  attempt.getId());
              return new TossPaymentsResult.Success<>(
                  new TossPaymentsPayment(
                      "tgen_" + attempt.getId(),
                      attempt.getId().toString(),
                      "DONE",
                      invoice.getTotalAmount(),
                      invoice.getTotalAmount(),
                      List.of()));
            });
    double doneBefore = attemptCount("done");

    PaymentAttemptResponse response = paymentAttemptService.approve(attempt, null);

    assertThat(response.status()).isEqualTo("failed");
    assertThat(response.failureCode()).isEqualTo("NOT_FOUND_PAYMENT");
    assertThat(response.paymentKey()).isNull();
    assertThat(attemptCount("done")).isEqualTo(doneBefore);
    assertThat(output.getAll())
        .containsPattern(
            "ERROR.*toss payments approved a payment attempt already marked failed: attempt_id="
                + attempt.getId()
                + ", organization_id="
                + invoice.getOrganizationId()
                + ", mode=test, failure_code=NOT_FOUND_PAYMENT")
        .doesNotContain(SECRET_KEY)
        .doesNotContain(BILLING_KEY);
  }

  @Test
  void 승인_전이가_중복돼도_웹훅_이벤트는_하나만_남는다() {
    Invoice invoice = invoice();
    PaymentAttempt attempt = pendingAttempt(invoice);

    assertThat(paymentAttemptService.markDone(attempt, "payment-" + attempt.getId())).isTrue();
    assertThat(paymentAttemptService.markDone(attempt, "payment-" + attempt.getId())).isFalse();

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM webhook_event WHERE organization_id = ? AND type = 'payment_succeeded'",
                Long.class,
                invoice.getOrganizationId()))
        .isEqualTo(1L);
  }

  @Test
  void 실패_전이가_롤백되면_웹훅_이벤트도_롤백된다() {
    Invoice invoice = invoice();
    PaymentAttempt attempt = pendingAttempt(invoice);

    new TransactionTemplate(platformTransactionManager)
        .executeWithoutResult(
            status -> {
              assertThat(paymentAttemptService.markFailed(attempt, "REJECT_CARD_PAYMENT", "거절"))
                  .isTrue();
              status.setRollbackOnly();
            });

    assertThat(paymentAttemptRepository.findById(attempt.getId()).orElseThrow().getStatus())
        .isEqualTo("pending");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM webhook_event WHERE organization_id = ?",
                Long.class,
                invoice.getOrganizationId()))
        .isZero();
  }

  private double attemptCount(String result) {
    return meterRegistry
        .get("meterengine.payment.attempt")
        .tags("mode", "test", "result", result)
        .counter()
        .count();
  }

  private void insertBillingKey(Invoice invoice) {
    billingKeyRepository.saveAndFlush(
        new BillingKey(
            invoice.getOrganizationId(),
            invoice.getCustomerId(),
            "test",
            BILLING_KEY,
            "61",
            "43301234****123*",
            OffsetDateTime.parse("2026-10-01T00:00:00Z"),
            "2026-10-01",
            OffsetDateTime.parse("2026-10-01T00:00:00Z")));
  }

  private void assertFailedWithoutToss(
      PaymentAttemptResponse response, Invoice invoice, ErrorCode reason) {
    assertThat(response.status()).isEqualTo("failed");
    assertThat(response.failureCode()).isEqualTo(reason.getCode());
    assertThat(response.failureMessage()).isEqualTo(reason.getMessage());
    assertThat(response.completedAt()).isNotNull();
    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            "SELECT * FROM payment_attempt WHERE invoice_id = ?", invoice.getId());
    assertThat(row)
        .containsEntry("status", "failed")
        .containsEntry("failure_code", reason.getCode())
        .containsEntry("payment_key", null);
    verifyNoInteractions(tossPaymentsClient);
  }

  private PaymentAttempt pendingAttempt(Invoice invoice) {
    return paymentAttemptRepository.saveAndFlush(
        PaymentAttempt.pending(
            UUID.randomUUID(),
            invoice.getOrganizationId(),
            invoice.getCustomerId(),
            invoice.getId(),
            "test",
            invoice.getTotalAmount(),
            "2026-08 사용료"));
  }

  private void insertCredential(UUID organizationId) {
    tossPaymentsCredentialRepository.saveAndFlush(
        new TossPaymentsCredential(
            organizationId,
            "test",
            "test_ck_D5GePWvyJnrK0W0k6q8gLzN97Eoq",
            SECRET_KEY,
            OffsetDateTime.parse("2026-10-01T00:00:00Z")));
  }

  private Invoice invoice() {
    UUID organizationId =
        jdbcTemplate.queryForObject(
            "INSERT INTO organization (name) VALUES ('승인 도입사') RETURNING id", UUID.class);
    UUID customerId =
        jdbcTemplate.queryForObject(
            "INSERT INTO customer (organization_id, name) VALUES (?, '승인 고객') RETURNING id",
            UUID.class,
            organizationId);
    return invoiceRepository.save(
        new Invoice(
            UUID.randomUUID(),
            organizationId,
            customerId,
            "승인 고객",
            "2026-08",
            12000,
            1200,
            OffsetDateTime.parse("2026-09-01T04:00:00Z"),
            OffsetDateTime.parse("2026-09-01T05:00:00Z")));
  }
}
