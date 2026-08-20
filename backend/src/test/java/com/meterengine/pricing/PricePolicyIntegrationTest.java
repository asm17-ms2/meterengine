package com.meterengine.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.ErrorCodes;
import com.meterengine.TestcontainersConfiguration;
import java.math.BigDecimal;
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
 * 가격 정책 등록 API를 HTTP 계층부터 DB까지 관통해 검증한다 (MS2-157).
 *
 * <p>MockMvc를 직접 구성하는 이유는 {@code EventIngestIntegrationTest} 참조. 컨텍스트를 공유해 Postgres 컨테이너가 한 번만 뜬다.
 *
 * <p>조회 API가 없으므로(MS2-176 예정) 저장 결과는 DB를 직접 읽어 확인한다.
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
  void 무차원_정책을_등록하면_201이고_정책과_기본_단가가_저장된다() {
    UUID orgId = insertOrganization();
    insertMetric(orgId, "token-usage");

    MvcTestResult result =
        post(
            orgId,
            "token-usage",
            """
            {"dimension_properties": [], "rates": [{"dimension_values": {}, "unit_price": 0.5}]}
            """);

    assertThat(result)
        .hasStatus(201)
        .bodyJson()
        .extractingPath("$.metric_code")
        .isEqualTo("token-usage");
    assertThat(result).bodyJson().extractingPath("$.dimension_properties").asArray().isEmpty();
    assertThat(result).bodyJson().extractingPath("$.rates[0].unit_price").isEqualTo(0.5);

    assertThat(policyCount(orgId, "token-usage")).isEqualTo(1);
    assertThat(rateCount(orgId, "token-usage")).isEqualTo(1);
    assertThat(baseUnitPrice(orgId, "token-usage")).isEqualByComparingTo(new BigDecimal("0.5"));
  }

  @Test
  void 다차원_선언과_조합별_단가를_등록하면_조합_행이_함께_저장된다() {
    UUID orgId = insertOrganization();
    insertMetric(orgId, "token-usage");

    MvcTestResult result =
        post(
            orgId,
            "token-usage",
            """
            {"dimension_properties": ["model"],
             "rates": [{"dimension_values": {}, "unit_price": 0.5},
                       {"dimension_values": {"model": "opus5"}, "unit_price": 2.0}]}
            """);

    assertThat(result).hasStatus(201).bodyJson().extractingPath("$.rates.length()").isEqualTo(2);

    assertThat(rateCount(orgId, "token-usage")).isEqualTo(2);
    // 조합 행이 jsonb로 정규화되어 저장됐는지까지 본다. 저장된 조합으로 단가를 되찾을 수 있어야 다차원 계산(MS2-178)이 읽는다.
    assertThat(
            jdbc.queryForObject(
                """
                SELECT unit_price FROM price_rate
                WHERE organization_id = ? AND metric_code = ?
                  AND dimension_values = '{"model": "opus5"}'::jsonb
                """,
                BigDecimal.class,
                orgId,
                "token-usage"))
        .isEqualByComparingTo(new BigDecimal("2.0"));
  }

  @Test
  void 등록한_기본_단가로_청구_예정액이_계산된다() {
    UUID orgId = insertOrganization();
    insertMetric(orgId, "token-usage");
    post(
        orgId,
        "token-usage",
        """
        {"dimension_properties": [], "rates": [{"dimension_values": {}, "unit_price": 0.5}]}
        """);

    // 이 API의 존재 이유다. 시드를 고치지 않고 등록한 단가가 청구 파이프라인에 실제로 붙는지 본다.
    UUID customerId =
        jdbc.queryForObject(
            "INSERT INTO customer (organization_id, name) VALUES (?, '아크메') RETURNING id",
            UUID.class,
            orgId);
    jdbc.update(
        """
        INSERT INTO usage_event
          (organization_id, transaction_id, customer_id, event_type, properties, occurred_at)
        VALUES (?, 'tx-1', ?, 'chat_completion', '{"token": 3000}', '2026-08-10T12:00:00+09:00')
        """,
        orgId,
        customerId);

    assertThat(
            mvc.get()
                .uri("/v1/invoice?month=2026-08")
                .header("X-Organization-Id", orgId.toString())
                .exchange())
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.total_amount")
        .isEqualTo(1500);
  }

  // --- 도메인 검증 (400 invalid_price_policy) ---

  @Test
  void 기본_단가_행이_없으면_400이고_저장은_0건이다() {
    UUID orgId = insertOrganization();
    insertMetric(orgId, "token-usage");

    assertInvalid(
        orgId,
        """
        {"dimension_properties": ["model"],
         "rates": [{"dimension_values": {"model": "opus5"}, "unit_price": 2.0}]}
        """);
  }

  @Test
  void 조합의_키_집합이_선언과_다르면_400이고_저장은_0건이다() {
    UUID orgId = insertOrganization();
    insertMetric(orgId, "token-usage");

    // 선언에 없는 키
    assertInvalid(
        orgId,
        """
        {"dimension_properties": ["model"],
         "rates": [{"dimension_values": {}, "unit_price": 0.5},
                   {"dimension_values": {"region": "kr"}, "unit_price": 2.0}]}
        """);
    // 선언의 일부만 채운 키 (부분 조합은 아직 규약에 없다)
    assertInvalid(
        orgId,
        """
        {"dimension_properties": ["model", "region"],
         "rates": [{"dimension_values": {}, "unit_price": 0.5},
                   {"dimension_values": {"model": "opus5"}, "unit_price": 2.0}]}
        """);
  }

  @Test
  void 같은_조합이_두_번_오면_400이고_저장은_0건이다() {
    UUID orgId = insertOrganization();
    insertMetric(orgId, "token-usage");

    // 키 순서만 다른 같은 조합. DB의 jsonb 정규화에 맡기지 않고 등록 경계가 잡는지 본다.
    assertInvalid(
        orgId,
        """
        {"dimension_properties": ["model", "region"],
         "rates": [{"dimension_values": {}, "unit_price": 0.5},
                   {"dimension_values": {"model": "opus5", "region": "kr"}, "unit_price": 2.0},
                   {"dimension_values": {"region": "kr", "model": "opus5"}, "unit_price": 3.0}]}
        """);
  }

  @Test
  void 선언이나_조합에_빈_키나_값이_있으면_400이고_저장은_0건이다() {
    UUID orgId = insertOrganization();
    insertMetric(orgId, "token-usage");

    assertInvalid(
        orgId,
        """
        {"dimension_properties": [" "],
         "rates": [{"dimension_values": {}, "unit_price": 0.5}]}
        """);
    assertInvalid(
        orgId,
        """
        {"dimension_properties": ["model", "model"],
         "rates": [{"dimension_values": {}, "unit_price": 0.5}]}
        """);
    assertInvalid(
        orgId,
        """
        {"dimension_properties": ["model"],
         "rates": [{"dimension_values": {}, "unit_price": 0.5},
                   {"dimension_values": {"model": " "}, "unit_price": 2.0}]}
        """);
  }

  // --- 형식 검증 (400 validation_error) ---

  @Test
  void rates가_비었거나_단가가_음수면_400이고_저장은_0건이다() {
    UUID orgId = insertOrganization();
    insertMetric(orgId, "token-usage");

    for (String invalid :
        new String[] {
          "{\"dimension_properties\": [], \"rates\": []}",
          "{\"dimension_properties\": [], \"rates\": [{\"dimension_values\": {}, \"unit_price\": -0.5}]}",
          "{\"dimension_properties\": [], \"rates\": [{\"dimension_values\": {}}]}",
          "{\"rates\": [{\"dimension_values\": {}, \"unit_price\": 0.5}]}",
          "{\"dimension_properties\": [], \"rates\": [null]}"
        }) {
      assertThat(post(orgId, "token-usage", invalid))
          .hasStatus(400)
          .bodyJson()
          .extractingPath("$.code")
          .asString()
          .isEqualTo(ErrorCodes.VALIDATION_ERROR);
    }
    assertThat(policyCount(orgId, "token-usage")).isZero();
  }

  // --- 미터와 테넌트 (404) ---

  @Test
  void 없는_미터에_등록하면_404다() {
    UUID orgId = insertOrganization();

    assertThat(post(orgId, "no-such-metric", baseOnlyBody()))
        .hasStatus(404)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCodes.METRIC_NOT_FOUND);
  }

  @Test
  void 다른_도입사의_미터에는_등록할_수_없다() {
    UUID otherOrgId = insertOrganization();
    insertMetric(otherOrgId, "token-usage");
    UUID orgId = insertOrganization();

    assertThat(post(orgId, "token-usage", baseOnlyBody())).hasStatus(404);
    assertThat(policyCount(otherOrgId, "token-usage")).isZero();
  }

  @Test
  void 등록되지_않은_도입사면_404다() {
    // 미등록 도입사의 미터는 존재할 수 없어 미터 확인이 먼저 걸린다. 고객 API의 400 unknown_organization과
    // 다른 결과인 이유는 MetricNotFoundException javadoc에 있다.
    assertThat(post(UUID.randomUUID(), "token-usage", baseOnlyBody()))
        .hasStatus(404)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCodes.METRIC_NOT_FOUND);
  }

  // --- 중복 (409) ---

  @Test
  void 정책이_이미_있는_미터에_다시_등록하면_409이고_기존_정책은_그대로다() {
    UUID orgId = insertOrganization();
    insertMetric(orgId, "token-usage");
    assertThat(post(orgId, "token-usage", baseOnlyBody())).hasStatus(201);

    assertThat(
            post(
                orgId,
                "token-usage",
                """
                {"dimension_properties": [], "rates": [{"dimension_values": {}, "unit_price": 9.9}]}
                """))
        .hasStatus(409)
        .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCodes.PRICE_POLICY_ALREADY_EXISTS);

    // 덮어쓰지 않는다. 청구가 읽는 단가가 등록 요청으로 조용히 바뀌면 안 된다.
    assertThat(baseUnitPrice(orgId, "token-usage")).isEqualByComparingTo(new BigDecimal("0.5"));
  }

  // --- 공통 ---

  @Test
  void 도입사_헤더가_없으면_400이다() {
    assertThat(
            mvc.post()
                .uri("/v1/metrics/token-usage/price-policy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(baseOnlyBody())
                .exchange())
        .hasStatus(400);
  }

  // --- 헬퍼 ---

  private MvcTestResult post(UUID organizationId, String metricCode, String jsonBody) {
    return mvc.post()
        .uri("/v1/metrics/%s/price-policy".formatted(metricCode))
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
    assertThat(rateCount(organizationId, "token-usage")).isZero();
  }

  private String baseOnlyBody() {
    return """
        {"dimension_properties": [], "rates": [{"dimension_values": {}, "unit_price": 0.5}]}
        """;
  }

  private UUID insertOrganization() {
    return jdbc.queryForObject(
        "INSERT INTO organization (name) VALUES ('테스트 도입사') RETURNING id", UUID.class);
  }

  private void insertMetric(UUID orgId, String code) {
    jdbc.update(
        """
        INSERT INTO billable_metric
          (organization_id, code, name, event_type, aggregation, target_property)
        VALUES (?, ?, '토큰 사용량', 'chat_completion', 'SUM', 'token')
        """,
        orgId,
        code);
  }

  private Integer policyCount(UUID orgId, String metricCode) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM price_policy WHERE organization_id = ? AND metric_code = ?",
        Integer.class,
        orgId,
        metricCode);
  }

  private Integer rateCount(UUID orgId, String metricCode) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM price_rate WHERE organization_id = ? AND metric_code = ?",
        Integer.class,
        orgId,
        metricCode);
  }

  private BigDecimal baseUnitPrice(UUID orgId, String metricCode) {
    return jdbc.queryForObject(
        """
        SELECT unit_price FROM price_rate
        WHERE organization_id = ? AND metric_code = ? AND dimension_values = '{}'::jsonb
        """,
        BigDecimal.class,
        orgId,
        metricCode);
  }
}
