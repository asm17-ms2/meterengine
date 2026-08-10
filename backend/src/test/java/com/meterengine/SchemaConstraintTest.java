package com.meterengine;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * V1 마이그레이션이 만든 스키마가 스토리 MS2-121의 팀 정책을 DB 수준에서 강제하는지 검증한다. 이 파일의 범위는 현재 슬라이스 인수 기준(MS2-123)에 명시된
 * 것으로 한정한다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class SchemaConstraintTest {

  @Autowired private JdbcTemplate jdbc;

  @Test
  void 같은_도입사에서_같은_transaction_id는_두_번_저장되지_않는다() {
    UUID orgId = insertOrganization();
    UUID customerId = insertCustomer(orgId, "acme");
    insertUsageEvent(orgId, customerId, "tx-1");

    assertThatThrownBy(() -> insertUsageEvent(orgId, customerId, "tx-1"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private UUID insertOrganization() {
    return jdbc.queryForObject(
        "INSERT INTO organization (name) VALUES ('테스트 도입사') RETURNING id", UUID.class);
  }

  private UUID insertCustomer(UUID orgId, String name) {
    return jdbc.queryForObject(
        "INSERT INTO customer (organization_id, name) VALUES (?, ?) RETURNING id",
        UUID.class,
        orgId,
        name);
  }

  private void insertUsageEvent(UUID orgId, UUID customerId, String transactionId) {
    jdbc.update(
        """
        INSERT INTO usage_event
          (organization_id, transaction_id, customer_id, event_type, properties, occurred_at)
        VALUES (?, ?, ?, 'chat_completion', '{"token": 1200}', now())
        """,
        orgId,
        transactionId,
        customerId);
  }
}
