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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class PaymentSchemaConstraintTest {

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void 고객을_지우면_빌링키도_지워진다() {
    Fixture fixture = fixture();
    insertBillingKey(fixture.organizationId(), fixture.customerId());

    jdbcTemplate.update("DELETE FROM customer WHERE id = ?", fixture.customerId());

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM billing_key WHERE customer_id = ?",
                Integer.class,
                fixture.customerId()))
        .isZero();
  }

  @Test
  void 고객마다_빌링키는_하나만_저장된다() {
    Fixture fixture = fixture();
    insertBillingKey(fixture.organizationId(), fixture.customerId());

    assertThatThrownBy(() -> insertBillingKey(fixture.organizationId(), fixture.customerId()))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("billing_key_pk");
  }

  @Test
  void 다른_도입사의_고객에는_빌링키를_넣을_수_없다() {
    Fixture fixture = fixture();
    UUID otherOrganization = insertOrganization();

    assertThatThrownBy(() -> insertBillingKey(otherOrganization, fixture.customerId()))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("billing_key_customer_same_organization_fk");
  }

  @Test
  void 진행중_시도가_있는_인보이스에는_진행중_시도를_넣을_수_없다() {
    Fixture fixture = fixture();
    UUID invoiceId = UUID.randomUUID();
    insertAttempt(fixture, invoiceId, "pending");

    assertThatThrownBy(() -> insertAttempt(fixture, invoiceId, "pending"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("payment_attempt_organization_invoice_unique");
  }

  @Test
  void 성공한_시도가_있는_인보이스에는_진행중_시도를_넣을_수_없다() {
    Fixture fixture = fixture();
    UUID invoiceId = UUID.randomUUID();
    insertAttempt(fixture, invoiceId, "done");

    assertThatThrownBy(() -> insertAttempt(fixture, invoiceId, "pending"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("payment_attempt_organization_invoice_unique");
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
    UUID invoiceId = UUID.randomUUID();
    insertAttempt(fixture, invoiceId, "failed");
    insertAttempt(fixture, invoiceId, "failed");
    insertAttempt(fixture, invoiceId, "pending");

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM payment_attempt WHERE invoice_id = ?",
                Integer.class,
                invoiceId))
        .isEqualTo(3);
  }

  @Test
  void 다른_도입사의_고객으로는_시도를_넣을_수_없다() {
    Fixture fixture = fixture();
    UUID otherOrganization = insertOrganization();

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO payment_attempt
                      (id, organization_id, customer_id, invoice_id, amount, order_name, status, requested_at)
                    VALUES (?, ?, ?, ?, 1000, 'n', 'pending', now())
                    """,
                    UUID.randomUUID(),
                    otherOrganization,
                    fixture.customerId(),
                    UUID.randomUUID()))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("payment_attempt_customer_same_organization_fk");
  }

  @Test
  void 결제_시도가_있는_고객은_지울_수_없다() {
    Fixture fixture = fixture();
    insertAttempt(fixture, UUID.randomUUID(), "failed");

    assertThatThrownBy(
            () -> jdbcTemplate.update("DELETE FROM customer WHERE id = ?", fixture.customerId()))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("payment_attempt_customer_same_organization_fk");
  }

  private record Fixture(UUID organizationId, UUID customerId) {}

  private Fixture fixture() {
    UUID organizationId = insertOrganization();
    UUID customerId =
        jdbcTemplate.queryForObject(
            "INSERT INTO customer (organization_id, name) VALUES (?, '결제 고객') RETURNING id",
            UUID.class,
            organizationId);
    return new Fixture(organizationId, customerId);
  }

  private UUID insertOrganization() {
    return jdbcTemplate.queryForObject(
        "INSERT INTO organization (name) VALUES ('결제 도입사') RETURNING id", UUID.class);
  }

  private void insertBillingKey(UUID organizationId, UUID customerId) {
    jdbcTemplate.update(
        """
        INSERT INTO billing_key
          (organization_id, customer_id, billing_key, card_issuer_code, card_number, authenticated_at)
        VALUES (?, ?, 'v1:x', '4V', '4330****', now())
        """,
        organizationId,
        customerId);
  }

  private void assertResultCheckRejects(
      String status,
      String paymentKey,
      String failureCode,
      String failureMessage,
      OffsetDateTime completedAt) {
    Fixture fixture = fixture();

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO payment_attempt
                      (id, organization_id, customer_id, invoice_id, amount, order_name, status,
                       payment_key, failure_code, failure_message, requested_at, completed_at)
                    VALUES (?, ?, ?, ?, 1000, '2026-09 사용료', ?, ?, ?, ?, now(), ?)
                    """,
                    UUID.randomUUID(),
                    fixture.organizationId(),
                    fixture.customerId(),
                    UUID.randomUUID(),
                    status,
                    paymentKey,
                    failureCode,
                    failureMessage,
                    completedAt))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("payment_attempt_status_result_check");
  }

  private UUID insertAttempt(Fixture fixture, UUID invoiceId, String status) {
    UUID attemptId = UUID.randomUUID();
    boolean completed = !status.equals("pending");
    jdbcTemplate.update(
        """
        INSERT INTO payment_attempt
          (id, organization_id, customer_id, invoice_id, amount, order_name, status,
           payment_key, failure_code, failure_message, requested_at, completed_at)
        VALUES (?, ?, ?, ?, 1000, '2026-09 사용료', ?, ?, ?, ?, now(), ?)
        """,
        attemptId,
        fixture.organizationId(),
        fixture.customerId(),
        invoiceId,
        status,
        status.equals("done") ? "pk" : null,
        status.equals("failed") ? "CODE" : null,
        status.equals("failed") ? "사유" : null,
        completed ? OffsetDateTime.now() : null);
    return attemptId;
  }
}
