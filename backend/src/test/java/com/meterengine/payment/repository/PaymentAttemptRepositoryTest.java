package com.meterengine.payment.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.TestcontainersConfiguration;
import com.meterengine.payment.entity.PaymentAttempt;
import com.meterengine.payment.entity.PaymentSetting;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
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

  private int invoiceCount;

  @Test
  void findActive는_인보이스의_pending이나_done_시도를_찾고_failed는_건너뛴다() {
    Fixture fixture = insertFixture();
    UUID pendingInvoice = insertInvoice(fixture);
    insertAttempt(fixture, pendingInvoice, PaymentAttempt.FAILED, PaymentSetting.TEST);
    UUID pending =
        insertAttempt(fixture, pendingInvoice, PaymentAttempt.PENDING, PaymentSetting.TEST);
    UUID doneInvoice = insertInvoice(fixture);
    UUID done = insertAttempt(fixture, doneInvoice, PaymentAttempt.DONE, PaymentSetting.TEST);
    UUID failedInvoice = insertInvoice(fixture);
    insertAttempt(fixture, failedInvoice, PaymentAttempt.FAILED, PaymentSetting.TEST);

    assertThat(
            paymentAttemptRepository.findActive(
                fixture.organizationId(), pendingInvoice, PaymentSetting.TEST))
        .map(PaymentAttempt::getId)
        .contains(pending);
    assertThat(
            paymentAttemptRepository.findActive(
                fixture.organizationId(), doneInvoice, PaymentSetting.TEST))
        .map(PaymentAttempt::getId)
        .contains(done);
    assertThat(
            paymentAttemptRepository.findActive(
                fixture.organizationId(), failedInvoice, PaymentSetting.TEST))
        .isEmpty();
    assertThat(
            paymentAttemptRepository.findActive(
                insertFixture().organizationId(), doneInvoice, PaymentSetting.TEST))
        .isEmpty();
  }

  @Test
  void findActive는_다른_모드의_시도를_건너뛴다() {
    Fixture fixture = insertFixture();
    UUID invoiceId = insertInvoice(fixture);
    insertAttempt(fixture, invoiceId, PaymentAttempt.DONE, PaymentSetting.TEST);

    assertThat(
            paymentAttemptRepository.findActive(
                fixture.organizationId(), invoiceId, PaymentSetting.LIVE))
        .isEmpty();
  }

  @Test
  void markDone은_pending_시도만_한_번_바꾼다() {
    Fixture fixture = insertFixture();
    UUID attemptId =
        insertAttempt(fixture, insertInvoice(fixture), PaymentAttempt.PENDING, PaymentSetting.TEST);

    int first = paymentAttemptRepository.markDone(attemptId, "pk", OffsetDateTime.now());
    int second = paymentAttemptRepository.markFailed(attemptId, "code", "실패", OffsetDateTime.now());

    assertThat(first).isEqualTo(1);
    assertThat(second).isZero();
    assertThat(statusOf(attemptId)).isEqualTo(PaymentAttempt.DONE);
  }

  @Test
  void markFailed는_pending_시도만_한_번_바꾼다() {
    Fixture fixture = insertFixture();
    UUID attemptId =
        insertAttempt(fixture, insertInvoice(fixture), PaymentAttempt.PENDING, PaymentSetting.TEST);

    int first = paymentAttemptRepository.markFailed(attemptId, "code", "실패", OffsetDateTime.now());
    int second = paymentAttemptRepository.markDone(attemptId, "pk", OffsetDateTime.now());

    assertThat(first).isEqualTo(1);
    assertThat(second).isZero();
    assertThat(statusOf(attemptId)).isEqualTo(PaymentAttempt.FAILED);
  }

  @Test
  void pending으로_만든_시도는_모드와_함께_저장된다() {
    Fixture fixture = insertFixture();
    UUID invoiceId = insertInvoice(fixture);
    PaymentAttempt attempt =
        PaymentAttempt.pending(
            UUID.randomUUID(),
            fixture.organizationId(),
            fixture.customerId(),
            invoiceId,
            PaymentSetting.LIVE,
            1100,
            "2026-09 사용료");

    paymentAttemptRepository.saveAndFlush(attempt);

    assertThat(
            jdbcTemplate.queryForMap(
                "SELECT status, mode FROM payment_attempt WHERE id = ?", attempt.getId()))
        .containsEntry("status", PaymentAttempt.PENDING)
        .containsEntry("mode", PaymentSetting.LIVE);
    assertThat(
            paymentAttemptRepository.countByOrganizationIdAndInvoiceIdAndMode(
                fixture.organizationId(), invoiceId, PaymentSetting.LIVE))
        .isEqualTo(1);
  }

  @Test
  void 같은_트랜잭션에서_읽은_시도도_markDone_뒤에는_done으로_다시_읽힌다() {
    Fixture fixture = insertFixture();
    UUID attemptId =
        insertAttempt(fixture, insertInvoice(fixture), PaymentAttempt.PENDING, PaymentSetting.TEST);
    assertThat(paymentAttemptRepository.findById(attemptId))
        .map(PaymentAttempt::getStatus)
        .contains(PaymentAttempt.PENDING);

    paymentAttemptRepository.markDone(attemptId, "pk", OffsetDateTime.now());

    assertThat(paymentAttemptRepository.findById(attemptId))
        .map(PaymentAttempt::getStatus)
        .contains(PaymentAttempt.DONE);
  }

  @Test
  void findRetryCandidates는_제외_코드와_7일_지난_없음_실패를_거르고_카드를_다시_등록했으면_돌려준다() {
    Fixture excluded = insertFixture();
    UUID excludedAttempt = insertFailedAttempt(excluded, "INVALID_CARD_EXPIRATION");
    insertBillingKey(excluded, LONG_AGO.minusDays(1));
    Fixture reregistered = insertFixture();
    UUID reregisteredAttempt = insertFailedAttempt(reregistered, "INVALID_CARD_EXPIRATION");
    insertBillingKey(reregistered, LONG_AGO.plusDays(1));
    Fixture retryable = insertFixture();
    UUID retryableAttempt = insertFailedAttempt(retryable, "REJECT_CARD_COMPANY");
    insertBillingKey(retryable, LONG_AGO.minusDays(1));
    Fixture removedWithCard = insertFixture();
    UUID removedWithCardAttempt =
        insertFailedAttempt(removedWithCard, "ALREADY_REMOVED_BILLING_KEY");
    insertBillingKey(removedWithCard, LONG_AGO.minusDays(1));
    Fixture removedWithoutCard = insertFixture();
    UUID removedWithoutCardAttempt =
        insertFailedAttempt(removedWithoutCard, "ALREADY_REMOVED_BILLING_KEY");
    Fixture oldNotFound = insertFixture();
    UUID oldNotFoundAttempt = insertFailedAttempt(oldNotFound, "NOT_FOUND_PAYMENT");
    insertBillingKey(oldNotFound, LONG_AGO.minusDays(1));
    Fixture recentNotFound = insertFixture();
    UUID recentNotFoundAttempt =
        insertFailedAttempt(recentNotFound, "NOT_FOUND_PAYMENT", OffsetDateTime.now().minusDays(1));
    insertBillingKey(recentNotFound, LONG_AGO.minusDays(1));

    assertThat(retryCandidateIds())
        .contains(
            reregisteredAttempt, retryableAttempt, removedWithCardAttempt, recentNotFoundAttempt)
        .doesNotContain(excludedAttempt, removedWithoutCardAttempt, oldNotFoundAttempt);
  }

  @Test
  void findRetryCandidates는_활성이_아닌_도입사의_실패를_돌려주지_않는다() {
    Fixture active = insertFixture();
    UUID activeAttempt = insertFailedAttempt(active, "REJECT_CARD_COMPANY");
    insertBillingKey(active, LONG_AGO.minusDays(1));
    Fixture suspended = insertFixture();
    UUID suspendedAttempt = insertFailedAttempt(suspended, "REJECT_CARD_COMPANY");
    insertBillingKey(suspended, LONG_AGO.minusDays(1));
    jdbcTemplate.update(
        "UPDATE organization SET status = 'suspended' WHERE id = ?", suspended.organizationId());

    List<UUID> candidateIds =
        paymentAttemptRepository
            .findRetryCandidates(
                OffsetDateTime.now(),
                Integer.MAX_VALUE,
                Set.of("INVALID_CARD_EXPIRATION"),
                "ALREADY_REMOVED_BILLING_KEY",
                "NOT_FOUND_PAYMENT",
                OffsetDateTime.now().minusDays(7))
            .stream()
            .map(PaymentRetryCandidateProjection::getId)
            .toList();

    assertThat(candidateIds).contains(activeAttempt).doesNotContain(suspendedAttempt);
  }

  @Test
  void findRetryCandidates는_카드를_보관했거나_카드가_없는_실패_시도를_돌려주지_않는다() {
    Fixture archived = insertFixture();
    UUID archivedAttempt = insertFailedAttempt(archived, "REJECT_CARD_COMPANY");
    insertBillingKey(archived, LONG_AGO.minusDays(1));
    archiveBillingKey(archived);
    Fixture archivedAfterReregistration = insertFixture();
    UUID archivedAfterReregistrationAttempt =
        insertFailedAttempt(archivedAfterReregistration, "ALREADY_REMOVED_BILLING_KEY");
    insertBillingKey(archivedAfterReregistration, LONG_AGO.plusDays(1));
    archiveBillingKey(archivedAfterReregistration);
    Fixture withoutCard = insertFixture();
    UUID withoutCardAttempt = insertFailedAttempt(withoutCard, "REJECT_CARD_COMPANY");

    assertThat(retryCandidateIds())
        .doesNotContain(archivedAttempt, archivedAfterReregistrationAttempt, withoutCardAttempt);
  }

  private List<UUID> retryCandidateIds() {
    return paymentAttemptRepository
        .findRetryCandidates(
            OffsetDateTime.now(),
            Integer.MAX_VALUE,
            Set.of("INVALID_CARD_EXPIRATION", "ALREADY_REMOVED_BILLING_KEY"),
            "ALREADY_REMOVED_BILLING_KEY",
            "NOT_FOUND_PAYMENT",
            OffsetDateTime.now().minusDays(7))
        .stream()
        .map(PaymentRetryCandidateProjection::getId)
        .toList();
  }

  private void archiveBillingKey(Fixture fixture) {
    jdbcTemplate.update(
        "UPDATE billing_key SET archived_at = now() WHERE organization_id = ? AND customer_id = ?",
        fixture.organizationId(),
        fixture.customerId());
  }

  private UUID insertFailedAttempt(Fixture fixture, String failureCode) {
    return insertFailedAttempt(fixture, failureCode, LONG_AGO);
  }

  private UUID insertFailedAttempt(Fixture fixture, String failureCode, OffsetDateTime failedAt) {
    UUID attemptId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO payment_attempt
          (id, organization_id, customer_id, invoice_id, mode, amount, order_name, status,
           failure_code, failure_message, requested_at, completed_at)
        VALUES (?, ?, ?, ?, 'test', 1000, '2026-09 사용료', 'failed', ?, '실패', ?, ?)
        """,
        attemptId,
        fixture.organizationId(),
        fixture.customerId(),
        insertInvoice(fixture),
        failureCode,
        failedAt,
        failedAt);
    return attemptId;
  }

  private void insertBillingKey(Fixture fixture, OffsetDateTime authenticatedAt) {
    jdbcTemplate.update(
        """
        INSERT INTO billing_key
          (organization_id, customer_id, mode, billing_key, card_issuer_code, card_number,
           authenticated_at, consent_document_version, consented_at)
        VALUES (?, ?, 'test', 'v1:bk', '61', '43301234****123*', ?, '2026-10-01', ?)
        """,
        fixture.organizationId(),
        fixture.customerId(),
        authenticatedAt,
        authenticatedAt);
  }

  private String statusOf(UUID attemptId) {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM payment_attempt WHERE id = ?", String.class, attemptId);
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

  private UUID insertInvoice(Fixture fixture) {
    invoiceCount++;
    return jdbcTemplate.queryForObject(
        """
        INSERT INTO invoice
          (organization_id, customer_id, customer_name, period, supply_amount, tax_amount,
           total_amount, finalized_at)
        VALUES (?, ?, '결제 고객', ?, 1000, 100, 1100, now())
        RETURNING id
        """,
        UUID.class,
        fixture.organizationId(),
        fixture.customerId(),
        "2026-%02d".formatted(invoiceCount));
  }

  private UUID insertAttempt(Fixture fixture, UUID invoiceId, String status, String mode) {
    UUID attemptId = UUID.randomUUID();
    boolean completed = !status.equals(PaymentAttempt.PENDING);
    jdbcTemplate.update(
        """
        INSERT INTO payment_attempt
          (id, organization_id, customer_id, invoice_id, mode, amount, order_name, status,
           payment_key, failure_code, failure_message, requested_at, completed_at)
        VALUES (?, ?, ?, ?, ?, 1000, '2026-09 사용료', ?, ?, ?, ?, ?, ?)
        """,
        attemptId,
        fixture.organizationId(),
        fixture.customerId(),
        invoiceId,
        mode,
        status,
        status.equals(PaymentAttempt.DONE) ? "pk" : null,
        status.equals(PaymentAttempt.FAILED) ? "code" : null,
        status.equals(PaymentAttempt.FAILED) ? "실패" : null,
        LONG_AGO,
        completed ? LONG_AGO : null);
    return attemptId;
  }

  private record Fixture(UUID organizationId, UUID customerId) {}
}
