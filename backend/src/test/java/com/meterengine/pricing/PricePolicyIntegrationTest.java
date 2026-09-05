package com.meterengine.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.ErrorCodes;
import com.meterengine.TestcontainersConfiguration;
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

/**
 * 가격 정책 등록과 목록 조회를 HTTP 계층부터 DB까지 관통해 검증한다.
 *
 * <p>MockMvc를 직접 구성하는 이유는 {@code EventIngestIntegrationTest} 참조. 컨텍스트를 공유해 Postgres 컨테이너가 한 번만 뜬다.
 */
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

    assertThat(policyCount(orgId, "token-usage")).isEqualTo(1);
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
    // 저장된 선언은 조합별 단가(MS2-177)의 키 집합 검증 기준이 되므로 DB에 실린 값까지 본다.
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
        .isEqualTo(ErrorCodes.VALIDATION_ERROR);
    assertThat(policyCount(orgId, "token-usage")).isZero();
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
        .isEqualTo(ErrorCodes.METRIC_NOT_FOUND);
  }

  @Test
  void 다른_도입사의_미터에는_등록할_수_없다() {
    UUID otherOrgId = insertOrganization();
    insertBillableMetric(otherOrgId, "token-usage");
    UUID orgId = insertOrganization();

    assertThat(post(orgId, "token-usage", dimensionlessBody())).hasStatus(404);
    assertThat(policyCount(otherOrgId, "token-usage")).isZero();
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
        .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCodes.PRICE_POLICY_ALREADY_EXISTS);

    // 덮어쓰지 않는다. 단가 해석의 기준인 축 선언이 등록 요청으로 조용히 바뀌면 안 된다.
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

  // --- 목록 조회 ---

  @Test
  void 미터가_없는_도입사를_조회하면_200이고_빈_배열이다() {
    UUID orgId = insertOrganization();

    assertThat(get(orgId))
        .hasStatus(200)
        .bodyJson()
        .extractingPath("$.price_policies")
        .asArray()
        .isEmpty();
  }

  @Test
  void 정책이_없는_미터는_dimension_properties가_JSON_null로_실린다() {
    UUID orgId = insertOrganization();
    insertBillableMetric(orgId, "token-usage");

    MvcTestResult result = get(orgId);

    assertThat(result)
        .hasStatus(200)
        .bodyJson()
        .extractingPath("$.price_policies[0].billable_metric_code")
        .isEqualTo("token-usage");
    assertThat(result)
        .bodyJson()
        .extractingPath("$.price_policies[0]")
        .asMap()
        .containsEntry("dimension_properties", null);
  }

  @Test
  void 등록한_정책이_목록에_그대로_실린다() {
    UUID orgId = insertOrganization();
    insertBillableMetric(orgId, "token-usage");
    assertThat(post(orgId, "token-usage", "{\"dimension_properties\": [\"model\"]}"))
        .hasStatus(201);

    MvcTestResult result = get(orgId);

    assertThat(result)
        .hasStatus(200)
        .bodyJson()
        .extractingPath("$.price_policies[0].dimension_properties")
        .isEqualTo(java.util.List.of("model"));
  }

  @Test
  void 무차원_정책은_dimension_properties가_빈_배열이다() {
    UUID orgId = insertOrganization();
    insertBillableMetric(orgId, "token-usage");
    assertThat(post(orgId, "token-usage", dimensionlessBody())).hasStatus(201);

    assertThat(get(orgId))
        .hasStatus(200)
        .bodyJson()
        .extractingPath("$.price_policies[0].dimension_properties")
        .asArray()
        .isEmpty();
  }

  @Test
  void 목록은_미터_code_오름차순이다() {
    UUID orgId = insertOrganization();
    insertBillableMetric(orgId, "token-usage");
    insertBillableMetric(orgId, "input-tokens");

    assertThat(get(orgId))
        .hasStatus(200)
        .bodyJson()
        .extractingPath("$.price_policies[*].billable_metric_code")
        .isEqualTo(java.util.List.of("input-tokens", "token-usage"));
  }

  @Test
  void 다른_도입사의_미터와_정책은_조회되지_않는다() {
    UUID otherOrgId = insertOrganization();
    insertBillableMetric(otherOrgId, "token-usage");
    assertThat(post(otherOrgId, "token-usage", dimensionlessBody())).hasStatus(201);
    UUID orgId = insertOrganization();

    assertThat(get(orgId))
        .hasStatus(200)
        .bodyJson()
        .extractingPath("$.price_policies")
        .asArray()
        .isEmpty();
  }

  @Test
  void 조회에_도입사_헤더가_없으면_400이고_validation_error다() {
    assertThat(mvc.get().uri("/v1/price-policies").exchange())
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCodes.VALIDATION_ERROR);
  }

  @Test
  void 단가가_없는_정책은_unit_price가_JSON_null이고_0이_아니다() {
    UUID orgId = insertOrganization();
    insertBillableMetric(orgId, "token-usage");
    assertThat(post(orgId, "token-usage", dimensionlessBody())).hasStatus(201);

    MvcTestResult result = get(orgId);

    assertThat(result)
        .hasStatus(200)
        .bodyJson()
        .extractingPath("$.price_policies[0]")
        .asMap()
        .containsEntry("unit_price", null);
  }

  @Test
  void 기본_단가가_있으면_unit_price에_실린다() {
    UUID orgId = insertOrganization();
    insertBillableMetric(orgId, "token-usage");
    assertThat(post(orgId, "token-usage", dimensionlessBody())).hasStatus(201);
    insertBaseRate(orgId, "token-usage", "0.007");

    assertThat(get(orgId))
        .hasStatus(200)
        .bodyJson()
        .extractingPath("$.price_policies[0].unit_price")
        .asNumber()
        .extracting(Number::doubleValue)
        .isEqualTo(0.007);
  }

  @Test
  void 단가가_0이면_null이_아니라_0으로_실린다() {
    UUID orgId = insertOrganization();
    insertBillableMetric(orgId, "token-usage");
    assertThat(post(orgId, "token-usage", dimensionlessBody())).hasStatus(201);
    insertBaseRate(orgId, "token-usage", "0");

    assertThat(get(orgId))
        .hasStatus(200)
        .bodyJson()
        .extractingPath("$.price_policies[0].unit_price")
        .asNumber()
        .extracting(Number::doubleValue)
        .isEqualTo(0.0);
  }

  // --- 헬퍼 ---

  private void insertBaseRate(UUID orgId, String billableMetricCode, String unitPrice) {
    jdbc.update(
        """
        INSERT INTO price_rate (organization_id, billable_metric_code, dimension_values, unit_price)
        VALUES (?, ?, '{}'::jsonb, ?::numeric)
        """,
        orgId,
        billableMetricCode,
        unitPrice);
  }

  private MvcTestResult get(UUID organizationId) {
    return mvc.get()
        .uri("/v1/price-policies")
        .header("X-Organization-Id", organizationId.toString())
        .exchange();
  }

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
        .isEqualTo(ErrorCodes.INVALID_PRICE_POLICY);
    assertThat(policyCount(organizationId, "token-usage")).isZero();
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

  private Integer policyCount(UUID orgId, String billableMetricCode) {
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
