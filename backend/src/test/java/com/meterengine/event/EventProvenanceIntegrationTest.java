package com.meterengine.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.jayway.jsonpath.JsonPath;
import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
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
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class EventProvenanceIntegrationTest {
  @Autowired private WebApplicationContext context;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private JsonMapper jsonMapper;
  private MockMvcTester mvc;
  private UUID organization;
  private UUID customer;
  private String first;
  private String second;

  @BeforeEach
  void setUp() {
    mvc = MockMvcTester.from(context, builder -> builder.apply(springSecurity()).build());
    organization = UUID.randomUUID();
    first = TestCredentials.issueApiKey(jdbcTemplate, organization, "ingest", "read");
    second = TestCredentials.issueApiKey(jdbcTemplate, organization, "ingest", "read");
    customer = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO customer(id,organization_id,name) VALUES (?,?,'주체 검증 고객')",
        customer,
        organization);
  }

  @Test
  void 최초_수집_키와_요청을_원본에_보존하고_다른_키의_중복은_덮어쓰지_않는다() {
    MvcTestResult accepted = post(first, null, "/v1/events", event("original", customer));
    assertThat(accepted).hasStatusOk();
    String requestId = accepted.getResponse().getHeader("X-Request-ID");
    String before = snapshot("original", null);
    MvcTestResult duplicate = post(second, null, "/v1/events", event("original", customer));
    assertThat(duplicate).hasStatusOk().bodyJson().extractingPath("$.duplicate").isEqualTo(true);
    assertThat(snapshot("original", null)).isEqualTo(before);
    MvcTestResult lookup = lookup(second, null, "original");
    assertThat(lookup).hasStatusOk();
    String json = body(lookup);
    assertThat((String) JsonPath.read(json, "$.request_id")).isEqualTo(requestId);
    assertThat((String) JsonPath.read(json, "$.actor_type")).isEqualTo("api_key");
    assertThat((String) JsonPath.read(json, "$.actor_id")).isEqualTo(keyId(first).toString());
    assertThat(json)
        .doesNotContain(
            TestCredentials.rawKeyOf(first), TestCredentials.rawKeyOf(second), "key_hash");
    MvcTestResult audit =
        mvc.get().uri("/v1/audit-logs?q=" + requestId).header(AUTHORIZATION, first).exchange();
    assertThat(audit).hasStatusOk();
    assertThat((List<String>) JsonPath.read(body(audit), "$.audit_logs[*].detail.request_id"))
        .isEmpty();
  }

  @Test
  void 배치_성공_행과_반려_행이_같은_요청에_연결되고_중복은_최초_기록을_유지한다() {
    MvcTestResult response =
        post(
            first,
            null,
            "/v1/event-batches",
            Map.of(
                "events",
                List.of(
                    event("batch-a", customer),
                    event("batch-b", customer),
                    event("batch-bad", UUID.randomUUID()))));
    assertThat(response).hasStatusOk();
    String requestId = response.getResponse().getHeader("X-Request-ID");
    var rows =
        jdbcTemplate.queryForList(
            "SELECT actor_type,actor_id::text,request_id::text FROM event WHERE organization_id=?",
            organization);
    assertThat(rows)
        .hasSize(2)
        .allSatisfy(
            row -> {
              assertThat(row.get("actor_type")).isEqualTo("api_key");
              assertThat(row.get("actor_id")).isEqualTo(keyId(first).toString());
              assertThat(row.get("request_id")).isEqualTo(requestId);
            });
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT request_id::text FROM event_rejection WHERE organization_id=?",
                String.class,
                organization))
        .isEqualTo(requestId);
    String original = snapshot("batch-a", null);
    assertThat(
            post(
                second,
                null,
                "/v1/event-batches",
                Map.of(
                    "events", List.of(event("batch-a", customer), event("batch-new", customer)))))
        .hasStatusOk();
    assertThat(snapshot("batch-a", null)).isEqualTo(original);
    assertThat((String) JsonPath.read(body(lookup(second, null, "batch-new")), "$.actor_id"))
        .isEqualTo(keyId(second).toString());
  }

  @Test
  void 이전_원본의_주체를_추정하거나_없는_메타데이터를_꾸미지_않는다() {
    jdbcTemplate.update(
        "INSERT INTO event(organization_id,transaction_id,customer_id,type,occurred_at) VALUES (?,'legacy',?,'usage',now())",
        organization,
        customer);
    MvcTestResult legacy = lookup(first, null, "legacy");
    assertThat(legacy).hasStatusOk();
    assertThat(body(legacy)).doesNotContain("request_id", "actor_type", "actor_id");
  }

  private UUID keyId(String authorization) {
    return jdbcTemplate.queryForObject(
        "SELECT id FROM api_key WHERE key_hash=sha256(convert_to(?,'UTF8'))",
        UUID.class,
        TestCredentials.rawKeyOf(authorization));
  }

  private String snapshot(String transaction, UUID environment) {
    return jdbcTemplate.queryForObject(
        "SELECT to_jsonb(e)::text FROM event e WHERE organization_id=? AND environment_id IS NOT DISTINCT FROM ?::uuid AND transaction_id=?",
        String.class,
        organization,
        environment,
        transaction);
  }

  private Map<String, Object> event(String transaction, UUID customerId) {
    return Map.of(
        "transaction_id",
        transaction,
        "customer_id",
        customerId,
        "type",
        "usage",
        "properties",
        Map.of("quantity", 1),
        "timestamp",
        OffsetDateTime.now().toString());
  }

  private MvcTestResult post(String actor, String environment, String path, Object payload) {
    var request =
        mvc.post()
            .uri(path)
            .header(AUTHORIZATION, actor)
            .contentType(MediaType.APPLICATION_JSON)
            .content(jsonMapper.writeValueAsString(payload));
    if (environment != null) request.header("X-MeterEngine-Environment", environment);
    return request.exchange();
  }

  private MvcTestResult lookup(String actor, String environment, String transaction) {
    var request =
        mvc.get()
            .uri("/v1/events/lookup?transaction_id=" + transaction)
            .header(AUTHORIZATION, actor);
    if (environment != null) request.header("X-MeterEngine-Environment", environment);
    return request.exchange();
  }

  private static String body(MvcTestResult result) {
    return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
  }
}
