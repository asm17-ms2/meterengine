package com.meterengine.metric;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.metric.dto.BillableMetricUsageResponse.BillableMetricUsageCustomer;
import com.meterengine.metric.dto.BillableMetricUsageResponse.BillableMetricUsageDimensionUsage;
import com.meterengine.metric.dto.ListBillableMetricUsagesResponse;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashMap;
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
import tools.jackson.databind.json.JsonMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class BillableMetricUsageIntegrationTest {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final String AUGUST = "2026-08";
  private static final String SEPTEMBER = "2026-09";

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private JsonMapper jsonMapper;

  private final Map<UUID, String> authorizationsByOrganizationId = new HashMap<>();

  private MockMvcTester mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcTester.from(
            webApplicationContext, builder -> builder.apply(springSecurity()).build());
  }

  // ---------------------------------------------------------------------------
  // 월 경계
  // ---------------------------------------------------------------------------

  @Test
  void 팔월_마지막_순간의_이벤트는_팔월에_귀속된다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID customerId = insertCustomer(organizationId, "아크메");
    insertEvent(organizationId, "tx-1", customerId, 500, "2026-08-31T23:59:59+09:00");

    assertThat(quantityOf(organizationId, AUGUST, customerId)).isEqualByComparingTo("500");
    assertThat(quantityOf(organizationId, SEPTEMBER, customerId)).isEqualByComparingTo("0");
  }

  @Test
  void 구월_첫_순간의_이벤트는_구월에_귀속된다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID customerId = insertCustomer(organizationId, "아크메");
    insertEvent(organizationId, "tx-1", customerId, 500, "2026-09-01T00:00:00+09:00");

    assertThat(quantityOf(organizationId, AUGUST, customerId)).isEqualByComparingTo("0");
    assertThat(quantityOf(organizationId, SEPTEMBER, customerId)).isEqualByComparingTo("500");
  }

  @Test
  void 칠월_마지막_순간의_이벤트는_팔월에_들어오지_않는다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID customerId = insertCustomer(organizationId, "아크메");
    insertEvent(organizationId, "tx-1", customerId, 500, "2026-07-31T23:59:59+09:00");

    assertThat(quantityOf(organizationId, AUGUST, customerId)).isEqualByComparingTo("0");
  }

  @Test
  void 같은_순간을_UTC로_보낸_이벤트도_같은_달에_귀속된다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID customerId = insertCustomer(organizationId, "아크메");
    insertEvent(organizationId, "tx-1", customerId, 500, "2026-08-31T14:59:59Z");
    insertEvent(organizationId, "tx-2", customerId, 700, "2026-08-31T15:00:00Z");

    assertThat(quantityOf(organizationId, AUGUST, customerId)).isEqualByComparingTo("500");
    assertThat(quantityOf(organizationId, SEPTEMBER, customerId)).isEqualByComparingTo("700");
  }

  // ---------------------------------------------------------------------------
  // 지금 이후 시각
  // ---------------------------------------------------------------------------

  @Test
  void 지금_이후_시각의_이벤트는_사용량과_청구_예정액에서_빠진다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    insertPriceRate(organizationId, "0.5");
    UUID acmeId = insertCustomer(organizationId, "아크메");
    OffsetDateTime now = OffsetDateTime.now(KST);
    String thisMonth = YearMonth.from(now).toString();
    insertEvent(
        organizationId,
        "tx-future",
        acmeId,
        "chat_completion",
        "{\"token\":10000}",
        now.plusHours(1));

    assertThat(quantityOf(organizationId, thisMonth, acmeId)).isEqualByComparingTo("0");
    MvcTestResult draft = getDraftInvoice(organizationId, thisMonth);
    assertThat(draft).hasStatusOk().bodyJson().extractingPath("$.total_amount").isEqualTo(0);
    assertThat(draft).bodyJson().extractingPath("$.customers[0].lines.length()").isEqualTo(0);
  }

  @Test
  void 방금_전_이벤트는_사용량과_청구_예정액에_들어간다() {
    OffsetDateTime now = OffsetDateTime.now(KST);
    OffsetDateTime justBefore = now.minusMinutes(1);
    assumeTrue(YearMonth.from(justBefore).equals(YearMonth.from(now)));
    UUID organizationId = organizationWithTokenBillableMetric();
    insertPriceRate(organizationId, "0.5");
    UUID acmeId = insertCustomer(organizationId, "아크메");
    String thisMonth = YearMonth.from(now).toString();
    insertEvent(
        organizationId, "tx-past", acmeId, "chat_completion", "{\"token\":500}", justBefore);
    insertEvent(
        organizationId,
        "tx-future",
        acmeId,
        "chat_completion",
        "{\"token\":10000}",
        now.plusHours(1));

    assertThat(quantityOf(organizationId, thisMonth, acmeId)).isEqualByComparingTo("500");
    MvcTestResult draft = getDraftInvoice(organizationId, thisMonth);
    assertThat(draft).hasStatusOk().bodyJson().extractingPath("$.total_amount").isEqualTo(250);
    assertThat(draft).bodyJson().extractingPath("$.customers[0].lines[0].quantity").isEqualTo(500);
    assertThat(draft).bodyJson().extractingPath("$.customers[0].amount").isEqualTo(250);
  }

  // ---------------------------------------------------------------------------
  // 집계 규칙
  // ---------------------------------------------------------------------------

  @Test
  void 합은_고객별로_나뉜다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID acmeId = insertCustomer(organizationId, "아크메");
    UUID betaId = insertCustomer(organizationId, "베타");
    insertEvent(organizationId, "tx-1", acmeId, 500, "2026-08-10T12:00:00+09:00");
    insertEvent(organizationId, "tx-2", acmeId, 700, "2026-08-11T12:00:00+09:00");
    insertEvent(organizationId, "tx-3", betaId, 300, "2026-08-12T12:00:00+09:00");

    assertThat(quantityOf(organizationId, AUGUST, acmeId)).isEqualByComparingTo("1200");
    assertThat(quantityOf(organizationId, AUGUST, betaId)).isEqualByComparingTo("300");
  }

  @Test
  void 이벤트가_없는_고객도_사용량_0으로_응답에_들어간다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID acmeId = insertCustomer(organizationId, "아크메");
    UUID betaId = insertCustomer(organizationId, "베타");
    insertEvent(organizationId, "tx-1", acmeId, 500, "2026-08-10T12:00:00+09:00");

    ListBillableMetricUsagesResponse response = usageOf(organizationId, AUGUST);

    assertThat(response.billableMetricUsages().getFirst().customers())
        .extracting(customer -> customer.customerId())
        .containsExactlyInAnyOrder(acmeId, betaId);
    assertThat(quantityOf(organizationId, AUGUST, betaId)).isEqualByComparingTo("0");
  }

  @Test
  void 다른_도입사의_이벤트는_섞이지_않는다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID acmeId = insertCustomer(organizationId, "아크메");
    insertEvent(organizationId, "tx-1", acmeId, 500, "2026-08-10T12:00:00+09:00");

    UUID otherOrganizationId = organizationWithTokenBillableMetric();
    UUID otherCustomerId = insertCustomer(otherOrganizationId, "남의 고객");
    insertEvent(otherOrganizationId, "tx-1", otherCustomerId, 999999, "2026-08-10T12:00:00+09:00");

    assertThat(quantityOf(organizationId, AUGUST, acmeId)).isEqualByComparingTo("500");
    assertThat(usageOf(organizationId, AUGUST).billableMetricUsages().getFirst().customers())
        .hasSize(1);
  }

  @Test
  void 미터의_event_type과_다른_이벤트는_합에서_빠진다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID acmeId = insertCustomer(organizationId, "아크메");
    insertEvent(organizationId, "tx-1", acmeId, 500, "2026-08-10T12:00:00+09:00");
    insertEvent(
        organizationId,
        "tx-2",
        acmeId,
        "embedding",
        "{\"token\":999999}",
        "2026-08-10T12:00:00+09:00");

    assertThat(quantityOf(organizationId, AUGUST, acmeId)).isEqualByComparingTo("500");
  }

  @Test
  void token이_숫자가_아니거나_없는_이벤트는_합에서_빠진다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID acmeId = insertCustomer(organizationId, "아크메");
    insertEvent(organizationId, "tx-1", acmeId, 500, "2026-08-10T12:00:00+09:00");
    insertEvent(
        organizationId,
        "tx-2",
        acmeId,
        "chat_completion",
        "{\"token\":\"많이\"}",
        "2026-08-10T12:00:00+09:00");
    insertEvent(
        organizationId,
        "tx-3",
        acmeId,
        "chat_completion",
        "{\"model\":\"opus-5\"}",
        "2026-08-10T12:00:00+09:00");
    insertEvent(
        organizationId,
        "tx-4",
        acmeId,
        "chat_completion",
        "{\"token\":null}",
        "2026-08-10T12:00:00+09:00");

    assertThat(quantityOf(organizationId, AUGUST, acmeId)).isEqualByComparingTo("500");
  }

  @Test
  void 소수_사용량은_자릿수가_잘리지_않고_합산된다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID acmeId = insertCustomer(organizationId, "아크메");
    insertEvent(
        organizationId,
        "tx-1",
        acmeId,
        "chat_completion",
        "{\"token\":0.1234567890123456789}",
        "2026-08-10T12:00:00+09:00");
    insertEvent(
        organizationId,
        "tx-2",
        acmeId,
        "chat_completion",
        "{\"token\":0.0000000000000000001}",
        "2026-08-10T12:00:00+09:00");

    assertThat(quantityOf(organizationId, AUGUST, acmeId))
        .isEqualByComparingTo("0.1234567890123456790");
  }

  // ---------------------------------------------------------------------------
  // 속성 값 조합
  // ---------------------------------------------------------------------------

  @Test
  void 차원_키가_없는_미터는_빈_객체_조합_하나에_합계가_실린다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID acmeId = insertCustomer(organizationId, "아크메");
    insertEvent(organizationId, "tx-1", acmeId, 500, "2026-08-10T12:00:00+09:00");
    insertEvent(organizationId, "tx-2", acmeId, 300, "2026-08-11T12:00:00+09:00");

    List<BillableMetricUsageDimensionUsage> dimensionUsages =
        dimensionUsagesOf(organizationId, AUGUST, acmeId);

    assertThat(dimensionUsages).hasSize(1);
    assertThat(dimensionUsages.getFirst().dimensionValues()).isEmpty();
    assertThat(dimensionUsages.getFirst().quantity()).isEqualByComparingTo("800");
  }

  @Test
  void 차원_키가_있는_미터는_키_값마다_조합이_갈리고_고객_합계는_그_합이다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    setDimensionProperties(organizationId, "{model}");
    UUID acmeId = insertCustomer(organizationId, "아크메");
    insertEvent(organizationId, "tx-1", acmeId, 500, "2026-08-10T12:00:00+09:00");
    insertEvent(organizationId, "tx-2", acmeId, 200, "2026-08-11T12:00:00+09:00");
    insertEvent(
        organizationId,
        "tx-3",
        acmeId,
        "chat_completion",
        "{\"model\":\"haiku-5\",\"token\":300}",
        "2026-08-12T12:00:00+09:00");

    List<BillableMetricUsageDimensionUsage> dimensionUsages =
        dimensionUsagesOf(organizationId, AUGUST, acmeId);

    assertThat(dimensionUsages)
        .extracting(BillableMetricUsageDimensionUsage::dimensionValues)
        .containsExactlyInAnyOrder(Map.of("model", "opus-5"), Map.of("model", "haiku-5"));
    assertThat(dimensionUsages)
        .filteredOn(dimensionUsage -> dimensionUsage.dimensionValues().containsValue("opus-5"))
        .first()
        .extracting(BillableMetricUsageDimensionUsage::quantity)
        .satisfies(quantity -> assertThat(quantity).isEqualByComparingTo("700"));
    assertThat(quantityOf(organizationId, AUGUST, acmeId)).isEqualByComparingTo("1000");
  }

  @Test
  void 이벤트가_없는_고객은_조합_목록이_빈_배열이다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    setDimensionProperties(organizationId, "{model}");
    UUID betaId = insertCustomer(organizationId, "베타");

    assertThat(dimensionUsagesOf(organizationId, AUGUST, betaId)).isEmpty();
  }

  @Test
  void 미터가_없는_도입사는_billable_metric_usages가_빈_배열이다() {
    UUID organizationId = insertOrganization();
    insertCustomer(organizationId, "아크메");

    assertThat(usageOf(organizationId, AUGUST).billableMetricUsages()).isEmpty();
  }

  @Test
  void 응답에_미터_정보와_고객_이름이_함께_나온다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID acmeId = insertCustomer(organizationId, "아크메");
    insertEvent(organizationId, "tx-1", acmeId, 500, "2026-08-10T12:00:00+09:00");

    MvcTestResult result = get(organizationId, AUGUST);

    assertThat(result).hasStatusOk().bodyJson().extractingPath("$.month").isEqualTo(AUGUST);
    assertThat(result)
        .bodyJson()
        .extractingPath("$.billable_metric_usages[0].code")
        .isEqualTo("token-usage");
    assertThat(result)
        .bodyJson()
        .extractingPath("$.billable_metric_usages[0].event_type")
        .isEqualTo("chat_completion");
    assertThat(result)
        .bodyJson()
        .extractingPath("$.billable_metric_usages[0].aggregation")
        .isEqualTo("sum");
    assertThat(result)
        .bodyJson()
        .extractingPath("$.billable_metric_usages[0].target_property")
        .isEqualTo("token");
    assertThat(result)
        .bodyJson()
        .extractingPath("$.billable_metric_usages[0].customers[0].customer_name")
        .isEqualTo("아크메");
    assertThat(result)
        .bodyJson()
        .extractingPath("$.billable_metric_usages[0]")
        .asMap()
        .doesNotContainKey("unit_price");
  }

  // ---------------------------------------------------------------------------
  // 요청 파라미터
  // ---------------------------------------------------------------------------

  @Test
  void month를_생략하면_이번_달_KST로_집계한다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID acmeId = insertCustomer(organizationId, "아크메");
    insertEvent(
        organizationId, "tx-1", acmeId, "chat_completion", "{\"token\":500}", withinThisMonth());

    MvcTestResult result =
        mvc.get().uri("/v1/usage").header(AUTHORIZATION, authorization(organizationId)).exchange();

    assertThat(result)
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.month")
        .isEqualTo(YearMonth.now(KST).toString());
    assertThat(readBody(result).billableMetricUsages().getFirst().customers().getFirst().quantity())
        .isEqualByComparingTo("500");
  }

  @Test
  void month_형식이_틀리면_400이다() {
    UUID organizationId = organizationWithTokenBillableMetric();

    assertThat(get(organizationId, "2026-13")).hasStatus(400);
    assertThat(get(organizationId, "2026")).hasStatus(400);
    assertThat(get(organizationId, "august")).hasStatus(400);
  }

  @Test
  void 인증이_없으면_401이다() {
    assertThat(mvc.get().uri("/v1/usage").exchange()).hasStatus(401);
  }

  // ---------------------------------------------------------------------------
  // 헬퍼
  // ---------------------------------------------------------------------------

  private String authorization(UUID organizationId) {
    return authorizationsByOrganizationId.computeIfAbsent(
        organizationId, id -> TestCredentials.issueApiKey(jdbcTemplate, id));
  }

  private MvcTestResult get(UUID organizationId, String month) {
    return mvc.get()
        .uri("/v1/usage?month={month}", month)
        .header(AUTHORIZATION, authorization(organizationId))
        .exchange();
  }

  private MvcTestResult getDraftInvoice(UUID organizationId, String month) {
    return mvc.get()
        .uri("/v1/invoices/draft?month={month}", month)
        .header(AUTHORIZATION, authorization(organizationId))
        .exchange();
  }

  private ListBillableMetricUsagesResponse usageOf(UUID organizationId, String month) {
    MvcTestResult result = get(organizationId, month);
    assertThat(result).hasStatusOk();
    return readBody(result);
  }

  private ListBillableMetricUsagesResponse readBody(MvcTestResult result) {
    try {
      return jsonMapper.readValue(
          result.getResponse().getContentAsString(StandardCharsets.UTF_8),
          ListBillableMetricUsagesResponse.class);
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException("응답 본문을 읽지 못했다", e);
    }
  }

  private BigDecimal quantityOf(UUID organizationId, String month, UUID customerId) {
    return customerOf(organizationId, month, customerId).quantity();
  }

  private List<BillableMetricUsageDimensionUsage> dimensionUsagesOf(
      UUID organizationId, String month, UUID customerId) {
    return customerOf(organizationId, month, customerId).dimensionUsages();
  }

  private BillableMetricUsageCustomer customerOf(
      UUID organizationId, String month, UUID customerId) {
    return usageOf(organizationId, month).billableMetricUsages().stream()
        .flatMap(billableMetricUsage -> billableMetricUsage.customers().stream())
        .filter(customer -> customer.customerId().equals(customerId))
        .findFirst()
        .orElseThrow(() -> new AssertionError("고객 %s가 응답에 없다".formatted(customerId)));
  }

  /** 이번 달 1일 자정(KST)을 낸다. */
  private static OffsetDateTime withinThisMonth() {
    return YearMonth.now(KST).atDay(1).atStartOfDay(KST).toOffsetDateTime();
  }

  /** chat_completion의 token을 SUM하는 미터를 가진 도입사를 만든다. */
  private UUID organizationWithTokenBillableMetric() {
    UUID organizationId = insertOrganization();
    jdbcTemplate.update(
        """
        INSERT INTO billable_metric
          (organization_id, code, name, event_type, aggregation, target_property)
        VALUES (?, 'token-usage', '토큰 사용량', 'chat_completion', 'sum', 'token')
        """,
        organizationId);
    return organizationId;
  }

  private void insertPriceRate(UUID organizationId, String unitPrice) {
    jdbcTemplate.update(
        """
        INSERT INTO price_rate (organization_id, billable_metric_code, dimension_values, unit_price)
        VALUES (?, 'token-usage', '{}', ?::numeric)
        """,
        organizationId,
        unitPrice);
  }

  private void setDimensionProperties(UUID organizationId, String dimensionProperties) {
    jdbcTemplate.update(
        "UPDATE billable_metric SET dimension_properties = ?::text[] WHERE organization_id = ?",
        dimensionProperties,
        organizationId);
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
      UUID organizationId, String transactionId, UUID customerId, int token, String occurredAt) {
    insertEvent(
        organizationId,
        transactionId,
        customerId,
        "chat_completion",
        "{\"model\":\"opus-5\",\"token\":%d}".formatted(token),
        OffsetDateTime.parse(occurredAt));
  }

  private void insertEvent(
      UUID organizationId,
      String transactionId,
      UUID customerId,
      String eventType,
      String propertiesJson,
      String occurredAt) {
    insertEvent(
        organizationId,
        transactionId,
        customerId,
        eventType,
        propertiesJson,
        OffsetDateTime.parse(occurredAt));
  }

  private void insertEvent(
      UUID organizationId,
      String transactionId,
      UUID customerId,
      String eventType,
      String propertiesJson,
      OffsetDateTime occurredAt) {
    jdbcTemplate.update(
        """
        INSERT INTO event
          (organization_id, transaction_id, customer_id, type, properties, occurred_at)
        VALUES (?, ?, ?, ?, ?::jsonb, ?)
        """,
        organizationId,
        transactionId,
        customerId,
        eventType,
        propertiesJson,
        occurredAt);
  }
}
