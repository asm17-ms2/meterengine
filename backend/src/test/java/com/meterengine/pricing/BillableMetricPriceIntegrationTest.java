package com.meterengine.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.global.error.ErrorCode;
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

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class BillableMetricPriceIntegrationTest {

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbcTemplate;

  private final Map<UUID, String> authorizationsByOrganizationId = new HashMap<>();

  private MockMvcTester mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcTester.from(
            webApplicationContext, builder -> builder.apply(springSecurity()).build());
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
  void 다른_도입사의_미터는_조회되지_않는다() {
    UUID otherOrganizationId = insertOrganization();
    insertBillableMetric(otherOrganizationId, "token-usage");
    UUID organizationId = insertOrganization();

    assertThat(get(organizationId))
        .hasStatus(200)
        .bodyJson()
        .extractingPath("$.billable_metric_prices")
        .asArray()
        .isEmpty();
  }

  // --- dimension_properties ---

  @Test
  void 미터의_dimension_properties가_목록에_그대로_실린다() {
    UUID organizationId = insertOrganization();
    insertBillableMetric(organizationId, "token-usage");
    updateDimensionProperties(organizationId, "token-usage", "{model}");

    assertThat(get(organizationId))
        .hasStatus(200)
        .bodyJson()
        .extractingPath("$.billable_metric_prices[0].dimension_properties")
        .isEqualTo(List.of("model"));
  }

  @Test
  void 키를_정하지_않은_미터는_dimension_properties가_빈_배열이다() {
    UUID organizationId = insertOrganization();
    insertBillableMetric(organizationId, "token-usage");

    assertThat(get(organizationId))
        .hasStatus(200)
        .bodyJson()
        .extractingPath("$.billable_metric_prices[0].dimension_properties")
        .asArray()
        .isEmpty();
  }

  // --- 조합별 단가 (price_rates) ---

  @Test
  void 단가가_없는_미터는_price_rates가_빈_배열이다() {
    UUID organizationId = insertOrganization();
    insertBillableMetric(organizationId, "token-usage");

    assertThat(get(organizationId))
        .hasStatus(200)
        .bodyJson()
        .extractingPath("$.billable_metric_prices[0].price_rates")
        .asArray()
        .isEmpty();
  }

  @Test
  void 조합별_단가가_dimension_values_오름차순으로_실린다() {
    UUID organizationId = insertOrganization();
    insertBillableMetric(organizationId, "token-usage");
    insertPriceRate(organizationId, "token-usage", "{}", "0.007");
    insertPriceRate(organizationId, "token-usage", "{\"model\": \"opus\"}", "0");

    MvcTestResult result = get(organizationId);

    assertThat(result)
        .hasStatus(200)
        .bodyJson()
        .extractingPath("$.billable_metric_prices[0].price_rates[*].dimension_values")
        .isEqualTo(List.of(Map.of(), Map.of("model", "opus")));
    assertThat(result)
        .bodyJson()
        .extractingPath("$.billable_metric_prices[0].price_rates[*].unit_price")
        .asArray()
        .extracting(unitPrice -> ((Number) unitPrice).doubleValue())
        .containsExactly(0.007, 0.0);
  }

  // --- 공통 ---

  @Test
  void 인증이_없으면_401이고_authentication_required다() {
    assertThat(mvc.get().uri("/v1/billable-metric-prices").exchange())
        .hasStatus(401)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED.getCode());
  }

  // --- 헬퍼 ---

  private String authorization(UUID organizationId) {
    return authorizationsByOrganizationId.computeIfAbsent(
        organizationId, id -> TestCredentials.issueApiKey(jdbcTemplate, id));
  }

  private MvcTestResult get(UUID organizationId) {
    return mvc.get()
        .uri("/v1/billable-metric-prices")
        .header(AUTHORIZATION, authorization(organizationId))
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
        VALUES (?, ?, '토큰 사용량', 'chat_completion', 'sum', ?)
        """,
        organizationId,
        code,
        code);
  }

  private void updateDimensionProperties(
      UUID organizationId, String billableMetricCode, String dimensionProperties) {
    jdbcTemplate.update(
        "UPDATE billable_metric SET dimension_properties = ?::text[]"
            + " WHERE organization_id = ? AND code = ?",
        dimensionProperties,
        organizationId,
        billableMetricCode);
  }

  private void insertPriceRate(
      UUID organizationId, String billableMetricCode, String dimensionValues, String unitPrice) {
    jdbcTemplate.update(
        """
        INSERT INTO price_rate (organization_id, billable_metric_code, dimension_values, unit_price)
        VALUES (?, ?, ?::jsonb, ?::numeric)
        """,
        organizationId,
        billableMetricCode,
        dimensionValues,
        unitPrice);
  }
}
