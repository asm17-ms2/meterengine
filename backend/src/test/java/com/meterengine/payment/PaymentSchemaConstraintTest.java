package com.meterengine.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meterengine.TestcontainersConfiguration;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class PaymentSchemaConstraintTest {

  @Autowired private JdbcTemplate jdbcTemplate;

  private int invoiceCount;

  @Test
  void 진행중_시도가_있는_인보이스에는_진행중_시도를_넣을_수_없다() {
    Fixture fixture = fixture();
    UUID invoiceId = insertInvoice(fixture);
    insertAttempt(fixture, invoiceId, "pending");

    assertThatThrownBy(() -> insertAttempt(fixture, invoiceId, "pending"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("payment_attempt_organization_invoice_mode_unique");
  }

  @Test
  void 성공한_시도가_있는_인보이스에는_진행중_시도를_넣을_수_없다() {
    Fixture fixture = fixture();
    UUID invoiceId = insertInvoice(fixture);
    insertAttempt(fixture, invoiceId, "done");

    assertThatThrownBy(() -> insertAttempt(fixture, invoiceId, "pending"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("payment_attempt_organization_invoice_mode_unique");
  }

  @Test
  void test_모드에_성공한_시도가_있어도_live_모드_진행중_시도를_넣을_수_있다() {
    Fixture fixture = fixture();
    UUID invoiceId = insertInvoice(fixture);
    insertAttempt(fixture, invoiceId, "done", "test");

    insertAttempt(fixture, invoiceId, "pending", "live");

    assertThat(countAttempts(invoiceId)).isEqualTo(2);
  }

  @Test
  void 없는_인보이스로는_시도를_넣을_수_없다() {
    Fixture fixture = fixture();

    assertThatThrownBy(() -> insertAttempt(fixture, UUID.randomUUID(), "pending"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("payment_attempt_invoice_same_organization_fk");
  }

  @Test
  void pending에는_실패_사유를_남길_수_없다() {
    assertResultCheckRejects("pending", null, null, "사유", null);
  }

  @Test
  void done에는_실패_코드를_남길_수_없다() {
    assertResultCheckRejects("done", "pk", "CODE", null, OffsetDateTime.now());
  }

  @Test
  void done에는_실패_사유를_남길_수_없다() {
    assertResultCheckRejects("done", "pk", null, "사유", OffsetDateTime.now());
  }

  @Test
  void failed에는_결제_키를_남길_수_없다() {
    assertResultCheckRejects("failed", "pk", "CODE", "사유", OffsetDateTime.now());
  }

  @Test
  void done에는_결제_키가_있어야_한다() {
    assertResultCheckRejects("done", null, null, null, OffsetDateTime.now());
  }

  @Test
  void done에는_완료_시각이_있어야_한다() {
    assertResultCheckRejects("done", "pk", null, null, null);
  }

  @Test
  void failed에는_실패_코드가_있어야_한다() {
    assertResultCheckRejects("failed", null, null, "사유", OffsetDateTime.now());
  }

  @Test
  void failed에는_완료_시각이_있어야_한다() {
    assertResultCheckRejects("failed", null, "CODE", "사유", null);
  }

  @Test
  void 실패한_시도는_여러_개_쌓인다() {
    Fixture fixture = fixture();
    UUID invoiceId = insertInvoice(fixture);
    insertAttempt(fixture, invoiceId, "failed");
    insertAttempt(fixture, invoiceId, "failed");
    insertAttempt(fixture, invoiceId, "pending");

    assertThat(countAttempts(invoiceId)).isEqualTo(3);
  }

  @Test
  void pending은_done으로_한_번_바뀐다() {
    UUID attemptId = insertAttempt(fixture(), "pending");

    jdbcTemplate.update(
        "UPDATE payment_attempt SET status = 'done', payment_key = 'pk', completed_at = now() WHERE id = ?",
        attemptId);

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT status FROM payment_attempt WHERE id = ?", String.class, attemptId))
        .isEqualTo("done");
  }

  @Test
  void pending은_failed로_한_번_바뀐다() {
    UUID attemptId = insertAttempt(fixture(), "pending");

    jdbcTemplate.update(
        "UPDATE payment_attempt SET status = 'failed', failure_code = 'code', completed_at = now() WHERE id = ?",
        attemptId);

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT status FROM payment_attempt WHERE id = ?", String.class, attemptId))
        .isEqualTo("failed");
  }

  @Test
  void 확정된_시도는_UPDATE할_수_없다() {
    UUID attemptId = insertAttempt(fixture(), "failed");

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE payment_attempt SET status = 'done', payment_key = 'pk' WHERE id = ?",
                    attemptId))
        .isInstanceOf(UncategorizedSQLException.class)
        .hasMessageContaining("payment_attempt is final");
  }

  @Test
  void done인_시도는_failed로_바꿀_수_없다() {
    UUID attemptId = insertAttempt(fixture(), "done");

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE payment_attempt SET status = 'failed', payment_key = NULL, failure_code = 'code' WHERE id = ?",
                    attemptId))
        .isInstanceOf(UncategorizedSQLException.class)
        .hasMessageContaining("payment_attempt is final");
  }

  @Test
  void 요청_값은_pending에서도_바꿀_수_없다() {
    UUID attemptId = insertAttempt(fixture(), "pending");

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE payment_attempt SET status = 'done', payment_key = 'pk', completed_at = now(), amount = 1 WHERE id = ?",
                    attemptId))
        .isInstanceOf(UncategorizedSQLException.class)
        .hasMessageContaining("pending to done or failed");
  }

  @Test
  void pending_시도의_mode는_바꿀_수_없다() {
    UUID attemptId = insertAttempt(fixture(), "pending");

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE payment_attempt SET status = 'done', payment_key = 'pk', completed_at = now(), mode = 'live' WHERE id = ?",
                    attemptId))
        .isInstanceOf(UncategorizedSQLException.class)
        .hasMessageContaining("pending to done or failed");
  }

  @Test
  void 시도는_DELETE할_수_없다() {
    UUID attemptId = insertAttempt(fixture(), "failed");

    assertThatThrownBy(
            () -> jdbcTemplate.update("DELETE FROM payment_attempt WHERE id = ?", attemptId))
        .isInstanceOf(UncategorizedSQLException.class)
        .hasMessageContaining("append-only");
  }

  @Test
  void 시도는_TRUNCATE할_수_없다() {
    insertAttempt(fixture(), "failed");

    assertThatThrownBy(() -> jdbcTemplate.execute("TRUNCATE payment_attempt, payment_cancellation"))
        .isInstanceOf(UncategorizedSQLException.class)
        .hasMessageContaining("payment_attempt is append-only");
  }

  @Test
  void 다른_도입사의_고객으로는_시도를_넣을_수_없다() {
    Fixture fixture = fixture();
    Fixture otherFixture = fixture();
    UUID otherInvoiceId = insertInvoice(otherFixture);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO payment_attempt
                      (id, organization_id, customer_id, invoice_id, mode, amount, order_name,
                       status, requested_at)
                    VALUES (?, ?, ?, ?, 'test', 1000, 'n', 'pending', now())
                    """,
                    UUID.randomUUID(),
                    otherFixture.organizationId(),
                    fixture.customerId(),
                    otherInvoiceId))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("payment_attempt_customer_same_organization_fk");
  }

  @Test
  void 결제_시도가_있는_고객은_지울_수_없다() {
    Fixture fixture = fixture();
    UUID otherCustomerId = insertCustomer(fixture.organizationId());
    UUID invoiceId = insertInvoice(new Fixture(fixture.organizationId(), otherCustomerId));
    insertAttempt(fixture, invoiceId, "failed");

    assertThatThrownBy(
            () -> jdbcTemplate.update("DELETE FROM customer WHERE id = ?", fixture.customerId()))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("payment_attempt_customer_same_organization_fk");
  }

  @Test
  void 취소는_pending에서_done으로_한_번_바뀐다() {
    Fixture fixture = fixture();
    UUID cancellationId = insertCancellation(fixture, insertAttempt(fixture, "done"), 1000);

    jdbcTemplate.update(
        "UPDATE payment_cancellation SET status = 'done', transaction_key = 'tk', completed_at = now() WHERE id = ?",
        cancellationId);

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT status FROM payment_cancellation WHERE id = ?",
                String.class,
                cancellationId))
        .isEqualTo("done");
  }

  @Test
  void done인_취소는_UPDATE할_수_없다() {
    Fixture fixture = fixture();
    UUID cancellationId = insertCancellation(fixture, insertAttempt(fixture, "done"), 1000);
    jdbcTemplate.update(
        "UPDATE payment_cancellation SET status = 'done', transaction_key = 'tk', completed_at = now() WHERE id = ?",
        cancellationId);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE payment_cancellation SET status = 'failed', transaction_key = NULL, failure_code = 'code' WHERE id = ?",
                    cancellationId))
        .isInstanceOf(UncategorizedSQLException.class)
        .hasMessageContaining("payment_cancellation is final");
  }

  @Test
  void 취소는_DELETE할_수_없다() {
    Fixture fixture = fixture();
    UUID cancellationId = insertCancellation(fixture, insertAttempt(fixture, "done"), 1000);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "DELETE FROM payment_cancellation WHERE id = ?", cancellationId))
        .isInstanceOf(UncategorizedSQLException.class)
        .hasMessageContaining("payment_cancellation is append-only");
  }

  @Test
  void 취소는_TRUNCATE할_수_없다() {
    Fixture fixture = fixture();
    insertCancellation(fixture, insertAttempt(fixture, "done"), 1000);

    assertThatThrownBy(() -> jdbcTemplate.execute("TRUNCATE payment_cancellation"))
        .isInstanceOf(UncategorizedSQLException.class)
        .hasMessageContaining("payment_cancellation is append-only");
  }

  @Test
  void 같은_시도에_진행중_취소를_둘_넣을_수_없다() {
    Fixture fixture = fixture();
    UUID attemptId = insertAttempt(fixture, "done");
    insertCancellation(fixture, attemptId, 1000);

    assertThatThrownBy(() -> insertCancellation(fixture, attemptId, 500))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("payment_cancellation_payment_attempt_pending_unique");
  }

  @Test
  void 취소_금액은_0원일_수_없다() {
    Fixture fixture = fixture();
    UUID attemptId = insertAttempt(fixture, "done");

    assertThatThrownBy(() -> insertCancellation(fixture, attemptId, 0))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("payment_cancellation_amount_check");
  }

  @Test
  void 진행중_취소의_금액은_바꿀_수_없다() {
    assertPendingCancellationUpdateRejects("amount = 1");
  }

  @Test
  void 진행중_취소의_사유는_바꿀_수_없다() {
    assertPendingCancellationUpdateRejects("reason = '다른 사유'");
  }

  @Test
  void 진행중_취소의_시도는_바꿀_수_없다() {
    assertPendingCancellationUpdateRejects("payment_attempt_id = gen_random_uuid()");
  }

  @Test
  void 진행중_취소의_mode는_바꿀_수_없다() {
    assertPendingCancellationUpdateRejects("mode = 'live'");
  }

  @Test
  void 다른_도입사의_시도로는_취소를_넣을_수_없다() {
    Fixture fixture = fixture();
    UUID attemptId = insertAttempt(fixture, "done");
    Fixture otherFixture = fixture();

    assertThatThrownBy(() -> insertCancellation(otherFixture, attemptId, 1000))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("payment_cancellation_payment_attempt_same_organization_fk");
  }

  @Test
  void 대사_불일치의_주문_번호는_비울_수_없다() {
    Fixture fixture = fixture();

    assertThatThrownBy(() -> insertMismatch(fixture, null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("order_id");
  }

  @Test
  void 같은_대사_불일치는_두_번_넣을_수_없다() {
    Fixture fixture = fixture();
    insertMismatch(fixture, "order-1");

    assertThatThrownBy(() -> insertMismatch(fixture, "order-1"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("payment_reconciliation_mismatch_kind_order_transaction_unique");
  }

  @Test
  void 대사_불일치는_UPDATE할_수_없다() {
    Fixture fixture = fixture();
    UUID mismatchId = insertMismatch(fixture, "order-1");

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE payment_reconciliation_mismatch SET order_id = 'order-2' WHERE id = ?",
                    mismatchId))
        .isInstanceOf(UncategorizedSQLException.class)
        .hasMessageContaining("payment_reconciliation_mismatch is append-only");
  }

  @Test
  void 고객을_지우면_빌링키와_카드_등록_링크도_지워진다() {
    Fixture fixture = fixture();
    jdbcTemplate.update(
        """
        INSERT INTO billing_key
          (organization_id, customer_id, mode, billing_key, card_issuer_code, card_number,
           authenticated_at, consent_document_version, consented_at)
        VALUES (?, ?, 'test', 'v1:cipher', '61', '1234****', now(), '2026-10', now())
        """,
        fixture.organizationId(),
        fixture.customerId());
    jdbcTemplate.update(
        """
        INSERT INTO payment_method_registration
          (id, organization_id, customer_id, token_hash, mode, created_at, expires_at)
        VALUES (?, ?, ?, 'hash', 'test', now(), now() + interval '1 day')
        """,
        UUID.randomUUID(),
        fixture.organizationId(),
        fixture.customerId());

    jdbcTemplate.update("DELETE FROM customer WHERE id = ?", fixture.customerId());

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM billing_key WHERE customer_id = ?",
                Integer.class,
                fixture.customerId()))
        .isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM payment_method_registration WHERE customer_id = ?",
                Integer.class,
                fixture.customerId()))
        .isZero();
  }

  private record Fixture(UUID organizationId, UUID customerId) {}

  private Fixture fixture() {
    UUID organizationId = insertOrganization();
    return new Fixture(organizationId, insertCustomer(organizationId));
  }

  private UUID insertOrganization() {
    return jdbcTemplate.queryForObject(
        "INSERT INTO organization (name) VALUES ('결제 도입사') RETURNING id", UUID.class);
  }

  private UUID insertCustomer(UUID organizationId) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO customer (organization_id, name) VALUES (?, '결제 고객') RETURNING id",
        UUID.class,
        organizationId);
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

  private int countAttempts(UUID invoiceId) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM payment_attempt WHERE invoice_id = ?", Integer.class, invoiceId);
  }

  private void assertResultCheckRejects(
      String status,
      String paymentKey,
      String failureCode,
      String failureMessage,
      OffsetDateTime completedAt) {
    Fixture fixture = fixture();
    UUID invoiceId = insertInvoice(fixture);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO payment_attempt
                      (id, organization_id, customer_id, invoice_id, mode, amount, order_name,
                       status, payment_key, failure_code, failure_message, requested_at,
                       completed_at)
                    VALUES (?, ?, ?, ?, 'test', 1000, '2026-09 사용료', ?, ?, ?, ?, now(), ?)
                    """,
                    UUID.randomUUID(),
                    fixture.organizationId(),
                    fixture.customerId(),
                    invoiceId,
                    status,
                    paymentKey,
                    failureCode,
                    failureMessage,
                    completedAt))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("payment_attempt_status_result_check");
  }

  private void assertPendingCancellationUpdateRejects(String assignment) {
    Fixture fixture = fixture();
    UUID cancellationId = insertCancellation(fixture, insertAttempt(fixture, "done"), 1000);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE payment_cancellation SET status = 'done', transaction_key = 'tk',"
                        + " completed_at = now(), "
                        + assignment
                        + " WHERE id = ?",
                    cancellationId))
        .isInstanceOf(UncategorizedSQLException.class)
        .hasMessageContaining("payment_cancellation can only move from pending to done or failed");
  }

  private UUID insertAttempt(Fixture fixture, String status) {
    return insertAttempt(fixture, insertInvoice(fixture), status);
  }

  private UUID insertAttempt(Fixture fixture, UUID invoiceId, String status) {
    return insertAttempt(fixture, invoiceId, status, "test");
  }

  private UUID insertAttempt(Fixture fixture, UUID invoiceId, String status, String mode) {
    UUID attemptId = UUID.randomUUID();
    boolean completed = !status.equals("pending");
    jdbcTemplate.update(
        """
        INSERT INTO payment_attempt
          (id, organization_id, customer_id, invoice_id, mode, amount, order_name, status,
           payment_key, failure_code, failure_message, requested_at, completed_at)
        VALUES (?, ?, ?, ?, ?, 1000, '2026-09 사용료', ?, ?, ?, ?, now(), ?)
        """,
        attemptId,
        fixture.organizationId(),
        fixture.customerId(),
        invoiceId,
        mode,
        status,
        status.equals("done") ? "pk" : null,
        status.equals("failed") ? "CODE" : null,
        status.equals("failed") ? "사유" : null,
        completed ? OffsetDateTime.now() : null);
    return attemptId;
  }

  private UUID insertCancellation(Fixture fixture, UUID attemptId, long amount) {
    UUID cancellationId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO payment_cancellation
          (id, organization_id, payment_attempt_id, mode, amount, reason, status, requested_at)
        VALUES (?, ?, ?, 'test', ?, '고객 요청', 'pending', now())
        """,
        cancellationId,
        fixture.organizationId(),
        attemptId,
        amount);
    return cancellationId;
  }

  private UUID insertMismatch(Fixture fixture, String orderId) {
    UUID mismatchId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO payment_reconciliation_mismatch
          (id, organization_id, mode, kind, order_id, payment_key, transaction_key,
           meterengine_amount, toss_payments_amount, transacted_at, detected_at)
        VALUES (?, ?, 'test', 'missing_in_toss_payments', ?, NULL, NULL, 1000, NULL, NULL, now())
        """,
        mismatchId,
        fixture.organizationId(),
        orderId);
    return mismatchId;
  }
}
