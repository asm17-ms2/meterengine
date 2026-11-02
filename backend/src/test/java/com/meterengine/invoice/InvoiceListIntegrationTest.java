package com.meterengine.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.invoice.entity.Invoice;
import com.meterengine.invoice.entity.InvoiceLine;
import com.meterengine.invoice.repository.InvoiceLineRepository;
import com.meterengine.invoice.repository.InvoiceRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class InvoiceListIntegrationTest {

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private InvoiceLineRepository invoiceLineRepository;

  private final Map<UUID, String> authorizationsByOrganizationId = new HashMap<>();

  private MockMvcTester mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcTester.from(
            webApplicationContext, builder -> builder.apply(springSecurity()).build());
  }

  @Test
  void 응답에_고객과_달과_KST_확정_시각과_저장된_금액_구성과_라인이_나온다() {
    UUID organizationId = insertOrganization();
    UUID acme = insertCustomer(organizationId, "아크메");
    UUID invoiceId = insertInvoice(organizationId, acme, "2026-08", 12000);
    insertInvoiceLine(organizationId, invoiceId, "token-usage", "token", "24000", "0.25", 6000);
    insertInvoiceLine(organizationId, invoiceId, "api-call", null, "600", "10", 6000);

    assertThat(list(organizationId, ""))
        .hasStatusOk()
        .bodyJson()
        .isStrictlyEqualTo(
            """
            {"invoices": [{
              "id": "%s", "invoice_number": "%s", "customer_id": "%s", "customer_name": "아크메", "month": "2026-08",
              "cutoff_at": "2026-09-01T13:00:00+09:00", "finalized_at": "2026-09-01T14:00:00+09:00",
              "supply_amount": 12000, "tax_amount": 1200, "total_amount": 13200,
              "lines": [
                {"billable_metric_code": "api-call", "billable_metric_name": null, "target_property": null,
                 "dimension_values": {}, "quantity": 600, "unit_price": 10, "amount": 6000, "line_type": "per_unit", "tier_index": 0,
                 "quantity_numerator": 600, "quantity_denominator": 1},
                {"billable_metric_code": "token-usage", "billable_metric_name": null, "target_property": "token",
                 "dimension_values": {}, "quantity": 24000, "unit_price": 0.25, "amount": 6000, "line_type": "per_unit", "tier_index": 0,
                 "quantity_numerator": 24000, "quantity_denominator": 1}
              ]
            }]}
            """
                .formatted(
                    invoiceId,
                    "ME-202608-"
                        + String.format(
                            java.util.Locale.ROOT,
                            "%08d",
                            jdbcTemplate.queryForObject(
                                "SELECT display_number FROM invoice WHERE id=?",
                                Long.class,
                                invoiceId)),
                    acme));
  }

  @Test
  void 같은_미터의_라인은_조합을_싣고_dimension_values_오름차순이다() {
    UUID organizationId = insertOrganization();
    UUID acme = insertCustomer(organizationId, "아크메");
    UUID invoiceId = insertInvoice(organizationId, acme, "2026-08", 9000);
    insertInvoiceLine(
        organizationId, invoiceId, "token-usage", "token", "{\"model\": \"b\"}", "700", "10", 7000);
    insertInvoiceLine(
        organizationId, invoiceId, "token-usage", "token", "{\"model\": \"a\"}", "500", "2", 1000);
    insertInvoiceLine(organizationId, invoiceId, "api-call", null, "100", "10", 1000);
    disableIndexScans();

    assertThat(list(organizationId, ""))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.invoices[0].lines[*].dimension_values")
        .asArray()
        .containsExactly(Map.of(), Map.of("model", "a"), Map.of("model", "b"));
  }

  @Test
  void 라인은_자기_인보이스에만_실리고_라인이_없으면_빈_배열이다() {
    UUID organizationId = insertOrganization();
    UUID acme = insertCustomer(organizationId, "아크메");
    UUID zeta = insertCustomer(organizationId, "제타상사");
    UUID beta = insertCustomer(organizationId, "베타");
    UUID acmeAugust = insertInvoice(organizationId, acme, "2026-08", 12000);
    UUID zetaAugust = insertInvoice(organizationId, zeta, "2026-08", 5000);
    insertInvoice(organizationId, beta, "2026-08", 0);
    insertInvoiceLine(organizationId, acmeAugust, "api-call", null, "1200", "10", 12000);
    insertInvoiceLine(organizationId, zetaAugust, "api-call", null, "500", "10", 5000);

    assertThat(list(organizationId, ""))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.invoices[*].lines[*].amount")
        .asArray()
        .containsExactly(12000, 5000);
    assertThat(list(organizationId, "?customer_id=" + beta))
        .bodyJson()
        .extractingPath("$.invoices[0].lines")
        .asArray()
        .isEmpty();
  }

  @Test
  void 달_내림차순_고객_이름_오름차순이고_month와_customer_id로_거른다() {
    UUID organizationId = insertOrganization();
    UUID zeta = insertCustomer(organizationId, "제타상사");
    UUID acme = insertCustomer(organizationId, "아크메");
    UUID acmeJuly = insertInvoice(organizationId, acme, "2026-07", 10000);
    UUID zetaAugust = insertInvoice(organizationId, zeta, "2026-08", 5000);
    UUID acmeAugust = insertInvoice(organizationId, acme, "2026-08", 12000);

    assertIds(list(organizationId, ""), acmeAugust, zetaAugust, acmeJuly);
    assertIds(list(organizationId, "?month=2026-08"), acmeAugust, zetaAugust);
    assertIds(list(organizationId, "?customer_id=" + acme), acmeAugust, acmeJuly);
    assertIds(list(organizationId, "?customer_id=%s&month=2026-07".formatted(acme)), acmeJuly);
  }

  @Test
  void 확정_뒤에_고객_이름을_고쳐도_확정할_때의_이름이_나온다() {
    UUID organizationId = insertOrganization();
    UUID acme = insertCustomer(organizationId, "아크메");
    insertInvoice(organizationId, acme, "2026-08", 12000);

    assertThat(
            mvc.put()
                .uri("/v1/customers/" + acme)
                .header(AUTHORIZATION, authorization(organizationId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"아크메코리아\"}")
                .exchange())
        .hasStatusOk();

    assertThat(list(organizationId, ""))
        .bodyJson()
        .extractingPath("$.invoices[0].customer_name")
        .isEqualTo("아크메");
  }

  @Test
  void 다른_도입사의_인보이스는_나오지_않고_그_고객으로_거르면_404다() {
    UUID organizationId = insertOrganization();
    UUID otherOrganizationId = insertOrganization();
    UUID otherCustomerId = insertCustomer(otherOrganizationId, "베타");
    insertInvoice(otherOrganizationId, otherCustomerId, "2026-08", 9000);

    assertIds(list(organizationId, ""));
    assertThat(list(organizationId, "?customer_id=" + otherCustomerId))
        .hasStatus(404)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(ErrorCode.CUSTOMER_NOT_FOUND.getCode());
  }

  @Test
  void customer_id나_month의_형식이_틀리면_400이고_인증이_없으면_401이다() {
    assertThat(mvc.get().uri("/v1/invoices").exchange()).hasStatus(401);
    assertThat(list(UUID.randomUUID(), "?customer_id=not-a-uuid")).hasStatus(400);
    assertThat(list(UUID.randomUUID(), "?month=2026-8")).hasStatus(400);
  }

  private String authorization(UUID organizationId) {
    return authorizationsByOrganizationId.computeIfAbsent(
        organizationId, id -> TestCredentials.issueApiKey(jdbcTemplate, id));
  }

  private MvcTestResult list(UUID organizationId, String query) {
    return mvc.get()
        .uri("/v1/invoices" + query)
        .header(AUTHORIZATION, authorization(organizationId))
        .exchange();
  }

  private void assertIds(MvcTestResult result, UUID... invoiceIds) {
    assertThat(result)
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.invoices[*].id")
        .asArray()
        .containsExactly(Arrays.stream(invoiceIds).map(UUID::toString).toArray());
  }

  // 이 트랜잭션의 조회가 인덱스를 쓰지 않게 한다.
  private void disableIndexScans() {
    jdbcTemplate.execute("SET LOCAL enable_indexscan = off");
    jdbcTemplate.execute("SET LOCAL enable_indexonlyscan = off");
    jdbcTemplate.execute("SET LOCAL enable_bitmapscan = off");
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

  private void insertInvoiceLine(
      UUID organizationId,
      UUID invoiceId,
      String billableMetricCode,
      String targetProperty,
      String quantity,
      String unitPrice,
      long amount) {
    insertInvoiceLine(
        organizationId,
        invoiceId,
        billableMetricCode,
        targetProperty,
        "{}",
        quantity,
        unitPrice,
        amount);
  }

  private void insertInvoiceLine(
      UUID organizationId,
      UUID invoiceId,
      String billableMetricCode,
      String targetProperty,
      String dimensionValues,
      String quantity,
      String unitPrice,
      long amount) {
    invoiceLineRepository.save(
        new InvoiceLine(
            UUID.randomUUID(),
            organizationId,
            invoiceId,
            billableMetricCode,
            targetProperty,
            dimensionValues,
            new BigDecimal(quantity),
            new BigDecimal(unitPrice),
            amount));
  }

  private UUID insertInvoice(
      UUID organizationId, UUID customerId, String period, long supplyAmount) {
    Invoice invoice =
        new Invoice(
            UUID.randomUUID(),
            organizationId,
            customerId,
            jdbcTemplate.queryForObject(
                "SELECT name FROM customer WHERE id = ?", String.class, customerId),
            period,
            supplyAmount,
            supplyAmount / 10,
            OffsetDateTime.parse("2026-09-01T04:00:00Z"),
            OffsetDateTime.parse("2026-09-01T05:00:00Z"));
    return invoiceRepository.save(invoice).getId();
  }
}
