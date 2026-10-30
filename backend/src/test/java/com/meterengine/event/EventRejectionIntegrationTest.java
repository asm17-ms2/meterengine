package com.meterengine.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.jayway.jsonpath.JsonPath;
import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class EventRejectionIntegrationTest {
  @Autowired private WebApplicationContext context;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private JsonMapper jsonMapper;
  @Autowired private MeterRegistry registry;
  private MockMvcTester mvc;
  private UUID organization;
  private UUID customer;
  private String authorization;

  @BeforeEach
  void setUp() {
    mvc = MockMvcTester.from(context, builder -> builder.apply(springSecurity()).build());
    organization = UUID.randomUUID();
    authorization = TestCredentials.issueApiKey(jdbcTemplate, organization, "ingest", "read");
    customer = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO customer(id,organization_id,name) VALUES (?,?,'수집 고객')",
        customer,
        organization);
  }

  @Test
  void 고객을_찾지_못한_수집의_주체와_요청_ID를_안전하게_남긴다() {
    UUID missing = UUID.randomUUID();
    MvcTestResult response = post(authorization, null, "/v1/events", event("missing", missing));
    assertThat(response).hasStatus(404);
    MvcTestResult result = get(authorization, null, "");
    assertThat(result).hasStatusOk();
    String json = body(result);
    assertThat(number(json, "$.total")).isEqualTo(1);
    assertThat((String) JsonPath.read(json, "$.rejections[0].error_code"))
        .isEqualTo("customer_not_found");
    assertThat((String) JsonPath.read(json, "$.rejections[0].request_id"))
        .isEqualTo(response.getResponse().getHeader("X-Request-ID"));
    assertThat((String) JsonPath.read(json, "$.rejections[0].customer_id"))
        .isEqualTo(missing.toString());
    assertThat((String) JsonPath.read(json, "$.recording_started_at")).isNotBlank();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT actor_type FROM event_rejection WHERE organization_id=?",
                String.class,
                organization))
        .isEqualTo("api_key");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT actor_id IS NOT NULL FROM event_rejection WHERE organization_id=?",
                Boolean.class,
                organization))
        .isTrue();
    assertThat(json)
        .doesNotContain(
            "hidden-property-value",
            "\"properties\"",
            "\"actor_id\"",
            TestCredentials.rawKeyOf(authorization));
    assertThat(eventCount()).isZero();
  }

  @Test
  void 배치의_반려_행만_원본_없이_기록하고_유효한_행의_저장을_유지한다() {
    Map<String, Object> invalidTime = new java.util.LinkedHashMap<>(event("bad-time", customer));
    invalidTime.put("timestamp", "not-a-time");
    Map<String, Object> invalidText =
        new java.util.LinkedHashMap<>(event("bad" + Character.toString(0), customer));
    var events =
        List.of(
            event("good", customer),
            event("missing", UUID.randomUUID()),
            invalidTime,
            invalidText,
            42);
    MvcTestResult response =
        post(authorization, null, "/v1/event-batches", Map.of("events", events));
    assertThat(response).hasStatusOk();
    assertThat((List<String>) JsonPath.read(body(response), "$.results[*].result"))
        .containsExactly("stored", "rejected", "rejected", "rejected", "rejected");
    String json = body(get(authorization, null, ""));
    assertThat(number(json, "$.total")).isEqualTo(4);
    assertThat((List<Integer>) JsonPath.read(json, "$.rejections[*].batch_index"))
        .containsExactlyInAnyOrder(1, 2, 3, 4);
    assertThat((List<String>) JsonPath.read(json, "$.rejections[*].request_id"))
        .containsOnly(response.getResponse().getHeader("X-Request-ID"));
    assertThat(json)
        .doesNotContain("hidden-property-value", "not-a-time", "\"message\"", "\"properties\"");
    assertThat(eventCount()).isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM event_rejection WHERE organization_id=? AND transaction_id IS NULL",
                Integer.class,
                organization))
        .isEqualTo(2);
  }

  @Test
  void 최상위_JSON과_입력_거절은_수집_감사와_업무_거절에서_제외한다() {
    assertThat(
            mvc.post()
                .uri("/v1/events")
                .header(AUTHORIZATION, authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{")
                .exchange())
        .hasStatus(400);
    assertThat(post(authorization, null, "/v1/events", Map.of("type", "usage"))).hasStatus(400);
    assertThat(number(body(get(authorization, null, "")), "$.total")).isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_log WHERE organization_id=? AND action='api_request_completed' AND detail->>'route'='/v1/events'",
                Integer.class,
                organization))
        .isZero();
  }

  @Test
  void 오래된_이벤트와_보관_고객의_거절을_기록한다() {
    Map<String, Object> old = new java.util.LinkedHashMap<>(event("old", customer));
    old.put("timestamp", OffsetDateTime.now().minusDays(36).toString());
    assertThat(post(authorization, null, "/v1/events", old)).hasStatus(400);
    jdbcTemplate.update("UPDATE customer SET archived_at=now() WHERE id=?", customer);
    assertThat(post(authorization, null, "/v1/events", event("archived", customer))).hasStatus(409);
    assertThat(
            (List<String>)
                JsonPath.read(body(get(authorization, null, "")), "$.rejections[*].error_code"))
        .containsExactlyInAnyOrder("event_too_old", "customer_archived");
    String oldResult = body(get(authorization, null, "&error_code=event_too_old"));
    assertThat((List<String>) JsonPath.read(oldResult, "$.rejections[0].error_fields"))
        .containsExactly("timestamp");
  }

  @Test
  void 거절_기록은_UPDATE_DELETE_TRUNCATE를_막는다() {
    assertThat(post(authorization, null, "/v1/events", event("immutable", UUID.randomUUID())))
        .hasStatus(404);
    for (String sql :
        List.of(
            "UPDATE event_rejection SET error_code='validation_error' WHERE organization_id=?",
            "DELETE FROM event_rejection WHERE organization_id=?"))
      assertThatThrownBy(() -> jdbcTemplate.update(sql, organization))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("append-only");
    assertThatThrownBy(() -> jdbcTemplate.execute("TRUNCATE event_rejection"))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("append-only");
    assertThat(number(body(get(authorization, null, "")), "$.total")).isEqualTo(1);
  }

  @Test
  void 같은_조직의_기존_테스트_실제_거절을_나누고_다른_조직을_숨긴다() {
    String session = TestCredentials.issueMemberSession(jdbcTemplate, organization);
    for (String mode : List.of("legacy", "test", "live"))
      assertThat(post(session, mode, "/v1/events", event("same-attempt", UUID.randomUUID())))
          .hasStatus(404);
    for (String mode : List.of("legacy", "test", "live"))
      assertThat(number(body(get(session, mode, "")), "$.total")).isEqualTo(1);
    assertThat(number(body(get(authorization, null, "")), "$.total")).isEqualTo(1);
    String other = TestCredentials.issueApiKey(jdbcTemplate, UUID.randomUUID(), "read");
    assertThat(number(body(get(other, null, "")), "$.total")).isZero();
    String ingestOnly = TestCredentials.issueApiKey(jdbcTemplate, organization, "ingest");
    assertThat(get(ingestOnly, null, "")).hasStatus(403);
  }

  @Test
  void 기록한_달과_검색_조건의_전체_건수와_페이지를_제공한다() {
    UUID missing = UUID.randomUUID();
    for (String id : List.of("filter-a", "filter-b", "other"))
      assertThat(post(authorization, null, "/v1/events", event(id, missing))).hasStatus(404);
    String result =
        body(
            get(
                authorization,
                null,
                "&q=filter-&customer_id="
                    + missing
                    + "&error_code=customer_not_found&size=1&page=1"));
    assertThat(number(result, "$.total")).isEqualTo(2);
    assertThat((List<?>) JsonPath.read(result, "$.rejections")).hasSize(1);
    assertThat(
            number(
                body(
                    mvc.get()
                        .uri(
                            "/v1/event-rejections/search?month="
                                + YearMonth.now(ZoneId.of("Asia/Seoul")))
                        .param("q", "%")
                        .header(AUTHORIZATION, authorization)
                        .exchange()),
                "$.total"))
        .isZero();
    assertThat(
            mvc.get()
                .uri("/v1/event-rejections/search?month=2000-01")
                .header(AUTHORIZATION, authorization)
                .exchange())
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.total")
        .isEqualTo(0);
    assertThat(get(authorization, null, "&page=-1")).hasStatus(400);
    assertThat(get(authorization, null, "&error_code=unknown")).hasStatus(400);
    MvcTestResult unsupported = get(authorization, null, "&unrecognized_private_field=value");
    assertThat(unsupported).hasStatus(400);
    assertThat(body(unsupported)).doesNotContain("unrecognized_private_field", "value");
  }

  @Test
  void 기록_실패가_원래_404나_부분_성공_응답과_원본_저장을_가리지_않는다() {
    double before =
        registry.get("meterengine.event.rejection.recording.failures").counter().count();
    jdbcTemplate.execute(
        "CREATE FUNCTION event_rejection_test_failure() RETURNS trigger AS $$ BEGIN RAISE EXCEPTION 'synthetic recording failure'; END; $$ LANGUAGE plpgsql");
    jdbcTemplate.execute(
        "CREATE TRIGGER event_rejection_test_failure BEFORE INSERT ON event_rejection FOR EACH ROW EXECUTE FUNCTION event_rejection_test_failure()");
    try {
      assertThat(post(authorization, null, "/v1/events", event("failure", UUID.randomUUID())))
          .hasStatus(404);
      assertThat(
              post(
                  authorization,
                  null,
                  "/v1/event-batches",
                  Map.of(
                      "events",
                      List.of(event("survives", customer), event("rejected", UUID.randomUUID())))))
          .hasStatusOk()
          .bodyJson()
          .extractingPath("$.results[0].result")
          .isEqualTo("stored");
      assertThat(eventCount()).isEqualTo(1);
      assertThat(number(body(get(authorization, null, "")), "$.total")).isZero();
      assertThat(registry.get("meterengine.event.rejection.recording.failures").counter().count())
          .isEqualTo(before + 2);
    } finally {
      jdbcTemplate.execute("DROP TRIGGER event_rejection_test_failure ON event_rejection");
      jdbcTemplate.execute("DROP FUNCTION event_rejection_test_failure()");
    }
  }

  @Test
  void 배치_백_개의_거절을_모두_기록하고_최상위_상한_거절은_추가하지_않는다() {
    var events =
        java.util.stream.IntStream.range(0, 100)
            .mapToObj(index -> event("bad-" + index, UUID.randomUUID()))
            .toList();
    assertThat(post(authorization, null, "/v1/event-batches", Map.of("events", events)))
        .hasStatusOk();
    assertThat(number(body(get(authorization, null, "")), "$.total")).isEqualTo(100);
    var tooMany = new java.util.ArrayList<>(events);
    tooMany.add(event("bad-extra", UUID.randomUUID()));
    assertThat(post(authorization, null, "/v1/event-batches", Map.of("events", tooMany)))
        .hasStatus(400);
    assertThat(number(body(get(authorization, null, "")), "$.total")).isEqualTo(100);
  }

  @Test
  void 거절_기록의_환경_FK도_다른_조직_참조를_막는다() {
    UUID environment =
        jdbcTemplate.queryForObject(
            "SELECT id FROM organization_environment WHERE organization_id=? AND mode='test'",
            UUID.class,
            organization);
    UUID other = UUID.randomUUID();
    TestCredentials.issueApiKey(jdbcTemplate, other);
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO event_rejection(organization_id,environment_id,error_code,actor_type) VALUES (?,?,'customer_not_found','system')",
                    other,
                    environment))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("event_rejection_environment_fk");
  }

  private Map<String, Object> event(String id, UUID customerId) {
    return Map.of(
        "transaction_id",
        id,
        "customer_id",
        customerId,
        "type",
        "usage",
        "properties",
        Map.of("private", "hidden-property-value"),
        "timestamp",
        OffsetDateTime.now().toString());
  }

  private MvcTestResult post(String actor, String mode, String path, Object payload) {
    var request =
        mvc.post()
            .uri(path)
            .header(AUTHORIZATION, actor)
            .contentType(MediaType.APPLICATION_JSON)
            .content(jsonMapper.writeValueAsString(payload));
    if (mode != null) request.header("X-MeterEngine-Environment", mode);
    return request.exchange();
  }

  private MvcTestResult get(String actor, String mode, String suffix) {
    var request =
        mvc.get()
            .uri(
                "/v1/event-rejections/search?month="
                    + YearMonth.now(ZoneId.of("Asia/Seoul"))
                    + suffix)
            .header(AUTHORIZATION, actor);
    if (mode != null) request.header("X-MeterEngine-Environment", mode);
    return request.exchange();
  }

  private int eventCount() {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM event WHERE organization_id=?", Integer.class, organization);
  }

  private static int number(String json, String path) {
    return JsonPath.<Number>read(json, path).intValue();
  }

  private static String body(MvcTestResult result) {
    return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
  }
}
