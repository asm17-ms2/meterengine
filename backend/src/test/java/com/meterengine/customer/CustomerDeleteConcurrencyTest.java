package com.meterengine.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meterengine.TestcontainersConfiguration;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CustomerDeleteConcurrencyTest {

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @AfterEach
  void cleanUp() {
    deleteTestCustomersWithoutEvents();
  }

  @Test
  void 이벤트가_커밋되기_전에_들어온_삭제는_대기하다_FK_위반으로_끝난다() throws Exception {
    UUID orgId = insertOrganization();
    UUID customerId = insertCustomer(orgId);

    try (Connection ingesting = dataSource.getConnection()) {
      ingesting.setAutoCommit(false);
      insertEvent(ingesting, orgId, customerId, "tx-1");

      CompletableFuture<Void> deleting =
          CompletableFuture.runAsync(
              () -> jdbc.update("DELETE FROM customer WHERE id = ?", customerId));

      assertThatThrownBy(() -> deleting.get(2, TimeUnit.SECONDS))
          .isInstanceOf(TimeoutException.class);

      ingesting.commit();

      assertThatThrownBy(() -> deleting.get(10, TimeUnit.SECONDS))
          .isInstanceOf(ExecutionException.class)
          .cause()
          .isInstanceOf(DataIntegrityViolationException.class);
    }

    assertThat(customerExists(customerId)).isTrue();
    assertThat(eventCount(orgId, customerId)).isEqualTo(1);
  }

  private void deleteTestCustomersWithoutEvents() {
    jdbc.update(
        "DELETE FROM customer WHERE name = '동시성 테스트 고객' AND NOT EXISTS ("
            + "SELECT 1 FROM usage_event e WHERE e.customer_id = customer.id)");
  }

  private void insertEvent(Connection connection, UUID orgId, UUID customerId, String transactionId)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            """
            INSERT INTO usage_event
              (organization_id, transaction_id, customer_id, event_type, properties, occurred_at)
            VALUES (?, ?, ?, 'chat_completion', '{"token": 1200}', now())
            """)) {
      statement.setObject(1, orgId);
      statement.setString(2, transactionId);
      statement.setObject(3, customerId);
      statement.executeUpdate();
    }
  }

  private UUID insertOrganization() {
    return jdbc.queryForObject(
        "INSERT INTO organization (name) VALUES ('동시성 테스트 도입사') RETURNING id", UUID.class);
  }

  private UUID insertCustomer(UUID orgId) {
    return jdbc.queryForObject(
        "INSERT INTO customer (organization_id, name) VALUES (?, '동시성 테스트 고객') RETURNING id",
        UUID.class,
        orgId);
  }

  private boolean customerExists(UUID customerId) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM customer WHERE id = ?)", Boolean.class, customerId));
  }

  private Integer eventCount(UUID orgId, UUID customerId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM usage_event WHERE organization_id = ? AND customer_id = ?",
        Integer.class,
        orgId,
        customerId);
  }
}
