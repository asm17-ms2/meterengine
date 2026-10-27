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

  // --- 단가 ---

  @Test
  void 미터가_없으면_단가를_만들_수_없다() {
    UUID organizationId = insertOrganization();

    assertThatThrownBy(() -> insertPriceRate(organizationId, "no-such-metric", "{}", "0.5"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 단가는_음수일_수_없다() {
    UUID organizationId = insertOrganization();
    insertBillableMetric(organizationId, "token-usage");

    assertThatThrownBy(() -> insertPriceRate(organizationId, "token-usage", "{}", "-0.5"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 같은_조합의_단가는_키_순서가_달라도_두_번_저장되지_않는다() {
    UUID organizationId = insertOrganization();
    insertBillableMetric(organizationId, "token-usage");
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
  void 같은_도입사에서_event_type과_target_property가_같은_미터는_두_번_저장되지_않는다() {
    UUID organizationId = insertOrganization();
    insertBillableMetric(organizationId, "token-usage");

    assertThatThrownBy(() -> insertBillableMetric(organizationId, "token-usage-copy"))
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
    assertThat(collationOf("invoice", "customer_name")).isEqualTo("korean");
    assertThat(collationOf("user_account", "name")).isEqualTo("korean");
    assertThat(collationOf("api_key", "label")).isEqualTo("korean");
  }

  @Test
  void 식별자_컬럼에는_한국어_collation을_붙이지_않는다() {
    assertThat(collationOf("billable_metric", "code")).isNull();
    assertThat(collationOf("event", "transaction_id")).isNull();
    assertThat(collationOf("event", "type")).isNull();
    assertThat(collationOf("user_account", "email")).isNull();
  }

  @Test
  void 코드가_비교하는_제약은_마이그레이션이_정한_이름으로_있다() {
    assertThat(constraintNames())
        .contains(
            "organization_pk",
            "customer_pk",
            "billable_metric_pk",
            "event_pk",
            "price_rate_pk",
            "invoice_pk",
            "invoice_line_pk",
            "customer_organization_fk",
            "billable_metric_organization_fk",
            "event_organization_fk",
            "price_rate_organization_fk",
            "invoice_organization_fk",
            "invoice_line_organization_fk",
            "event_customer_same_organization_fk",
            "price_rate_billable_metric_same_organization_fk",
            "invoice_customer_same_organization_fk",
            "invoice_line_invoice_same_organization_fk",
            "user_account_pk",
            "organization_member_pk",
            "user_session_pk",
            "consent_pk",
            "api_key_pk",
            "organization_member_organization_fk",
            "organization_member_user_account_fk",
            "user_session_user_account_fk",
            "user_session_organization_member_fk",
            "consent_user_account_fk",
            "api_key_organization_fk");
  }

  @Test
  void 계정과_API_키의_검사와_유일_제약은_마이그레이션이_정한_이름으로_있다() {
    assertThat(
            jdbcTemplate.queryForList(
                """
                SELECT conname FROM pg_constraint
                WHERE connamespace = 'public'::regnamespace AND contype IN ('c', 'u')
                """,
                String.class))
        .contains(
            "organization_status_check",
            "user_account_role_check",
            "user_session_token_hash_unique",
            "user_session_token_hash_check",
            "consent_document_check",
            "api_key_mode_check",
            "api_key_key_hash_unique",
            "api_key_key_hash_check",
            "api_key_last_four_check",
            "api_key_scopes_check",
            "api_key_rate_limit_per_minute_check");
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT indexname FROM pg_indexes WHERE tablename = 'user_account'", String.class))
        .contains("user_account_email_unique");
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
                      (organization_id, customer_id, customer_name, period, supply_amount, tax_amount,
                       total_amount)
                    VALUES (?, ?, 'acme', '2026-08', 12000, 1200, 13200)
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
  void 합계가_공급가액과_세액의_합과_다른_인보이스는_저장되지_않는다() {
    UUID organizationId = insertOrganization();
    UUID customerId = insertCustomer(organizationId, "acme");

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO invoice
                      (organization_id, customer_id, customer_name, period, supply_amount, tax_amount,
                       total_amount, finalized_at)
                    VALUES (?, ?, 'acme', '2026-08', 12000, 1200, 13201, now())
                    """,
                    organizationId,
                    customerId))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("invoice_total_amount_check");
  }

  @Test
  void 고객_이름은_생략하면_DB가_대신_채우지_않는다() {
    UUID organizationId = insertOrganization();
    UUID customerId = insertCustomer(organizationId, "acme");

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO invoice
                      (organization_id, customer_id, period, supply_amount, tax_amount, total_amount,
                       finalized_at)
                    VALUES (?, ?, '2026-08', 12000, 1200, 13200, now())
                    """,
                    organizationId,
                    customerId))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("customer_name");
  }

  @Test
  void 합계는_생략하면_DB가_대신_채우지_않는다() {
    UUID organizationId = insertOrganization();
    UUID customerId = insertCustomer(organizationId, "acme");

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO invoice
                      (organization_id, customer_id, customer_name, period, supply_amount, tax_amount,
                       finalized_at)
                    VALUES (?, ?, 'acme', '2026-08', 12000, 1200, now())
                    """,
                    organizationId,
                    customerId))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("total_amount");
  }

  @Test
  void 저장된_인보이스는_UPDATE할_수_없다() {
    UUID invoiceId = insertInvoiceWithLine();

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE invoice SET finalized_at = now() WHERE id = ?", invoiceId))
        .isInstanceOf(UncategorizedSQLException.class)
        .hasMessageContaining("invoice is append-only");
  }

  @Test
  void 저장된_인보이스는_DELETE할_수_없다() {
    UUID organizationId = insertOrganization();
    UUID invoiceId =
        insertInvoice(organizationId, insertCustomer(organizationId, "acme"), "2026-08");

    assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM invoice WHERE id = ?", invoiceId))
        .isInstanceOf(UncategorizedSQLException.class)
        .hasMessageContaining("invoice is append-only");
  }

  @Test
  void 인보이스_테이블은_TRUNCATE할_수_없다() {
    assertThatThrownBy(
            () ->
                jdbcTemplate.execute(
                    "TRUNCATE invoice, invoice_line, outbound_email, payment_attempt,"
                        + " payment_cancellation"))
        .isInstanceOf(UncategorizedSQLException.class)
        .hasMessageContaining("invoice is append-only");
  }

  @Test
  void 저장된_라인은_UPDATE할_수_없다() {
    UUID invoiceId = insertInvoiceWithLine();

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE invoice_line SET amount = 0 WHERE invoice_id = ?", invoiceId))
        .isInstanceOf(UncategorizedSQLException.class)
        .hasMessageContaining("invoice_line is append-only");
  }

  @Test
  void 저장된_라인은_DELETE할_수_없다() {
    UUID invoiceId = insertInvoiceWithLine();

    assertThatThrownBy(
            () -> jdbcTemplate.update("DELETE FROM invoice_line WHERE invoice_id = ?", invoiceId))
        .isInstanceOf(UncategorizedSQLException.class)
        .hasMessageContaining("invoice_line is append-only");
  }

  @Test
  void 라인_테이블은_TRUNCATE할_수_없다() {
    assertThatThrownBy(() -> jdbcTemplate.execute("TRUNCATE invoice_line"))
        .isInstanceOf(UncategorizedSQLException.class)
        .hasMessageContaining("invoice_line is append-only");
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
  void 확정된_인보이스는_공급가액과_세액과_합계를_따로_담는다() {
    UUID organizationId = insertOrganization();
    UUID invoiceId =
        insertInvoice(organizationId, insertCustomer(organizationId, "acme"), "2026-08");
    insertInvoiceLine(organizationId, invoiceId, "token-usage");

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT total_amount FROM invoice WHERE id = ?", Long.class, invoiceId))
        .isEqualTo(13200L);
    assertThat(invoiceLineCountOf(invoiceId)).isEqualTo(1);
    assertThat(amountColumnsOf("invoice"))
        .containsExactly("supply_amount", "tax_amount", "total_amount");
    assertThat(amountColumnsOf("invoice_line")).containsExactly("amount");
  }

  // --- 보낼 메일 ---

  @Test
  void 같은_인보이스의_청구_메일은_받는_주소_없이도_여러_건_쌓인다() {
    UUID organizationId = insertOrganization();
    UUID invoiceId =
        insertInvoice(organizationId, insertCustomer(organizationId, "acme"), "2026-08");

    insertOutboundEmail(organizationId, "invoice", "2026-08", invoiceId, null);
    insertOutboundEmail(organizationId, "invoice", "2026-08", invoiceId, "billing@acme.com");

    assertThat(
            jdbcTemplate.queryForList(
                "SELECT recipient_email FROM outbound_email WHERE invoice_id = ?",
                String.class,
                invoiceId))
        .containsExactlyInAnyOrder(null, "billing@acme.com");
  }

  @Test
  void 청구_메일에는_인보이스가_있어야_한다() {
    UUID organizationId = insertOrganization();

    assertThatThrownBy(() -> insertOutboundEmail(organizationId, "invoice", "2026-08", null, null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("outbound_email_kind_invoice_check");
  }

  @Test
  void 보고_메일은_인보이스_없이_저장되고_인보이스를_붙이면_거절된다() {
    UUID organizationId = insertOrganization();
    UUID invoiceId =
        insertInvoice(organizationId, insertCustomer(organizationId, "acme"), "2026-08");

    insertOutboundEmail(organizationId, "finalization_report", "2026-08", null, "ops@acme.com");

    assertThatThrownBy(
            () ->
                insertOutboundEmail(
                    organizationId, "finalization_report", "2026-08", invoiceId, null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("outbound_email_kind_invoice_check");
  }

  @Test
  void 정한_종류가_아닌_메일은_저장되지_않는다() {
    UUID organizationId = insertOrganization();

    assertThatThrownBy(() -> insertOutboundEmail(organizationId, "receipt", "2026-08", null, null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("outbound_email_kind_check");
  }

  @Test
  void 메일의_달은_월을_두_자리로_적은_표기만_저장된다() {
    UUID organizationId = insertOrganization();

    assertThatThrownBy(
            () ->
                insertOutboundEmail(
                    organizationId, "finalization_report", "2026-8", null, "ops@acme.com"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("outbound_email_period_format");
  }

  @Test
  void 다른_도입사의_인보이스에는_메일을_붙일_수_없다() {
    UUID organizationId = insertOrganization();
    UUID invoiceId =
        insertInvoice(organizationId, insertCustomer(organizationId, "acme"), "2026-08");

    assertThatThrownBy(
            () -> insertOutboundEmail(insertOrganization(), "invoice", "2026-08", invoiceId, null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("outbound_email_invoice_same_organization_fk");
  }

  @Test
  void 저장된_메일은_UPDATE할_수_없다() {
    UUID outboundEmailId = insertOutboundEmailOfNewInvoice();

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE outbound_email SET recipient_email = 'new@acme.com' WHERE id = ?",
                    outboundEmailId))
        .isInstanceOf(UncategorizedSQLException.class)
        .hasMessageContaining("outbound_email is append-only");
  }

  @Test
  void 저장된_메일은_DELETE할_수_없다() {
    UUID outboundEmailId = insertOutboundEmailOfNewInvoice();

    assertThatThrownBy(
            () -> jdbcTemplate.update("DELETE FROM outbound_email WHERE id = ?", outboundEmailId))
        .isInstanceOf(UncategorizedSQLException.class)
        .hasMessageContaining("outbound_email is append-only");
  }

  @Test
  void 메일_테이블은_TRUNCATE할_수_없다() {
    assertThatThrownBy(() -> jdbcTemplate.execute("TRUNCATE outbound_email"))
        .isInstanceOf(UncategorizedSQLException.class)
        .hasMessageContaining("outbound_email is append-only");
  }

  // --- 계정과 API 키 ---

  @Test
  void 정한_상태가_아닌_도입사는_저장되지_않는다() {
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO organization (name, status) VALUES ('테스트 도입사', 'deleted')"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("organization_status_check");
  }

  @Test
  void 상태를_생략한_도입사는_active로_저장되고_생성_시각이_찍힌다() {
    UUID organizationId = insertOrganization();

    var organization =
        jdbcTemplate.queryForMap(
            "SELECT status, created_at FROM organization WHERE id = ?", organizationId);

    assertThat(organization.get("status")).isEqualTo("active");
    assertThat(organization.get("created_at")).isNotNull();
  }

  @Test
  void 대소문자만_다른_이메일의_계정은_두_번_저장되지_않는다() {
    insertUserAccount("Member@Example.com");

    assertThatThrownBy(() -> insertUserAccount("member@example.com"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("user_account_email_unique");
  }

  @Test
  void 소속되지_않은_도입사로는_세션을_만들_수_없다() {
    UUID organizationId = insertOrganization();
    UUID userAccountId = insertUserAccount("member@example.com");

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO user_session
                      (id, user_account_id, token_hash, organization_id, created_at, expires_at)
                    VALUES (gen_random_uuid(), ?, sha256('token'::bytea), ?, now(),
                            now() + interval '12 hours')
                    """,
                    userAccountId,
                    organizationId))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("user_session_organization_member_fk");
  }

  @Test
  void 저장된_동의는_UPDATE할_수_없다() {
    UUID consentId = insertConsent(insertUserAccount("member@example.com"));

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE consent SET document_version = '2026-11-01' WHERE id = ?", consentId))
        .isInstanceOf(UncategorizedSQLException.class)
        .hasMessageContaining("consent is append-only");
  }

  @Test
  void 저장된_동의는_DELETE할_수_없다() {
    UUID consentId = insertConsent(insertUserAccount("member@example.com"));

    assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM consent WHERE id = ?", consentId))
        .isInstanceOf(UncategorizedSQLException.class)
        .hasMessageContaining("consent is append-only");
  }

  @Test
  void 동의_테이블은_TRUNCATE할_수_없다() {
    assertThatThrownBy(() -> jdbcTemplate.execute("TRUNCATE consent"))
        .isInstanceOf(UncategorizedSQLException.class)
        .hasMessageContaining("consent is append-only");
  }

  @Test
  void 스코프가_없는_API_키는_저장되지_않는다() {
    UUID organizationId = insertOrganization();

    assertThatThrownBy(() -> insertApiKey(organizationId, "{}"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("api_key_scopes_check");
  }

  @Test
  void 정한_스코프가_아닌_API_키는_저장되지_않는다() {
    UUID organizationId = insertOrganization();

    assertThatThrownBy(() -> insertApiKey(organizationId, "{read,write}"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("api_key_scopes_check");
  }

  @Test
  void 정한_스코프만_가진_API_키는_저장된다() {
    UUID organizationId = insertOrganization();

    insertApiKey(organizationId, "{ingest,read,admin}");

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM api_key WHERE organization_id = ?",
                Integer.class,
                organizationId))
        .isEqualTo(1);
  }

  // --- 감사 ---

  @Test
  void 감사_테이블의_제약과_인덱스는_마이그레이션이_정한_이름으로_있다() {
    assertThat(constraintNames())
        .contains("audit_log_pk")
        .doesNotContain("audit_log_organization_fk");
    assertThat(
            jdbcTemplate.queryForList(
                """
                SELECT conname FROM pg_constraint
                WHERE connamespace = 'public'::regnamespace AND contype = 'c'
                """,
                String.class))
        .contains(
            "audit_log_actor_type_check", "audit_log_action_check", "audit_log_actor_id_check");
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT indexname FROM pg_indexes WHERE tablename = 'audit_log'", String.class))
        .contains("audit_log_organization_id_occurred_at_index");
  }

  @Test
  void 대문자가_섞인_감사_행위는_저장되지_않는다() {
    assertThatThrownBy(() -> insertAuditLog("system", null, "PriceRateCreated"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("audit_log_action_check");
  }

  @Test
  void 밑줄로_끝나는_감사_행위는_저장되지_않는다() {
    assertThatThrownBy(() -> insertAuditLog("system", null, "price_rate_"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("audit_log_action_check");
  }

  @Test
  void 정한_종류가_아닌_감사_행위자는_저장되지_않는다() {
    assertThatThrownBy(() -> insertAuditLog("robot", UUID.randomUUID(), "price_rate_created"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("audit_log_actor_type_check");
  }

  @Test
  void 시스템이_아닌_감사_행위자는_id_없이_저장되지_않는다() {
    assertThatThrownBy(() -> insertAuditLog("api_key", null, "price_rate_created"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("audit_log_actor_id_check");
  }

  @Test
  void 시스템_감사_행위자는_id를_가질_수_없다() {
    assertThatThrownBy(() -> insertAuditLog("system", UUID.randomUUID(), "price_rate_created"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("audit_log_actor_id_check");
  }

  @Test
  void 감사_detail은_생략하면_빈_객체로_저장된다() {
    UUID auditLogId = insertAuditLog("system", null, "price_rate_created");

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT detail::text FROM audit_log WHERE id = ?", String.class, auditLogId))
        .isEqualTo("{}");
  }

  private UUID insertAuditLog(String actorType, UUID actorId, String action) {
    return jdbcTemplate.queryForObject(
        """
        INSERT INTO audit_log (id, occurred_at, actor_type, actor_id, action)
        VALUES (gen_random_uuid(), now(), ?, ?, ?)
        RETURNING id
        """,
        UUID.class,
        actorType,
        actorId,
        action);
  }

  private UUID insertUserAccount(String email) {
    UUID userAccountId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO user_account (id, email, password_hash, name, role, created_at)
        VALUES (?, ?, '{bcrypt}hash', '테스트 사용자', 'member', now())
        """,
        userAccountId,
        email);
    return userAccountId;
  }

  private UUID insertConsent(UUID userAccountId) {
    UUID consentId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO consent (id, user_account_id, document, document_version, agreed_at)
        VALUES (?, ?, 'terms_of_service', '2026-10-01', now())
        """,
        consentId,
        userAccountId);
    return consentId;
  }

  private void insertApiKey(UUID organizationId, String scopes) {
    jdbcTemplate.update(
        """
        INSERT INTO api_key
          (id, organization_id, mode, label, key_hash, last_four, scopes, created_at)
        VALUES (gen_random_uuid(), ?, 'live', '테스트 키', sha256(gen_random_uuid()::text::bytea),
                'abcd', ?::varchar[], now())
        """,
        organizationId,
        scopes);
  }

  private UUID insertOutboundEmail(
      UUID organizationId, String kind, String period, UUID invoiceId, String recipientEmail) {
    return jdbcTemplate.queryForObject(
        """
        INSERT INTO outbound_email
          (id, organization_id, kind, period, invoice_id, recipient_email, subject, html_body,
           created_at)
        VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, '제목', '<p>본문</p>', now())
        RETURNING id
        """,
        UUID.class,
        organizationId,
        kind,
        period,
        invoiceId,
        recipientEmail);
  }

  private UUID insertOutboundEmailOfNewInvoice() {
    UUID organizationId = insertOrganization();
    UUID invoiceId =
        insertInvoice(organizationId, insertCustomer(organizationId, "acme"), "2026-08");
    return insertOutboundEmail(organizationId, "invoice", "2026-08", invoiceId, null);
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
          (organization_id, customer_id, customer_name, period, supply_amount, tax_amount,
           total_amount, finalized_at)
        VALUES (?, ?, 'acme', ?, 12000, 1200, 13200, now())
        RETURNING id
        """,
        UUID.class,
        organizationId,
        customerId,
        period);
  }

  private UUID insertInvoiceWithLine() {
    UUID organizationId = insertOrganization();
    UUID invoiceId =
        insertInvoice(organizationId, insertCustomer(organizationId, "acme"), "2026-08");
    insertInvoiceLine(organizationId, invoiceId, "token-usage");
    return invoiceId;
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
