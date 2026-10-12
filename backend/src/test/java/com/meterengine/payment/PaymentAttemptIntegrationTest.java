package com.meterengine.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.TestcontainersConfiguration;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.invoice.entity.Invoice;
import com.meterengine.invoice.repository.InvoiceRepository;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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
class PaymentAttemptIntegrationTest {

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private InvoiceRepository invoiceRepository;

  private MockMvcTester mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcTester.from(webApplicationContext);
  }

  @Test
  void 결제를_요청하면_인보이스_합계로_승인된_시도가_남고_201로_돌려준다() {
    UUID organizationId = insertOrganization();
    UUID customerId = insertCustomer(organizationId);
    UUID invoiceId = insertInvoice(organizationId, customerId, 12000);

    MvcTestResult result = createPaymentAttempt(organizationId, invoiceId.toString());

    assertThat(result).hasStatus(201);
    Map<String, Object> attempt =
        jdbcTemplate.queryForMap(
            "SELECT * FROM payment_attempt WHERE organization_id = ? AND invoice_id = ?",
            organizationId,
            invoiceId);
    UUID attemptId = (UUID) attempt.get("id");
    assertThat(attempt)
        .containsEntry("customer_id", customerId)
        .containsEntry("amount", 13200L)
        .containsEntry("order_name", "2026-08 사용료")
        .containsEntry("status", "done")
        .containsEntry("payment_key", "mock_" + attemptId);
    assertThat(result)
        .bodyJson()
        .isStrictlyEqualTo(
            """
            {
              "id": "%s", "invoice_id": "%s", "status": "done", "amount": 13200,
              "payment_key": "mock_%s",
              "requested_at": "%s", "completed_at": "%s"
            }
            """
                .formatted(
                    attemptId,
                    invoiceId,
                    attemptId,
                    kst(attempt.get("requested_at")),
                    kst(attempt.get("completed_at"))));
  }

  @Test
  void 결제한_인보이스에_다시_요청하면_409이고_시도는_하나만_남는다() {
    UUID organizationId = insertOrganization();
    UUID invoiceId = insertInvoice(organizationId, insertCustomer(organizationId), 12000);
    assertThat(createPaymentAttempt(organizationId, invoiceId.toString())).hasStatus(201);

    assertError(
        createPaymentAttempt(organizationId, invoiceId.toString()),
        409,
        ErrorCode.INVOICE_ALREADY_PAID);
    assertThat(countAttempts(invoiceId)).isEqualTo(1);
  }

  @Test
  void 합계가_0원인_인보이스는_409이고_시도를_남기지_않는다() {
    UUID organizationId = insertOrganization();
    UUID invoiceId = insertInvoice(organizationId, insertCustomer(organizationId), 0);

    assertError(
        createPaymentAttempt(organizationId, invoiceId.toString()),
        409,
        ErrorCode.INVOICE_AMOUNT_ZERO);
    assertThat(countAttempts(invoiceId)).isZero();
  }

  @Test
  void 없는_인보이스나_다른_도입사의_인보이스는_404다() {
    UUID organizationId = insertOrganization();
    UUID otherOrganizationId = insertOrganization();
    UUID otherInvoiceId =
        insertInvoice(otherOrganizationId, insertCustomer(otherOrganizationId), 12000);

    assertError(
        createPaymentAttempt(organizationId, UUID.randomUUID().toString()),
        404,
        ErrorCode.INVOICE_NOT_FOUND);
    assertError(
        createPaymentAttempt(organizationId, otherInvoiceId.toString()),
        404,
        ErrorCode.INVOICE_NOT_FOUND);
    assertThat(countAttempts(otherInvoiceId)).isZero();
  }

  @Test
  void 도입사_헤더가_없거나_인보이스_id가_UUID가_아니면_400이다() {
    assertThat(mvc.post().uri("/v1/invoices/" + UUID.randomUUID() + "/payment-attempts").exchange())
        .hasStatus(400);
    assertError(
        createPaymentAttempt(UUID.randomUUID(), "not-a-uuid"), 400, ErrorCode.VALIDATION_ERROR);
  }

  private MvcTestResult createPaymentAttempt(UUID organizationId, String invoiceId) {
    return mvc.post()
        .uri("/v1/invoices/" + invoiceId + "/payment-attempts")
        .header("X-Organization-Id", organizationId.toString())
        .exchange();
  }

  private void assertError(MvcTestResult result, int status, ErrorCode errorCode) {
    assertThat(result)
        .hasStatus(status)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(errorCode.getCode());
  }

  private int countAttempts(UUID invoiceId) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM payment_attempt WHERE invoice_id = ?", Integer.class, invoiceId);
  }

  private static String kst(Object timestamp) {
    return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
        ((Timestamp) timestamp).toInstant().atOffset(ZoneOffset.ofHours(9)));
  }

  private UUID insertOrganization() {
    return jdbcTemplate.queryForObject(
        "INSERT INTO organization (name) VALUES ('도입사') RETURNING id", UUID.class);
  }

  private UUID insertCustomer(UUID organizationId) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO customer (organization_id, name) VALUES (?, '아크메') RETURNING id",
        UUID.class,
        organizationId);
  }

  private UUID insertInvoice(UUID organizationId, UUID customerId, long supplyAmount) {
    Invoice invoice =
        new Invoice(
            UUID.randomUUID(),
            organizationId,
            customerId,
            "아크메",
            "2026-08",
            supplyAmount,
            supplyAmount / 10,
            OffsetDateTime.parse("2026-09-01T05:00:00Z"));
    return invoiceRepository.save(invoice).getId();
  }
}
