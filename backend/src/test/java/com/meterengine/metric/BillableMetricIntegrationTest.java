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
  void 같은_event_type과_target_property를_다른_코드로_등록하면_409이고_기존_미터만_남는다() {
    UUID organizationId = insertOrganization();
    assertThat(post(organizationId, sumBody("token-usage"))).hasStatus(201);

    assertThat(post(organizationId, sumBody("token-usage-copy")))
        .hasStatus(409)
        .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCode.BILLABLE_METRIC_EVENT_TYPE_TARGET_PROPERTY_ALREADY_EXISTS.getCode());

    assertThat(billableMetricCount(organizationId, "token-usage-copy")).isZero();
  }

  @Test
  void 같은_event_type이라도_target_property가_다르면_등록된다() {
    UUID organizationId = insertOrganization();
    assertThat(post(organizationId, sumBody("token-usage"))).hasStatus(201);

    assertThat(post(organizationId, sumBody("character-usage", "chars"))).hasStatus(201);
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
    assertThat(
            mvc.put()
                .uri("/v1/billable-metrics/token-usage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody("chat_completion", "token"))
                .exchange())
        .hasStatus(400);
  }

  @Test
  void 등록한_미터가_code_오름차순_목록으로_나온다() {
    UUID organizationId = insertOrganization();
    assertThat(post(organizationId, sumBody("token-usage"))).hasStatus(201);
    assertThat(post(organizationId, sumBody("api-calls", "calls"))).hasStatus(201);

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

  @Test
  void 수정하면_200이고_보낸_값으로_덮어써진다() {
    UUID organizationId = insertOrganization();
    assertThat(post(organizationId, sumBody("token-usage"))).hasStatus(201);

    MvcTestResult result =
        put(organizationId, "token-usage", updateBody("text_completion", "chars"));

    assertThat(result).hasStatus(200).bodyJson().extractingPath("$.code").isEqualTo("token-usage");
    assertThat(result).bodyJson().extractingPath("$.name").isEqualTo("글자 수");
    assertThat(result).bodyJson().extractingPath("$.event_type").isEqualTo("text_completion");
    assertThat(result).bodyJson().extractingPath("$.target_property").isEqualTo("chars");
    assertThat(storedName(organizationId, "token-usage")).isEqualTo("글자 수");
    assertThat(storedEventType(organizationId, "token-usage")).isEqualTo("text_completion");
  }

  @Test
  void 없는_미터를_수정하면_404다() {
    assertThat(put(insertOrganization(), "token-usage", updateBody("chat_completion", "token")))
        .hasStatus(404)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCode.BILLABLE_METRIC_NOT_FOUND.getCode());
  }

  @Test
  void 다른_도입사의_미터는_수정할_수_없다() {
    UUID organizationId = insertOrganization();
    UUID otherOrganizationId = insertOrganization();
    assertThat(post(otherOrganizationId, sumBody("token-usage"))).hasStatus(201);

    assertThat(put(organizationId, "token-usage", updateBody("chat_completion", "token")))
        .hasStatus(404)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCode.BILLABLE_METRIC_NOT_FOUND.getCode());
    assertThat(storedName(otherOrganizationId, "token-usage")).isEqualTo("토큰 사용량");
  }

  @Test
  void 이벤트가_있는_미터의_event_type을_바꾸면_409이고_미터는_그대로다() {
    UUID organizationId = insertOrganization();
    assertThat(post(organizationId, sumBody("token-usage"))).hasStatus(201);
    insertEvent(organizationId, insertCustomer(organizationId));

    assertThat(put(organizationId, "token-usage", updateBody("text_completion", "token")))
        .hasStatus(409)
        .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCode.BILLABLE_METRIC_HAS_EVENTS.getCode());
    assertThat(storedEventType(organizationId, "token-usage")).isEqualTo("chat_completion");
  }

  @Test
  void 바꾸려는_event_type과_target_property가_다른_미터에_있으면_409다() {
    UUID organizationId = insertOrganization();
    assertThat(post(organizationId, sumBody("token-usage"))).hasStatus(201);
    assertThat(post(organizationId, sumBody("character-usage", "chars"))).hasStatus(201);

    assertThat(put(organizationId, "character-usage", updateBody("chat_completion", "token")))
        .hasStatus(409)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCode.BILLABLE_METRIC_EVENT_TYPE_TARGET_PROPERTY_ALREADY_EXISTS.getCode());
  }

  @Test
  void 수정도_필수_필드가_비면_400이고_미터는_그대로다() {
    UUID organizationId = insertOrganization();
    assertThat(post(organizationId, sumBody("token-usage"))).hasStatus(201);

    assertThat(put(organizationId, "token-usage", "{\"name\": \"글자 수\"}"))
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
    assertThat(storedName(organizationId, "token-usage")).isEqualTo("토큰 사용량");
  }

  @Test
  void 수정_경로의_code에_NUL이_있으면_500이_아니라_400이다() {
    MvcTestResult result =
        put(insertOrganization(), "token\u0000usage", updateBody("chat_completion", "token"));

    assertThat(result)
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.errors[*].field")
        .asArray()
        .containsExactly("code");
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
    return sumBody(code, "token");
  }

  private String sumBody(String code, String targetProperty) {
    return """
        {"code": "%s", "name": "토큰 사용량", "event_type": "chat_completion",
         "aggregation": "sum", "target_property": "%s"}
        """
        .formatted(code, targetProperty);
  }

  private MvcTestResult put(UUID organizationId, String code, String jsonBody) {
    return mvc.put()
        .uri("/v1/billable-metrics/{code}", code)
        .header("X-Organization-Id", organizationId.toString())
        .contentType(MediaType.APPLICATION_JSON)
        .content(jsonBody)
        .exchange();
  }

  private String updateBody(String eventType, String targetProperty) {
    return """
        {"name": "글자 수", "event_type": "%s", "aggregation": "sum", "target_property": "%s"}
        """
        .formatted(eventType, targetProperty);
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

  private UUID insertCustomer(UUID organizationId) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO customer (organization_id, name) VALUES (?, '아크메') RETURNING id",
        UUID.class,
        organizationId);
  }

  private void insertEvent(UUID organizationId, UUID customerId) {
    jdbcTemplate.update(
        """
        INSERT INTO event
          (organization_id, transaction_id, customer_id, type, properties, occurred_at)
        VALUES (?, 'tx-1', ?, 'chat_completion', '{"token": 1200}', now())
        """,
        organizationId,
        customerId);
  }

  private String storedEventType(UUID organizationId, String code) {
    return jdbcTemplate.queryForObject(
        "SELECT event_type FROM billable_metric WHERE organization_id = ? AND code = ?",
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
