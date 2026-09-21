package com.meterengine.global;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meterengine.TestcontainersConfiguration;
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

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class SchemaConstraintTest {

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void 같은_도입사에서_같은_transaction_id는_두_번_저장되지_않는다() {
    UUID organizationId = insertOrganization();
    UUID customerId = insertCustomer(organizationId, "acme");
    insertEvent(organizationId, customerId, "tx-1");

    assertThatThrownBy(() -> insertEvent(organizationId, customerId, "tx-1"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 저장된_이벤트는_UPDATE할_수_없다() {
    UUID organizationId = insertOrganization();
    insertEvent(organizationId, insertCustomer(organizationId, "acme"), "tx-1");

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE event SET occurred_at = occurred_at + interval '1 hour' WHERE transaction_id = 'tx-1'"))
        .isInstanceOf(UncategorizedSQLException.class)
        .hasMessageContaining("append-only");
  }

  @Test
  void 저장된_이벤트는_DELETE할_수_없다() {
    UUID organizationId = insertOrganization();
    insertEvent(organizationId, insertCustomer(organizationId, "acme"), "tx-1");

    assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM event WHERE transaction_id = 'tx-1'"))
        .isInstanceOf(UncategorizedSQLException.class)
        .hasMessageContaining("append-only");
  }

  @Test
  void 이벤트_테이블은_TRUNCATE할_수_없다() {
    assertThatThrownBy(() -> jdbcTemplate.execute("TRUNCATE event"))
        .isInstanceOf(UncategorizedSQLException.class)
        .hasMessageContaining("append-only");
  }

  @Test
  void received_at은_요청이_값을_보내도_서버가_찍은_시각으로_덮어쓴다() {
    OffsetDateTime clientSuppliedTime = OffsetDateTime.parse("2020-01-01T00:00:00Z");
    UUID organizationId = insertOrganization();

    jdbcTemplate.update(
        """
        INSERT INTO event
          (organization_id, transaction_id, customer_id, type, occurred_at, received_at)
        VALUES (?, 'tx-1', ?, 'chat_completion', now(), ?)
        """,
        organizationId,
        insertCustomer(organizationId, "acme"),
        clientSuppliedTime);

    OffsetDateTime receivedAt =
        jdbcTemplate.queryForObject(
            "SELECT received_at FROM event WHERE transaction_id = 'tx-1'", OffsetDateTime.class);
    assertThat(receivedAt).isAfter(clientSuppliedTime);
  }

  // --- 고객 삭제 가드 ---

  @Test
  void 이벤트가_있는_고객은_지울_수_없다() {
    UUID organizationId = insertOrganization();
    UUID customerId = insertCustomer(organizationId, "acme");
    insertEvent(organizationId, customerId, "tx-1");

    assertThatThrownBy(() -> deleteCustomer(customerId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 이벤트가_없는_고객은_지울_수_있다() {
    UUID organizationId = insertOrganization();
    UUID customerId = insertCustomer(organizationId, "acme");

    deleteCustomer(customerId);

    assertThat(customerExists(customerId)).isFalse();
  }

  // --- 가격 정책 / 단가 ---

  @Test
  void 미터가_없으면_가격_정책을_만들_수_없다() {
    UUID organizationId = insertOrganization();

    assertThatThrownBy(() -> insertPricePolicy(organizationId, "no-such-metric"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 다른_도입사의_미터에는_가격_정책을_붙일_수_없다() {
    UUID organizationId = insertOrganization();
    insertBillableMetric(organizationId, "token-usage");
    UUID otherOrganizationId = insertOrganization();

    assertThatThrownBy(() -> insertPricePolicy(otherOrganizationId, "token-usage"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 같은_미터에_가격_정책은_하나만_만들_수_있다() {
    UUID organizationId = insertOrganization();
    insertBillableMetric(organizationId, "token-usage");
    insertPricePolicy(organizationId, "token-usage");

    assertThatThrownBy(() -> insertPricePolicy(organizationId, "token-usage"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 가격_정책이_없으면_단가를_만들_수_없다() {
    UUID organizationId = insertOrganization();
    insertBillableMetric(organizationId, "token-usage");

    assertThatThrownBy(() -> insertPriceRate(organizationId, "token-usage", "{}", "0.5"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 단가는_음수일_수_없다() {
    UUID organizationId = insertOrganization();
    insertBillableMetric(organizationId, "token-usage");
    insertPricePolicy(organizationId, "token-usage");

    assertThatThrownBy(() -> insertPriceRate(organizationId, "token-usage", "{}", "-0.5"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 같은_조합의_단가는_키_순서가_달라도_두_번_저장되지_않는다() {
    UUID organizationId = insertOrganization();
    insertBillableMetric(organizationId, "token-usage");
    insertPricePolicy(organizationId, "token-usage");
    insertPriceRate(
        organizationId, "token-usage", "{\"model\": \"opus5\", \"region\": \"kr\"}", "0.5");

    assertThatThrownBy(
            () ->
                insertPriceRate(
                    organizationId,
                    "token-usage",
                    "{\"region\": \"kr\", \"model\": \"opus5\"}",
                    "2.5"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void billable_metric에는_더_이상_unit_price_열이_없다() {
    Boolean columnExists =
        jdbcTemplate.queryForObject(
            """
            SELECT EXISTS(
              SELECT 1 FROM information_schema.columns
              WHERE table_name = 'billable_metric' AND column_name = 'unit_price')
            """,
            Boolean.class);

    assertThat(columnExists).isFalse();
  }

  private void insertBillableMetric(UUID organizationId, String code) {
    jdbcTemplate.update(
        """
        INSERT INTO billable_metric
          (organization_id, code, name, event_type, aggregation, target_property)
        VALUES (?, ?, '토큰 사용량', 'chat_completion', 'sum', 'token')
        """,
        organizationId,
        code);
  }

  // --- 이름 collation ---

  @Test
  void 사람이_읽는_이름_컬럼은_한국어_collation을_쓴다() {
    assertThat(collationOf("customer", "name")).isEqualTo("korean");
    assertThat(collationOf("organization", "name")).isEqualTo("korean");
    assertThat(collationOf("billable_metric", "name")).isEqualTo("korean");
  }

  @Test
  void 식별자_컬럼에는_한국어_collation을_붙이지_않는다() {
    assertThat(collationOf("billable_metric", "code")).isNull();
    assertThat(collationOf("event", "transaction_id")).isNull();
    assertThat(collationOf("event", "type")).isNull();
  }

  @Test
  void 코드가_비교하는_제약은_마이그레이션이_정한_이름으로_있다() {
    assertThat(constraintNames())
        .contains(
            "organization_pk",
            "customer_pk",
            "billable_metric_pk",
            "event_pk",
            "price_policy_pk",
            "price_rate_pk",
            "invoice_pk",
            "invoice_line_pk",
            "customer_organization_fk",
            "billable_metric_organization_fk",
            "event_organization_fk",
            "price_policy_organization_fk",
            "price_rate_organization_fk",
            "invoice_organization_fk",
            "invoice_line_organization_fk",
            "event_customer_same_organization_fk",
            "price_policy_billable_metric_same_organization_fk",
            "price_rate_price_policy_same_organization_fk",
            "invoice_customer_same_organization_fk",
            "invoice_line_invoice_same_organization_fk");
  }

  @Test
  void PostgreSQL_기본_이름의_PK와_FK는_남아_있지_않다() {
    assertThat(constraintNames())
        .noneMatch(name -> name.endsWith("_pkey") || name.endsWith("_fkey"));
  }

  // --- 인보이스 확정본 ---

  @Test
  void 같은_고객의_같은_달은_두_번_확정되지_않는다() {
    UUID organizationId = insertOrganization();
    UUID customerId = insertCustomer(organizationId, "acme");
    insertInvoice(organizationId, customerId, "2026-08");

    assertThatThrownBy(() -> insertInvoice(organizationId, customerId, "2026-08"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 확정_인보이스는_고객마다_그리고_달마다_따로_저장된다() {
    UUID organizationId = insertOrganization();
    UUID customerId = insertCustomer(organizationId, "acme");
    insertInvoice(organizationId, customerId, "2026-08");
    insertInvoice(organizationId, customerId, "2026-09");
    insertInvoice(organizationId, insertCustomer(organizationId, "beta"), "2026-08");

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM invoice WHERE organization_id = ?",
                Integer.class,
                organizationId))
        .isEqualTo(3);
  }

  @Test
  void 청구_기간은_월을_두_자리로_적은_표기만_저장된다() {
    UUID organizationId = insertOrganization();
    UUID customerId = insertCustomer(organizationId, "acme");

    assertThatThrownBy(() -> insertInvoice(organizationId, customerId, "2026-8"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 존재하지_않는_달은_청구_기간이_될_수_없다() {
    UUID organizationId = insertOrganization();
    UUID customerId = insertCustomer(organizationId, "acme");

    assertThatThrownBy(() -> insertInvoice(organizationId, customerId, "2026-13"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 다른_도입사의_고객에는_인보이스를_붙일_수_없다() {
    UUID organizationId = insertOrganization();
    UUID customerId = insertCustomer(organizationId, "acme");
    UUID otherOrganizationId = insertOrganization();

    assertThatThrownBy(() -> insertInvoice(otherOrganizationId, customerId, "2026-08"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 확정_시각은_생략하면_DB가_대신_채우지_않는다() {
    UUID organizationId = insertOrganization();
    UUID customerId = insertCustomer(organizationId, "acme");

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO invoice
                      (organization_id, customer_id, period, supply_amount, tax_amount)
                    VALUES (?, ?, '2026-08', 12000, 1200)
                    """,
                    organizationId,
                    customerId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 다른_도입사의_인보이스에는_라인을_붙일_수_없다() {
    UUID organizationId = insertOrganization();
    UUID invoiceId =
        insertInvoice(organizationId, insertCustomer(organizationId, "acme"), "2026-08");
    UUID otherOrganizationId = insertOrganization();

    assertThatThrownBy(() -> insertInvoiceLine(otherOrganizationId, invoiceId, "token-usage"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 같은_인보이스에_같은_미터의_라인은_두_번_저장되지_않는다() {
    UUID organizationId = insertOrganization();
    UUID invoiceId =
        insertInvoice(organizationId, insertCustomer(organizationId, "acme"), "2026-08");
    insertInvoiceLine(organizationId, invoiceId, "token-usage");

    assertThatThrownBy(() -> insertInvoiceLine(organizationId, invoiceId, "token-usage"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 라인이_있는_인보이스는_지울_수_없다() {
    UUID organizationId = insertOrganization();
    UUID invoiceId =
        insertInvoice(organizationId, insertCustomer(organizationId, "acme"), "2026-08");
    insertInvoiceLine(organizationId, invoiceId, "token-usage");

    assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM invoice WHERE id = ?", invoiceId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 확정_인보이스가_있는_고객은_지울_수_없다() {
    UUID organizationId = insertOrganization();
    UUID customerId = insertCustomer(organizationId, "acme");
    insertInvoice(organizationId, customerId, "2026-08");

    assertThatThrownBy(() -> deleteCustomer(customerId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 라인의_단가는_음수일_수_없다() {
    UUID organizationId = insertOrganization();
    UUID invoiceId =
        insertInvoice(organizationId, insertCustomer(organizationId, "acme"), "2026-08");

    assertThatThrownBy(() -> insertInvoiceLine(organizationId, invoiceId, "token-usage", "-0.5"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 확정_라인은_등록되지_않은_미터_코드와_빈_집계_기준으로도_저장된다() {
    UUID organizationId = insertOrganization();
    UUID invoiceId =
        insertInvoice(organizationId, insertCustomer(organizationId, "acme"), "2026-08");

    jdbcTemplate.update(
        """
        INSERT INTO invoice_line
          (organization_id, invoice_id, billable_metric_code, target_property, dimension_values,
           quantity, unit_price, amount)
        VALUES (?, ?, 'deleted-metric', NULL, '{}', 1200, 0.5, 600)
        """,
        organizationId,
        invoiceId);

    assertThat(invoiceLineCountOf(invoiceId)).isEqualTo(1);
  }

  @Test
  void 확정_라인에서_비울_수_있는_열은_집계_기준뿐이다() {
    assertThat(
            jdbcTemplate.queryForList(
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
    UUID organizationId = insertOrganization();
    UUID invoiceId =
        insertInvoice(organizationId, insertCustomer(organizationId, "acme"), "2026-08");

    jdbcTemplate.update(
        """
        INSERT INTO invoice_line
          (organization_id, invoice_id, billable_metric_code, target_property, dimension_values,
           quantity, unit_price, amount)
        VALUES (?, ?, 'token-usage', 'token', '{}', -1200, 0.5, -600)
        """,
        organizationId,
        invoiceId);

    assertThat(invoiceLineCountOf(invoiceId)).isEqualTo(1);
  }

  @Test
  void 라인은_인보이스로_찾는_인덱스를_가진다() {
    Boolean indexExists =
        jdbcTemplate.queryForObject(
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
    UUID organizationId = insertOrganization();
    UUID invoiceId =
        insertInvoice(organizationId, insertCustomer(organizationId, "acme"), "2026-08");
    insertInvoiceLine(organizationId, invoiceId, "token-usage");

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT supply_amount + tax_amount FROM invoice WHERE id = ?",
                Long.class,
                invoiceId))
        .isEqualTo(13200L);
    assertThat(invoiceLineCountOf(invoiceId)).isEqualTo(1);
    assertThat(amountColumnsOf("invoice")).containsExactly("supply_amount", "tax_amount");
    assertThat(amountColumnsOf("invoice_line")).containsExactly("amount");
  }

  private String collationOf(String table, String column) {
    return jdbcTemplate.queryForObject(
        """
        SELECT collation_name FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
        """,
        String.class,
        table,
        column);
  }

  private UUID insertInvoice(UUID organizationId, UUID customerId, String period) {
    return jdbcTemplate.queryForObject(
        """
        INSERT INTO invoice
          (organization_id, customer_id, period, supply_amount, tax_amount, finalized_at)
        VALUES (?, ?, ?, 12000, 1200, now())
        RETURNING id
        """,
        UUID.class,
        organizationId,
        customerId,
        period);
  }

  private void insertInvoiceLine(UUID organizationId, UUID invoiceId, String billableMetricCode) {
    insertInvoiceLine(organizationId, invoiceId, billableMetricCode, "0.5");
  }

  private void insertInvoiceLine(
      UUID organizationId, UUID invoiceId, String billableMetricCode, String unitPrice) {
    jdbcTemplate.update(
        """
        INSERT INTO invoice_line
          (organization_id, invoice_id, billable_metric_code, target_property, dimension_values,
           quantity, unit_price, amount)
        VALUES (?, ?, ?, 'token', '{}', 1200, ?::numeric, 600)
        """,
        organizationId,
        invoiceId,
        billableMetricCode,
        unitPrice);
  }

  private int invoiceLineCountOf(UUID invoiceId) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM invoice_line WHERE invoice_id = ?", Integer.class, invoiceId);
  }

  private List<String> amountColumnsOf(String table) {
    return jdbcTemplate.queryForList(
        """
        SELECT column_name FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = ? AND column_name LIKE '%amount%'
        ORDER BY ordinal_position
        """,
        String.class, table);
  }

  private void insertPricePolicy(UUID organizationId, String billableMetricCode) {
    jdbcTemplate.update(
        "INSERT INTO price_policy (organization_id, billable_metric_code) VALUES (?, ?)",
        organizationId,
        billableMetricCode);
  }

  private void insertPriceRate(
      UUID organizationId, String billableMetricCode, String dimensionValues, String price) {
    jdbcTemplate.update(
        """
        INSERT INTO price_rate (organization_id, billable_metric_code, dimension_values, unit_price)
        VALUES (?, ?, ?::jsonb, ?::numeric)
        """,
        organizationId,
        billableMetricCode,
        dimensionValues,
        price);
  }

  private void deleteCustomer(UUID customerId) {
    jdbcTemplate.update("DELETE FROM customer WHERE id = ?", customerId);
  }

  private List<String> constraintNames() {
    return jdbcTemplate.queryForList(
        """
        SELECT conname FROM pg_constraint
        WHERE connamespace = 'public'::regnamespace AND contype IN ('p', 'f')
        """,
        String.class);
  }

  private boolean customerExists(UUID customerId) {
    return Boolean.TRUE.equals(
        jdbcTemplate.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM customer WHERE id = ?)", Boolean.class, customerId));
  }

  private UUID insertOrganization() {
    return jdbcTemplate.queryForObject(
        "INSERT INTO organization (name) VALUES ('테스트 도입사') RETURNING id", UUID.class);
  }

  private UUID insertCustomer(UUID organizationId, String name) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO customer (organization_id, name) VALUES (?, ?) RETURNING id",
        UUID.class,
        organizationId,
        name);
  }

  private void insertEvent(UUID organizationId, UUID customerId, String transactionId) {
    jdbcTemplate.update(
        """
        INSERT INTO event
          (organization_id, transaction_id, customer_id, type, properties, occurred_at)
        VALUES (?, ?, ?, 'chat_completion', '{"token": 1200}', now())
        """,
        organizationId,
        transactionId,
        customerId);
  }
}
