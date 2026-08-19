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
 * 마이그레이션이 만든 스키마가 팀 정책을 DB 수준에서 강제하는지 검증한다. 앱을 거치지 않아도 지켜지는 것만 여기서 본다.
 *
 * <p>이벤트 append-only와 received_at 강제는 스토리 MS2-121의 정책이고 현재 슬라이스 인수 기준(MS2-123)에 명시된 것으로 한정한다. 고객 삭제
 * 가드는 MS2-155가 기대는 성질이라 아래에 따로 묶었다.
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

  @Test
  void 저장된_이벤트는_UPDATE할_수_없다() {
    UUID orgId = insertOrganization();
    insertUsageEvent(orgId, insertCustomer(orgId, "acme"), "tx-1");

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE usage_event SET occurred_at = occurred_at + interval '1 hour' WHERE transaction_id = 'tx-1'"))
        .isInstanceOf(UncategorizedSQLException.class)
        .hasMessageContaining("append-only");
  }

  @Test
  void 저장된_이벤트는_DELETE할_수_없다() {
    UUID orgId = insertOrganization();
    insertUsageEvent(orgId, insertCustomer(orgId, "acme"), "tx-1");

    assertThatThrownBy(() -> jdbc.update("DELETE FROM usage_event WHERE transaction_id = 'tx-1'"))
        .isInstanceOf(UncategorizedSQLException.class)
        .hasMessageContaining("append-only");
  }

  @Test
  void 이벤트_테이블은_TRUNCATE할_수_없다() {
    assertThatThrownBy(() -> jdbc.execute("TRUNCATE usage_event"))
        .isInstanceOf(UncategorizedSQLException.class)
        .hasMessageContaining("append-only");
  }

  @Test
  void received_at은_요청이_값을_보내도_서버가_찍은_시각으로_덮어쓴다() {
    OffsetDateTime clientSuppliedTime = OffsetDateTime.parse("2020-01-01T00:00:00Z");
    UUID orgId = insertOrganization();

    jdbc.update(
        """
        INSERT INTO usage_event
          (organization_id, transaction_id, customer_id, event_type, occurred_at, received_at)
        VALUES (?, 'tx-1', ?, 'chat_completion', now(), ?)
        """,
        orgId,
        insertCustomer(orgId, "acme"),
        clientSuppliedTime);

    OffsetDateTime receivedAt =
        jdbc.queryForObject(
            "SELECT received_at FROM usage_event WHERE transaction_id = 'tx-1'",
            OffsetDateTime.class);
    assertThat(receivedAt).isAfter(clientSuppliedTime);
  }

  // --- 고객 삭제 가드 (MS2-155) ---

  /**
   * 이벤트가 있는 고객은 지울 수 없다.
   *
   * <p><b>이 성질이 고객 삭제 API의 바닥이다.</b> 막는 것은 새로 만든 장치가 아니라 V1의 복합 FK {@code
   * usage_event_customer_same_org}다. 그 FK는 {@code ON DELETE} 절이 없어 기본값 {@code NO ACTION}이고, 참조하는
   * 이벤트가 한 건이라도 있으면 DELETE를 거부한다.
   *
   * <p>앱({@code CustomerService})도 지우기 전에 같은 것을 확인하고 409로 거절한다. 층이 둘인 이유는 V1의 append-only 트리거와 같다.
   * 앱의 확인은 사용자에게 쓸 만한 오류를 주는 몫이고, FK는 앱을 거치지 않는 경로(수동 SQL, 배치, 후속 관리 도구)까지 막는 몫이다. 이벤트가 남은 채 고객이
   * 사라지면 그 사용량은 어느 청구서에도 오르지 않는다.
   *
   * <p>이미 있는 제약을 검사하는 테스트를 굳이 두는 이유: 삭제 API가 이 성질 하나에 기대고 있어서, 나중에 FK에 {@code ON DELETE CASCADE}가
   * 붙거나 제약이 느슨해지면 API가 조용히 청구 근거를 지우게 된다. 그때 여기가 먼저 빨개진다.
   */
  @Test
  void 이벤트가_있는_고객은_지울_수_없다() {
    UUID orgId = insertOrganization();
    UUID customerId = insertCustomer(orgId, "acme");
    insertUsageEvent(orgId, customerId, "tx-1");

    // 행이 남았는지 뒤이어 조회하지 않는다. FK 위반이 트랜잭션을 중단시켜(SQL state 25P02) 이 트랜잭션
    // 안에서는 어떤 문장도 더 실행되지 않는다. DELETE가 거부됐다는 것이 곧 행이 남았다는 뜻이기도 하다.
    assertThatThrownBy(() -> deleteCustomer(customerId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  /** 뒤집힌 짝이다. 이벤트가 없으면 막을 이유가 없고, 실제로 행이 사라진다. */
  @Test
  void 이벤트가_없는_고객은_지울_수_있다() {
    UUID orgId = insertOrganization();
    UUID customerId = insertCustomer(orgId, "acme");

    deleteCustomer(customerId);

    assertThat(customerExists(customerId)).isFalse();
  }

  private void deleteCustomer(UUID customerId) {
    jdbc.update("DELETE FROM customer WHERE id = ?", customerId);
  }

  private boolean customerExists(UUID customerId) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM customer WHERE id = ?)", Boolean.class, customerId));
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
