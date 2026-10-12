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
