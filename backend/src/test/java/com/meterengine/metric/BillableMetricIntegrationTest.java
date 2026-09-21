package com.meterengine.metric;

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
class BillableMetricIntegrationTest {

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbcTemplate;

  private MockMvcTester mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcTester.from(webApplicationContext);
  }

  @Test
  void 미터를_등록하면_201이고_보낸_값이_그대로_저장된다() {
    UUID organizationId = insertOrganization();

    MvcTestResult result = post(organizationId, sumBody("token-usage"));

    assertThat(result).hasStatus(201).bodyJson().extractingPath("$.code").isEqualTo("token-usage");
    assertThat(result).bodyJson().extractingPath("$.event_type").isEqualTo("chat_completion");
    assertThat(result).bodyJson().extractingPath("$.target_property").isEqualTo("token");
    assertThat(billableMetricCount(organizationId, "token-usage")).isEqualTo(1);
    assertThat(storedAggregation(organizationId, "token-usage")).isEqualTo("sum");
  }

  @Test
  void 같은_코드로_다시_등록하면_409이고_기존_미터는_그대로다() {
    UUID organizationId = insertOrganization();
    assertThat(post(organizationId, sumBody("token-usage"))).hasStatus(201);

    assertThat(
            post(
                organizationId,
                """
                {"code": "token-usage", "name": "다른 이름", "event_type": "other",
                 "aggregation": "sum", "target_property": "chars"}
                """))
        .hasStatus(409)
        .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCode.BILLABLE_METRIC_ALREADY_EXISTS.getCode());

    assertThat(storedName(organizationId, "token-usage")).isEqualTo("토큰 사용량");
  }

  @Test
  void 다른_도입사에는_같은_코드를_등록할_수_있다() {
    UUID organizationId = insertOrganization();
    UUID otherOrganizationId = insertOrganization();
    assertThat(post(organizationId, sumBody("token-usage"))).hasStatus(201);

    assertThat(post(otherOrganizationId, sumBody("token-usage"))).hasStatus(201);
  }

  @Test
  void SUM이_아닌_집계_함수는_400이고_저장은_0건이다() {
    UUID organizationId = insertOrganization();

    assertThat(
            post(
                organizationId,
                """
                {"code": "call-count", "name": "호출 수", "event_type": "chat_completion",
                 "aggregation": "COUNT", "target_property": "calls"}
                """))
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCode.INVALID_BILLABLE_METRIC.getCode());
    assertThat(billableMetricCount(organizationId, "call-count")).isZero();
  }

  @Test
  void SUM인데_target_property가_없으면_400이다() {
    UUID organizationId = insertOrganization();

    assertThat(
            post(
                organizationId,
                """
                {"code": "token-usage", "name": "토큰 사용량", "event_type": "chat_completion",
                 "aggregation": "sum"}
                """))
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCode.INVALID_BILLABLE_METRIC.getCode());
  }

  @Test
  void 필수_필드가_비면_400_validation_error다() {
    UUID organizationId = insertOrganization();

    assertThat(post(organizationId, "{\"code\": \"token-usage\"}"))
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
    assertThat(billableMetricCount(organizationId, "token-usage")).isZero();
  }

  @Test
  void 미등록_도입사면_400_unknown_organization이다() {
    assertThat(post(UUID.randomUUID(), sumBody("token-usage")))
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCode.UNKNOWN_ORGANIZATION.getCode());
  }

  @Test
  void 도입사_헤더가_없으면_400이다() {
    assertThat(
            mvc.post()
                .uri("/v1/billable-metrics")
                .contentType(MediaType.APPLICATION_JSON)
                .content(sumBody("token-usage"))
                .exchange())
        .hasStatus(400);
    assertThat(mvc.get().uri("/v1/billable-metrics").exchange()).hasStatus(400);
  }

  @Test
  void 등록한_미터가_code_오름차순_목록으로_나온다() {
    UUID organizationId = insertOrganization();
    assertThat(post(organizationId, sumBody("token-usage"))).hasStatus(201);
    assertThat(post(organizationId, sumBody("api-calls"))).hasStatus(201);

    MvcTestResult result = getList(organizationId);

    assertThat(result).hasStatus(200);
    assertThat(result)
        .bodyJson()
        .extractingPath("$.billable_metrics[*].code")
        .asArray()
        .containsExactly("api-calls", "token-usage");
  }

  @Test
  void 미터가_없으면_목록이_빈_배열이다() {
    UUID organizationId = insertOrganization();

    assertThat(getList(organizationId))
        .hasStatus(200)
        .bodyJson()
        .extractingPath("$.billable_metrics")
        .asArray()
        .isEmpty();
  }

  @Test
  void 목록은_자기_도입사의_미터만_담는다() {
    UUID organizationId = insertOrganization();
    UUID otherOrganizationId = insertOrganization();
    assertThat(post(organizationId, sumBody("token-usage"))).hasStatus(201);
    assertThat(post(otherOrganizationId, sumBody("api-calls"))).hasStatus(201);

    assertThat(getList(organizationId))
        .hasStatus(200)
        .bodyJson()
        .extractingPath("$.billable_metrics[*].code")
        .asArray()
        .containsExactly("token-usage");
  }

  private MvcTestResult post(UUID organizationId, String jsonBody) {
    return mvc.post()
        .uri("/v1/billable-metrics")
        .header("X-Organization-Id", organizationId.toString())
        .contentType(MediaType.APPLICATION_JSON)
        .content(jsonBody)
        .exchange();
  }

  private MvcTestResult getList(UUID organizationId) {
    return mvc.get()
        .uri("/v1/billable-metrics")
        .header("X-Organization-Id", organizationId.toString())
        .exchange();
  }

  private String sumBody(String code) {
    return """
        {"code": "%s", "name": "토큰 사용량", "event_type": "chat_completion",
         "aggregation": "sum", "target_property": "token"}
        """
        .formatted(code);
  }

  private UUID insertOrganization() {
    return jdbcTemplate.queryForObject(
        "INSERT INTO organization (name) VALUES ('테스트 도입사') RETURNING id", UUID.class);
  }

  private Integer billableMetricCount(UUID organizationId, String code) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM billable_metric WHERE organization_id = ? AND code = ?",
        Integer.class,
        organizationId,
        code);
  }

  private String storedAggregation(UUID organizationId, String code) {
    return jdbcTemplate.queryForObject(
        "SELECT aggregation FROM billable_metric WHERE organization_id = ? AND code = ?",
        String.class,
        organizationId,
        code);
  }

  private String storedName(UUID organizationId, String code) {
    return jdbcTemplate.queryForObject(
        "SELECT name FROM billable_metric WHERE organization_id = ? AND code = ?",
        String.class,
        organizationId,
        code);
  }
}
