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
  void 저장된_이벤트는_삭제할_수_없다() {
    UUID eventId = insertUsageEvent(insertOrganization(), "tx-1");

    assertThatThrownBy(() -> jdbc.update("DELETE FROM usage_events WHERE id = ?", eventId))
        .isInstanceOf(UncategorizedSQLException.class)
        .hasMessageContaining("append-only");
  }

  @Test
  void 이벤트_테이블은_TRUNCATE할_수_없다() {
    assertThatThrownBy(() -> jdbc.execute("TRUNCATE usage_events"))
        .isInstanceOf(UncategorizedSQLException.class)
        .hasMessageContaining("append-only");
  }

  @Test
  void 같은_도입사의_고객은_이벤트에_귀속시킬_수_있다() {
    UUID orgId = insertOrganization();
    insertUsageEventWithCustomer(orgId, "tx-1", insertCustomer(orgId, "acme"));
  }

  @Test
  void 다른_도입사의_고객은_이벤트에_귀속시킬_수_없다() {
    UUID orgId = insertOrganization();
    UUID otherOrgCustomerId = insertCustomer(insertOrganization(), "acme");

    assertThatThrownBy(() -> insertUsageEventWithCustomer(orgId, "tx-1", otherOrgCustomerId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 같은_도입사의_미터는_이벤트에_연결할_수_있다() {
    UUID orgId = insertOrganization();
    insertUsageEventWithMeter(orgId, "tx-1", insertMeter(orgId, "api_call"));
  }

  @Test
  void 다른_도입사의_미터는_이벤트에_연결할_수_없다() {
    UUID orgId = insertOrganization();
    UUID otherOrgMeterId = insertMeter(insertOrganization(), "api_call");

    assertThatThrownBy(() -> insertUsageEventWithMeter(orgId, "tx-1", otherOrgMeterId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 다른_도입사의_미터로는_가격정책을_만들_수_없다() {
    UUID orgId = insertOrganization();
    UUID otherOrgMeterId = insertMeter(insertOrganization(), "api_call");

    assertThatThrownBy(() -> insertPricePolicy(orgId, otherOrgMeterId))
        .isInstanceOf(DataIntegrityViolationException.class);
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
  void received_at은_앱이_값을_넣어도_서버가_찍은_시각으로_덮어쓴다() {
    OffsetDateTime clientSuppliedTime = OffsetDateTime.parse("2020-01-01T00:00:00Z");

    UUID eventId =
        jdbc.queryForObject(
            """
            INSERT INTO usage_events
              (organization_id, transaction_id, external_customer_id, code, occurred_at, received_at)
            VALUES (?, 'tx-1', 'cust-1', 'api_call', now(), ?)
            RETURNING id
            """,
            UUID.class,
            insertOrganization(),
            clientSuppliedTime);

    OffsetDateTime receivedAt =
        jdbc.queryForObject(
            "SELECT received_at FROM usage_events WHERE id = ?", OffsetDateTime.class, eventId);

    assertThat(receivedAt).isAfter(clientSuppliedTime);
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

  private UUID insertMeter(UUID orgId, String code) {
    return jdbc.queryForObject(
        """
        INSERT INTO meters (organization_id, code, name, aggregation_type)
        VALUES (?, ?, ?, 'COUNT')
        RETURNING id
        """,
        UUID.class,
        orgId,
        code,
        code);
  }

  private void insertPricePolicy(UUID orgId, UUID meterId) {
    jdbc.update(
        "INSERT INTO price_policies (organization_id, meter_id, unit_price_krw) VALUES (?, ?, 4)",
        orgId,
        meterId);
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

  private void insertUsageEventWithCustomer(UUID orgId, String transactionId, UUID customerId) {
    jdbc.update(
        """
        INSERT INTO usage_events
          (organization_id, transaction_id, external_customer_id, customer_id, code, occurred_at)
        VALUES (?, ?, 'cust-1', ?, 'api_call', now())
        """,
        orgId,
        transactionId,
        customerId);
  }

  private void insertUsageEventWithMeter(UUID orgId, String transactionId, UUID meterId) {
    jdbc.update(
        """
        INSERT INTO usage_events
          (organization_id, transaction_id, external_customer_id, code, meter_id, occurred_at)
        VALUES (?, ?, 'cust-1', 'api_call', ?, now())
        """,
        orgId,
        transactionId,
        meterId);
  }
}
