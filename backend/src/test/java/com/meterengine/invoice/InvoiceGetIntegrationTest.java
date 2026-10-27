package com.meterengine.invoice;

import static org.assertj.core.api.Assertions.assertThat;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class InvoiceGetIntegrationTest {

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private InvoiceLineRepository invoiceLineRepository;
  @Autowired private JsonMapper jsonMapper;

  private MockMvcTester mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcTester.from(
            webApplicationContext, builder -> builder.apply(springSecurity()).build());
  }

  @Test
  void 단건은_인보이스_목록의_같은_원소와_같은_JSON이다() throws Exception {
    UUID organizationId = insertOrganization();
    UUID acme = insertCustomer(organizationId, "아크메");
    UUID zeta = insertCustomer(organizationId, "제타상사");
    UUID acmeInvoiceId = insertInvoice(organizationId, acme, 12000);
    insertInvoiceLine(
        organizationId, acmeInvoiceId, "token-usage", "token", "{\"model\": \"b\"}", 7000);
    insertInvoiceLine(
        organizationId, acmeInvoiceId, "token-usage", "token", "{\"model\": \"a\"}", 1000);
    insertInvoiceLine(organizationId, acmeInvoiceId, "api-call", null, "{}", 4000);
    UUID zetaInvoiceId = insertInvoice(organizationId, zeta, 5000);
    insertInvoiceLine(organizationId, zetaInvoiceId, "api-call", null, "{}", 5000);

    JsonNode listed =
        body(withOrganization(mvc.get().uri("/v1/invoices"), organizationId).exchange())
            .get("invoices");
    MvcTestResult acmeInvoice = getInvoice(organizationId, acmeInvoiceId);
    MvcTestResult zetaInvoice = getInvoice(organizationId, zetaInvoiceId);

    assertThat(acmeInvoice).hasStatusOk();
    assertThat(zetaInvoice).hasStatusOk();
    assertThat(listed.get(0).get("id").asString()).isEqualTo(acmeInvoiceId.toString());
    assertThat(body(acmeInvoice)).isEqualTo(listed.get(0));
    assertThat(body(zetaInvoice)).isEqualTo(listed.get(1));
    assertThat(body(acmeInvoice).get("lines")).hasSize(3);
  }

  @Test
  void 없는_인보이스나_다른_도입사의_인보이스는_404다() {
    UUID organizationId = insertOrganization();
    UUID otherOrganizationId = insertOrganization();
    UUID otherInvoiceId =
        insertInvoice(otherOrganizationId, insertCustomer(otherOrganizationId, "베타"), 9000);

    assertError(getInvoice(organizationId, UUID.randomUUID()), 404, ErrorCode.INVOICE_NOT_FOUND);
    assertError(getInvoice(organizationId, otherInvoiceId), 404, ErrorCode.INVOICE_NOT_FOUND);
  }

  @Test
  void 인증이_없으면_401이고_id가_UUID가_아니면_400이다() {
    assertError(
        mvc.get().uri("/v1/invoices/" + UUID.randomUUID()).exchange(),
        401,
        ErrorCode.AUTHENTICATION_REQUIRED);
    assertError(
        withOrganization(mvc.get().uri("/v1/invoices/not-a-uuid"), UUID.randomUUID()).exchange(),
        400,
        ErrorCode.VALIDATION_ERROR);
  }

  private MvcTestResult getInvoice(UUID organizationId, UUID invoiceId) {
    return withOrganization(mvc.get().uri("/v1/invoices/{id}", invoiceId), organizationId)
        .exchange();
  }

  private MockMvcTester.MockMvcRequestBuilder withOrganization(
      MockMvcTester.MockMvcRequestBuilder request, UUID organizationId) {
    return request.header(
        "Authorization", TestCredentials.issueMemberSession(jdbcTemplate, organizationId));
  }

  private JsonNode body(MvcTestResult result) throws Exception {
    return jsonMapper.readTree(result.getResponse().getContentAsString());
  }

  private void assertError(MvcTestResult result, int status, ErrorCode errorCode) {
    assertThat(result)
        .hasStatus(status)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(errorCode.getCode());
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
      String dimensionValues,
      long amount) {
    invoiceLineRepository.save(
        new InvoiceLine(
            UUID.randomUUID(),
            organizationId,
            invoiceId,
            billableMetricCode,
            targetProperty,
            dimensionValues,
            new BigDecimal(amount / 10),
            new BigDecimal("10"),
            amount));
  }

  private UUID insertInvoice(UUID organizationId, UUID customerId, long supplyAmount) {
    Invoice invoice =
        new Invoice(
            UUID.randomUUID(),
            organizationId,
            customerId,
            jdbcTemplate.queryForObject(
                "SELECT name FROM customer WHERE id = ?", String.class, customerId),
            "2026-08",
            supplyAmount,
            supplyAmount / 10,
            OffsetDateTime.parse("2026-09-01T05:00:00Z"));
    return invoiceRepository.save(invoice).getId();
  }
}
