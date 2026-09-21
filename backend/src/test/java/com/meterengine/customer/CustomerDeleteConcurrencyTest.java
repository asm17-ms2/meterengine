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
import org.springframework.test.annotation.DirtiesContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@DirtiesContext
class CustomerDeleteConcurrencyTest {

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUp() {
    deleteTestCustomersWithoutEvents();
  }

  @Test
  void 이벤트가_커밋되기_전에_들어온_삭제는_대기하다_FK_위반으로_끝난다() throws Exception {
    UUID organizationId = insertOrganization();
    UUID customerId = insertCustomer(organizationId);

    try (Connection ingesting = dataSource.getConnection()) {
      ingesting.setAutoCommit(false);
      insertEvent(ingesting, organizationId, customerId, "tx-1");

      CompletableFuture<Void> deleting =
          CompletableFuture.runAsync(
              () -> jdbcTemplate.update("DELETE FROM customer WHERE id = ?", customerId));

      assertThatThrownBy(() -> deleting.get(2, TimeUnit.SECONDS))
          .isInstanceOf(TimeoutException.class);

      ingesting.commit();

      assertThatThrownBy(() -> deleting.get(10, TimeUnit.SECONDS))
          .isInstanceOf(ExecutionException.class)
          .cause()
          .isInstanceOf(DataIntegrityViolationException.class);
    }

    assertThat(customerExists(customerId)).isTrue();
    assertThat(eventCount(organizationId, customerId)).isEqualTo(1);
  }

  private void deleteTestCustomersWithoutEvents() {
    jdbcTemplate.update(
        "DELETE FROM customer WHERE name = '동시성 테스트 고객' AND NOT EXISTS ("
            + "SELECT 1 FROM event e WHERE e.customer_id = customer.id)");
  }

  private void insertEvent(
      Connection connection, UUID organizationId, UUID customerId, String transactionId)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            """
            INSERT INTO event
              (organization_id, transaction_id, customer_id, type, properties, occurred_at)
            VALUES (?, ?, ?, 'chat_completion', '{"token": 1200}', now())
            """)) {
      statement.setObject(1, organizationId);
      statement.setString(2, transactionId);
      statement.setObject(3, customerId);
      statement.executeUpdate();
    }
  }

  private UUID insertOrganization() {
    return jdbcTemplate.queryForObject(
        "INSERT INTO organization (name) VALUES ('동시성 테스트 도입사') RETURNING id", UUID.class);
  }

  private UUID insertCustomer(UUID organizationId) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO customer (organization_id, name) VALUES (?, '동시성 테스트 고객') RETURNING id",
        UUID.class,
        organizationId);
  }

  private boolean customerExists(UUID customerId) {
    return Boolean.TRUE.equals(
        jdbcTemplate.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM customer WHERE id = ?)", Boolean.class, customerId));
  }

  private Integer eventCount(UUID organizationId, UUID customerId) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM event WHERE organization_id = ? AND customer_id = ?",
        Integer.class,
        organizationId,
        customerId);
  }
}
