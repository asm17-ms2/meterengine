package com.meterengine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

/**
 * V1 마이그레이션이 만든 스키마가 되돌리기 어려운 결정(불변 결정 목록, 7/29 비준)을 DB 수준에서 강제하는지 검증한다. 결정 번호는 docs/erd/erd.md의 제약
 * 표를 따른다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class SchemaConstraintTest {

  @Autowired private JdbcTemplate jdbc;

  @Test
  void 같은_도입사에서_같은_transaction_id는_두_번_저장되지_않는다() {
    UUID orgId = insertOrganization();
    insertUsageEvent(orgId, "tx-1");

    assertThatThrownBy(() -> insertUsageEvent(orgId, "tx-1"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 다른_도입사라면_같은_transaction_id를_저장할_수_있다() {
    insertUsageEvent(insertOrganization(), "tx-1");
    insertUsageEvent(insertOrganization(), "tx-1");
  }

  @Test
  void 저장된_이벤트의_원본_컬럼은_수정할_수_없다() {
    UUID eventId = insertUsageEvent(insertOrganization(), "tx-1");

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE usage_events SET occurred_at = occurred_at + interval '1 hour' WHERE id = ?",
                    eventId))
        .isInstanceOf(UncategorizedSQLException.class)
        .hasMessageContaining("append-only");
  }

  @Test
  void 이벤트의_customer_id는_NULL에서_한_번만_채울_수_있다() {
    UUID orgId = insertOrganization();
    UUID eventId = insertUsageEvent(orgId, "tx-1");
    UUID firstCustomerId = insertCustomer(orgId, "acme");
    UUID secondCustomerId = insertCustomer(orgId, "globex");

    jdbc.update("UPDATE usage_events SET customer_id = ? WHERE id = ?", firstCustomerId, eventId);

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE usage_events SET customer_id = ? WHERE id = ?",
                    secondCustomerId,
                    eventId))
        .isInstanceOf(UncategorizedSQLException.class)
        .hasMessageContaining("customer_id");
  }

  @Test
  void received_at은_앱이_넣지_않아도_서버가_찍는다() {
    UUID eventId = insertUsageEvent(insertOrganization(), "tx-1");

    OffsetDateTime receivedAt =
        jdbc.queryForObject(
            "SELECT received_at FROM usage_events WHERE id = ?", OffsetDateTime.class, eventId);

    assertThat(receivedAt).isNotNull();
  }

  @Test
  void 살아있는_고객끼리는_같은_external_id를_가질_수_없다() {
    UUID orgId = insertOrganization();
    insertCustomer(orgId, "acme");

    assertThatThrownBy(() -> insertCustomer(orgId, "acme"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void soft_delete된_고객의_external_id는_재사용할_수_있다() {
    UUID orgId = insertOrganization();
    UUID deletedCustomerId = insertCustomer(orgId, "acme");
    jdbc.update("UPDATE customers SET deleted_at = now() WHERE id = ?", deletedCustomerId);

    insertCustomer(orgId, "acme");
  }

  private UUID insertOrganization() {
    return jdbc.queryForObject("INSERT INTO organizations DEFAULT VALUES RETURNING id", UUID.class);
  }

  private UUID insertCustomer(UUID orgId, String externalId) {
    return jdbc.queryForObject(
        "INSERT INTO customers (organization_id, external_id, name) VALUES (?, ?, ?) RETURNING id",
        UUID.class,
        orgId,
        externalId,
        externalId);
  }

  private UUID insertUsageEvent(UUID orgId, String transactionId) {
    return jdbc.queryForObject(
        """
        INSERT INTO usage_events
          (organization_id, transaction_id, external_customer_id, code, occurred_at)
        VALUES (?, ?, 'cust-1', 'api_call', now())
        RETURNING id
        """,
        UUID.class,
        orgId,
        transactionId);
  }
}
