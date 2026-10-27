package com.meterengine.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.jayway.jsonpath.JsonPath;
import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.audit.service.AuditActor;
import com.meterengine.audit.service.AuditLogService;
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
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class AuditQueryAndRequestIntegrationTest {
  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private AuditLogService auditLogService;
  private MockMvcTester mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcTester.from(
            webApplicationContext, builder -> builder.apply(springSecurity()).build());
  }

  @Test
  void 감사조회는_도입사와_read권한을_지키며_필터와_안정정렬과_페이지를_제공한다() {
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    String read = TestCredentials.issueApiKey(jdbcTemplate, first, "read");
    String operator = TestCredentials.issueOperatorSession(jdbcTemplate);
    TestCredentials.issueApiKey(jdbcTemplate, second, "read");
    String target = UUID.randomUUID().toString();
    auditLogService.record(
        AuditActor.system(first), "customer_created", "customer", target, Map.of("safe", true));
    auditLogService.record(
        AuditActor.system(first), "customer_created", "customer", target, Map.of());
    auditLogService.record(
        AuditActor.system(second), "customer_created", "customer", target, Map.of());
    MvcTestResult firstPage =
        mvc.get()
            .uri("/v1/audit-logs")
            .header(AUTHORIZATION, read)
            .queryParam("action", "customer_created")
            .queryParam("q", target)
            .queryParam("actor_type", "system")
            .queryParam("size", "1")
            .queryParam("sort", "occurred_at_asc")
            .exchange();
    assertThat(firstPage).hasStatusOk().bodyJson().extractingPath("$.total").isEqualTo(2);
    String firstId = JsonPath.read(body(firstPage), "$.audit_logs[0].id");
    MvcTestResult next =
        mvc.get()
            .uri("/v1/audit-logs")
            .header(AUTHORIZATION, read)
            .queryParam("action", "customer_created")
            .queryParam("q", target)
            .queryParam("size", "1")
            .queryParam("page", "1")
            .queryParam("sort", "occurred_at_asc")
            .exchange();
    assertThat(next).hasStatusOk();
    assertThat((String) JsonPath.read(body(next), "$.audit_logs[0].id")).isNotEqualTo(firstId);
    assertThat((String) JsonPath.read(body(next), "$.audit_logs[0].organization_id"))
        .isEqualTo(first.toString());
    assertThat(mvc.get().uri("/v1/operations/audit-logs").header(AUTHORIZATION, read).exchange())
        .hasStatus(403);
    assertThat(
            mvc.get()
                .uri("/v1/operations/audit-logs")
                .header(AUTHORIZATION, operator)
                .queryParam("action", "customer_created")
                .queryParam("q", target)
                .exchange())
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.total")
        .isEqualTo(3);
    String ingest = TestCredentials.issueApiKey(jdbcTemplate, first, "ingest");
    assertThat(mvc.get().uri("/v1/audit-logs").header(AUTHORIZATION, ingest).exchange())
        .hasStatus(403);
  }

  @Test
  void 감사조회의_잘못된_필터와_기간은_400이다() {
    String read = TestCredentials.issueApiKey(jdbcTemplate, UUID.randomUUID(), "read");
    for (Map<String, String> invalid :
        List.of(
            Map.of("page", "-1"),
            Map.of("size", "0"),
            Map.of("size", "101"),
            Map.of("actor_type", "member"),
            Map.of("sort", "random"),
            Map.of("action", "Customer"),
            Map.of("from", "2026-99-99"),
            Map.of("from", "2026-10-02", "to", "2026-10-01"))) {
      var request = mvc.get().uri("/v1/audit-logs").header(AUTHORIZATION, read);
      invalid.forEach(request::queryParam);
      assertThat(request.exchange()).as(invalid.toString()).hasStatus(400);
    }
    assertThat(
            mvc.get()
                .uri("/v1/audit-logs")
                .header(AUTHORIZATION, read)
                .queryParam("from", "2026-10-01T00:00:00+09:00")
                .queryParam("to", "2026-10-31T23:59:59+09:00")
                .exchange())
        .hasStatusOk();
  }

  @Test
  void 조회와_키와_세션과_권한거절과_실패로그인을_본문과_비밀값_없이_감사한다() {
    UUID organizationId = UUID.randomUUID();
    String key = TestCredentials.issueApiKey(jdbcTemplate, organizationId, "read");
    String member = TestCredentials.issueMemberSession(jdbcTemplate, organizationId);
    MvcTestResult read =
        mvc.get()
            .uri("/v1/customers")
            .header(AUTHORIZATION, key)
            .queryParam("q", "secret-query-value")
            .exchange();
    assertThat(read).hasStatusOk();
    assertThat(requestAudit(read))
        .containsEntry("actor_type", "api_key")
        .containsEntry("organization_id", organizationId)
        .containsEntry("method", "GET")
        .containsEntry("route", "/v1/customers")
        .containsEntry("status", "200");
    MvcTestResult session =
        mvc.get().uri("/v1/sessions/current").header(AUTHORIZATION, member).exchange();
    assertThat(requestAudit(session)).containsEntry("actor_type", "user_account");
    MvcTestResult denied =
        mvc.delete()
            .uri("/v1/customers/" + UUID.randomUUID())
            .header(AUTHORIZATION, key)
            .exchange();
    assertThat(denied).hasStatus(403);
    assertThat(requestAudit(denied))
        .containsEntry("status", "403")
        .containsEntry("actor_type", "api_key");
    MvcTestResult login =
        mvc.post()
            .uri("/v1/sessions")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"missing@example.com\",\"password\":\"secret-login-password\"}")
            .exchange();
    assertThat(login).hasStatus(401);
    assertThat(requestAudit(login))
        .containsEntry("status", "401")
        .containsEntry("actor_type", "anonymous");
    String token = "me_invite_" + "A".repeat(32);
    MvcTestResult invalid = mvc.get().uri("/v1/organization-invitations/" + token).exchange();
    assertThat(invalid).hasStatus(404);
    assertThat(requestAudit(invalid))
        .containsEntry("route", "/v1/organization-invitations/{token}");
    for (MvcTestResult result : List.of(read, session, denied, login, invalid)) {
      String requestId = result.getResponse().getHeader("X-Request-ID");
      String detail =
          jdbcTemplate.queryForObject(
              "SELECT detail::text FROM audit_log WHERE detail->>'request_id'=?",
              String.class,
              requestId);
      assertThat(detail)
          .doesNotContain(
              "secret-query-value",
              "secret-login-password",
              TestCredentials.rawKeyOf(key),
              TestCredentials.rawKeyOf(member),
              token,
              "missing@example.com",
              "Authorization");
    }
  }

  @Test
  void 미인증실패와_잘못된인증도_기록하고_감사행은_수정할_수_없다() {
    MvcTestResult anonymous = mvc.get().uri("/v1/customers").exchange();
    assertThat(anonymous).hasStatus(401);
    assertThat(requestAudit(anonymous))
        .containsEntry("actor_type", "anonymous")
        .containsEntry("status", "401");
    MvcTestResult invalid =
        mvc.get()
            .uri("/v1/customers")
            .header(AUTHORIZATION, "Bearer invalid-secret-value")
            .exchange();
    assertThat(invalid).hasStatus(401);
    assertThat(requestAudit(invalid)).containsEntry("actor_type", "anonymous");
    String requestId = invalid.getResponse().getHeader("X-Request-ID");
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE audit_log SET action='modified' WHERE detail->>'request_id'=?",
                    requestId))
        .isInstanceOf(UncategorizedSQLException.class)
        .hasMessageContaining("append-only");
  }

  @Test
  void 수집_성공은_감사하지_않고_수집권한의_읽기거절은_기록한다() {
    UUID organizationId = UUID.randomUUID();
    String key = TestCredentials.issueApiKey(jdbcTemplate, organizationId, "ingest");
    UUID customerId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO customer(id,organization_id,name) VALUES(?,?,'수집 감사 고객')",
        customerId,
        organizationId);
    String content =
        "{\"transaction_id\":\""
            + UUID.randomUUID()
            + "\",\"customer_id\":\""
            + customerId
            + "\",\"type\":\"audit_test\",\"timestamp\":\""
            + java.time.OffsetDateTime.now()
            + "\",\"properties\":{\"secret\":\"never-store-this-value\"}}";
    MvcTestResult result =
        mvc.post()
            .uri("/v1/events")
            .header(AUTHORIZATION, key)
            .contentType(MediaType.APPLICATION_JSON)
            .content(content)
            .exchange();
    assertThat(result).hasStatusOk();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_log WHERE detail->>'request_id'=?",
                Integer.class,
                result.getResponse().getHeader("X-Request-ID")))
        .isZero();
    MvcTestResult denied = mvc.get().uri("/v1/events").header(AUTHORIZATION, key).exchange();
    assertThat(denied).hasStatus(403);
    assertThat(requestAudit(denied))
        .containsEntry("status", "403")
        .containsEntry("actor_type", "api_key");
  }

  @Test
  void API경로_부분검색은_도입사를_격리하고_다른detail을_검색하지_않는다() {
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    String read = TestCredentials.issueApiKey(jdbcTemplate, first, "read");
    TestCredentials.issueApiKey(jdbcTemplate, second, "read");
    String suffix = UUID.randomUUID().toString();
    String route = "/v1/customers/" + suffix + "/{id}";
    String unrelatedDetail = UUID.randomUUID().toString();
    auditLogService.record(
        AuditActor.system(first),
        "api_request_completed",
        "http_request",
        null,
        Map.of("route", route, "status", 200));
    auditLogService.record(
        AuditActor.system(second),
        "api_request_completed",
        "http_request",
        null,
        Map.of("route", route, "status", 200));
    auditLogService.record(
        AuditActor.system(first),
        "customer_updated",
        "customer",
        UUID.randomUUID().toString(),
        Map.of("note", unrelatedDetail));
    MvcTestResult result =
        mvc.get()
            .uri("/v1/audit-logs")
            .header(AUTHORIZATION, read)
            .queryParam("q", "/CUSTOMERS/" + suffix)
            .exchange();
    assertThat(result).hasStatusOk().bodyJson().extractingPath("$.total").isEqualTo(1);
    assertThat((String) JsonPath.read(body(result), "$.audit_logs[0].organization_id"))
        .isEqualTo(first.toString());
    assertThat(
            mvc.get()
                .uri("/v1/audit-logs")
                .header(AUTHORIZATION, read)
                .queryParam("q", unrelatedDetail)
                .exchange())
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.total")
        .isEqualTo(0);
  }

  private Map<String, Object> requestAudit(MvcTestResult result) {
    return jdbcTemplate.queryForMap(
        "SELECT actor_type,organization_id,detail->>'method' AS method,detail->>'route' AS"
            + " route,detail->>'status' AS status FROM audit_log WHERE detail->>'request_id'=?",
        result.getResponse().getHeader("X-Request-ID"));
  }

  private static String body(MvcTestResult result) {
    return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
  }
}
