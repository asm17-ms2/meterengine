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
import com.meterengine.audit.service.RequestAuditService;
import com.meterengine.global.security.AuthenticatedPrincipal;
import com.meterengine.global.security.BusinessEnvironment;
import com.meterengine.global.security.BusinessEnvironmentContext;
import com.meterengine.global.security.PrincipalKind;
import com.meterengine.global.security.RequestAuditTarget;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class AuditQueryAndRequestIntegrationTest {
  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private AuditLogService auditLogService;
  @Autowired private RequestAuditService requestAuditService;
  private MockMvcTester mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcTester.from(
            webApplicationContext, builder -> builder.apply(springSecurity()).build());
  }

  @Test
  void 도입사에_소속된_운영자의_운영자_전용_요청은_그_도입사의_감사에_남지_않는다() {
    UUID organizationId = UUID.randomUUID();
    String session = TestCredentials.issueMemberSession(jdbcTemplate, organizationId);
    jdbcTemplate.update(
        "UPDATE user_account SET role = 'operator' WHERE id = (SELECT user_account_id FROM organization_member WHERE organization_id = ?)",
        organizationId);

    MvcTestResult result =
        mvc.get().uri("/v1/operations").header(AUTHORIZATION, session).exchange();

    assertThat(result).hasStatusOk();
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT organization_id FROM audit_log WHERE detail->>'request_id'=?",
                UUID.class,
                result.getResponse().getHeader("X-Request-ID")))
        .containsExactly((UUID) null);

    UUID environmentId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM organization_environment WHERE organization_id=? AND mode='test'",
            UUID.class,
            organizationId);
    MvcTestResult scoped =
        mvc.get()
            .uri("/v1/operations")
            .header(AUTHORIZATION, session)
            .header("X-MeterEngine-Environment", "test")
            .exchange();
    assertThat(scoped).hasStatusOk();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT environment_id FROM audit_log WHERE detail->>'request_id'=?",
                UUID.class,
                scoped.getResponse().getHeader("X-Request-ID")))
        .isEqualTo(environmentId);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void 요청_문맥과_달라도_주체의_환경을_보존하고_익명은_환경을_물려받지_않는다() {
    UUID organizationId = UUID.randomUUID();
    TestCredentials.issueMemberSession(jdbcTemplate, organizationId);
    UUID environmentId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM organization_environment WHERE organization_id=? AND mode='test'",
            UUID.class,
            organizationId);
    UUID requestId = UUID.randomUUID();
    requestAuditService.record(
        new AuthenticatedPrincipal(
            PrincipalKind.API_KEY,
            organizationId,
            UUID.randomUUID(),
            null,
            null,
            "test",
            null,
            Set.of("read"),
            environmentId),
        "GET",
        "/v1/customers",
        403,
        1,
        requestId,
        RequestAuditTarget.empty());
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT environment_id FROM audit_log WHERE detail->>'request_id'=?",
                UUID.class,
                requestId.toString()))
        .isEqualTo(environmentId);
    UUID anonymous = UUID.randomUUID();
    BusinessEnvironmentContext.run(
        new BusinessEnvironment(organizationId, environmentId, "test"),
        () ->
            requestAuditService.record(
                null, "GET", "/v1/unknown", 400, 1, anonymous, RequestAuditTarget.empty()));
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT environment_id FROM audit_log WHERE detail->>'request_id'=?",
                UUID.class,
                anonymous.toString()))
        .isNull();
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
  void 감사조회는_발생시각과_id로_정렬하고_정렬방향을_지킨다() {
    UUID organizationId = UUID.randomUUID();
    String read = TestCredentials.issueApiKey(jdbcTemplate, organizationId, "read");
    String target = UUID.randomUUID().toString();
    String prefix = UUID.randomUUID().toString().substring(0, 35);
    String smaller = prefix + "1";
    String larger = prefix + "2";
    String earliest = UUID.randomUUID().toString();
    insertAudit(larger, "2026-10-01T00:01:00Z", organizationId, target);
    insertAudit(smaller, "2026-10-01T00:01:00Z", organizationId, target);
    insertAudit(earliest, "2026-10-01T00:00:00Z", organizationId, target);

    assertThat(auditIds(read, target, "occurred_at_asc"))
        .containsExactly(earliest, smaller, larger);
    assertThat(auditIds(read, target, null)).containsExactly(larger, smaller, earliest);
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
            Map.of("to", "+300000-01-01T00:00:00Z"),
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
  void 주체_없는_401은_요청_감사에_남기지_않는다() {
    MvcTestResult anonymous = mvc.get().uri("/v1/customers").exchange();
    assertThat(anonymous).hasStatus(401);
    MvcTestResult invalid =
        mvc.get()
            .uri("/v1/customers")
            .header(AUTHORIZATION, "Bearer invalid-secret-value")
            .exchange();
    assertThat(invalid).hasStatus(401);

    assertThat(requestAuditCount(anonymous)).isZero();
    assertThat(requestAuditCount(invalid)).isZero();
  }

  @Test
  void 주체_없는_429는_로그인과_가입만_요청_감사에_남긴다() {
    UUID other = UUID.randomUUID();
    UUID login = UUID.randomUUID();
    UUID signup = UUID.randomUUID();
    requestAuditService.record(
        null, "GET", "/v1/customers", 429, 1, other, RequestAuditTarget.empty());
    requestAuditService.record(
        null, "POST", "/v1/sessions", 429, 1, login, RequestAuditTarget.empty());
    requestAuditService.record(
        null, "POST", "/v1/signups", 429, 1, signup, RequestAuditTarget.empty());

    assertThat(requestAuditCount(other)).isZero();
    assertThat(requestAuditCount(login)).isOne();
    assertThat(requestAuditCount(signup)).isOne();
  }

  @Test
  void 요청_감사행은_수정할_수_없다() {
    String key = TestCredentials.issueApiKey(jdbcTemplate, UUID.randomUUID(), "read");
    MvcTestResult read = mvc.get().uri("/v1/customers").header(AUTHORIZATION, key).exchange();
    assertThat(read).hasStatusOk();
    String requestId = read.getResponse().getHeader("X-Request-ID");
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE audit_log SET action='modified' WHERE detail->>'request_id'=?",
                    requestId))
        .isInstanceOf(UncategorizedSQLException.class)
        .hasMessageContaining("append-only");
  }

  @Test
  void 체인_안에서_방화벽이_거절한_요청은_400_감사행_하나만_남긴다() {
    long before = requestAuditCount();
    MvcTestResult rejected =
        mvc.get()
            .uri("/v1/customers")
            .header(AUTHORIZATION, "Bearer me_live_" + "R".repeat(32) + "\u0007")
            .exchange();
    assertThat(rejected).hasStatus(400);
    assertThat(requestAuditCount()).isEqualTo(before + 1);
    assertThat(requestAudit(rejected))
        .containsEntry("status", "400")
        .containsEntry("actor_type", "anonymous");
  }

  @Test
  void 체인_앞에서_방화벽이_거절한_요청도_400_감사행_하나를_남긴다() {
    long before = requestAuditCount();
    MvcTestResult rejected =
        mvc.get()
            .uri(URI.create("/v1/payment-method-registrations/tok3nSecretValue;x=1"))
            .exchange();
    assertThat(rejected).hasStatus(400);
    assertThat(requestAuditCount()).isEqualTo(before + 1);
    assertThat(
            jdbcTemplate.queryForMap(
                "SELECT actor_type,detail->>'route' AS route,detail->>'status' AS status FROM"
                    + " audit_log WHERE action='api_request_completed' ORDER BY occurred_at DESC"
                    + " LIMIT 1"))
        .containsEntry("status", "400")
        .containsEntry("route", "/v1/{unmatched}")
        .containsEntry("actor_type", "anonymous");
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

  private void insertAudit(String id, String occurredAt, UUID organizationId, String target) {
    jdbcTemplate.update(
        "INSERT INTO audit_log(id,occurred_at,organization_id,actor_type,action,target_type,"
            + "target_id) VALUES(?::uuid,?::timestamptz,?,'system','customer_created',"
            + "'customer',?)",
        id,
        occurredAt,
        organizationId,
        target);
  }

  private List<String> auditIds(String authorization, String target, String sort) {
    var request =
        mvc.get()
            .uri("/v1/audit-logs")
            .header(AUTHORIZATION, authorization)
            .queryParam("q", target);
    if (sort != null) request.queryParam("sort", sort);
    MvcTestResult result = request.exchange();
    assertThat(result).hasStatusOk();
    return JsonPath.read(body(result), "$.audit_logs[*].id");
  }

  private long requestAuditCount(MvcTestResult result) {
    return requestAuditCount(UUID.fromString(result.getResponse().getHeader("X-Request-ID")));
  }

  private long requestAuditCount(UUID requestId) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM audit_log WHERE detail->>'request_id'=?",
        Long.class,
        requestId.toString());
  }

  private long requestAuditCount() {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM audit_log WHERE action='api_request_completed'", Long.class);
  }

  private static String body(MvcTestResult result) {
    return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
  }
}
