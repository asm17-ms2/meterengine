package com.meterengine.metric;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.TestcontainersConfiguration;
import com.meterengine.metric.dto.ListBillableMetricUsagesResponse;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
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
  @Autowired private JdbcTemplate jdbc;
  @Autowired private JsonMapper jsonMapper;

  private MockMvcTester mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcTester.from(webApplicationContext);
  }

  // ---------------------------------------------------------------------------
  // 월 경계
  // ---------------------------------------------------------------------------

  @Test
  void 팔월_마지막_순간의_이벤트는_팔월에_귀속된다() {
    UUID orgId = organizationWithTokenBillableMetric();
    UUID customerId = insertCustomer(orgId, "아크메");
    insertEvent(orgId, "tx-1", customerId, 500, "2026-08-31T23:59:59+09:00");

    assertThat(quantityOf(orgId, AUGUST, customerId)).isEqualByComparingTo("500");
    assertThat(quantityOf(orgId, SEPTEMBER, customerId)).isEqualByComparingTo("0");
  }

  @Test
  void 구월_첫_순간의_이벤트는_구월에_귀속된다() {
    UUID orgId = organizationWithTokenBillableMetric();
    UUID customerId = insertCustomer(orgId, "아크메");
    insertEvent(orgId, "tx-1", customerId, 500, "2026-09-01T00:00:00+09:00");

    assertThat(quantityOf(orgId, AUGUST, customerId)).isEqualByComparingTo("0");
    assertThat(quantityOf(orgId, SEPTEMBER, customerId)).isEqualByComparingTo("500");
  }

  @Test
  void 칠월_마지막_순간의_이벤트는_팔월에_들어오지_않는다() {
    UUID orgId = organizationWithTokenBillableMetric();
    UUID customerId = insertCustomer(orgId, "아크메");
    insertEvent(orgId, "tx-1", customerId, 500, "2026-07-31T23:59:59+09:00");

    assertThat(quantityOf(orgId, AUGUST, customerId)).isEqualByComparingTo("0");
  }

  @Test
  void 같은_순간을_UTC로_보낸_이벤트도_같은_달에_귀속된다() {
    UUID orgId = organizationWithTokenBillableMetric();
    UUID customerId = insertCustomer(orgId, "아크메");
    insertEvent(orgId, "tx-1", customerId, 500, "2026-08-31T14:59:59Z");
    insertEvent(orgId, "tx-2", customerId, 700, "2026-08-31T15:00:00Z");

    assertThat(quantityOf(orgId, AUGUST, customerId)).isEqualByComparingTo("500");
    assertThat(quantityOf(orgId, SEPTEMBER, customerId)).isEqualByComparingTo("700");
  }

  // ---------------------------------------------------------------------------
  // 집계 규칙
  // ---------------------------------------------------------------------------

  @Test
  void 합은_고객별로_나뉜다() {
    UUID orgId = organizationWithTokenBillableMetric();
    UUID acme = insertCustomer(orgId, "아크메");
    UUID beta = insertCustomer(orgId, "베타");
    insertEvent(orgId, "tx-1", acme, 500, "2026-08-10T12:00:00+09:00");
    insertEvent(orgId, "tx-2", acme, 700, "2026-08-11T12:00:00+09:00");
    insertEvent(orgId, "tx-3", beta, 300, "2026-08-12T12:00:00+09:00");

    assertThat(quantityOf(orgId, AUGUST, acme)).isEqualByComparingTo("1200");
    assertThat(quantityOf(orgId, AUGUST, beta)).isEqualByComparingTo("300");
  }

  @Test
  void 이벤트가_없는_고객도_사용량_0으로_응답에_들어간다() {
    UUID orgId = organizationWithTokenBillableMetric();
    UUID acme = insertCustomer(orgId, "아크메");
    UUID beta = insertCustomer(orgId, "베타");
    insertEvent(orgId, "tx-1", acme, 500, "2026-08-10T12:00:00+09:00");

    ListBillableMetricUsagesResponse response = usageOf(orgId, AUGUST);

    assertThat(response.billableMetricUsages().getFirst().customers())
        .extracting(customer -> customer.customerId())
        .containsExactlyInAnyOrder(acme, beta);
    assertThat(quantityOf(orgId, AUGUST, beta)).isEqualByComparingTo("0");
  }

  @Test
  void 다른_도입사의_이벤트는_섞이지_않는다() {
    UUID orgId = organizationWithTokenBillableMetric();
    UUID acme = insertCustomer(orgId, "아크메");
    insertEvent(orgId, "tx-1", acme, 500, "2026-08-10T12:00:00+09:00");

    UUID otherOrgId = organizationWithTokenBillableMetric();
    UUID otherCustomer = insertCustomer(otherOrgId, "남의 고객");
    insertEvent(otherOrgId, "tx-1", otherCustomer, 999999, "2026-08-10T12:00:00+09:00");

    assertThat(quantityOf(orgId, AUGUST, acme)).isEqualByComparingTo("500");
    assertThat(usageOf(orgId, AUGUST).billableMetricUsages().getFirst().customers()).hasSize(1);
  }

  @Test
  void 미터의_event_type과_다른_이벤트는_합에서_빠진다() {
    UUID orgId = organizationWithTokenBillableMetric();
    UUID acme = insertCustomer(orgId, "아크메");
    insertEvent(orgId, "tx-1", acme, 500, "2026-08-10T12:00:00+09:00");
    insertEvent(
        orgId, "tx-2", acme, "embedding", "{\"token\":999999}", "2026-08-10T12:00:00+09:00");

    assertThat(quantityOf(orgId, AUGUST, acme)).isEqualByComparingTo("500");
  }

  @Test
  void token이_숫자가_아니거나_없는_이벤트는_합에서_빠진다() {
    UUID orgId = organizationWithTokenBillableMetric();
    UUID acme = insertCustomer(orgId, "아크메");
    insertEvent(orgId, "tx-1", acme, 500, "2026-08-10T12:00:00+09:00");
    insertEvent(
        orgId, "tx-2", acme, "chat_completion", "{\"token\":\"많이\"}", "2026-08-10T12:00:00+09:00");
    insertEvent(
        orgId,
        "tx-3",
        acme,
        "chat_completion",
        "{\"model\":\"opus-5\"}",
        "2026-08-10T12:00:00+09:00");
    insertEvent(
        orgId, "tx-4", acme, "chat_completion", "{\"token\":null}", "2026-08-10T12:00:00+09:00");

    assertThat(quantityOf(orgId, AUGUST, acme)).isEqualByComparingTo("500");
  }

  @Test
  void 소수_사용량은_자릿수가_잘리지_않고_합산된다() {
    UUID orgId = organizationWithTokenBillableMetric();
    UUID acme = insertCustomer(orgId, "아크메");
    insertEvent(
        orgId,
        "tx-1",
        acme,
        "chat_completion",
        "{\"token\":0.1234567890123456789}",
        "2026-08-10T12:00:00+09:00");
    insertEvent(
        orgId,
        "tx-2",
        acme,
        "chat_completion",
        "{\"token\":0.0000000000000000001}",
        "2026-08-10T12:00:00+09:00");

    assertThat(quantityOf(orgId, AUGUST, acme)).isEqualByComparingTo("0.1234567890123456790");
  }

  @Test
  void 미터가_없는_도입사는_billable_metric_usages가_빈_배열이다() {
    UUID orgId = insertOrganization();
    insertCustomer(orgId, "아크메");

    assertThat(usageOf(orgId, AUGUST).billableMetricUsages()).isEmpty();
  }

  @Test
  void 응답에_미터_정보와_고객_이름이_함께_나온다() {
    UUID orgId = organizationWithTokenBillableMetric();
    UUID acme = insertCustomer(orgId, "아크메");
    insertEvent(orgId, "tx-1", acme, 500, "2026-08-10T12:00:00+09:00");

    MvcTestResult result = get(orgId, AUGUST);

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
        .isEqualTo("SUM");
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
    UUID orgId = organizationWithTokenBillableMetric();
    UUID acme = insertCustomer(orgId, "아크메");
    insertEvent(orgId, "tx-1", acme, "chat_completion", "{\"token\":500}", 지금이_월말이어도_이번_달_안인_시각());

    MvcTestResult result =
        mvc.get().uri("/v1/usage").header("X-Organization-Id", orgId.toString()).exchange();

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
    UUID orgId = organizationWithTokenBillableMetric();

    assertThat(get(orgId, "2026-13")).hasStatus(400);
    assertThat(get(orgId, "2026")).hasStatus(400);
    assertThat(get(orgId, "august")).hasStatus(400);
  }

  @Test
  void 도입사_헤더가_없거나_형식이_틀리면_400이다() {
    assertThat(mvc.get().uri("/v1/usage").exchange()).hasStatus(400);
    assertThat(mvc.get().uri("/v1/usage").header("X-Organization-Id", "not-a-uuid").exchange())
        .hasStatus(400);
  }

  @Test
  void 등록되지_않은_도입사로_조회하면_빈_결과다() {
    assertThat(usageOf(UUID.randomUUID(), AUGUST).billableMetricUsages()).isEmpty();
  }

  // ---------------------------------------------------------------------------
  // 헬퍼
  // ---------------------------------------------------------------------------

  private MvcTestResult get(UUID organizationId, String month) {
    return mvc.get()
        .uri("/v1/usage?month={month}", month)
        .header("X-Organization-Id", organizationId.toString())
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
    return usageOf(organizationId, month).billableMetricUsages().stream()
        .flatMap(billableMetricUsage -> billableMetricUsage.customers().stream())
        .filter(customer -> customer.customerId().equals(customerId))
        .findFirst()
        .orElseThrow(() -> new AssertionError("고객 %s가 응답에 없다".formatted(customerId)))
        .quantity();
  }

  private static OffsetDateTime 지금이_월말이어도_이번_달_안인_시각() {
    return YearMonth.now(KST).atDay(1).atTime(12, 0).atZone(KST).toOffsetDateTime();
  }

  /** chat_completion의 token을 SUM하는 미터를 가진 도입사를 만든다. */
  private UUID organizationWithTokenBillableMetric() {
    UUID organizationId = insertOrganization();
    jdbc.update(
        """
        INSERT INTO billable_metric
          (organization_id, code, name, event_type, aggregation, target_property)
        VALUES (?, 'token-usage', '토큰 사용량', 'chat_completion', 'SUM', 'token')
        """,
        organizationId);
    return organizationId;
  }

  private UUID insertOrganization() {
    return jdbc.queryForObject(
        "INSERT INTO organization (name) VALUES ('도입사') RETURNING id", UUID.class);
  }

  private UUID insertCustomer(UUID organizationId, String name) {
    return jdbc.queryForObject(
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
    jdbc.update(
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
