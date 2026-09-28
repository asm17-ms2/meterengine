package com.meterengine.payment.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.TestcontainersConfiguration;
import com.meterengine.payment.entity.PaymentAttempt;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class PaymentAttemptRepositoryTest {

  private static final OffsetDateTime LONG_AGO =
      OffsetDateTime.of(2000, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

  @Autowired private PaymentAttemptRepository paymentAttemptRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void markDone은_pending인_시도만_닫고_두_번째_호출은_0건이다() {
    UUID attemptId = insertPending();

    assertThat(paymentAttemptRepository.markDone(attemptId, "first", OffsetDateTime.now()))
        .isEqualTo(1);
    assertThat(paymentAttemptRepository.markDone(attemptId, "second", OffsetDateTime.now()))
        .isZero();

    assertThat(paymentAttemptRepository.findById(attemptId).orElseThrow().getPaymentKey())
        .isEqualTo("first");
  }

  @Test
  void done으로_닫힌_시도는_markFailed로_바뀌지_않는다() {
    UUID attemptId = insertPending();
    paymentAttemptRepository.markDone(attemptId, "pk", OffsetDateTime.now());

    assertThat(paymentAttemptRepository.markFailed(attemptId, "code", "실패", OffsetDateTime.now()))
        .isZero();

    PaymentAttempt attempt = paymentAttemptRepository.findById(attemptId).orElseThrow();
    assertThat(attempt.getStatus()).isEqualTo(PaymentAttempt.DONE);
    assertThat(attempt.getFailureCode()).isNull();
  }

  @Test
  void failed로_닫힌_시도는_markDone으로_바뀌지_않는다() {
    UUID attemptId = insertPending();

    assertThat(paymentAttemptRepository.markFailed(attemptId, "code", "실패", OffsetDateTime.now()))
        .isEqualTo(1);
    assertThat(paymentAttemptRepository.markDone(attemptId, "pk", OffsetDateTime.now())).isZero();

    PaymentAttempt attempt = paymentAttemptRepository.findById(attemptId).orElseThrow();
    assertThat(attempt.getStatus()).isEqualTo(PaymentAttempt.FAILED);
    assertThat(attempt.getPaymentKey()).isNull();
  }

  @Test
  void findActive는_인보이스의_pending이나_done_시도를_찾고_failed는_건너뛴다() {
    Fixture fixture = insertFixture();
    UUID pendingInvoice = UUID.randomUUID();
    insertAttempt(fixture, pendingInvoice, PaymentAttempt.FAILED, LONG_AGO);
    UUID pending = insertAttempt(fixture, pendingInvoice, PaymentAttempt.PENDING, LONG_AGO);
    UUID doneInvoice = UUID.randomUUID();
    UUID done = insertAttempt(fixture, doneInvoice, PaymentAttempt.DONE, LONG_AGO);
    UUID failedInvoice = UUID.randomUUID();
    insertAttempt(fixture, failedInvoice, PaymentAttempt.FAILED, LONG_AGO);

    assertThat(paymentAttemptRepository.findActive(fixture.organizationId(), pendingInvoice))
        .map(PaymentAttempt::getId)
        .contains(pending);
    assertThat(paymentAttemptRepository.findActive(fixture.organizationId(), doneInvoice))
        .map(PaymentAttempt::getId)
        .contains(done);
    assertThat(paymentAttemptRepository.findActive(fixture.organizationId(), failedInvoice))
        .isEmpty();
    assertThat(paymentAttemptRepository.findActive(insertFixture().organizationId(), doneInvoice))
        .isEmpty();
  }

  @Test
  void findPendingRequestedBefore는_기준_시각_전의_pending을_오래된_순으로_limit건까지_찾는다() {
    Fixture fixture = insertFixture();
    UUID second = insertAttempt(fixture, UUID.randomUUID(), PaymentAttempt.PENDING, minutes(2));
    UUID first = insertAttempt(fixture, UUID.randomUUID(), PaymentAttempt.PENDING, minutes(1));
    insertAttempt(fixture, UUID.randomUUID(), PaymentAttempt.PENDING, minutes(5));
    insertAttempt(fixture, UUID.randomUUID(), PaymentAttempt.DONE, LONG_AGO);
    insertAttempt(fixture, UUID.randomUUID(), PaymentAttempt.FAILED, LONG_AGO);

    assertThat(paymentAttemptRepository.findPendingRequestedBefore(minutes(5), Limit.of(10)))
        .extracting(PaymentAttempt::getId)
        .containsExactly(first, second);
    assertThat(paymentAttemptRepository.findPendingRequestedBefore(minutes(5), Limit.of(1)))
        .extracting(PaymentAttempt::getId)
        .containsExactly(first);
  }

  private static OffsetDateTime minutes(int minutes) {
    return LONG_AGO.plusMinutes(minutes);
  }

  private UUID insertPending() {
    Fixture fixture = insertFixture();
    return paymentAttemptRepository
        .saveAndFlush(
            PaymentAttempt.pending(
                fixture.organizationId(),
                fixture.customerId(),
                UUID.randomUUID(),
                1000,
                "2026-09 사용료"))
        .getId();
  }

  private Fixture insertFixture() {
    UUID organizationId =
        jdbcTemplate.queryForObject(
            "INSERT INTO organization (name) VALUES ('결제 도입사') RETURNING id", UUID.class);
    UUID customerId =
        jdbcTemplate.queryForObject(
            "INSERT INTO customer (organization_id, name) VALUES (?, '결제 고객') RETURNING id",
            UUID.class,
            organizationId);
    return new Fixture(organizationId, customerId);
  }

  private UUID insertAttempt(
      Fixture fixture, UUID invoiceId, String status, OffsetDateTime requestedAt) {
    UUID attemptId = UUID.randomUUID();
    boolean completed = !status.equals(PaymentAttempt.PENDING);
    jdbcTemplate.update(
        """
        INSERT INTO payment_attempt
          (id, organization_id, customer_id, invoice_id, amount, order_name, status,
           payment_key, failure_code, failure_message, requested_at, completed_at)
        VALUES (?, ?, ?, ?, 1000, '2026-09 사용료', ?, ?, ?, ?, ?, ?)
        """,
        attemptId,
        fixture.organizationId(),
        fixture.customerId(),
        invoiceId,
        status,
        status.equals(PaymentAttempt.DONE) ? "pk" : null,
        status.equals(PaymentAttempt.FAILED) ? "code" : null,
        status.equals(PaymentAttempt.FAILED) ? "실패" : null,
        requestedAt,
        completed ? requestedAt : null);
    return attemptId;
  }

  private record Fixture(UUID organizationId, UUID customerId) {}
}
