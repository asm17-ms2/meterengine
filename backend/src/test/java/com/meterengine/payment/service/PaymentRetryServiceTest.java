package com.meterengine.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meterengine.TestcontainersConfiguration;
import com.meterengine.invoice.entity.Invoice;
import com.meterengine.invoice.repository.InvoiceRepository;
import com.meterengine.payment.client.TossPaymentsApproveBillingRequest;
import com.meterengine.payment.client.TossPaymentsClient;
import com.meterengine.payment.client.TossPaymentsPayment;
import com.meterengine.payment.client.TossPaymentsResult;
import com.meterengine.payment.dto.PaymentJobResult;
import com.meterengine.payment.entity.BillingKey;
import com.meterengine.payment.entity.PaymentAttempt;
import com.meterengine.payment.entity.TossPaymentsCredential;
import com.meterengine.payment.repository.BillingKeyRepository;
import com.meterengine.payment.repository.PaymentAttemptRepository;
import com.meterengine.payment.repository.TossPaymentsCredentialRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PaymentRetryServiceTest {

  private static final String CLIENT_KEY = "test_ck_D5GePWvyJnrK0W0k6q8gLzN97Eoq";
  private static final String RETRYABLE_FAILURE_CODE = "REJECT_CARD_COMPANY";

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private TossPaymentsCredentialRepository tossPaymentsCredentialRepository;
  @Autowired private BillingKeyRepository billingKeyRepository;
  @Autowired private MeterRegistry meterRegistry;
  @Autowired private PaymentRetryService paymentRetryService;
  @Autowired private PaymentAttemptService paymentAttemptService;
  @Autowired private PaymentAttemptRepository paymentAttemptRepository;

  @MockitoBean private TossPaymentsClient tossPaymentsClient;

  // 다른 도입사 후보의 토스 승인과 주문 조회에 결과 모름으로 답한다.
  @BeforeEach
  void stubOtherCandidates() {
    when(tossPaymentsClient.approveBilling(anyString(), anyString(), any(), anyString(), any()))
        .thenReturn(new TossPaymentsResult.Unknown<>("다른 테스트의 후보"));
    when(tossPaymentsClient.findPaymentByOrderId(anyString(), anyString()))
        .thenReturn(new TossPaymentsResult.Unknown<>("다른 테스트의 후보"));
  }

  @Test
  void 재시도_간격이_지난_실패_시도는_새_시도와_새_orderId로_다시_승인한다() {
    Organization organization = organization(true);
    UUID invoiceId = invoice(organization);
    UUID failedAttemptId =
        failedAttempt(organization, invoiceId, RETRYABLE_FAILURE_CODE, Duration.ofHours(25));
    when(tossPaymentsClient.approveBilling(
            eq(organization.secretKey()), anyString(), any(), anyString(), isNull()))
        .thenAnswer(
            invocation -> {
              TossPaymentsApproveBillingRequest approval = invocation.getArgument(2);
              return new TossPaymentsResult.Success<>(
                  new TossPaymentsPayment(
                      "tgen_" + approval.orderId(),
                      approval.orderId(),
                      "DONE",
                      approval.amount(),
                      approval.amount(),
                      List.of()));
            });

    PaymentJobResult result = paymentRetryService.retry();

    List<Map<String, Object>> attempts = attemptRows(invoiceId);
    assertThat(attempts).hasSize(2);
    Map<String, Object> retried = attempts.getFirst();
    UUID retriedAttemptId = (UUID) retried.get("id");
    assertThat(retriedAttemptId).isNotEqualTo(failedAttemptId);
    assertThat(retried)
        .containsEntry("status", "done")
        .containsEntry("mode", "test")
        .containsEntry("amount", 13200L)
        .containsEntry("payment_key", "tgen_" + retriedAttemptId);
    ArgumentCaptor<TossPaymentsApproveBillingRequest> approval =
        ArgumentCaptor.forClass(TossPaymentsApproveBillingRequest.class);
    ArgumentCaptor<String> idempotencyKey = ArgumentCaptor.forClass(String.class);
    verify(tossPaymentsClient)
        .approveBilling(
            eq(organization.secretKey()),
            eq(organization.billingKey()),
            approval.capture(),
            idempotencyKey.capture(),
            isNull());
    assertThat(approval.getValue().orderId()).isEqualTo(retriedAttemptId.toString());
    assertThat(idempotencyKey.getValue()).isEqualTo(retriedAttemptId.toString());
    assertThat(result.processedCount()).isPositive();
    assertThat(result.failedCount()).isZero();
  }

  @Test
  void 실패_뒤에_더_늦은_done_시도가_있으면_다시_하지_않는다() {
    Organization organization = organization(true);
    UUID invoiceId = invoice(organization);
    failedAttempt(organization, invoiceId, RETRYABLE_FAILURE_CODE, Duration.ofHours(26));
    laterAttempt(organization, invoiceId, "done", Duration.ofHours(25));

    PaymentJobResult result = paymentRetryService.retry();

    assertNotRetried(organization, invoiceId, 2);
    assertThat(result.failedCount()).isZero();
  }

  @Test
  void 실패_뒤에_더_늦은_pending_시도가_있으면_다시_하지_않는다() {
    Organization organization = organization(true);
    UUID invoiceId = invoice(organization);
    failedAttempt(organization, invoiceId, RETRYABLE_FAILURE_CODE, Duration.ofHours(26));
    laterAttempt(organization, invoiceId, "pending", Duration.ofHours(25));

    PaymentJobResult result = paymentRetryService.retry();

    assertNotRetried(organization, invoiceId, 2);
    assertThat(result.failedCount()).isZero();
  }

  @Test
  void 재시도_두_개가_겹쳐도_새_시도는_하나이고_토스는_한_번_부른다() throws Exception {
    Organization organization = organization(true);
    UUID invoiceId = invoice(organization);
    failedAttempt(organization, invoiceId, RETRYABLE_FAILURE_CODE, Duration.ofHours(25));
    CountDownLatch approvalEntered = new CountDownLatch(1);
    CountDownLatch approvalReleased = new CountDownLatch(1);
    when(tossPaymentsClient.approveBilling(
            eq(organization.secretKey()), anyString(), any(), anyString(), isNull()))
        .thenAnswer(
            invocation -> {
              approvalEntered.countDown();
              if (!approvalReleased.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("승인 응답을 풀어 주지 않았다");
              }
              return new TossPaymentsResult.Rejected<>(400, RETRYABLE_FAILURE_CODE, "거절");
            });
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<PaymentJobResult> first = executor.submit(paymentRetryService::retry);
      assertThat(approvalEntered.await(10, TimeUnit.SECONDS)).isTrue();

      paymentRetryService.retry();
      approvalReleased.countDown();
      first.get(10, TimeUnit.SECONDS);
    } finally {
      approvalReleased.countDown();
      executor.shutdownNow();
    }

    assertThat(attemptRows(invoiceId)).hasSize(2);
    verify(tossPaymentsClient)
        .approveBilling(eq(organization.secretKey()), anyString(), any(), anyString(), any());
  }

  @Test
  void 후보를_읽은_뒤_더_늦은_실패_시도가_생기면_새_시도를_만들지_않는다() throws Exception {
    Organization organization = organization(true);
    UUID invoiceId = invoice(organization);
    UUID failedAttemptId =
        failedAttempt(organization, invoiceId, "NOT_FOUND_PAYMENT", Duration.ofHours(25));
    CountDownLatch lookupEntered = new CountDownLatch(1);
    CountDownLatch lookupReleased = new CountDownLatch(1);
    when(tossPaymentsClient.findPaymentByOrderId(
            organization.secretKey(), failedAttemptId.toString()))
        .thenAnswer(
            invocation -> {
              lookupEntered.countDown();
              if (!lookupReleased.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("주문 조회 응답을 풀어 주지 않았다");
              }
              return new TossPaymentsResult.Rejected<>(404, "NOT_FOUND_PAYMENT", "없는 결제");
            });
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<PaymentJobResult> retried = executor.submit(paymentRetryService::retry);
      assertThat(lookupEntered.await(10, TimeUnit.SECONDS)).isTrue();

      failedAttempt(organization, invoiceId, RETRYABLE_FAILURE_CODE, Duration.ofMinutes(1));
      lookupReleased.countDown();
      retried.get(10, TimeUnit.SECONDS);
    } finally {
      lookupReleased.countDown();
      executor.shutdownNow();
    }

    assertNotRetried(organization, invoiceId, 2);
  }

  @Test
  void 재시도_간격이_지나지_않았으면_새_시도를_만들지_않는다() {
    Organization organization = organization(true);
    UUID invoiceId = invoice(organization);
    failedAttempt(organization, invoiceId, RETRYABLE_FAILURE_CODE, Duration.ofHours(1));

    paymentRetryService.retry();

    assertNotRetried(organization, invoiceId, 1);
  }

  @Test
  void 시도_수가_상한에_이르면_새_시도를_만들지_않는다() {
    Organization organization = organization(true);
    UUID invoiceId = invoice(organization);
    failedAttempt(organization, invoiceId, RETRYABLE_FAILURE_CODE, Duration.ofHours(75));
    failedAttempt(organization, invoiceId, RETRYABLE_FAILURE_CODE, Duration.ofHours(50));
    failedAttempt(organization, invoiceId, RETRYABLE_FAILURE_CODE, Duration.ofHours(25));

    paymentRetryService.retry();

    assertNotRetried(organization, invoiceId, 3);
  }

  @Test
  void 실패_코드가_INVALID_CARD_EXPIRATION이면_새_시도를_만들지_않는다() {
    Organization organization = organization(true);
    UUID invoiceId = invoice(organization);
    failedAttempt(organization, invoiceId, "INVALID_CARD_EXPIRATION", Duration.ofHours(25));

    paymentRetryService.retry();

    assertNotRetried(organization, invoiceId, 1);
  }

  @Test
  void 카드_오류로_실패했어도_그_뒤에_카드를_다시_등록했으면_다시_승인한다() {
    Organization organization = organization(true, OffsetDateTime.now().minus(Duration.ofHours(1)));
    UUID invoiceId = invoice(organization);
    UUID failedAttemptId =
        failedAttempt(organization, invoiceId, "INVALID_CARD_EXPIRATION", Duration.ofHours(25));
    when(tossPaymentsClient.approveBilling(
            eq(organization.secretKey()), anyString(), any(), anyString(), isNull()))
        .thenReturn(new TossPaymentsResult.Rejected<>(400, RETRYABLE_FAILURE_CODE, "거절"));

    paymentRetryService.retry();

    List<Map<String, Object>> attempts = attemptRows(invoiceId);
    assertThat(attempts).hasSize(2);
    assertThat(attempts.getFirst().get("id")).isNotEqualTo(failedAttemptId);
    verify(tossPaymentsClient)
        .approveBilling(
            eq(organization.secretKey()), eq(organization.billingKey()), any(), anyString(), any());
  }

  @Test
  void 카드_오류로_실패하기_전에_등록한_카드뿐이면_다시_하지_않는다() {
    Organization organization =
        organization(true, OffsetDateTime.now().minus(Duration.ofHours(26)));
    UUID invoiceId = invoice(organization);
    failedAttempt(organization, invoiceId, "INVALID_CARD_EXPIRATION", Duration.ofHours(25));

    paymentRetryService.retry();

    assertNotRetried(organization, invoiceId, 1);
  }

  @Test
  void NOT_FOUND_PAYMENT로_실패한_시도의_주문이_토스에_DONE이면_다시_청구하지_않고_skipped로_센다() {
    Organization organization = organization(true);
    UUID invoiceId = invoice(organization);
    UUID failedAttemptId =
        failedAttempt(organization, invoiceId, "NOT_FOUND_PAYMENT", Duration.ofHours(25));
    when(tossPaymentsClient.findPaymentByOrderId(
            organization.secretKey(), failedAttemptId.toString()))
        .thenReturn(
            new TossPaymentsResult.Success<>(
                new TossPaymentsPayment(
                    "tgen_" + failedAttemptId,
                    failedAttemptId.toString(),
                    "DONE",
                    13200,
                    13200,
                    List.of())));

    PaymentJobResult result = paymentRetryService.retry();

    assertNotRetried(organization, invoiceId, 1);
    assertThat(result.skippedCount()).isPositive();
  }

  @Test
  void NOT_FOUND_PAYMENT로_실패한_시도의_주문이_토스에_CANCELED면_다시_청구하지_않는다() {
    Organization organization = organization(true);
    UUID invoiceId = invoice(organization);
    UUID failedAttemptId =
        failedAttempt(organization, invoiceId, "NOT_FOUND_PAYMENT", Duration.ofHours(25));
    lookupReturns(organization, failedAttemptId, failedAttemptId.toString(), "CANCELED");

    PaymentJobResult result = paymentRetryService.retry();

    assertNotRetried(organization, invoiceId, 1);
    assertThat(result.skippedCount()).isPositive();
  }

  @Test
  void NOT_FOUND_PAYMENT로_실패한_시도의_주문_조회가_다른_orderId를_주면_다시_청구하지_않는다() {
    Organization organization = organization(true);
    UUID invoiceId = invoice(organization);
    UUID failedAttemptId =
        failedAttempt(organization, invoiceId, "NOT_FOUND_PAYMENT", Duration.ofHours(25));
    lookupReturns(organization, failedAttemptId, UUID.randomUUID().toString(), "ABORTED");

    PaymentJobResult result = paymentRetryService.retry();

    assertNotRetried(organization, invoiceId, 1);
    assertThat(result.skippedCount()).isPositive();
  }

  @Test
  void NOT_FOUND_PAYMENT로_실패한_시도의_주문이_토스에_ABORTED면_다시_승인한다() {
    Organization organization = organization(true);
    UUID invoiceId = invoice(organization);
    UUID failedAttemptId =
        failedAttempt(organization, invoiceId, "NOT_FOUND_PAYMENT", Duration.ofHours(25));
    lookupReturns(organization, failedAttemptId, failedAttemptId.toString(), "ABORTED");
    when(tossPaymentsClient.approveBilling(
            eq(organization.secretKey()), anyString(), any(), anyString(), isNull()))
        .thenReturn(new TossPaymentsResult.Rejected<>(400, RETRYABLE_FAILURE_CODE, "거절"));

    paymentRetryService.retry();

    assertThat(attemptRows(invoiceId)).hasSize(2);
    verify(tossPaymentsClient)
        .approveBilling(eq(organization.secretKey()), anyString(), any(), anyString(), any());
  }

  @Test
  void NOT_FOUND_PAYMENT로_실패한_시도의_주문이_토스에도_404_NOT_FOUND_PAYMENT면_다시_승인한다() {
    Organization organization = organization(true);
    UUID invoiceId = invoice(organization);
    UUID failedAttemptId =
        failedAttempt(organization, invoiceId, "NOT_FOUND_PAYMENT", Duration.ofHours(25));
    when(tossPaymentsClient.findPaymentByOrderId(
            organization.secretKey(), failedAttemptId.toString()))
        .thenReturn(new TossPaymentsResult.Rejected<>(404, "NOT_FOUND_PAYMENT", "없는 결제"));
    when(tossPaymentsClient.approveBilling(
            eq(organization.secretKey()), anyString(), any(), anyString(), isNull()))
        .thenReturn(new TossPaymentsResult.Rejected<>(400, RETRYABLE_FAILURE_CODE, "거절"));

    paymentRetryService.retry();

    assertThat(attemptRows(invoiceId)).hasSize(2);
    verify(tossPaymentsClient)
        .approveBilling(eq(organization.secretKey()), anyString(), any(), anyString(), any());
  }

  @Test
  void NOT_FOUND_PAYMENT로_실패한_시도의_주문_조회_결과를_모르거나_404_말고_거절이면_다시_하지_않는다() {
    Organization unknownOrganization = organization(true);
    UUID unknownInvoiceId = invoice(unknownOrganization);
    failedAttempt(unknownOrganization, unknownInvoiceId, "NOT_FOUND_PAYMENT", Duration.ofHours(25));
    Organization rejectedOrganization = organization(true);
    UUID rejectedInvoiceId = invoice(rejectedOrganization);
    UUID rejectedAttemptId =
        failedAttempt(
            rejectedOrganization, rejectedInvoiceId, "NOT_FOUND_PAYMENT", Duration.ofHours(25));
    when(tossPaymentsClient.findPaymentByOrderId(
            rejectedOrganization.secretKey(), rejectedAttemptId.toString()))
        .thenReturn(new TossPaymentsResult.Rejected<>(401, "UNAUTHORIZED_KEY", "인증되지 않은 키"));

    PaymentJobResult result = paymentRetryService.retry();

    assertNotRetried(unknownOrganization, unknownInvoiceId, 1);
    assertNotRetried(rejectedOrganization, rejectedInvoiceId, 1);
    assertThat(result.skippedCount()).isGreaterThanOrEqualTo(2);
    assertThat(result.failedCount()).isZero();
  }

  @Test
  void 지워진_빌링키로_실패했어도_지금_빌링키가_있으면_다시_승인한다() {
    Organization organization = organization(true);
    UUID invoiceId = invoice(organization);
    failedAttempt(organization, invoiceId, "ALREADY_REMOVED_BILLING_KEY", Duration.ofHours(25));
    when(tossPaymentsClient.approveBilling(
            eq(organization.secretKey()), anyString(), any(), anyString(), isNull()))
        .thenReturn(new TossPaymentsResult.Rejected<>(400, RETRYABLE_FAILURE_CODE, "거절"));

    paymentRetryService.retry();

    assertThat(attemptRows(invoiceId)).hasSize(2);
    verify(tossPaymentsClient)
        .approveBilling(
            eq(organization.secretKey()), eq(organization.billingKey()), any(), anyString(), any());
  }

  @Test
  void 지워진_빌링키로_실패하고_지금_빌링키가_없으면_다시_하지_않는다() {
    Organization organization = organization(false);
    UUID invoiceId = invoice(organization);
    failedAttempt(organization, invoiceId, "ALREADY_REMOVED_BILLING_KEY", Duration.ofHours(25));

    paymentRetryService.retry();

    assertNotRetried(organization, invoiceId, 1);
  }

  @Test
  void NOT_FOUND_PAYMENT로_실패한_지_7일이_넘은_시도는_토스에_묻지_않고_다시_하지_않는다() {
    Organization organization = organization(true);
    UUID invoiceId = invoice(organization);
    UUID failedAttemptId =
        failedAttempt(
            organization, invoiceId, "NOT_FOUND_PAYMENT", Duration.ofDays(7).plusMinutes(1));

    paymentRetryService.retry();

    assertNotRetried(organization, invoiceId, 1);
    verify(tossPaymentsClient, never())
        .findPaymentByOrderId(organization.secretKey(), failedAttemptId.toString());
  }

  @Test
  void NOT_FOUND_PAYMENT로_실패한_시도_뒤에_키가_바뀌었으면_404여도_다시_청구하지_않는다() {
    Organization organization = organization(true);
    UUID invoiceId = invoice(organization);
    UUID failedAttemptId =
        failedAttempt(organization, invoiceId, "NOT_FOUND_PAYMENT", Duration.ofHours(25));
    jdbcTemplate.update(
        "UPDATE toss_payments_credential SET updated_at = now() WHERE organization_id = ?",
        organization.organizationId());
    when(tossPaymentsClient.findPaymentByOrderId(
            organization.secretKey(), failedAttemptId.toString()))
        .thenReturn(new TossPaymentsResult.Rejected<>(404, "NOT_FOUND_PAYMENT", "없는 결제"));

    PaymentJobResult result = paymentRetryService.retry();

    assertNotRetried(organization, invoiceId, 1);
    assertThat(result.skippedCount()).isPositive();
  }

  @Test
  void 현재_결제_모드가_아닌_실패_시도는_다시_하지_않는다() {
    Organization organization = organization(true);
    UUID invoiceId = invoice(organization);
    failedAttempt(organization, invoiceId, RETRYABLE_FAILURE_CODE, Duration.ofHours(25));
    jdbcTemplate.update(
        "INSERT INTO payment_setting (organization_id, mode, updated_at) VALUES (?, 'live', now())",
        organization.organizationId());

    paymentRetryService.retry();

    assertNotRetried(organization, invoiceId, 1);
  }

  @Test
  void 빌링키가_없으면_새_시도를_만들지_않고_skipped로_센다() {
    Organization organization = organization(false);
    UUID invoiceId = invoice(organization);
    failedAttempt(organization, invoiceId, RETRYABLE_FAILURE_CODE, Duration.ofHours(25));

    PaymentJobResult result = paymentRetryService.retry();

    assertNotRetried(organization, invoiceId, 1);
    assertThat(result.skippedCount()).isPositive();
  }

  @Test
  void 세_번째_시도가_실패하면_retry_exhausted가_한_번_오르고_상한을_넘긴_실패는_세지_않는다() {
    Organization organization = organization(true);
    UUID invoiceId = invoice(organization);
    failedAttempt(organization, invoiceId, RETRYABLE_FAILURE_CODE, Duration.ofHours(50));
    failedAttempt(organization, invoiceId, RETRYABLE_FAILURE_CODE, Duration.ofHours(25));
    when(tossPaymentsClient.approveBilling(
            eq(organization.secretKey()), anyString(), any(), anyString(), isNull()))
        .thenReturn(new TossPaymentsResult.Rejected<>(400, RETRYABLE_FAILURE_CODE, "카드사에서 거절했습니다"));
    double exhaustedBefore = exhaustedCount();

    paymentRetryService.retry();

    List<Map<String, Object>> attempts = attemptRows(invoiceId);
    assertThat(attempts).hasSize(3);
    assertThat(attempts.getFirst())
        .containsEntry("status", "failed")
        .containsEntry("failure_code", RETRYABLE_FAILURE_CODE);
    assertThat(exhaustedCount()).isEqualTo(exhaustedBefore + 1);

    laterAttempt(organization, invoiceId, "pending", Duration.ZERO);
    PaymentAttempt fourth =
        paymentAttemptRepository.findById(attemptIdWithStatus(invoiceId, "pending")).orElseThrow();
    assertThat(paymentAttemptService.markFailed(fourth, RETRYABLE_FAILURE_CODE, "거절")).isTrue();
    assertThat(attemptRows(invoiceId)).hasSize(4);
    assertThat(exhaustedCount()).isEqualTo(exhaustedBefore + 1);
  }

  private UUID attemptIdWithStatus(UUID invoiceId, String status) {
    return jdbcTemplate.queryForObject(
        "SELECT id FROM payment_attempt WHERE invoice_id = ? AND status = ?",
        UUID.class,
        invoiceId,
        status);
  }

  private void lookupReturns(
      Organization organization, UUID failedAttemptId, String orderId, String status) {
    when(tossPaymentsClient.findPaymentByOrderId(
            organization.secretKey(), failedAttemptId.toString()))
        .thenReturn(
            new TossPaymentsResult.Success<>(
                new TossPaymentsPayment(null, orderId, status, 13200, 13200, List.of())));
  }

  private void assertNotRetried(Organization organization, UUID invoiceId, int attemptCount) {
    assertThat(attemptRows(invoiceId)).hasSize(attemptCount);
    verify(tossPaymentsClient, never())
        .approveBilling(eq(organization.secretKey()), anyString(), any(), anyString(), any());
  }

  private List<Map<String, Object>> attemptRows(UUID invoiceId) {
    return jdbcTemplate.queryForList(
        "SELECT * FROM payment_attempt WHERE invoice_id = ? ORDER BY requested_at DESC, id DESC",
        invoiceId);
  }

  private double exhaustedCount() {
    return meterRegistry
        .get("meterengine.payment.retry.exhausted")
        .tags("mode", "test")
        .counter()
        .count();
  }

  private UUID failedAttempt(
      Organization organization, UUID invoiceId, String failureCode, Duration age) {
    UUID attemptId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO payment_attempt
          (id, organization_id, customer_id, invoice_id, mode, amount, order_name, status,
           failure_code, failure_message, requested_at, completed_at)
        VALUES (?, ?, ?, ?, 'test', 13200, '2026-08 사용료', 'failed', ?, '거절',
                now() - make_interval(secs => ?), now() - make_interval(secs => ?))
        """,
        attemptId,
        organization.organizationId(),
        organization.customerId(),
        invoiceId,
        failureCode,
        age.toSeconds() + 1,
        age.toSeconds());
    return attemptId;
  }

  private void laterAttempt(
      Organization organization, UUID invoiceId, String status, Duration age) {
    UUID attemptId = UUID.randomUUID();
    boolean done = "done".equals(status);
    jdbcTemplate.update(
        """
        INSERT INTO payment_attempt
          (id, organization_id, customer_id, invoice_id, mode, amount, order_name, status,
           payment_key, requested_at, completed_at)
        VALUES (?, ?, ?, ?, 'test', 13200, '2026-08 사용료', ?, ?,
                now() - make_interval(secs => ?), ?)
        """,
        attemptId,
        organization.organizationId(),
        organization.customerId(),
        invoiceId,
        status,
        done ? "tgen_" + attemptId : null,
        age.toSeconds(),
        done ? OffsetDateTime.now().minus(age) : null);
  }

  private UUID invoice(Organization organization) {
    return invoiceRepository
        .save(
            new Invoice(
                UUID.randomUUID(),
                organization.organizationId(),
                organization.customerId(),
                "재시도 고객",
                "2026-08",
                12000,
                1200,
                OffsetDateTime.parse("2026-09-01T05:00:00Z")))
        .getId();
  }

  private Organization organization(boolean withBillingKey) {
    return organization(withBillingKey, OffsetDateTime.parse("2026-10-01T00:00:00Z"));
  }

  private Organization organization(boolean withBillingKey, OffsetDateTime authenticatedAt) {
    UUID organizationId =
        jdbcTemplate.queryForObject(
            "INSERT INTO organization (name) VALUES ('재시도 도입사') RETURNING id", UUID.class);
    UUID customerId =
        jdbcTemplate.queryForObject(
            "INSERT INTO customer (organization_id, name) VALUES (?, '재시도 고객') RETURNING id",
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
    String billingKey = "bill_" + UUID.randomUUID();
    if (withBillingKey) {
      billingKeyRepository.saveAndFlush(
          new BillingKey(
              organizationId,
              customerId,
              "test",
              billingKey,
              "61",
              "43301234****123*",
              authenticatedAt,
              "2026-10-01",
              OffsetDateTime.parse("2026-10-01T00:00:00Z")));
    }
    return new Organization(organizationId, customerId, secretKey, billingKey);
  }

  private record Organization(
      UUID organizationId, UUID customerId, String secretKey, String billingKey) {}
}
