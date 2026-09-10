package com.meterengine.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.TestcontainersConfiguration;
import com.meterengine.global.error.ErrorCode;
import java.util.List;
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
class BillableMetricPriceIntegrationTest {

  private static final String DIMENSIONLESS = "{}";

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbcTemplate;

  private MockMvcTester mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcTester.from(webApplicationContext);
  }

  // --- 목록과 순서 ---

  @Test
  void 미터가_없는_도입사를_조회하면_200이고_빈_배열이다() {
    UUID organizationId = insertOrganization();

    assertThat(get(organizationId))
        .hasStatus(200)
        .bodyJson()
        .extractingPath("$.billable_metric_prices")
        .asArray()
        .isEmpty();
  }

  @Test
  void 목록은_미터_code_오름차순이다() {
    UUID organizationId = insertOrganization();
    insertBillableMetric(organizationId, "token-usage");
    insertBillableMetric(organizationId, "input-tokens");

    assertThat(get(organizationId))
        .hasStatus(200)
        .bodyJson()
        .extractingPath("$.billable_metric_prices[*].billable_metric_code")
        .isEqualTo(List.of("input-tokens", "token-usage"));
  }

  @Test
  void 다른_도입사의_미터와_정책은_조회되지_않는다() {
    UUID otherOrganizationId = insertOrganization();
    insertBillableMetric(otherOrganizationId, "token-usage");
    insertPricePolicy(otherOrganizationId, "token-usage", DIMENSIONLESS);
    UUID organizationId = insertOrganization();

    assertThat(get(organizationId))
        .hasStatus(200)
        .bodyJson()
        .extractingPath("$.billable_metric_prices")
        .asArray()
        .isEmpty();
  }

  // --- 정책 (dimension_properties) ---

  @Test
  void 정책이_없는_미터는_dimension_properties가_JSON_null로_실린다() {
    UUID organizationId = insertOrganization();
    insertBillableMetric(organizationId, "token-usage");

    MvcTestResult result = get(organizationId);

    assertThat(result)
        .hasStatus(200)
        .bodyJson()
        .extractingPath("$.billable_metric_prices[0].billable_metric_code")
        .isEqualTo("token-usage");
    assertThat(result)
        .bodyJson()
        .extractingPath("$.billable_metric_prices[0]")
        .asMap()
        .containsEntry("dimension_properties", null);
  }

  @Test
  void 정책의_선언이_목록에_그대로_실린다() {
    UUID organizationId = insertOrganization();
    insertBillableMetric(organizationId, "token-usage");
    insertPricePolicy(organizationId, "token-usage", "{model}");

    assertThat(get(organizationId))
        .hasStatus(200)
        .bodyJson()
        .extractingPath("$.billable_metric_prices[0].dimension_properties")
        .isEqualTo(List.of("model"));
  }

  @Test
  void 무차원_정책은_dimension_properties가_빈_배열이다() {
    UUID organizationId = insertOrganization();
    insertBillableMetric(organizationId, "token-usage");
    insertPricePolicy(organizationId, "token-usage", DIMENSIONLESS);

    assertThat(get(organizationId))
        .hasStatus(200)
        .bodyJson()
        .extractingPath("$.billable_metric_prices[0].dimension_properties")
        .asArray()
        .isEmpty();
  }

  // --- 기본 단가 (unit_price) ---

  @Test
  void 단가가_없는_정책은_unit_price가_JSON_null이고_0이_아니다() {
    UUID organizationId = insertOrganization();
    insertBillableMetric(organizationId, "token-usage");
    insertPricePolicy(organizationId, "token-usage", DIMENSIONLESS);

    assertThat(get(organizationId))
        .hasStatus(200)
        .bodyJson()
        .extractingPath("$.billable_metric_prices[0]")
        .asMap()
        .containsEntry("unit_price", null);
  }

  @Test
  void 기본_단가가_있으면_unit_price에_실린다() {
    UUID organizationId = insertOrganization();
    insertBillableMetric(organizationId, "token-usage");
    insertPricePolicy(organizationId, "token-usage", DIMENSIONLESS);
    insertBasePriceRate(organizationId, "token-usage", "0.007");

    assertThat(get(organizationId))
        .hasStatus(200)
        .bodyJson()
        .extractingPath("$.billable_metric_prices[0].unit_price")
        .asNumber()
        .extracting(Number::doubleValue)
        .isEqualTo(0.007);
  }

  @Test
  void 단가가_0이면_null이_아니라_0으로_실린다() {
    UUID organizationId = insertOrganization();
    insertBillableMetric(organizationId, "token-usage");
    insertPricePolicy(organizationId, "token-usage", DIMENSIONLESS);
    insertBasePriceRate(organizationId, "token-usage", "0");

    assertThat(get(organizationId))
        .hasStatus(200)
        .bodyJson()
        .extractingPath("$.billable_metric_prices[0].unit_price")
        .asNumber()
        .extracting(Number::doubleValue)
        .isEqualTo(0.0);
  }

  // --- 공통 ---

  @Test
  void 도입사_헤더가_없으면_400이고_validation_error다() {
    assertThat(mvc.get().uri("/v1/billable-metric-prices").exchange())
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
  }

  // --- 헬퍼 ---

  private MvcTestResult get(UUID organizationId) {
    return mvc.get()
        .uri("/v1/billable-metric-prices")
        .header("X-Organization-Id", organizationId.toString())
        .exchange();
  }

  private UUID insertOrganization() {
    return jdbcTemplate.queryForObject(
        "INSERT INTO organization (name) VALUES ('테스트 도입사') RETURNING id", UUID.class);
  }

  private void insertBillableMetric(UUID organizationId, String code) {
    jdbcTemplate.update(
        """
        INSERT INTO billable_metric
          (organization_id, code, name, event_type, aggregation, target_property)
        VALUES (?, ?, '토큰 사용량', 'chat_completion', 'SUM', 'token')
        """,
        organizationId,
        code);
  }

  private void insertPricePolicy(
      UUID organizationId, String billableMetricCode, String dimensionProperties) {
    jdbcTemplate.update(
        """
        INSERT INTO price_policy (organization_id, billable_metric_code, dimension_properties)
        VALUES (?, ?, ?::text[])
        """,
        organizationId,
        billableMetricCode,
        dimensionProperties);
  }

  private void insertBasePriceRate(
      UUID organizationId, String billableMetricCode, String unitPrice) {
    jdbcTemplate.update(
        """
        INSERT INTO price_rate (organization_id, billable_metric_code, dimension_values, unit_price)
        VALUES (?, ?, '{}'::jsonb, ?::numeric)
        """,
        organizationId,
        billableMetricCode,
        unitPrice);
  }
}
