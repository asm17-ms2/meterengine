package com.meterengine.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.global.error.ConflictException;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.invoice.dto.DraftInvoiceResponse;
import com.meterengine.invoice.dto.InvoiceResponse;
import com.meterengine.invoice.entity.Invoice;
import com.meterengine.invoice.service.DraftInvoiceService;
import com.meterengine.invoice.service.InvoiceFinalizationService;
import com.meterengine.invoice.service.InvoiceService;
import com.meterengine.metric.dto.BillableMetricUsage;
import com.meterengine.metric.dto.CustomerUsage;
import com.meterengine.metric.dto.DimensionUsage;
import com.meterengine.metric.entity.BillableMetric;
import com.meterengine.metric.service.BillableMetricUsageService;
import com.meterengine.pricing.dto.CreatePricePlanRequest;
import com.meterengine.pricing.dto.CreatePriceRateRequest;
import com.meterengine.pricing.dto.PriceRateResponse;
import com.meterengine.pricing.dto.PriceRateTier;
import com.meterengine.pricing.service.BillableMetricPriceService;
import com.meterengine.pricing.service.ChargeService;
import com.meterengine.pricing.service.PricePlanService;
import com.meterengine.pricing.service.PriceRateService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class VersionedPricingIntegrationTest {
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PriceRateService priceRateService;
  @Autowired private PricePlanService pricePlanService;
  @Autowired private BillableMetricPriceService billableMetricPriceService;
  @Autowired private ChargeService chargeService;
  @Autowired private DraftInvoiceService draftInvoiceService;
  @Autowired private InvoiceFinalizationService invoiceFinalizationService;
  @Autowired private InvoiceService invoiceService;
  @Autowired private EntityManager entityManager;
  @Autowired private WebApplicationContext webApplicationContext;
  private MockMvcTester mvc;
  private UUID organizationId;
  private UUID customerId;
  private YearMonth month;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcTester.from(
            webApplicationContext, builder -> builder.apply(springSecurity()).build());
    organizationId =
        jdbcTemplate.queryForObject(
            "INSERT INTO organization(name) VALUES ('요금제 검증') RETURNING id", UUID.class);
    customerId =
        jdbcTemplate.queryForObject(
            "INSERT INTO customer(organization_id,name) VALUES (?,'고객') RETURNING id",
            UUID.class,
            organizationId);
    jdbcTemplate.update(
        "INSERT INTO"
            + " billable_metric(organization_id,code,name,event_type,aggregation,target_property)"
            + " VALUES (?,'tokens','토큰','tokens','sum','quantity')",
        organizationId);
    month = YearMonth.now(BillableMetricUsageService.BILLING_ZONE);
  }

  @Test
  void 고객_단가가_공통_단가보다_우선하고_일반_목록에_섞이지_않는다() {
    rate(null, month, null, "per_unit", new BigDecimal("10"), null);
    rate(customerId, month, null, "per_unit", new BigDecimal("3"), null);
    assertThat(amount(month, "100")).isEqualTo(300);
    assertThat(
            billableMetricPriceService
                .list(organizationId, month, null)
                .billableMetricPrices()
                .getFirst()
                .priceRates())
        .hasSize(1);
    assertThat(
            billableMetricPriceService
                .list(organizationId, month, null)
                .billableMetricPrices()
                .getFirst()
                .priceRates()
                .getFirst()
                .unitPrice())
        .isEqualByComparingTo("10");
  }

  @Test
  void 미래_버전과_종료_월은_해당_월에만_적용된다() {
    rate(null, month, month.plusMonths(1), "per_unit", new BigDecimal("10"), null);
    rate(null, month.plusMonths(2), null, "per_unit", new BigDecimal("20"), null);
    assertThat(amount(month, "100")).isEqualTo(1000);
    assertThat(amount(month.plusMonths(1), "100")).isZero();
    assertThat(amount(month.plusMonths(2), "100")).isEqualTo(2000);
  }

  @Test
  void 지난_월_등록과_삭제는_거절하고_기존_단가를_보존한다() {
    UUID id = legacyRate();
    assertThatThrownBy(
            () -> rate(null, month.minusMonths(1), null, "per_unit", BigDecimal.ONE, null))
        .isInstanceOfSatisfying(
            ConflictException.class,
            error ->
                assertThat(error.getErrorCode()).isEqualTo(ErrorCode.PRICE_RATE_HISTORY_IMMUTABLE));
    priceRateService.delete(organizationId, "tokens", id);
    assertThat(amount(month.minusMonths(1), "100")).isEqualTo(1000);
  }

  @Test
  void 지난_월_요금제_등록은_거절하고_삭제해도_지난달_요금제를_보존한다() {
    legacyRate();
    UUID id =
        jdbcTemplate.queryForObject(
            "INSERT INTO price_plan(organization_id,effective_from,base_fee,minimum_amount)"
                + " VALUES (?,?,100,0) RETURNING id",
            UUID.class,
            organizationId,
            month.minusMonths(1).toString());
    assertThatThrownBy(
            () ->
                pricePlanService.create(
                    organizationId,
                    new CreatePricePlanRequest(null, month.minusMonths(1), null, 500L, 12000L)))
        .isInstanceOfSatisfying(
            ConflictException.class,
            error ->
                assertThat(error.getErrorCode()).isEqualTo(ErrorCode.PRICE_PLAN_HISTORY_IMMUTABLE));
    pricePlanService.delete(organizationId, id);
    assertThat(amount(month.minusMonths(1), "100")).isEqualTo(1100);
  }

  @Test
  void 기존_조합_삭제는_이전_금액과_고객_단가와_미래_버전을_보존한다() {
    legacyRate();
    rate(null, month, null, "per_unit", new BigDecimal("20"), null);
    PriceRateResponse customerRate =
        rate(customerId, month, null, "per_unit", new BigDecimal("3"), null);
    rate(null, month.plusMonths(1), null, "per_unit", new BigDecimal("30"), null);
    priceRateService.delete(organizationId, "tokens", "{}");
    assertThat(
            billableMetricPriceService
                .list(organizationId, month, null)
                .billableMetricPrices()
                .getFirst()
                .priceRates())
        .hasSize(1);
    assertThat(amount(month.minusMonths(1), "100")).isEqualTo(1000);
    assertThat(amount(month, "100")).isEqualTo(300);
    priceRateService.delete(organizationId, "tokens", customerRate.id());
    assertThat(amount(month.plusMonths(1), "100")).isEqualTo(3000);
  }

  @Test
  void 최신_단가_보관은_과거와_현재를_유지하고_다음월에_구단가로_돌아가지_않는다() {
    legacyRate();
    var current = rate(null, month, null, "per_unit", new BigDecimal("20"), null);
    priceRateService.delete(organizationId, "tokens", current.id());
    assertThat(amount(month.minusMonths(1), "100")).isEqualTo(1000);
    assertThat(amount(month, "100")).isEqualTo(2000);
    assertThat(amount(month.plusMonths(1), "100")).isZero();
    assertThat(
            billableMetricPriceService
                .list(organizationId, month.plusMonths(1), null)
                .billableMetricPrices()
                .getFirst()
                .priceRates())
        .isEmpty();
  }

  @Test
  void 최신_요금제_보관은_다음월에_구요금제를_되살리지_않는다() {
    legacyRate();
    jdbcTemplate.update(
        "INSERT INTO price_plan (organization_id,effective_from,base_fee,minimum_amount) VALUES (?, ?, 100, 0)",
        organizationId,
        month.minusMonths(1).toString());
    var current =
        pricePlanService.create(
            organizationId, new CreatePricePlanRequest(null, month, null, 200L, 0L));
    pricePlanService.delete(organizationId, current.id());
    assertThat(amount(month.minusMonths(1), "100")).isEqualTo(1100);
    assertThat(amount(month, "100")).isEqualTo(1200);
    assertThat(amount(month.plusMonths(1), "100")).isEqualTo(1000);
  }

  @Test
  void 누진_구간은_각_라인에서_절사하고_경계를_겹치지_않는다() {
    rate(null, month, null, "graduated", null, tiers());
    String[] quantities = {"0", "100", "101", "1000", "1500.5"};
    long[] amounts = {0, 1000, 1000, 8200, 10700};
    for (int index = 0; index < quantities.length; index++)
      assertThat(amount(month, quantities[index])).as(quantities[index]).isEqualTo(amounts[index]);
    assertThat(
            chargeService
                .calculate(organizationId, month, List.of(usage("1500.5")))
                .get(customerId)
                .dimensionCharges())
        .hasSize(3);
  }

  @Test
  void 총량_구간은_선택한_단가를_전체_수량에_적용한다() {
    rate(null, month, null, "volume", null, tiers());
    String[] quantities = {"0", "100", "101", "1000", "1500.5"};
    long[] amounts = {0, 1000, 800, 8000, 7500};
    for (int index = 0; index < quantities.length; index++)
      assertThat(amount(month, quantities[index])).as(quantities[index]).isEqualTo(amounts[index]);
  }

  @Test
  void 기본료는_최소_사용료와_별도이고_사용량이_없거나_음수면_붙지_않는다() {
    rate(null, month, null, "per_unit", BigDecimal.ONE, null);
    pricePlanService.create(
        organizationId, new CreatePricePlanRequest(null, month, null, 500L, 1000L));
    assertThat(amount(month, "100")).isEqualTo(1500);
    assertThat(amount(month, "0")).isZero();
    assertThat(amount(month, "-100")).isZero();
    pricePlanService.create(
        organizationId, new CreatePricePlanRequest(customerId, month, null, 200L, 400L));
    assertThat(amount(month, "100")).isEqualTo(600);
  }

  @Test
  void 구간_기본료_최소조정은_실제_인보이스로_확정되고_조회된다() {
    rate(null, month, null, "graduated", null, tiers());
    pricePlanService.create(
        organizationId, new CreatePricePlanRequest(null, month, null, 500L, 12000L));
    jdbcTemplate.update(
        "INSERT INTO event(organization_id,transaction_id,customer_id,type,occurred_at,properties)"
            + " VALUES (?,?,?,'tokens',?,?::jsonb)",
        organizationId,
        UUID.randomUUID().toString(),
        customerId,
        OffsetDateTime.now(),
        "{\"quantity\":1500.5}");
    DraftInvoiceResponse draft = draftInvoiceService.preview(organizationId, month);
    assertThat(draft.customers().getFirst().amount()).isEqualTo(12500);
    assertThat(draft.customers().getFirst().lines()).hasSize(5);
    Invoice invoice = invoiceFinalizationService.finalize(organizationId, month).getFirst();
    entityManager.flush();
    entityManager.clear();
    InvoiceResponse reloaded = invoiceService.get(organizationId, invoice.getId());
    assertThat(reloaded.supplyAmount()).isEqualTo(12500);
    assertThat(reloaded.taxAmount()).isEqualTo(1250);
    assertThat(reloaded.lines()).hasSize(5);
    assertThat(reloaded.lines())
        .extracting(InvoiceResponse.InvoiceLineResponse::lineType)
        .contains("graduated_tier", "base_fee", "minimum_adjustment");
    assertThat(
            reloaded.lines().stream().mapToLong(InvoiceResponse.InvoiceLineResponse::amount).sum())
        .isEqualTo(reloaded.supplyAmount());
    String read = TestCredentials.issueApiKey(jdbcTemplate, organizationId, "read");
    assertThat(
            mvc.get().uri("/v1/invoices/" + invoice.getId()).header(AUTHORIZATION, read).exchange())
        .hasStatusOk();
    UUID other = UUID.randomUUID();
    String foreign = TestCredentials.issueApiKey(jdbcTemplate, other, "read");
    assertThat(
            mvc.get()
                .uri("/v1/invoices/" + invoice.getId())
                .header(AUTHORIZATION, foreign)
                .exchange())
        .hasStatus(404);
  }

  @Test
  void read_키는_가격과_요금제와_공급자를_조회하고_쓰기에는_거절된다() {
    rate(null, month, null, "per_unit", BigDecimal.ONE, null);
    String read = TestCredentials.issueApiKey(jdbcTemplate, organizationId, "read");
    for (String path :
        List.of(
            "/v1/billable-metric-prices",
            "/v1/billable-metrics/tokens/price-rates",
            "/v1/price-plans",
            "/v1/organization/supplier"))
      assertThat(mvc.get().uri(path).header(AUTHORIZATION, read).exchange()).as(path).hasStatusOk();
    assertThat(
            mvc.post()
                .uri("/v1/price-plans")
                .header(AUTHORIZATION, read)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"base_fee\":10,\"minimum_amount\":20}")
                .exchange())
        .hasStatus(403);
    UUID other = UUID.randomUUID();
    String foreign = TestCredentials.issueApiKey(jdbcTemplate, other, "read");
    assertThat(
            mvc.get()
                .uri("/v1/billable-metric-prices")
                .queryParam("customer_id", customerId.toString())
                .header(AUTHORIZATION, foreign)
                .exchange())
        .hasStatus(404);
  }

  @Test
  void 잘못된_구간과_종료월은_저장_전에_거절된다() {
    String admin = TestCredentials.issueApiKey(jdbcTemplate, organizationId);
    for (String body :
        List.of(
            "{\"dimension_values\":{},\"model\":\"graduated\",\"tiers\":[{\"up_to\":100,\"unit_price\":1}]}",
            "{\"dimension_values\":{},\"model\":\"graduated\",\"tiers\":[{\"up_to\":100,\"unit_price\":1},{\"up_to\":50,\"unit_price\":1},{\"up_to\":null,\"unit_price\":1}]}",
            "{\"dimension_values\":{},\"unit_price\":-1}",
            "{\"dimension_values\":{},\"unit_price\":1,\"effective_from\":\""
                + month
                + "\",\"effective_until\":\""
                + month
                + "\"}"))
      assertThat(
              mvc.post()
                  .uri("/v1/billable-metrics/tokens/price-rates")
                  .header(AUTHORIZATION, admin)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body)
                  .exchange())
          .as(body)
          .hasStatus(400);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM price_rate WHERE organization_id=?",
                Integer.class,
                organizationId))
        .isZero();
  }

  @Test
  void 공급자_수정은_도입사별로_저장하고_감사_기록을_남긴다() {
    String admin = TestCredentials.issueApiKey(jdbcTemplate, organizationId);
    assertThat(
            mvc.put()
                .uri("/v1/organization/supplier")
                .header(AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"business_registration_number\":\"1234567890\",\"representative_name\":\"대표자\",\"business_address\":\"서울\"}")
                .exchange())
        .hasStatusOk();
    entityManager.flush();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT representative_name FROM organization WHERE id=?",
                String.class,
                organizationId))
        .isEqualTo("대표자");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_log WHERE organization_id=? AND"
                    + " action='supplier_updated'",
                Integer.class,
                organizationId))
        .isEqualTo(1);
  }

  private PriceRateResponse rate(
      UUID scopedCustomerId,
      YearMonth from,
      YearMonth until,
      String model,
      BigDecimal price,
      List<PriceRateTier> tiers) {
    return priceRateService.create(
        organizationId,
        "tokens",
        new CreatePriceRateRequest(Map.of(), price, scopedCustomerId, from, until, model, tiers));
  }

  private UUID legacyRate() {
    return jdbcTemplate.queryForObject(
        "INSERT INTO price_rate(organization_id,billable_metric_code,dimension_values,unit_price)"
            + " VALUES (?,'tokens','{}',10) RETURNING id",
        UUID.class,
        organizationId);
  }

  private List<PriceRateTier> tiers() {
    return List.of(
        new PriceRateTier(new BigDecimal("100"), new BigDecimal("10")),
        new PriceRateTier(new BigDecimal("1000"), new BigDecimal("8")),
        new PriceRateTier(null, new BigDecimal("5")));
  }

  private long amount(YearMonth period, String quantity) {
    return chargeService
        .calculate(organizationId, period, List.of(usage(quantity)))
        .get(customerId)
        .amount();
  }

  private BillableMetricUsage usage(String quantity) {
    return new BillableMetricUsage(
        new BillableMetric(organizationId, "tokens", "토큰", "tokens", "sum", "quantity", List.of()),
        List.of(
            new CustomerUsage(
                customerId,
                "고객",
                new BigDecimal(quantity),
                List.of(new DimensionUsage(Map.of(), new BigDecimal(quantity))))));
  }
}
