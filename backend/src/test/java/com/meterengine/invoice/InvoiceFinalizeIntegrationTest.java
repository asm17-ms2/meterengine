package com.meterengine.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.meterengine.TestcontainersConfiguration;
import com.meterengine.invoice.dto.DraftInvoiceResponse;
import com.meterengine.invoice.dto.DraftInvoiceResponse.DraftInvoiceLine;
import com.meterengine.invoice.dto.InvoiceResponse.InvoiceLineResponse;
import com.meterengine.invoice.entity.Invoice;
import com.meterengine.invoice.entity.InvoiceLine;
import com.meterengine.invoice.repository.InvoiceLineRepository;
import com.meterengine.invoice.repository.InvoiceRepository;
import com.meterengine.invoice.service.DraftInvoiceService;
import com.meterengine.invoice.service.InvoiceFinalizationService;
import com.meterengine.invoice.service.InvoiceService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class InvoiceFinalizeIntegrationTest {

  private static final YearMonth AUGUST = YearMonth.of(2026, 8);

  @Autowired private InvoiceFinalizationService invoiceFinalizationService;
  @Autowired private DraftInvoiceService draftInvoiceService;
  @Autowired private InvoiceService invoiceService;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private InvoiceLineRepository invoiceLineRepository;
  @Autowired private EntityManager entityManager;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private WebApplicationContext webApplicationContext;

  private MockMvcTester mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcTester.from(webApplicationContext);
  }

  @Test
  void 확정하면_고객_이름과_달과_KST_확정_시각과_라인의_수량과_단가가_저장된다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID acme = insertCustomer(organizationId, "아크메");
    insertEvent(organizationId, "tx-1", acme, "token", 500, "2026-08-10T12:00:00+09:00");
    insertEvent(organizationId, "tx-2", acme, "token", 2799, "2026-08-31T23:59:59+09:00");
    insertEvent(organizationId, "tx-3", acme, "token", 1000, "2026-09-01T00:00:00+09:00");

    Invoice invoice = invoiceFinalizationService.finalize(organizationId, AUGUST).getFirst();
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

    Invoice invoice = invoiceFinalizationService.finalize(organizationId, AUGUST).getFirst();

    assertThat(invoice.getSupplyAmount()).isEqualTo(1640);
    assertThat(invoice.getTaxAmount()).isEqualTo(164);
    assertThat(invoice.getTotalAmount()).isEqualTo(1804);
  }

  @Test
  void 확정_금액은_확정_직전의_청구_예정액과_같다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID acme = insertCustomer(organizationId, "아크메");
    insertEvent(organizationId, "tx-1", acme, "token", 3299, "2026-08-10T12:00:00+09:00");
    long draftAmount = draft(organizationId).totalAmount();

    Invoice invoice = invoiceFinalizationService.finalize(organizationId, AUGUST).getFirst();

    assertThat(invoice.getSupplyAmount()).isEqualTo(draftAmount).isEqualTo(1640);
  }

  @Test
  void 한_미터에_조합이_여럿이면_인보이스_목록의_라인이_확정_직전의_청구_예정액_라인과_같다() {
    UUID organizationId = insertOrganization();
    jdbcTemplate.update(
        """
        INSERT INTO billable_metric
          (organization_id, code, name, event_type, aggregation, target_property,
           dimension_properties)
        VALUES (?, 'token-usage', '토큰 사용량', 'chat_completion', 'sum', 'token', '{model}')
        """,
        organizationId);
    insertPrice(organizationId, "token-usage", "{\"model\": \"a\"}", "0.5");
    insertPrice(organizationId, "token-usage", "{\"model\": \"b\"}", "2");
    UUID acme = insertCustomer(organizationId, "아크메");
    insertEvent(organizationId, "tx-1", acme, "b", 700);
    insertEvent(organizationId, "tx-2", acme, "a", 500);
    insertEvent(organizationId, "tx-3", acme, "b", 300);
    List<DraftInvoiceLine> draftInvoiceLines = draft(organizationId).customers().getFirst().lines();

    invoiceFinalizationService.finalize(organizationId, AUGUST);
    flushAndClear();

    List<InvoiceLineResponse> invoiceLines =
        invoiceService.list(organizationId, acme, AUGUST).invoices().getFirst().lines();
    assertThat(invoiceLines)
        .extracting(InvoiceLineResponse::dimensionValues, InvoiceLineResponse::amount)
        .containsExactly(tuple(Map.of("model", "a"), 250L), tuple(Map.of("model", "b"), 2000L));
    assertThat(invoiceLines)
        .usingRecursiveComparison()
        .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .isEqualTo(draftInvoiceLines);
  }

  @Test
  void 확정_뒤에_새_이벤트가_들어오고_단가가_바뀌어도_금액과_라인이_변하지_않는다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID acme = insertCustomer(organizationId, "아크메");
    insertEvent(organizationId, "tx-1", acme, "token", 3280, "2026-08-10T12:00:00+09:00");
    Invoice invoice = invoiceFinalizationService.finalize(organizationId, AUGUST).getFirst();
    flushAndClear();

    insertEvent(organizationId, "tx-2", acme, "token", 1000, "2026-08-20T12:00:00+09:00");
    jdbcTemplate.update(
        "UPDATE price_rate SET unit_price = 2 WHERE organization_id = ?", organizationId);

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
  void 그_달_사용량이_없는_고객은_확정하지_않는다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID acme = insertCustomer(organizationId, "아크메");
    UUID beta = insertCustomer(organizationId, "베타");
    insertCustomer(organizationId, "제타상사");
    insertEvent(organizationId, "tx-1", acme, "token", 500, "2026-08-10T12:00:00+09:00");
    insertEvent(organizationId, "tx-2", beta, "token", 500, "2026-07-31T23:59:59+09:00");
    insertEvent(organizationId, "tx-3", beta, "token", 500, "2026-09-01T00:00:00+09:00");

    assertThat(invoiceFinalizationService.finalize(organizationId, AUGUST))
        .extracting(Invoice::getCustomerId)
        .containsExactly(acme);
  }

  @Test
  void 사용량이_없어_건너뛴_고객은_나중에_사용량이_생기면_다시_불러_확정된다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID acme = insertCustomer(organizationId, "아크메");
    assertThat(invoiceFinalizationService.finalize(organizationId, AUGUST)).isEmpty();

    insertEvent(organizationId, "tx-1", acme, "token", 1000, "2026-08-10T12:00:00+09:00");

    assertThat(invoiceFinalizationService.finalize(organizationId, AUGUST))
        .extracting(Invoice::getSupplyAmount)
        .containsExactly(500L);
  }

  @Test
  void 단가가_없는_미터는_라인에서_빠진_채_확정한다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    insertBillableMetric(organizationId, "api-calls", "count");
    UUID acme = insertCustomer(organizationId, "아크메");
    insertEvent(organizationId, "tx-1", acme, "token", 3280, "2026-08-10T12:00:00+09:00");
    insertEvent(organizationId, "tx-2", acme, "count", 3, "2026-08-11T12:00:00+09:00");

    Invoice invoice = invoiceFinalizationService.finalize(organizationId, AUGUST).getFirst();
    flushAndClear();

    assertThat(invoice.getSupplyAmount()).isEqualTo(1640);
    assertThat(lines(organizationId, invoice))
        .extracting(InvoiceLine::getBillableMetricCode)
        .containsExactly("token-usage");
  }

  @Test
  void 단가가_있는_미터의_사용량이_없으면_이벤트가_있어도_확정하지_않고_단가를_넣으면_확정한다() {
    UUID organizationId = insertOrganization();
    insertBillableMetric(organizationId, "api-calls", "count");
    UUID acme = insertCustomer(organizationId, "아크메");
    insertEvent(organizationId, "tx-1", acme, "count", 3, "2026-08-11T12:00:00+09:00");
    assertThat(invoiceFinalizationService.finalize(organizationId, AUGUST)).isEmpty();

    insertPrice(organizationId, "api-calls", "100");

    assertThat(invoiceFinalizationService.finalize(organizationId, AUGUST))
        .extracting(Invoice::getSupplyAmount)
        .containsExactly(300L);
  }

  @Test
  void 수량이_0인_라인은_저장하지_않고_금액이_0원이어도_수량이_있으면_저장한다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    insertBillableMetric(organizationId, "api-calls", "count");
    insertPrice(organizationId, "api-calls", "100");
    insertBillableMetric(organizationId, "cache-usage", "cache");
    insertPrice(organizationId, "cache-usage", "0.5");
    UUID acme = insertCustomer(organizationId, "아크메");
    insertEvent(organizationId, "tx-1", acme, "token", 3280, "2026-08-10T12:00:00+09:00");
    insertEvent(organizationId, "tx-2", acme, "cache", 19, "2026-08-10T12:00:00+09:00");

    Invoice invoice = invoiceFinalizationService.finalize(organizationId, AUGUST).getFirst();
    flushAndClear();

    assertThat(invoice.getSupplyAmount()).isEqualTo(1640);
    assertThat(lines(organizationId, invoice))
        .extracting(InvoiceLine::getBillableMetricCode, InvoiceLine::getAmount)
        .containsExactly(tuple("cache-usage", 0L), tuple("token-usage", 1640L));
  }

  @Test
  void 사용량이_있어도_청구_예정액이_0원인_고객은_확정하지_않는다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID acme = insertCustomer(organizationId, "아크메");
    insertEvent(organizationId, "tx-1", acme, "token", 19, "2026-08-10T12:00:00+09:00");

    assertThat(invoiceFinalizationService.finalize(organizationId, AUGUST)).isEmpty();
  }

  @Test
  void 다시_부르면_새로_확정하지_않는다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID acme = insertCustomer(organizationId, "아크메");
    insertEvent(organizationId, "tx-1", acme, "token", 3280, "2026-08-10T12:00:00+09:00");

    assertThat(finalizeInvoices(organizationId, "?month=2026-08"))
        .hasStatusOk()
        .bodyJson()
        .isStrictlyEqualTo("{\"month\": \"2026-08\", \"finalized_count\": 1}");
    insertEvent(organizationId, "tx-2", acme, "token", 1000, "2026-08-20T12:00:00+09:00");
    assertThat(finalizeInvoices(organizationId, "?month=2026-08"))
        .bodyJson()
        .extractingPath("$.finalized_count")
        .isEqualTo(0);

    assertThat(
            mvc.get()
                .uri("/v1/invoices?month=2026-08")
                .header("X-Organization-Id", organizationId.toString())
                .exchange())
        .bodyJson()
        .extractingPath("$.invoices[*].total_amount")
        .asArray()
        .containsExactly(1804);
  }

  @Test
  void 같은_고객도_달이_다르면_따로_확정한다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID acme = insertCustomer(organizationId, "아크메");
    insertEvent(organizationId, "tx-1", acme, "token", 3280, "2026-08-10T12:00:00+09:00");
    insertEvent(organizationId, "tx-2", acme, "token", 1000, "2026-09-10T12:00:00+09:00");

    invoiceFinalizationService.finalize(organizationId, AUGUST);
    Invoice september =
        invoiceFinalizationService.finalize(organizationId, AUGUST.plusMonths(1)).getFirst();

    assertThat(september.getPeriod()).isEqualTo("2026-09");
    assertThat(september.getSupplyAmount()).isEqualTo(500);
  }

  @Test
  void 다른_고객과_다른_도입사의_사용량은_섞이지_않는다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID acme = insertCustomer(organizationId, "아크메");
    UUID beta = insertCustomer(organizationId, "베타");
    UUID otherOrganizationId = organizationWithTokenBillableMetric();
    UUID otherCustomer = insertCustomer(otherOrganizationId, "아크메");
    insertEvent(organizationId, "tx-1", acme, "token", 3280, "2026-08-10T12:00:00+09:00");
    insertEvent(organizationId, "tx-2", beta, "token", 1000, "2026-08-10T12:00:00+09:00");
    insertEvent(
        otherOrganizationId, "tx-1", otherCustomer, "token", 9000, "2026-08-10T12:00:00+09:00");

    assertThat(invoiceFinalizationService.finalize(organizationId, AUGUST))
        .extracting(Invoice::getCustomerId, Invoice::getSupplyAmount)
        .containsExactlyInAnyOrder(tuple(acme, 1640L), tuple(beta, 500L));
    assertThat(invoiceRepository.findNewestFirst(otherOrganizationId, null, null)).isEmpty();
  }

  @Test
  void month가_없으면_400_validation_error다() {
    UUID organizationId = organizationWithTokenBillableMetric();

    assertThat(finalizeInvoices(organizationId, ""))
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("validation_error");
  }

  @Test
  void month_형식이_틀리거나_도입사_헤더가_없으면_400이다() {
    UUID organizationId = organizationWithTokenBillableMetric();

    assertThat(finalizeInvoices(organizationId, "?month=2026-8")).hasStatus(400);
    assertThat(mvc.post().uri("/v1/invoices?month=2026-08").exchange()).hasStatus(400);
  }

  // --- 헬퍼 ---

  private MvcTestResult finalizeInvoices(UUID organizationId, String query) {
    return mvc.post()
        .uri("/v1/invoices" + query)
        .header("X-Organization-Id", organizationId.toString())
        .exchange();
  }

  private DraftInvoiceResponse draft(UUID organizationId) {
    return draftInvoiceService.preview(organizationId, AUGUST);
  }

  private List<InvoiceLine> lines(UUID organizationId, Invoice invoice) {
    return invoiceLineRepository
        .findByOrganizationIdAndInvoiceIdOrderByBillableMetricCodeAscDimensionValuesAsc(
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
    insertPrice(organizationId, "token-usage", "0.5");
    return organizationId;
  }

  private void insertPrice(UUID organizationId, String billableMetricCode, String unitPrice) {
    insertPrice(organizationId, billableMetricCode, "{}", unitPrice);
  }

  private void insertPrice(
      UUID organizationId, String billableMetricCode, String dimensionValues, String unitPrice) {
    jdbcTemplate.update(
        """
        INSERT INTO price_rate (organization_id, billable_metric_code, dimension_values, unit_price)
        VALUES (?, ?, ?::jsonb, ?)
        """,
        organizationId,
        billableMetricCode,
        dimensionValues,
        new BigDecimal(unitPrice));
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
      UUID organizationId, String transactionId, UUID customerId, String model, int token) {
    jdbcTemplate.update(
        """
        INSERT INTO event
          (organization_id, transaction_id, customer_id, type, properties, occurred_at)
        VALUES (?, ?, ?, 'chat_completion', ?::jsonb, ?)
        """,
        organizationId,
        transactionId,
        customerId,
        "{\"model\":\"%s\",\"token\":%d}".formatted(model, token),
        OffsetDateTime.parse("2026-08-10T12:00:00+09:00"));
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
