package com.meterengine.metric;

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

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class BillableMetricIntegrationTest {

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbc;

  private MockMvcTester mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcTester.from(webApplicationContext);
  }

  @Test
  void 미터를_등록하면_201이고_보낸_값이_그대로_저장된다() {
    UUID orgId = insertOrganization();

    MvcTestResult result = post(orgId, sumBody("token-usage"));

    assertThat(result).hasStatus(201).bodyJson().extractingPath("$.code").isEqualTo("token-usage");
    assertThat(result).bodyJson().extractingPath("$.event_type").isEqualTo("chat_completion");
    assertThat(result).bodyJson().extractingPath("$.target_property").isEqualTo("token");
    assertThat(metricCount(orgId, "token-usage")).isEqualTo(1);
    assertThat(storedAggregation(orgId, "token-usage")).isEqualTo("SUM");
  }

  @Test
  void 같은_코드로_다시_등록하면_409이고_기존_미터는_그대로다() {
    UUID orgId = insertOrganization();
    assertThat(post(orgId, sumBody("token-usage"))).hasStatus(201);

    assertThat(
            post(
                orgId,
                """
                {"code": "token-usage", "name": "다른 이름", "event_type": "other",
                 "aggregation": "SUM", "target_property": "chars"}
                """))
        .hasStatus(409)
        .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCodes.METRIC_ALREADY_EXISTS);

    assertThat(storedName(orgId, "token-usage")).isEqualTo("토큰 사용량");
  }

  @Test
  void 다른_도입사에는_같은_코드를_등록할_수_있다() {
    UUID orgId = insertOrganization();
    UUID otherOrgId = insertOrganization();
    assertThat(post(orgId, sumBody("token-usage"))).hasStatus(201);

    assertThat(post(otherOrgId, sumBody("token-usage"))).hasStatus(201);
  }

  @Test
  void SUM이_아닌_집계_함수는_400이고_저장은_0건이다() {
    UUID orgId = insertOrganization();

    assertThat(
            post(
                orgId,
                """
                {"code": "call-count", "name": "호출 수", "event_type": "chat_completion",
                 "aggregation": "COUNT", "target_property": "calls"}
                """))
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCodes.INVALID_BILLABLE_METRIC);
    assertThat(metricCount(orgId, "call-count")).isZero();
  }

  @Test
  void SUM인데_target_property가_없으면_400이다() {
    UUID orgId = insertOrganization();

    assertThat(
            post(
                orgId,
                """
                {"code": "token-usage", "name": "토큰 사용량", "event_type": "chat_completion",
                 "aggregation": "SUM"}
                """))
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCodes.INVALID_BILLABLE_METRIC);
  }

  @Test
  void 필수_필드가_비면_400_validation_error다() {
    UUID orgId = insertOrganization();

    assertThat(post(orgId, "{\"code\": \"token-usage\"}"))
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCodes.VALIDATION_ERROR);
    assertThat(metricCount(orgId, "token-usage")).isZero();
  }

  @Test
  void 미등록_도입사면_400_unknown_organization이다() {
    assertThat(post(UUID.randomUUID(), sumBody("token-usage")))
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCodes.UNKNOWN_ORGANIZATION);
  }

  @Test
  void 도입사_헤더가_없으면_400이다() {
    assertThat(
            mvc.post()
                .uri("/v1/metrics")
                .contentType(MediaType.APPLICATION_JSON)
                .content(sumBody("token-usage"))
                .exchange())
        .hasStatus(400);
    assertThat(mvc.get().uri("/v1/metrics").exchange()).hasStatus(400);
    assertThat(
            mvc.put()
                .uri("/v1/metrics/token-usage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody("토큰 사용량", "chat_completion", "token"))
                .exchange())
        .hasStatus(400);
  }

  @Test
  void 미터를_고치면_200이고_목록에_반영된다() {
    UUID orgId = insertOrganization();
    assertThat(post(orgId, sumBody("token-usage"))).hasStatus(201);

    MvcTestResult result =
        put(orgId, "token-usage", updateBody("입력 토큰", "llm_request", "input_tokens"));

    assertThat(result).hasStatus(200).bodyJson().extractingPath("$.name").isEqualTo("입력 토큰");
    assertThat(result).bodyJson().extractingPath("$.event_type").isEqualTo("llm_request");
    assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("token-usage");
    assertThat(getList(orgId))
        .bodyJson()
        .extractingPath("$.metrics[*].name")
        .asArray()
        .containsExactly("입력 토큰");
  }

  @Test
  void 없는_미터를_수정하면_404_metric_not_found다() {
    UUID orgId = insertOrganization();

    assertThat(put(orgId, "nope", updateBody("이름", "chat_completion", "token")))
        .hasStatus(404)
        .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCodes.METRIC_NOT_FOUND);
  }

  @Test
  void 다른_도입사의_미터는_수정도_404다() {
    UUID orgId = insertOrganization();
    UUID otherOrgId = insertOrganization();
    assertThat(post(orgId, sumBody("token-usage"))).hasStatus(201);

    assertThat(put(otherOrgId, "token-usage", updateBody("남의 이름", "chat_completion", "token")))
        .hasStatus(404);
    assertThat(storedName(orgId, "token-usage")).isEqualTo("토큰 사용량");
  }

  @Test
  void 이벤트가_잡히는_미터의_집계_기준_변경은_409다() {
    UUID orgId = insertOrganization();
    assertThat(post(orgId, sumBody("token-usage"))).hasStatus(201);
    insertEvent(orgId, "chat_completion", "{\"token\": 10}");

    assertThat(put(orgId, "token-usage", updateBody("토큰 사용량", "llm_request", "input_tokens")))
        .hasStatus(409)
        .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCodes.METRIC_BASIS_HAS_EVENTS);
    assertThat(storedEventType(orgId, "token-usage")).isEqualTo("chat_completion");
  }

  @Test
  void 대상_속성이_없는_이벤트는_기준_변경을_막지_않는다() {
    UUID orgId = insertOrganization();
    assertThat(post(orgId, sumBody("token-usage"))).hasStatus(201);
    insertEvent(orgId, "chat_completion", "{\"other_property\": 10}");

    assertThat(put(orgId, "token-usage", updateBody("토큰 사용량", "llm_request", "input_tokens")))
        .hasStatus(200);
  }

  @Test
  void 등록한_미터가_code_오름차순_목록으로_나온다() {
    UUID orgId = insertOrganization();
    assertThat(post(orgId, sumBody("token-usage"))).hasStatus(201);
    assertThat(post(orgId, sumBody("api-calls"))).hasStatus(201);

    MvcTestResult result = getList(orgId);

    assertThat(result).hasStatus(200);
    assertThat(result)
        .bodyJson()
        .extractingPath("$.metrics[*].code")
        .asArray()
        .containsExactly("api-calls", "token-usage");
  }

  @Test
  void 미터가_없으면_목록이_빈_배열이다() {
    UUID orgId = insertOrganization();

    assertThat(getList(orgId))
        .hasStatus(200)
        .bodyJson()
        .extractingPath("$.metrics")
        .asArray()
        .isEmpty();
  }

  @Test
  void 목록은_자기_도입사의_미터만_담는다() {
    UUID orgId = insertOrganization();
    UUID otherOrgId = insertOrganization();
    assertThat(post(orgId, sumBody("token-usage"))).hasStatus(201);
    assertThat(post(otherOrgId, sumBody("api-calls"))).hasStatus(201);

    assertThat(getList(orgId))
        .hasStatus(200)
        .bodyJson()
        .extractingPath("$.metrics[*].code")
        .asArray()
        .containsExactly("token-usage");
  }

  private MvcTestResult post(UUID organizationId, String jsonBody) {
    return mvc.post()
        .uri("/v1/metrics")
        .header("X-Organization-Id", organizationId.toString())
        .contentType(MediaType.APPLICATION_JSON)
        .content(jsonBody)
        .exchange();
  }

  private MvcTestResult getList(UUID organizationId) {
    return mvc.get()
        .uri("/v1/metrics")
        .header("X-Organization-Id", organizationId.toString())
        .exchange();
  }

  private MvcTestResult put(UUID organizationId, String code, String jsonBody) {
    return mvc.put()
        .uri("/v1/metrics/{code}", code)
        .header("X-Organization-Id", organizationId.toString())
        .contentType(MediaType.APPLICATION_JSON)
        .content(jsonBody)
        .exchange();
  }

  private String updateBody(String name, String eventType, String targetProperty) {
    return """
        {"name": "%s", "event_type": "%s", "aggregation": "SUM", "target_property": "%s"}
        """
        .formatted(name, eventType, targetProperty);
  }

  private void insertEvent(UUID orgId, String eventType, String propertiesJson) {
    UUID customerId =
        jdbc.queryForObject(
            "INSERT INTO customer (organization_id, name) VALUES (?, '테스트 고객') RETURNING id",
            UUID.class,
            orgId);
    jdbc.update(
        """
        INSERT INTO usage_event
          (organization_id, transaction_id, customer_id, event_type, properties, occurred_at)
        VALUES (?, ?, ?, ?, ?::jsonb, now())
        """,
        orgId,
        UUID.randomUUID().toString(),
        customerId,
        eventType,
        propertiesJson);
  }

  private String storedEventType(UUID orgId, String code) {
    return jdbc.queryForObject(
        "SELECT event_type FROM billable_metric WHERE organization_id = ? AND code = ?",
        String.class,
        orgId,
        code);
  }

  private String sumBody(String code) {
    return """
        {"code": "%s", "name": "토큰 사용량", "event_type": "chat_completion",
         "aggregation": "SUM", "target_property": "token"}
        """
        .formatted(code);
  }

  private UUID insertOrganization() {
    return jdbc.queryForObject(
        "INSERT INTO organization (name) VALUES ('테스트 도입사') RETURNING id", UUID.class);
  }

  private Integer metricCount(UUID orgId, String code) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM billable_metric WHERE organization_id = ? AND code = ?",
        Integer.class,
        orgId,
        code);
  }

  private String storedAggregation(UUID orgId, String code) {
    return jdbc.queryForObject(
        "SELECT aggregation FROM billable_metric WHERE organization_id = ? AND code = ?",
        String.class,
        orgId,
        code);
  }

  private String storedName(UUID orgId, String code) {
    return jdbc.queryForObject(
        "SELECT name FROM billable_metric WHERE organization_id = ? AND code = ?",
        String.class,
        orgId,
        code);
  }
}
