package com.meterengine.payment.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.TestcontainersConfiguration;
import com.meterengine.payment.entity.PaymentCancellation;
import com.meterengine.payment.entity.PaymentSetting;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class PaymentCancellationRepositoryTest {

  @Autowired private PaymentCancellationRepository paymentCancellationRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void markDone은_pending_취소만_한_번_바꾼다() {
    Attempt attempt = insertDoneAttempt();
    UUID cancellationId = savePendingCancellation(attempt, 300);

    int first = paymentCancellationRepository.markDone(cancellationId, "tk", OffsetDateTime.now());
    int second =
        paymentCancellationRepository.markFailed(
            cancellationId, "code", "실패", OffsetDateTime.now());

    assertThat(first).isEqualTo(1);
    assertThat(second).isZero();
    assertThat(statusOf(cancellationId)).isEqualTo(PaymentCancellation.DONE);
  }

  @Test
  void markFailed는_pending_취소만_한_번_바꾼다() {
    Attempt attempt = insertDoneAttempt();
    UUID cancellationId = savePendingCancellation(attempt, 300);

    int first =
        paymentCancellationRepository.markFailed(
            cancellationId, "code", "실패", OffsetDateTime.now());
    int second = paymentCancellationRepository.markDone(cancellationId, "tk", OffsetDateTime.now());

    assertThat(first).isEqualTo(1);
    assertThat(second).isZero();
    assertThat(statusOf(cancellationId)).isEqualTo(PaymentCancellation.FAILED);
  }

  @Test
  void 같은_트랜잭션에서_읽은_취소도_markDone_뒤에는_done으로_다시_읽힌다() {
    Attempt attempt = insertDoneAttempt();
    UUID cancellationId = savePendingCancellation(attempt, 300);
    assertThat(paymentCancellationRepository.findById(cancellationId))
        .map(PaymentCancellation::getStatus)
        .contains(PaymentCancellation.PENDING);

    paymentCancellationRepository.markDone(cancellationId, "tk", OffsetDateTime.now());

    assertThat(paymentCancellationRepository.findById(cancellationId))
        .map(PaymentCancellation::getStatus)
        .contains(PaymentCancellation.DONE);
  }

  @Test
  void 취소가_없으면_완료된_취소_합계는_0이다() {
    Attempt attempt = insertDoneAttempt();

    assertThat(paymentCancellationRepository.sumDoneAmount(attempt.id())).isZero();
  }

  @Test
  void 완료된_취소_합계는_done만_더한다() {
    Attempt attempt = insertDoneAttempt();
    paymentCancellationRepository.markDone(
        savePendingCancellation(attempt, 300), "tk1", OffsetDateTime.now());
    paymentCancellationRepository.markDone(
        savePendingCancellation(attempt, 200), "tk2", OffsetDateTime.now());
    paymentCancellationRepository.markFailed(
        savePendingCancellation(attempt, 100), "code", "실패", OffsetDateTime.now());
    savePendingCancellation(attempt, 50);

    assertThat(paymentCancellationRepository.sumDoneAmount(attempt.id())).isEqualTo(500);
  }

  private UUID savePendingCancellation(Attempt attempt, long amount) {
    PaymentCancellation cancellation =
        PaymentCancellation.pending(
            UUID.randomUUID(),
            attempt.organizationId(),
            attempt.id(),
            PaymentSetting.TEST,
            amount,
            "고객 요청");
    return paymentCancellationRepository.saveAndFlush(cancellation).getId();
  }

  private String statusOf(UUID cancellationId) {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM payment_cancellation WHERE id = ?", String.class, cancellationId);
  }

  private Attempt insertDoneAttempt() {
    UUID organizationId =
        jdbcTemplate.queryForObject(
            "INSERT INTO organization (name) VALUES ('결제 도입사') RETURNING id", UUID.class);
    UUID customerId =
        jdbcTemplate.queryForObject(
            "INSERT INTO customer (organization_id, name) VALUES (?, '결제 고객') RETURNING id",
            UUID.class,
            organizationId);
    UUID invoiceId =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO invoice
              (organization_id, customer_id, customer_name, period, supply_amount, tax_amount,
               total_amount, cutoff_at, finalized_at)
            VALUES (?, ?, '결제 고객', '2026-09', 1000, 100, 1100, now(), now())
            RETURNING id
            """,
            UUID.class,
            organizationId,
            customerId);
    UUID attemptId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO payment_attempt
          (id, organization_id, customer_id, invoice_id, mode, amount, order_name, status,
           payment_key, requested_at, completed_at)
        VALUES (?, ?, ?, ?, 'test', 1100, '2026-09 사용료', 'done', 'pk', now(), now())
        """,
        attemptId,
        organizationId,
        customerId,
        invoiceId);
    return new Attempt(organizationId, attemptId);
  }

  private record Attempt(UUID organizationId, UUID id) {}
}
