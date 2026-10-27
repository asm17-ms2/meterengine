package com.meterengine.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.jayway.jsonpath.JsonPath;
import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class AuditTargetIntegrationTest {
  @Autowired private WebApplicationContext context;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private JsonMapper jsonMapper;
  @Autowired private jakarta.persistence.EntityManager entityManager;
  private MockMvcTester mvc;
  private UUID organizationId;
  private String admin;

  @BeforeEach
  void setup() {
    mvc = MockMvcTester.from(context, builder -> builder.apply(springSecurity()).build());
    organizationId = UUID.randomUUID();
    admin = TestCredentials.issueApiKey(jdbcTemplate, organizationId);
  }

  @Test
  void 고객_생성과_수정은_대상과_필드만_감사한다() {
    String externalId = "private-external-" + UUID.randomUUID();
    String initial = "private-initial-name";
    String changed = "private-changed-name";
    String email = "private-billing@example.invalid";
    MvcTestResult created = post("/v1/customers", Map.of("name", initial));
    assertThat(created).hasStatus(201);
    String id = JsonPath.read(body(created), "$.id");
    MvcTestResult updated =
        put("/v1/customers/" + id, admin, Map.of("name", changed, "billing_email", email));
    assertThat(updated).hasStatusOk();
    var request = requestAudit(updated);
    assertThat(request.get("target_id")).isEqualTo(id);
    var detail = jsonMapper.readTree((String) request.get("detail"));
    assertThat(detail.path("path_ids").path("id").asString()).isEqualTo(id);
    var createdLogs = semantic("customer_created", id);
    assertThat(createdLogs).hasSize(1);
    assertThat(jsonMapper.readTree(createdLogs.getFirst()).path("changed_fields").toString())
        .contains("name");
    var changedLogs = semantic("customer_updated", id);
    assertThat(changedLogs).hasSize(1);
    assertThat(jsonMapper.readTree(changedLogs.getFirst()).path("changed_fields").toString())
        .contains("name", "billing_email");
    assertThat(String.join("", createdLogs) + String.join("", changedLogs) + request.get("detail"))
        .doesNotContain(initial, changed, email, externalId);
    MvcTestResult ordinary = post("/v1/customers", Map.of("name", "ordinary-private-name"));
    assertThat(ordinary).hasStatus(201);
    assertThat(semantic("customer_created", JsonPath.read(body(ordinary), "$.id"))).hasSize(1);
  }

  @Test
  void 권한실패와_없는대상에도_UUID를_남기고_다른조직의_데이터와_감사는_노출하지_않는다() {
    UUID second = UUID.randomUUID();
    String secondRead = TestCredentials.issueApiKey(jdbcTemplate, second, "read");
    UUID foreignCustomer =
        jdbcTemplate.queryForObject(
            "INSERT INTO customer(organization_id,name) VALUES (?, 'foreign-private-name') RETURNING id",
            UUID.class,
            second);
    String read = TestCredentials.issueApiKey(jdbcTemplate, organizationId, "read");
    MvcTestResult denied =
        put("/v1/customers/" + foreignCustomer, read, Map.of("name", "attempted-private-name"));
    assertThat(denied).hasStatus(403);
    assertThat(requestAudit(denied))
        .containsEntry("target_id", foreignCustomer.toString())
        .containsEntry("organization_id", organizationId);
    MvcTestResult missing =
        put("/v1/customers/" + foreignCustomer, admin, Map.of("name", "attempted-private-name"));
    assertThat(missing).hasStatus(404);
    assertThat(requestAudit(missing))
        .containsEntry("target_id", foreignCustomer.toString())
        .containsEntry("organization_id", organizationId);
    assertThat(semantic("customer_updated", foreignCustomer.toString())).isEmpty();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT name FROM customer WHERE id=?", String.class, foreignCustomer))
        .isEqualTo("foreign-private-name");
    assertThat(
            mvc.get()
                .uri("/v1/audit-logs")
                .header(AUTHORIZATION, secondRead)
                .queryParam("q", foreignCustomer.toString())
                .exchange())
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.total")
        .isEqualTo(0);
    assertThat(
            mvc.get()
                .uri("/v1/audit-logs")
                .header(AUTHORIZATION, read)
                .queryParam("q", foreignCustomer.toString())
                .exchange())
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.total")
        .isEqualTo(2);
  }

  @Test
  void UUID형_미터코드는_generic감사에서_빼고_semantic대상으로만_남긴다() {
    String code = UUID.randomUUID().toString();
    MvcTestResult created =
        post(
            "/v1/billable-metrics",
            Map.of(
                "code",
                code,
                "name",
                "private-meter-name",
                "event_type",
                "private-type",
                "aggregation",
                "sum",
                "target_property",
                "quantity"));
    assertThat(created).hasStatus(201);
    MvcTestResult updated =
        put(
            "/v1/billable-metrics/" + code,
            admin,
            Map.of(
                "name",
                "private-new-name",
                "event_type",
                "private-type",
                "aggregation",
                "sum",
                "target_property",
                "quantity",
                "dimension_properties",
                List.of("private-dimension")));
    assertThat(updated).hasStatusOk();
    var request = requestAudit(updated);
    assertThat(request.get("target_id")).isNull();
    assertThat((String) request.get("detail"))
        .doesNotContain(code, "private-meter-name", "private-new-name", "private-dimension");
    assertThat(semantic("billable_metric_created", code)).hasSize(1);
    var logs = semantic("billable_metric_updated", code);
    assertThat(logs).hasSize(1);
    assertThat(logs.getFirst())
        .contains("name", "dimension_properties")
        .doesNotContain("private-new-name", "private-dimension");
  }

  private MvcTestResult post(String path, Map<String, ?> payload) {
    return mvc.post()
        .uri(path)
        .header(AUTHORIZATION, admin)
        .contentType(MediaType.APPLICATION_JSON)
        .content(jsonMapper.writeValueAsString(payload))
        .exchange();
  }

  private MvcTestResult put(String path, String authorization, Map<String, ?> payload) {
    return mvc.put()
        .uri(path)
        .header(AUTHORIZATION, authorization)
        .contentType(MediaType.APPLICATION_JSON)
        .content(jsonMapper.writeValueAsString(payload))
        .exchange();
  }

  private Map<String, Object> requestAudit(MvcTestResult response) {
    return jdbcTemplate.queryForMap(
        "SELECT target_id, organization_id, detail::text AS detail FROM audit_log WHERE action='api_request_completed' AND detail->>'request_id'=?",
        response.getResponse().getHeader("X-Request-ID"));
  }

  private List<String> semantic(String action, String id) {
    entityManager.flush();
    return jdbcTemplate.queryForList(
        "SELECT detail::text FROM audit_log WHERE organization_id=? AND action=? AND target_id=?",
        String.class,
        organizationId,
        action,
        id);
  }

  private String body(MvcTestResult response) {
    return new String(response.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
  }
}
