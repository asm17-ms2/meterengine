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

/**
 * 삭제와 이벤트 수집이 겹칠 때 무슨 일이 나는지 실제 커넥션 두 개로 확인한다 (MS2-155).
 *
 * <p><b>왜 이 테스트가 따로 있나.</b> {@code CustomerService.delete()}는 "이벤트가 있는지" 먼저 확인하고 지운다. 그 확인과 DELETE
 * 사이에 그 고객의 이벤트가 들어오는 창이 남는데, 앱은 그 창을 스스로 닫지 않고 DB에 맡긴다. 맡길 수 있다는 근거가 이 테스트다. 근거가 틀리면 이벤트가 있는 고객이
 * 사라지고, 그 이벤트는 가리킬 고객이 없는 채 남아 어느 청구서에도 오르지 않는다.
 *
 * <p>기대하는 DB의 성질은 두 가지다. 이벤트를 넣는 트랜잭션이 고객 행에 FK 검사용 잠금(FOR KEY SHARE)을 잡으므로 <b>그 사이의 DELETE는
 * 기다리고</b>, 그 트랜잭션이 커밋되면 <b>기다리던 DELETE는 FK 위반으로 실패한다</b>. 문서만 읽고 단정할 수 있는 성질이 아니라 실제 Postgres에
 * 물어봐야 한다.
 *
 * <p><b>{@code @Transactional}을 붙이지 않는다.</b> 그것을 붙이면 테스트 전체가 한 트랜잭션이라 두 트랜잭션이 겹치는 상황 자체를 만들 수 없다.
 * 대신 뒷정리를 직접 한다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CustomerDeleteConcurrencyTest {

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @AfterEach
  void cleanUp() {
    // usage_event는 append-only라 지울 수 없다. 이 테스트가 만든 이벤트는 남으므로 도입사를 새로 만들어
    // 테스트끼리 섞이지 않게 하고, 여기서는 지울 수 있는 것만 지운다.
    jdbc.update(
        "DELETE FROM customer WHERE name = '동시성 테스트 고객' AND NOT EXISTS ("
            + "SELECT 1 FROM usage_event e WHERE e.customer_id = customer.id)");
  }

  /**
   * 확인과 DELETE 사이에 이벤트가 끼어들면 삭제는 성공하지 못한다.
   *
   * <p>{@code CustomerService.delete()}가 처한 상황을 SQL로 재현한다. 서비스를 직접 부르지 않는 이유는 서비스가 확인과 DELETE 사이에서
   * 멈춰 주지 않아서다. 여기서 보려는 것은 서비스의 흐름이 아니라 그 흐름이 기대는 DB의 성질이다.
   *
   * <p>순서: 이벤트 INSERT가 커밋되지 않은 채 열려 있고, 그동안 DELETE가 들어온다. DELETE는 대기해야 하고(그렇지 않으면 이벤트가 커밋되기 전에 고객이
   * 사라진다), 이벤트가 커밋된 뒤에는 FK 위반으로 끝나야 한다.
   *
   * <p>이 테스트가 깨지면 서비스의 {@code catch} 절이 잡을 것이 없어지고, 이벤트가 있는 고객이 지워질 수 있다는 뜻이다.
   */
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

      // 이벤트 트랜잭션이 열려 있는 동안에는 끝나지 않아야 한다. 기다려 보고 시간이 다 되어야 대기가 맞다.
      // 여기서 DELETE가 끝나 버리면 고객이 먼저 사라지고 이벤트가 뒤늦게 커밋된다는 뜻이다.
      assertThatThrownBy(() -> deleting.get(2, TimeUnit.SECONDS))
          .isInstanceOf(TimeoutException.class);

      ingesting.commit();

      // 커밋되면 그제야 진행하고, 이제는 참조가 생겼으므로 FK가 거절한다.
      assertThatThrownBy(() -> deleting.get(10, TimeUnit.SECONDS))
          .isInstanceOf(ExecutionException.class)
          .cause()
          .isInstanceOf(DataIntegrityViolationException.class);
    }

    assertThat(customerExists(customerId)).isTrue();
    assertThat(eventCount(orgId, customerId)).isEqualTo(1);
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
