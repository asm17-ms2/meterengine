package com.meterengine.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meterengine.TestcontainersConfiguration;
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
}
