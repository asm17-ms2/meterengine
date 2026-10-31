package com.meterengine.payment.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.TestcontainersConfiguration;
import com.meterengine.invoice.entity.Invoice;
import com.meterengine.invoice.repository.InvoiceRepository;
import com.meterengine.payment.client.TossPaymentsClient;
import com.meterengine.payment.client.TossPaymentsPayment;
import com.meterengine.payment.entity.PaymentCancellation;
import com.meterengine.payment.repository.PaymentCancellationRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PaymentCancellationServiceTest {

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private PaymentCancellationRepository paymentCancellationRepository;
  @Autowired private PaymentCancellationService paymentCancellationService;

  // 토스 클라이언트를 목으로 바꿔 이 테스트를 결제 통합 테스트와 같은 컨텍스트에서 돌린다.
  @MockitoBean private TossPaymentsClient tossPaymentsClient;

  @Test
  void 취소_완료를_중복_반영해도_웹훅_이벤트는_하나만_남는다() {
    PaymentCancellation cancellation = pendingCancellation(doneAttempt(), 3000L);

    assertThat(
            paymentCancellationService.markDone(
                cancellation, "cancellation-" + cancellation.getId()))
        .isTrue();
    assertThat(
            paymentCancellationService.markDone(
                cancellation, "cancellation-" + cancellation.getId()))
        .isFalse();

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM webhook_event WHERE organization_id = ? AND type = 'payment_canceled'",
                Long.class,
                cancellation.getOrganizationId()))
        .isEqualTo(1L);
  }

  @Test
  void 이미_done인_우리_취소의_키를_빼고_금액이_같은_마지막_원소를_고른다() {
    UUID attemptId = doneAttempt();
    PaymentCancellation earlierDone = pendingCancellation(attemptId, 3000L);
    assertThat(paymentCancellationService.markDone(earlierDone, "txkey-earlier")).isTrue();
    PaymentCancellation pending = pendingCancellation(attemptId, 3000L);

    assertThat(
            paymentCancellationService.matchTransactionKey(
                pending,
                payment(
                    cancel("txkey-other", 3000L, pending),
                    cancel("txkey-ours", 3000L, pending),
                    cancel("txkey-earlier", 3000L, pending),
                    cancel("txkey-different-amount", 1000L, pending))))
        .contains("txkey-ours");
  }

  @Test
  void cancels에_이미_done인_우리_키만_있거나_cancels가_비면_없다() {
    UUID attemptId = doneAttempt();
    PaymentCancellation first = pendingCancellation(attemptId, 3000L);
    assertThat(paymentCancellationService.markDone(first, "txkey-first")).isTrue();
    PaymentCancellation second = pendingCancellation(attemptId, 3000L);
    assertThat(paymentCancellationService.markDone(second, "txkey-second")).isTrue();
    PaymentCancellation pending = pendingCancellation(attemptId, 3000L);

    assertThat(
            paymentCancellationService.matchTransactionKey(
                pending, payment(cancel("txkey-first", 3000L, pending))))
        .isEmpty();
    assertThat(
            paymentCancellationService.matchTransactionKey(
                pending,
                payment(
                    cancel("txkey-first", 3000L, pending), cancel("txkey-second", 3000L, pending))))
        .isEmpty();
    assertThat(paymentCancellationService.matchTransactionKey(pending, payment())).isEmpty();
  }

  @Test
  void 금액이_다른_원소로_대신하지_않는다() {
    PaymentCancellation pending = pendingCancellation(doneAttempt(), 3000L);

    assertThat(
            paymentCancellationService.matchTransactionKey(
                pending,
                payment(
                    cancel("txkey-first", 1000L, pending), cancel("txkey-last", 2000L, pending))))
        .isEmpty();
  }

  @Test
  void cancelStatus가_DONE이_아닌_원소는_고르지_않는다() {
    PaymentCancellation pending = pendingCancellation(doneAttempt(), 3000L);

    assertThat(
            paymentCancellationService.matchTransactionKey(
                pending,
                payment(
                    cancel("txkey-done", 3000L, pending.getRequestedAt(), "DONE"),
                    cancel("txkey-in-progress", 3000L, pending.getRequestedAt(), "IN_PROGRESS"))))
        .contains("txkey-done");
    assertThat(
            paymentCancellationService.matchTransactionKey(
                pending,
                payment(cancel("txkey-aborted", 3000L, pending.getRequestedAt(), "ABORTED"))))
        .isEmpty();
  }

  @Test
  void 요청보다_1분넘게_먼저_취소된_원소는_고르지_않는다() {
    PaymentCancellation pending = pendingCancellation(doneAttempt(), 3000L);
    OffsetDateTime requestedAt = pending.getRequestedAt();

    assertThat(
            paymentCancellationService.matchTransactionKey(
                pending,
                payment(
                    cancel("txkey-within", 3000L, requestedAt.minusMinutes(1), "DONE"),
                    cancel("txkey-too-early", 3000L, requestedAt.minusSeconds(61), "DONE"))))
        .contains("txkey-within");
    assertThat(
            paymentCancellationService.matchTransactionKey(
                pending,
                payment(cancel("txkey-too-early", 3000L, requestedAt.minusMinutes(2), "DONE"))))
        .isEmpty();
  }

  @Test
  void markDone과_markFailed는_pending에서_한_번만_바꾸고_두번째는_false다() {
    UUID attemptId = doneAttempt();
    PaymentCancellation first = pendingCancellation(attemptId, 1000L);

    assertThat(paymentCancellationService.markDone(first, "txkey-first")).isTrue();
    assertThat(paymentCancellationService.markFailed(first, "HTTP_500", "실패")).isFalse();
    assertThat(paymentCancellationService.markDone(first, "txkey-again")).isFalse();

    PaymentCancellation second = pendingCancellation(attemptId, 1000L);
    assertThat(paymentCancellationService.markFailed(second, "HTTP_500", "실패")).isTrue();
    assertThat(paymentCancellationService.markDone(second, "txkey-second")).isFalse();

    assertThat(
            jdbcTemplate.queryForList(
                "SELECT status || ':' || coalesce(transaction_key, failure_code)"
                    + " FROM payment_cancellation WHERE payment_attempt_id = ?"
                    + " ORDER BY requested_at",
                String.class,
                attemptId))
        .containsExactly("done:txkey-first", "failed:HTTP_500");
  }

  private PaymentCancellation pendingCancellation(UUID attemptId, long amount) {
    UUID organizationId =
        jdbcTemplate.queryForObject(
            "SELECT organization_id FROM payment_attempt WHERE id = ?", UUID.class, attemptId);
    return paymentCancellationRepository.saveAndFlush(
        PaymentCancellation.pending(
            UUID.randomUUID(), organizationId, attemptId, "test", amount, "고객 요청"));
  }

  private static TossPaymentsPayment payment(TossPaymentsPayment.Cancel... cancels) {
    return new TossPaymentsPayment(
        "tgen_payment", "order", "PARTIAL_CANCELED", 13200L, 0L, List.of(cancels));
  }

  private static TossPaymentsPayment.Cancel cancel(
      String transactionKey, long cancelAmount, PaymentCancellation pending) {
    return cancel(transactionKey, cancelAmount, pending.getRequestedAt().plusSeconds(1), "DONE");
  }

  private static TossPaymentsPayment.Cancel cancel(
      String transactionKey, long cancelAmount, OffsetDateTime canceledAt, String cancelStatus) {
    return new TossPaymentsPayment.Cancel(transactionKey, cancelAmount, canceledAt, cancelStatus);
  }

  private UUID doneAttempt() {
    UUID organizationId =
        jdbcTemplate.queryForObject(
            "INSERT INTO organization (name) VALUES ('취소 도입사') RETURNING id", UUID.class);
    UUID customerId =
        jdbcTemplate.queryForObject(
            "INSERT INTO customer (organization_id, name) VALUES (?, '취소 고객') RETURNING id",
            UUID.class,
            organizationId);
    Invoice invoice =
        invoiceRepository.save(
            new Invoice(
                UUID.randomUUID(),
                organizationId,
                customerId,
                "취소 고객",
                "2026-08",
                12000,
                1200,
                OffsetDateTime.parse("2026-09-01T04:00:00Z"),
                OffsetDateTime.parse("2026-09-01T05:00:00Z")));
    UUID attemptId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO payment_attempt
          (id, organization_id, customer_id, invoice_id, mode, amount, order_name, status,
           payment_key, requested_at, completed_at)
        VALUES (?, ?, ?, ?, 'test', 13200, '2026-08 사용료', 'done', ?, now(), now())
        """,
        attemptId,
        organizationId,
        customerId,
        invoice.getId(),
        "tgen_" + attemptId);
    return attemptId;
  }
}
