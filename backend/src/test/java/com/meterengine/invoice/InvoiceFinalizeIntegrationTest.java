package com.meterengine.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.meterengine.TestClock;
import com.meterengine.TestCredentials;
import com.meterengine.TestEvents;
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
import com.meterengine.mail.entity.OutboundEmail;
import com.meterengine.mail.repository.OutboundEmailRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
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
  @Autowired private OutboundEmailRepository outboundEmailRepository;
  @Autowired private EntityManager entityManager;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private TestClock testClock;

  private final Map<UUID, String> authorizationsByOrganizationId = new HashMap<>();

  private MockMvcTester mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcTester.from(
            webApplicationContext, builder -> builder.apply(springSecurity()).build());
  }

  @AfterEach
  void resetClock() {
    testClock.reset();
  }

  @Test
  void 확정_시각은_서버_시계의_지금을_KST로_나타낸_것이다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID acme = insertCustomer(organizationId, "아크메");
    insertEvent(organizationId, "tx-1", acme, "token", 3299, "2026-08-10T12:00:00+09:00");
    testClock.fix(OffsetDateTime.parse("2026-09-01T05:00:00Z"));

    Invoice invoice =
        invoiceFinalizationService.finalize(organizationId, AUGUST).invoices().getFirst();

    assertThat(invoice.getFinalizedAt())
        .isEqualTo(OffsetDateTime.parse("2026-09-01T14:00:00+09:00"));
  }

  @Test
  void 확정하면_고객_이름과_달과_KST_확정_시각과_라인의_수량과_단가가_저장된다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID acme = insertCustomer(organizationId, "아크메");
    insertEvent(organizationId, "tx-1", acme, "token", 500, "2026-08-10T12:00:00+09:00");
    insertEvent(organizationId, "tx-2", acme, "token", 2799, "2026-08-31T23:59:59+09:00");
    insertEvent(organizationId, "tx-3", acme, "token", 1000, "2026-09-01T00:00:00+09:00");

    Invoice invoice =
        invoiceFinalizationService.finalize(organizationId, AUGUST).invoices().getFirst();
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
  void 마감이_지난_달은_마감_시각_전에_받은_이벤트로_확정하고_인보이스에_마감_시각을_남긴다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID acme = insertCustomer(organizationId, "아크메");
    TestEvents.insertToken(
        jdbcTemplate,
        organizationId,
        "tx-1",
        acme,
        3299,
        "2026-08-31T10:00:00+09:00",
        "2026-09-01T12:59:59+09:00");
    TestEvents.insertToken(
        jdbcTemplate,
        organizationId,
        "tx-2",
        acme,
        1000,
        "2026-08-31T11:00:00+09:00",
        "2026-09-01T13:00:00+09:00");
    long draftAmount = draft(organizationId).totalAmount();

    Invoice invoice =
        invoiceFinalizationService.finalize(organizationId, AUGUST).invoices().getFirst();

    assertThat(invoice.getSupplyAmount()).isEqualTo(draftAmount).isEqualTo(1640);
    assertThat(invoice.getCutoffAt()).isEqualTo(OffsetDateTime.parse("2026-09-01T13:00:00+09:00"));
  }

  @Test
  void 마감_시각_전에_확정하면_확정_시각까지_받은_이벤트로_확정하고_그_시각을_마감으로_남긴다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID acme = insertCustomer(organizationId, "아크메");
    TestEvents.insertToken(
        jdbcTemplate,
        organizationId,
        "tx-1",
        acme,
        3299,
        "2026-08-10T10:00:00+09:00",
        "2026-08-10T10:00:00+09:00");
    TestEvents.insertToken(
        jdbcTemplate,
        organizationId,
        "tx-2",
        acme,
        1000,
        "2026-08-10T11:00:00+09:00",
        "2026-08-20T10:00:01+09:00");
    testClock.fix(OffsetDateTime.parse("2026-08-20T10:00:00+09:00"));

    Invoice invoice =
        invoiceFinalizationService.finalize(organizationId, AUGUST).invoices().getFirst();

    assertThat(invoice.getSupplyAmount()).isEqualTo(1640);
    assertThat(invoice.getCutoffAt())
        .isEqualTo(invoice.getFinalizedAt())
        .isEqualTo(OffsetDateTime.parse("2026-08-20T10:00:00+09:00"));
  }

  @Test
  void 단가는_세금_별도이고_세액은_공급가액의_10퍼센트이며_합계는_둘의_합이다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID acme = insertCustomer(organizationId, "아크메");
    insertEvent(organizationId, "tx-1", acme, "token", 3299, "2026-08-10T12:00:00+09:00");

    Invoice invoice =
        invoiceFinalizationService.finalize(organizationId, AUGUST).invoices().getFirst();

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

    Invoice invoice =
        invoiceFinalizationService.finalize(organizationId, AUGUST).invoices().getFirst();

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
        .ignoringFields("billableMetricName")
        .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .isEqualTo(draftInvoiceLines);
    assertThat(invoiceLines)
        .extracting(InvoiceLineResponse::billableMetricName)
        .containsOnly("토큰 사용량");
  }

  @Test
  void 확정_뒤에_새_이벤트가_들어오고_단가가_바뀌어도_금액과_라인이_변하지_않는다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID acme = insertCustomer(organizationId, "아크메");
    insertEvent(organizationId, "tx-1", acme, "token", 3280, "2026-08-10T12:00:00+09:00");
    Invoice invoice =
        invoiceFinalizationService.finalize(organizationId, AUGUST).invoices().getFirst();
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
  void 확정한_라인은_그때의_미터_이름을_남겨_미터_이름을_바꿔도_인보이스의_이름은_그대로다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID acme = insertCustomer(organizationId, "아크메");
    insertEvent(organizationId, "tx-1", acme, "token", 3280, "2026-08-10T12:00:00+09:00");
    Invoice invoice =
        invoiceFinalizationService.finalize(organizationId, AUGUST).invoices().getFirst();
    flushAndClear();
    jdbcTemplate.update(
        "UPDATE billable_metric SET name = '바꾼 이름' WHERE organization_id = ?", organizationId);

    assertThat(
            mvc.get()
                .uri("/v1/invoices/{id}", invoice.getId())
                .header(AUTHORIZATION, authorization(organizationId))
                .exchange())
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.lines[0].billable_metric_name")
        .isEqualTo("token-usage 미터");
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

    assertThat(invoiceFinalizationService.finalize(organizationId, AUGUST).invoices())
        .extracting(Invoice::getCustomerId)
        .containsExactly(acme);
  }

  @Test
  void 사용량이_없어_건너뛴_고객은_나중에_사용량이_생기면_다시_불러_확정된다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID acme = insertCustomer(organizationId, "아크메");
    assertThat(invoiceFinalizationService.finalize(organizationId, AUGUST).invoices()).isEmpty();

    insertEvent(organizationId, "tx-1", acme, "token", 1000, "2026-08-10T12:00:00+09:00");

    assertThat(invoiceFinalizationService.finalize(organizationId, AUGUST).invoices())
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

    Invoice invoice =
        invoiceFinalizationService.finalize(organizationId, AUGUST).invoices().getFirst();
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
    assertThat(invoiceFinalizationService.finalize(organizationId, AUGUST).invoices()).isEmpty();

    insertPrice(organizationId, "api-calls", "100");

    assertThat(invoiceFinalizationService.finalize(organizationId, AUGUST).invoices())
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

    Invoice invoice =
        invoiceFinalizationService.finalize(organizationId, AUGUST).invoices().getFirst();
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

    assertThat(invoiceFinalizationService.finalize(organizationId, AUGUST).invoices()).isEmpty();
  }

  @Test
  void 다시_부르면_새로_확정하지_않는다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID acme = insertCustomer(organizationId, "아크메");
    insertEvent(organizationId, "tx-1", acme, "token", 3280, "2026-08-10T12:00:00+09:00");

    MvcTestResult finalized = finalizeInvoices(organizationId, "?month=2026-08");
    assertThat(finalized).hasStatusOk().bodyJson().extractingPath("$.finalized_count").isEqualTo(1);
    assertThat(finalized).bodyJson().extractingPath("$.failed_count").isEqualTo(0);
    assertThat(finalized).bodyJson().extractingPath("$.invoices").asArray().hasSize(1);
    assertThat(finalized).bodyJson().extractingPath("$.total_amount").isEqualTo(1804);
    assertThat(finalized).bodyJson().extractingPath("$.invoices[0].total_amount").isEqualTo(1804);
    insertEvent(organizationId, "tx-2", acme, "token", 1000, "2026-08-20T12:00:00+09:00");
    assertThat(finalizeInvoices(organizationId, "?month=2026-08"))
        .bodyJson()
        .extractingPath("$.finalized_count")
        .isEqualTo(0);

    assertThat(
            mvc.get()
                .uri("/v1/invoices?month=2026-08")
                .header(AUTHORIZATION, authorization(organizationId))
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
        invoiceFinalizationService
            .finalize(organizationId, AUGUST.plusMonths(1))
            .invoices()
            .getFirst();

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

    assertThat(invoiceFinalizationService.finalize(organizationId, AUGUST).invoices())
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
  void month_형식이_틀리면_400이고_인증이_없으면_401이다() {
    UUID organizationId = organizationWithTokenBillableMetric();

    assertThat(finalizeInvoices(organizationId, "?month=2026-8")).hasStatus(400);
    assertThat(mvc.post().uri("/v1/invoices?month=2026-08").exchange()).hasStatus(401);
  }

  @Test
  void 확정하면_확정한_고객마다_청구_메일이_한_건_남고_청구_이메일이_없으면_받는_주소를_비운다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID acme = insertCustomer(organizationId, "아크메", "billing@acme.com");
    UUID beta = insertCustomer(organizationId, "베타", null);
    UUID zeta = insertCustomer(organizationId, "제타상사", "billing@zeta.com");
    insertEvent(organizationId, "tx-1", acme, "token", 3280, "2026-08-10T12:00:00+09:00");
    insertEvent(organizationId, "tx-2", beta, "token", 1000, "2026-08-10T12:00:00+09:00");
    insertEvent(organizationId, "tx-3", zeta, "token", 19, "2026-08-10T12:00:00+09:00");

    Map<UUID, UUID> invoiceIdByCustomerId =
        invoiceFinalizationService.finalize(organizationId, AUGUST).invoices().stream()
            .collect(Collectors.toMap(Invoice::getCustomerId, Invoice::getId));
    flushAndClear();

    assertThat(outboundEmails(organizationId))
        .extracting(
            OutboundEmail::getKind,
            OutboundEmail::getPeriod,
            OutboundEmail::getInvoiceId,
            OutboundEmail::getRecipientEmail)
        .containsExactlyInAnyOrder(
            tuple("invoice", "2026-08", invoiceIdByCustomerId.get(acme), "billing@acme.com"),
            tuple("invoice", "2026-08", invoiceIdByCustomerId.get(beta), null));
  }

  @Test
  void 청구_메일_본문에_도입사와_고객과_달과_라인과_인보이스의_금액이_HTML로_해석되지_않게_담긴다() {
    UUID organizationId = insertOrganization();
    jdbcTemplate.update("UPDATE organization SET name = '<i>도입사</i>' WHERE id = ?", organizationId);
    jdbcTemplate.update(
        """
        INSERT INTO billable_metric
          (organization_id, code, name, event_type, aggregation, target_property,
           dimension_properties)
        VALUES (?, 'token-usage', '토큰 사용량', 'chat_completion', 'sum', 'token', '{model}')
        """,
        organizationId);
    insertPrice(organizationId, "token-usage", "{\"model\": \"a\"}", "0.5");
    insertPrice(organizationId, "token-usage", "{\"model\": \"b\"}", "1200");
    UUID acme = insertCustomer(organizationId, "<b>아크메</b>");
    insertEvent(organizationId, "tx-1", acme, "a", 1235);
    insertEvent(organizationId, "tx-2", acme, "b", 3);

    UUID invoiceId =
        invoiceFinalizationService.finalize(organizationId, AUGUST).invoices().getFirst().getId();
    flushAndClear();

    Invoice invoice = invoiceRepository.findById(invoiceId).orElseThrow();
    OutboundEmail outboundEmail = outboundEmails(organizationId).getFirst();
    assertThat(invoice.getTotalAmount()).isEqualTo(4631);
    assertThat(outboundEmail.getSubject()).isEqualTo("[<i>도입사</i>] 2026-08 사용료 청구서");
    assertThat(outboundEmail.getHtmlBody())
        .contains("&lt;i&gt;도입사&lt;/i&gt;", "&lt;b&gt;아크메&lt;/b&gt;", "2026-08", "token-usage")
        .contains("model=a", "1,235", "0.5원", "610원")
        .contains("model=b", "1,200원", "3,600원")
        .contains("4,210원", "421원", "4,631원")
        .doesNotContain("<b>", "<i>");
    assertThat(outboundEmail.getCreatedAt()).isAtSameInstantAs(invoice.getFinalizedAt());
  }

  @Test
  void 확정_뒤에_고객과_단가가_바뀌고_다시_확정해도_남긴_청구_메일은_그대로_하나다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID acme = insertCustomer(organizationId, "아크메", "billing@acme.com");
    insertEvent(organizationId, "tx-1", acme, "token", 3280, "2026-08-10T12:00:00+09:00");
    invoiceFinalizationService.finalize(organizationId, AUGUST);
    flushAndClear();

    jdbcTemplate.update(
        "UPDATE customer SET billing_email = 'new@acme.com', name = '새이름' WHERE id = ?", acme);
    jdbcTemplate.update(
        "UPDATE price_rate SET unit_price = 2 WHERE organization_id = ?", organizationId);
    insertEvent(organizationId, "tx-2", acme, "token", 1000, "2026-08-20T12:00:00+09:00");
    invoiceFinalizationService.finalize(organizationId, AUGUST);
    flushAndClear();

    assertThat(outboundEmails(organizationId))
        .singleElement()
        .satisfies(
            outboundEmail -> {
              assertThat(outboundEmail.getRecipientEmail()).isEqualTo("billing@acme.com");
              assertThat(outboundEmail.getHtmlBody()).contains("아크메", "1,804원");
            });
  }

  // --- 헬퍼 ---

  private String authorization(UUID organizationId) {
    return authorizationsByOrganizationId.computeIfAbsent(
        organizationId, id -> TestCredentials.issueApiKey(jdbcTemplate, id));
  }

  private List<OutboundEmail> outboundEmails(UUID organizationId) {
    return outboundEmailRepository.findAll().stream()
        .filter(outboundEmail -> outboundEmail.getOrganizationId().equals(organizationId))
        .toList();
  }

  private MvcTestResult finalizeInvoices(UUID organizationId, String query) {
    return mvc.post()
        .uri("/v1/invoices" + query)
        .header(AUTHORIZATION, authorization(organizationId))
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
    return insertCustomer(organizationId, name, null);
  }

  private UUID insertCustomer(UUID organizationId, String name, String billingEmail) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO customer (organization_id, name, billing_email) VALUES (?, ?, ?) RETURNING id",
        UUID.class,
        organizationId,
        name,
        billingEmail);
  }

  private void insertEvent(
      UUID organizationId, String transactionId, UUID customerId, String model, int token) {
    OffsetDateTime occurredAt = OffsetDateTime.parse("2026-08-10T12:00:00+09:00");
    TestEvents.insert(
        jdbcTemplate,
        organizationId,
        transactionId,
        customerId,
        "chat_completion",
        "{\"model\":\"%s\",\"token\":%d}".formatted(model, token),
        occurredAt,
        occurredAt);
  }

  private void insertEvent(
      UUID organizationId,
      String transactionId,
      UUID customerId,
      String property,
      int value,
      String occurredAt) {
    TestEvents.insert(
        jdbcTemplate,
        organizationId,
        transactionId,
        customerId,
        "chat_completion",
        "{\"%s\":%d}".formatted(property, value),
        OffsetDateTime.parse(occurredAt),
        OffsetDateTime.parse(occurredAt));
  }
}
