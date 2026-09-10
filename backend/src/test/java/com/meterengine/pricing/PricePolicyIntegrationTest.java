package com.meterengine.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.TestcontainersConfiguration;
import com.meterengine.global.error.ErrorCode;
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
class PricePolicyIntegrationTest {

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbc;

  private MockMvcTester mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcTester.from(webApplicationContext);
  }

  // --- 등록 성공 ---

  @Test
  void 무차원_정책을_등록하면_201이고_빈_선언이_저장된다() {
    UUID orgId = insertOrganization();
    insertBillableMetric(orgId, "token-usage");

    MvcTestResult result = post(orgId, "token-usage", "{\"dimension_properties\": []}");

    assertThat(result)
        .hasStatus(201)
        .bodyJson()
        .extractingPath("$.billable_metric_code")
        .isEqualTo("token-usage");
    assertThat(result).bodyJson().extractingPath("$.dimension_properties").asArray().isEmpty();

    assertThat(pricePolicyCount(orgId, "token-usage")).isEqualTo(1);
    assertThat(storedProperties(orgId, "token-usage")).isEqualTo("");
  }

  @Test
  void 다차원_선언을_등록하면_선언이_순서대로_저장된다() {
    UUID orgId = insertOrganization();
    insertBillableMetric(orgId, "token-usage");

    MvcTestResult result =
        post(orgId, "token-usage", "{\"dimension_properties\": [\"model\", \"region\"]}");

    assertThat(result)
        .hasStatus(201)
        .bodyJson()
        .extractingPath("$.dimension_properties")
        .isEqualTo(java.util.List.of("model", "region"));
    assertThat(storedProperties(orgId, "token-usage")).isEqualTo("model,region");
  }

  // --- 도메인 검증 (400 invalid_price_policy) ---

  @Test
  void 선언에_중복_키나_빈_키가_있으면_400이고_저장은_0건이다() {
    UUID orgId = insertOrganization();
    insertBillableMetric(orgId, "token-usage");

    assertInvalid(orgId, "{\"dimension_properties\": [\"model\", \"model\"]}");
    assertInvalid(orgId, "{\"dimension_properties\": [\" \"]}");
  }

  // --- 형식 검증 (400 validation_error) ---

  @Test
  void 선언이_없으면_400이고_저장은_0건이다() {
    UUID orgId = insertOrganization();
    insertBillableMetric(orgId, "token-usage");

    assertThat(post(orgId, "token-usage", "{}"))
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
    assertThat(pricePolicyCount(orgId, "token-usage")).isZero();
  }

  // --- 미터와 테넌트 (404) ---

  @Test
  void 없는_미터에_등록하면_404다() {
    UUID orgId = insertOrganization();

    assertThat(post(orgId, "no-such-metric", dimensionlessBody()))
        .hasStatus(404)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCode.BILLABLE_METRIC_NOT_FOUND.getCode());
  }

  @Test
  void 다른_도입사의_미터에는_등록할_수_없다() {
    UUID otherOrgId = insertOrganization();
    insertBillableMetric(otherOrgId, "token-usage");
    UUID orgId = insertOrganization();

    assertThat(post(orgId, "token-usage", dimensionlessBody())).hasStatus(404);
    assertThat(pricePolicyCount(otherOrgId, "token-usage")).isZero();
  }

  // --- 중복 (409) ---

  @Test
  void 정책이_이미_있는_미터에_다시_등록하면_409이고_기존_정책은_그대로다() {
    UUID orgId = insertOrganization();
    insertBillableMetric(orgId, "token-usage");
    assertThat(post(orgId, "token-usage", "{\"dimension_properties\": [\"model\"]}"))
        .hasStatus(201);

    assertThat(post(orgId, "token-usage", "{\"dimension_properties\": [\"region\"]}"))
        .hasStatus(409)
        .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCode.PRICE_POLICY_ALREADY_EXISTS.getCode());

    assertThat(storedProperties(orgId, "token-usage")).isEqualTo("model");
  }

  // --- 공통 ---

  @Test
  void 도입사_헤더가_없으면_400이다() {
    assertThat(
            mvc.post()
                .uri("/v1/billable-metrics/token-usage/price-policy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(dimensionlessBody())
                .exchange())
        .hasStatus(400);
  }

  // --- 헬퍼 ---

  private MvcTestResult post(UUID organizationId, String billableMetricCode, String jsonBody) {
    return mvc.post()
        .uri("/v1/billable-metrics/%s/price-policy".formatted(billableMetricCode))
        .header("X-Organization-Id", organizationId.toString())
        .contentType(MediaType.APPLICATION_JSON)
        .content(jsonBody)
        .exchange();
  }

  private void assertInvalid(UUID organizationId, String jsonBody) {
    assertThat(post(organizationId, "token-usage", jsonBody))
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCode.INVALID_PRICE_POLICY.getCode());
    assertThat(pricePolicyCount(organizationId, "token-usage")).isZero();
  }

  private String dimensionlessBody() {
    return "{\"dimension_properties\": []}";
  }

  private UUID insertOrganization() {
    return jdbc.queryForObject(
        "INSERT INTO organization (name) VALUES ('테스트 도입사') RETURNING id", UUID.class);
  }

  private void insertBillableMetric(UUID orgId, String code) {
    jdbc.update(
        """
        INSERT INTO billable_metric
          (organization_id, code, name, event_type, aggregation, target_property)
        VALUES (?, ?, '토큰 사용량', 'chat_completion', 'SUM', 'token')
        """,
        orgId,
        code);
  }

  private Integer pricePolicyCount(UUID orgId, String billableMetricCode) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM price_policy WHERE organization_id = ? AND billable_metric_code = ?",
        Integer.class,
        orgId,
        billableMetricCode);
  }

  private String storedProperties(UUID orgId, String billableMetricCode) {
    return jdbc.queryForObject(
        """
        SELECT array_to_string(dimension_properties, ',') FROM price_policy
        WHERE organization_id = ? AND billable_metric_code = ?
        """,
        String.class,
        orgId,
        billableMetricCode);
  }
}
