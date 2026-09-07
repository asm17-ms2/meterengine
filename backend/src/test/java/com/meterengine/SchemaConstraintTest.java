package com.meterengine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.List;
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
 * <p>이벤트 append-only와 received_at 강제는 수집 정책이고 그 슬라이스의 인수 기준에 명시된 것으로 한정한다. 고객 삭제 가드는 고객 삭제 API가 기대는
 * 성질이라 아래에 따로 묶었다.
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

  // --- 고객 삭제 가드 ---

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

  // --- 가격 정책 / 단가 ---

  /**
   * 단가 행(price_rate)의 FK 과녁이 미터가 아니라 정책(price_policy)인 이유: 단가는 축 선언 없이는 해석할 수 없어서, 선언 없는 단가가 존재하는
   * 순간 계산이 도달하지 못하는 죽은 행이 되고 기본 단가로 조용히 계산된다. FK가 그 상태를 입력 시점의 실패로 바꾼다. 미터 존재와 same-org는 policy ->
   * billable_metric 복합 FK를 통해 이행적으로 보장된다.
   */
  @Test
  void 미터가_없으면_가격_정책을_만들_수_없다() {
    UUID orgId = insertOrganization();

    assertThatThrownBy(() -> insertPricePolicy(orgId, "no-such-metric"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 다른_도입사의_미터에는_가격_정책을_붙일_수_없다() {
    UUID orgId = insertOrganization();
    insertMetric(orgId, "token-usage");
    UUID otherOrgId = insertOrganization();

    assertThatThrownBy(() -> insertPricePolicy(otherOrgId, "token-usage"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 같은_미터에_가격_정책은_하나만_만들_수_있다() {
    UUID orgId = insertOrganization();
    insertMetric(orgId, "token-usage");
    insertPricePolicy(orgId, "token-usage");

    assertThatThrownBy(() -> insertPricePolicy(orgId, "token-usage"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 가격_정책이_없으면_단가를_만들_수_없다() {
    UUID orgId = insertOrganization();
    insertMetric(orgId, "token-usage");

    assertThatThrownBy(() -> insertPriceRate(orgId, "token-usage", "{}", "0.5"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 단가는_음수일_수_없다() {
    UUID orgId = insertOrganization();
    insertMetric(orgId, "token-usage");
    insertPricePolicy(orgId, "token-usage");

    assertThatThrownBy(() -> insertPriceRate(orgId, "token-usage", "{}", "-0.5"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  /**
   * JSONB는 키 순서를 정규화하므로 표기만 다른 같은 조합도 같은 값으로 취급되어 PK가 막는다. 조합당 단가 1행이라는 불변식이 표기 차이에 뚫리지 않는지까지 확인한다.
   */
  @Test
  void 같은_조합의_단가는_키_순서가_달라도_두_번_저장되지_않는다() {
    UUID orgId = insertOrganization();
    insertMetric(orgId, "token-usage");
    insertPricePolicy(orgId, "token-usage");
    insertPriceRate(orgId, "token-usage", "{\"model\": \"opus5\", \"region\": \"kr\"}", "0.5");

    assertThatThrownBy(
            () ->
                insertPriceRate(
                    orgId, "token-usage", "{\"region\": \"kr\", \"model\": \"opus5\"}", "2.5"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  /** 단가의 정본이 price_rate로 옮겨졌으므로, 옛 자리가 남아 있으면 두 정본이 생긴다. */
  @Test
  void billable_metric에는_더_이상_unit_price_열이_없다() {
    Boolean columnExists =
        jdbc.queryForObject(
            """
            SELECT EXISTS(
              SELECT 1 FROM information_schema.columns
              WHERE table_name = 'billable_metric' AND column_name = 'unit_price')
            """,
            Boolean.class);

    assertThat(columnExists).isFalse();
  }

  private void insertMetric(UUID orgId, String code) {
    jdbc.update(
        """
        INSERT INTO billable_metric
          (organization_id, code, name, event_type, aggregation, target_property)
        VALUES (?, ?, '토큰 사용량', 'chat_completion', 'SUM', 'token')
        """,
        orgId,
        code);
  }

  // --- 이름 collation ---

  /**
   * 사람이 읽는 이름 컬럼이 한국어 collation을 쓰는지 본다.
   *
   * <p>여기서 보는 이유: {@code ddl-auto=validate}는 컬럼의 존재와 타입만 확인하고 collation은 검사 항목이 아니다. 마이그레이션 V4가
   * 되돌려져도 앱은 아무 말 없이 뜬다. 이 단언이 유일한 직접 가드다.
   *
   * <p>정렬 결과 자체는 {@code CustomerIntegrationTest}가 API를 관통해 본다. 그쪽이 깨졌을 때 원인이 collation인지 다른 것인지 이
   * 테스트가 갈라 준다.
   */
  @Test
  void 사람이_읽는_이름_컬럼은_한국어_collation을_쓴다() {
    assertThat(collationOf("customer", "name")).isEqualTo("korean");
    assertThat(collationOf("organization", "name")).isEqualTo("korean");
    assertThat(collationOf("billable_metric", "name")).isEqualTo("korean");
  }

  /** 식별자 컬럼은 기계가 매칭하는 값이라 언어별 정렬을 붙이지 않는다. DB 기본값을 쓰면 collation_name이 비어 있다. */
  @Test
  void 식별자_컬럼에는_한국어_collation을_붙이지_않는다() {
    assertThat(collationOf("billable_metric", "code")).isNull();
    assertThat(collationOf("usage_event", "transaction_id")).isNull();
    assertThat(collationOf("usage_event", "event_type")).isNull();
  }

  // --- 인보이스 확정본 ---

  @Test
  void 같은_고객의_같은_달은_두_번_확정되지_않는다() {
    UUID orgId = insertOrganization();
    UUID customerId = insertCustomer(orgId, "acme");
    insertInvoice(orgId, customerId, "2026-08");

    assertThatThrownBy(() -> insertInvoice(orgId, customerId, "2026-08"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 확정_인보이스는_고객마다_그리고_달마다_따로_저장된다() {
    UUID orgId = insertOrganization();
    UUID customerId = insertCustomer(orgId, "acme");
    insertInvoice(orgId, customerId, "2026-08");
    insertInvoice(orgId, customerId, "2026-09");
    insertInvoice(orgId, insertCustomer(orgId, "beta"), "2026-08");

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM invoice WHERE organization_id = ?", Integer.class, orgId))
        .isEqualTo(3);
  }

  @Test
  void 청구_기간은_월을_두_자리로_적은_표기만_저장된다() {
    UUID orgId = insertOrganization();
    UUID customerId = insertCustomer(orgId, "acme");

    assertThatThrownBy(() -> insertInvoice(orgId, customerId, "2026-8"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 존재하지_않는_달은_청구_기간이_될_수_없다() {
    UUID orgId = insertOrganization();
    UUID customerId = insertCustomer(orgId, "acme");

    assertThatThrownBy(() -> insertInvoice(orgId, customerId, "2026-13"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 다른_도입사의_고객에는_인보이스를_붙일_수_없다() {
    UUID orgId = insertOrganization();
    UUID customerId = insertCustomer(orgId, "acme");
    UUID otherOrgId = insertOrganization();

    assertThatThrownBy(() -> insertInvoice(otherOrgId, customerId, "2026-08"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 확정_시각은_생략하면_DB가_대신_채우지_않는다() {
    UUID orgId = insertOrganization();
    UUID customerId = insertCustomer(orgId, "acme");

    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    INSERT INTO invoice
                      (organization_id, customer_id, period, supply_amount, tax_amount)
                    VALUES (?, ?, '2026-08', 12000, 1200)
                    """,
                    orgId,
                    customerId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 다른_도입사의_인보이스에는_라인을_붙일_수_없다() {
    UUID orgId = insertOrganization();
    UUID invoiceId = insertInvoice(orgId, insertCustomer(orgId, "acme"), "2026-08");
    UUID otherOrgId = insertOrganization();

    assertThatThrownBy(() -> insertInvoiceLine(otherOrgId, invoiceId, "token-usage"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 같은_인보이스에_같은_미터의_라인은_두_번_저장되지_않는다() {
    UUID orgId = insertOrganization();
    UUID invoiceId = insertInvoice(orgId, insertCustomer(orgId, "acme"), "2026-08");
    insertInvoiceLine(orgId, invoiceId, "token-usage");

    assertThatThrownBy(() -> insertInvoiceLine(orgId, invoiceId, "token-usage"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 라인이_있는_인보이스는_지울_수_없다() {
    UUID orgId = insertOrganization();
    UUID invoiceId = insertInvoice(orgId, insertCustomer(orgId, "acme"), "2026-08");
    insertInvoiceLine(orgId, invoiceId, "token-usage");

    assertThatThrownBy(() -> jdbc.update("DELETE FROM invoice WHERE id = ?", invoiceId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 확정_인보이스가_있는_고객은_지울_수_없다() {
    UUID orgId = insertOrganization();
    UUID customerId = insertCustomer(orgId, "acme");
    insertInvoice(orgId, customerId, "2026-08");

    assertThatThrownBy(() -> deleteCustomer(customerId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 라인의_단가는_음수일_수_없다() {
    UUID orgId = insertOrganization();
    UUID invoiceId = insertInvoice(orgId, insertCustomer(orgId, "acme"), "2026-08");

    assertThatThrownBy(() -> insertInvoiceLine(orgId, invoiceId, "token-usage", "-0.5"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 확정_라인은_등록되지_않은_미터_코드와_빈_집계_기준으로도_저장된다() {
    UUID orgId = insertOrganization();
    UUID invoiceId = insertInvoice(orgId, insertCustomer(orgId, "acme"), "2026-08");

    jdbc.update(
        """
        INSERT INTO invoice_line
          (organization_id, invoice_id, billable_metric_code, target_property, dimension_values,
           quantity, unit_price, amount)
        VALUES (?, ?, 'deleted-metric', NULL, '{}', 1200, 0.5, 600)
        """,
        orgId,
        invoiceId);

    assertThat(lineCountOf(invoiceId)).isEqualTo(1);
  }

  @Test
  void 확정_라인에서_비울_수_있는_열은_집계_기준뿐이다() {
    assertThat(
            jdbc.queryForList(
                """
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'invoice_line'
                  AND is_nullable = 'YES'
                """,
                String.class))
        .containsExactly("target_property");
  }

  @Test
  void 라인의_수량과_금액에는_음수를_막는_제약을_걸지_않는다() {
    UUID orgId = insertOrganization();
    UUID invoiceId = insertInvoice(orgId, insertCustomer(orgId, "acme"), "2026-08");

    jdbc.update(
        """
        INSERT INTO invoice_line
          (organization_id, invoice_id, billable_metric_code, target_property, dimension_values,
           quantity, unit_price, amount)
        VALUES (?, ?, 'token-usage', 'token', '{}', -1200, 0.5, -600)
        """,
        orgId,
        invoiceId);

    assertThat(lineCountOf(invoiceId)).isEqualTo(1);
  }

  @Test
  void 라인은_인보이스로_찾는_인덱스를_가진다() {
    Boolean indexExists =
        jdbc.queryForObject(
            """
            SELECT EXISTS(
              SELECT 1 FROM pg_index i
              JOIN pg_class t ON t.oid = i.indrelid
              WHERE t.relname = 'invoice_line'
                AND pg_get_indexdef(i.indexrelid, 1, true) = 'organization_id'
                AND pg_get_indexdef(i.indexrelid, 2, true) = 'invoice_id')
            """,
            Boolean.class);

    assertThat(indexExists).isTrue();
  }

  @Test
  void 확정된_인보이스는_공급가액과_세액을_따로_담는다() {
    UUID orgId = insertOrganization();
    UUID invoiceId = insertInvoice(orgId, insertCustomer(orgId, "acme"), "2026-08");
    insertInvoiceLine(orgId, invoiceId, "token-usage");

    assertThat(
            jdbc.queryForObject(
                "SELECT supply_amount + tax_amount FROM invoice WHERE id = ?",
                Long.class,
                invoiceId))
        .isEqualTo(13200L);
    assertThat(lineCountOf(invoiceId)).isEqualTo(1);
    assertThat(amountColumnsOf("invoice")).containsExactly("supply_amount", "tax_amount");
    assertThat(amountColumnsOf("invoice_line")).containsExactly("amount");
  }

  private String collationOf(String table, String column) {
    return jdbc.queryForObject(
        """
        SELECT collation_name FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
        """,
        String.class,
        table,
        column);
  }

  private UUID insertInvoice(UUID orgId, UUID customerId, String period) {
    return jdbc.queryForObject(
        """
        INSERT INTO invoice
          (organization_id, customer_id, period, supply_amount, tax_amount, finalized_at)
        VALUES (?, ?, ?, 12000, 1200, now())
        RETURNING id
        """,
        UUID.class,
        orgId,
        customerId,
        period);
  }

  private void insertInvoiceLine(UUID orgId, UUID invoiceId, String billableMetricCode) {
    insertInvoiceLine(orgId, invoiceId, billableMetricCode, "0.5");
  }

  private void insertInvoiceLine(
      UUID orgId, UUID invoiceId, String billableMetricCode, String unitPrice) {
    jdbc.update(
        """
        INSERT INTO invoice_line
          (organization_id, invoice_id, billable_metric_code, target_property, dimension_values,
           quantity, unit_price, amount)
        VALUES (?, ?, ?, 'token', '{}', 1200, ?::numeric, 600)
        """,
        orgId,
        invoiceId,
        billableMetricCode,
        unitPrice);
  }

  private int lineCountOf(UUID invoiceId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM invoice_line WHERE invoice_id = ?", Integer.class, invoiceId);
  }

  private List<String> amountColumnsOf(String table) {
    return jdbc.queryForList(
        """
        SELECT column_name FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = ? AND column_name LIKE '%amount%'
        ORDER BY ordinal_position
        """,
        String.class, table);
  }

  private void insertPricePolicy(UUID orgId, String billableMetricCode) {
    jdbc.update(
        "INSERT INTO price_policy (organization_id, billable_metric_code) VALUES (?, ?)",
        orgId,
        billableMetricCode);
  }

  private void insertPriceRate(
      UUID orgId, String billableMetricCode, String dimensionValues, String price) {
    jdbc.update(
        """
        INSERT INTO price_rate (organization_id, billable_metric_code, dimension_values, unit_price)
        VALUES (?, ?, ?::jsonb, ?::numeric)
        """,
        orgId,
        billableMetricCode,
        dimensionValues,
        price);
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
