package com.meterengine.invoice;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.TestcontainersConfiguration;
import com.meterengine.invoice.entity.Invoice;
import com.meterengine.invoice.entity.InvoiceLine;
import com.meterengine.invoice.repository.InvoiceLineRepository;
import com.meterengine.invoice.repository.InvoiceRepository;
import com.meterengine.invoice.service.DraftInvoiceService;
import com.meterengine.invoice.service.InvoiceFinalizationService;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class InvoiceFinalizeIntegrationTest {

  private static final YearMonth AUGUST = YearMonth.of(2026, 8);

  @Autowired private InvoiceFinalizationService invoiceFinalizationService;
  @Autowired private DraftInvoiceService draftInvoiceService;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private InvoiceLineRepository invoiceLineRepository;
  @Autowired private EntityManager entityManager;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void 확정하면_고객_이름과_달과_KST_확정_시각과_라인의_수량과_단가가_저장된다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID acme = insertCustomer(organizationId, "아크메");
    insertEvent(organizationId, "tx-1", acme, "token", 500, "2026-08-10T12:00:00+09:00");
    insertEvent(organizationId, "tx-2", acme, "token", 2799, "2026-08-31T23:59:59+09:00");
    insertEvent(organizationId, "tx-3", acme, "token", 1000, "2026-09-01T00:00:00+09:00");

    Invoice invoice = invoiceFinalizationService.finalize(organizationId, acme, AUGUST);
    flushAndClear();

    Invoice reloadedInvoice = invoiceRepository.findById(invoice.getId()).orElseThrow();
    assertThat(reloadedInvoice.getCustomerId()).isEqualTo(acme);
    assertThat(reloadedInvoice.getCustomerName()).isEqualTo("아크메");
    assertThat(reloadedInvoice.getPeriod()).isEqualTo("2026-08");
    assertThat(invoice.getFinalizedAt().getOffset()).isEqualTo(ZoneOffset.ofHours(9));
    assertThat(reloadedInvoice.getFinalizedAt()).isNotNull();
    assertThat(reloadedInvoice.getSupplyAmount()).isEqualTo(1640);
    assertThat(lines(organizationId, invoice))
        .singleElement()
        .satisfies(
            invoiceLine -> {
              assertThat(invoiceLine.getBillableMetricCode()).isEqualTo("token-usage");
              assertThat(invoiceLine.getTargetProperty()).isEqualTo("token");
              assertThat(invoiceLine.getDimensionValues()).isEqualTo("{}");
              assertThat(invoiceLine.getQuantity()).isEqualByComparingTo("3299");
              assertThat(invoiceLine.getUnitPrice()).isEqualByComparingTo("0.5");
              assertThat(invoiceLine.getAmount()).isEqualTo(1640);
            });
  }

  @Test
  void 단가는_세금_별도이고_세액은_공급가액의_10퍼센트이며_합계는_둘의_합이다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID acme = insertCustomer(organizationId, "아크메");
    insertEvent(organizationId, "tx-1", acme, "token", 3299, "2026-08-10T12:00:00+09:00");

    Invoice invoice = invoiceFinalizationService.finalize(organizationId, acme, AUGUST);

    assertThat(invoice.getSupplyAmount()).isEqualTo(1640);
    assertThat(invoice.getTaxAmount()).isEqualTo(164);
    assertThat(invoice.getTotalAmount()).isEqualTo(1804);
  }

  @Test
  void 확정_뒤에_새_이벤트가_들어오고_단가가_바뀌어도_금액과_라인이_변하지_않는다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID acme = insertCustomer(organizationId, "아크메");
    insertEvent(organizationId, "tx-1", acme, "token", 3280, "2026-08-10T12:00:00+09:00");
    Invoice invoice = invoiceFinalizationService.finalize(organizationId, acme, AUGUST);
    flushAndClear();

    insertEvent(organizationId, "tx-2", acme, "token", 1000, "2026-08-20T12:00:00+09:00");
    jdbcTemplate.update(
        "UPDATE price_rate SET unit_price = 2 WHERE organization_id = ?", organizationId);

    assertThat(draftAmount(organizationId)).isEqualTo(8560);
    flushAndClear();
    Invoice reloadedInvoice = invoiceRepository.findById(invoice.getId()).orElseThrow();
    assertThat(reloadedInvoice.getSupplyAmount()).isEqualTo(1640);
    assertThat(reloadedInvoice.getTaxAmount()).isEqualTo(164);
    assertThat(reloadedInvoice.getTotalAmount()).isEqualTo(1804);
    assertThat(lines(organizationId, invoice))
        .singleElement()
        .satisfies(
            invoiceLine -> {
              assertThat(invoiceLine.getQuantity()).isEqualByComparingTo("3280");
              assertThat(invoiceLine.getUnitPrice()).isEqualByComparingTo("0.5");
              assertThat(invoiceLine.getAmount()).isEqualTo(1640);
            });
  }

  @Test
  void 단가가_없는_미터는_라인에서_빠진_채_확정한다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    insertBillableMetric(organizationId, "api-calls", "count");
    UUID acme = insertCustomer(organizationId, "아크메");
    insertEvent(organizationId, "tx-1", acme, "token", 3280, "2026-08-10T12:00:00+09:00");
    insertEvent(organizationId, "tx-2", acme, "count", 3, "2026-08-11T12:00:00+09:00");

    Invoice invoice = invoiceFinalizationService.finalize(organizationId, acme, AUGUST);
    flushAndClear();

    assertThat(invoice.getSupplyAmount()).isEqualTo(1640);
    assertThat(lines(organizationId, invoice))
        .extracting(InvoiceLine::getBillableMetricCode)
        .containsExactly("token-usage");
  }

  // --- 헬퍼 ---

  private long draftAmount(UUID organizationId) {
    return draftInvoiceService.preview(organizationId, AUGUST).totalAmount();
  }

  private List<InvoiceLine> lines(UUID organizationId, Invoice invoice) {
    return invoiceLineRepository.findByOrganizationIdAndInvoiceIdOrderByBillableMetricCodeAsc(
        organizationId, invoice.getId());
  }

  private void flushAndClear() {
    entityManager.flush();
    entityManager.clear();
  }

  // 토큰 사용량 미터와 그 기본 단가를 가진 도입사를 만든다.
  private UUID organizationWithTokenBillableMetric() {
    UUID organizationId = insertOrganization();
    insertBillableMetric(organizationId, "token-usage", "token");
    jdbcTemplate.update(
        "INSERT INTO price_policy (organization_id, billable_metric_code) VALUES (?, 'token-usage')",
        organizationId);
    jdbcTemplate.update(
        """
        INSERT INTO price_rate (organization_id, billable_metric_code, dimension_values, unit_price)
        VALUES (?, 'token-usage', '{}', 0.5)
        """,
        organizationId);
    return organizationId;
  }

  private void insertBillableMetric(UUID organizationId, String code, String targetProperty) {
    jdbcTemplate.update(
        """
        INSERT INTO billable_metric
          (organization_id, code, name, event_type, aggregation, target_property)
        VALUES (?, ?, ?, 'chat_completion', 'sum', ?)
        """,
        organizationId,
        code,
        code + " 미터",
        targetProperty);
  }

  private UUID insertOrganization() {
    return jdbcTemplate.queryForObject(
        "INSERT INTO organization (name) VALUES ('도입사') RETURNING id", UUID.class);
  }

  private UUID insertCustomer(UUID organizationId, String name) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO customer (organization_id, name) VALUES (?, ?) RETURNING id",
        UUID.class,
        organizationId,
        name);
  }

  private void insertEvent(
      UUID organizationId,
      String transactionId,
      UUID customerId,
      String property,
      int value,
      String occurredAt) {
    jdbcTemplate.update(
        """
        INSERT INTO event
          (organization_id, transaction_id, customer_id, type, properties, occurred_at)
        VALUES (?, ?, ?, 'chat_completion', ?::jsonb, ?)
        """,
        organizationId,
        transactionId,
        customerId,
        "{\"%s\":%d}".formatted(property, value),
        OffsetDateTime.parse(occurredAt));
  }
}
