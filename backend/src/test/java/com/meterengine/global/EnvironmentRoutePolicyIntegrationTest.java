package com.meterengine.global;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.EnvironmentTestSupport;
import org.junit.jupiter.api.Test;

class EnvironmentRoutePolicyIntegrationTest extends EnvironmentTestSupport {
  @Test
  void 세션의_업무_환경_누락과_legacy는_명시적_오류다() {
    for (var mode : new String[] {null, "legacy"}) {
      var result = get("/v1/customers", mode);
      assertThat(result.statusCode()).isEqualTo(400);
      assertThat(json(result).path("code").asString()).isEqualTo("validation_error");
    }
  }

  @Test
  void 키는_발급_환경을_사용하고_불일치와_환경_없는_키를_거절한다() {
    String key = issueKey("test", "read", true, null);
    assertThat(request("GET", "/v1/customers", key, null, null).statusCode()).isEqualTo(200);
    assertThat(request("GET", "/v1/customers", key, "live", null).statusCode()).isEqualTo(403);
    var denied = request("GET", "/v1/customers", issueKey("test", "read", false, null), null, null);
    assertThat(denied.statusCode()).isEqualTo(403);
    assertThat(json(denied).path("code").asString()).isEqualTo("environment_mismatch");
  }

  @Test
  void 공통_경로는_환경_없이_성공하고_단일_환경_헤더를_무시한다() {
    for (var path :
        new String[] {
          "/v1/sessions/current",
          "/v1/organization",
          "/v1/organization/environments",
          "/v1/organization/members",
          "/v1/organization/invitations",
          "/v1/user-accounts",
          "/v1/organizations",
          "/v1/operations/audit-logs"
        }) {
      assertThat(get(path, null).statusCode()).as(path).isEqualTo(200);
      assertThat(get(path, "live").statusCode()).as(path).isEqualTo(200);
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM shared.audit_log WHERE actor_id=? AND environment_id IS NOT NULL",
                Long.class,
                userId))
        .isZero();
  }

  @Test
  void 공통_요청의_인증된_한도_거절도_감사에_환경을_붙이지_않는다() {
    String key = issueKey("test", "read", true, 1);
    assertThat(request("GET", "/v1/sessions/current", key, "live", null).statusCode())
        .isEqualTo(403);
    var denied = request("GET", "/v1/sessions/current", key, "live", null);
    assertThat(denied.statusCode()).isEqualTo(429);
    assertThat(denied.headers().firstValue("Retry-After")).isPresent();
    assertThat(
            jdbc.queryForObject(
                "SELECT environment_id FROM shared.audit_log WHERE detail->>'request_id'=?",
                java.util.UUID.class,
                denied.headers().firstValue("X-Request-ID").orElseThrow()))
        .isNull();
  }

  @Test
  void 조직_헤더와_환경_헤더의_중복과_잘못된_값을_거절한다() {
    assertThat(request("GET", "/v1/customers", session, "test", null, HEADER, "live").statusCode())
        .isEqualTo(400);
    assertThat(get("/v1/organization", "test,live").statusCode()).isEqualTo(400);
    assertThat(
            request(
                    "GET",
                    "/v1/organization",
                    session,
                    null,
                    null,
                    "X-MeterEngine-Organization-Id",
                    "bad")
                .statusCode())
        .isEqualTo(400);
    assertThat(
            request(
                    "GET",
                    "/v1/organization",
                    session,
                    null,
                    null,
                    "X-MeterEngine-Organization-Id",
                    java.util.UUID.randomUUID().toString())
                .statusCode())
        .isEqualTo(403);
    assertThat(get("/v1/organization/memberships", null).statusCode()).isEqualTo(400);
    assertThat(get("/v1/operations/extra", null).statusCode()).isEqualTo(400);
  }

  @Test
  void 업무_실패_뒤_요청_감사는_shared에서_저장하고_익명_거절은_생략한다() {
    long before = jdbc.queryForObject("SELECT count(*) FROM shared.audit_log", Long.class);
    var anonymous = request("GET", "/v1/customers", null, null, null);
    assertThat(anonymous.statusCode()).isEqualTo(401);
    assertThat(anonymous.headers().firstValue("WWW-Authenticate")).contains("Bearer");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM shared.audit_log", Long.class))
        .isEqualTo(before);
    var malformed = request("POST", "/v1/customers", session, "live", "{broken");
    assertThat(malformed.statusCode()).isEqualTo(400);
    var row =
        jdbc.queryForMap(
            "SELECT environment_id,detail->>'status' AS status FROM shared.audit_log WHERE detail->>'request_id'=?",
            malformed.headers().firstValue("X-Request-ID").orElseThrow());
    assertThat(row.get("environment_id")).isEqualTo(environmentId("live"));
    assertThat(row.get("status")).isEqualTo("400");
    assertThat(get("/v1/sessions/current", null).statusCode()).isEqualTo(200);
    assertThat(
            context
                .getBean(io.micrometer.core.instrument.MeterRegistry.class)
                .get("meterengine.request.audit.failures")
                .counter()
                .count())
        .isZero();
  }

  @Test
  void 실제_Servlet과_보안_chain은_인증과_한도_뒤에_업무_scope를_등록한다() {
    var web =
        (org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext)
            context;
    var registrations = web.getServletContext().getFilterRegistrations();
    assertThat(registrations).containsKey("springSecurityFilterChain");
    assertThat(
            registrations.values().stream()
                .map(jakarta.servlet.FilterRegistration::getClassName)
                .toList())
        .contains("com.meterengine.global.database.DatabaseScopeFilter");
    var filters =
        context
            .getBean(org.springframework.security.web.SecurityFilterChain.class)
            .getFilters()
            .stream()
            .map(filter -> filter.getClass().getSimpleName())
            .toList();
    assertThat(filters.indexOf("RequestAuditFilter"))
        .isLessThan(filters.indexOf("ApiCredentialAuthenticationFilter"));
    assertThat(filters.indexOf("ApiCredentialAuthenticationFilter"))
        .isLessThan(filters.indexOf("RateLimitFilter"));
    assertThat(filters.indexOf("RateLimitFilter"))
        .isLessThan(filters.indexOf("BusinessEnvironmentFilter"));
    System.out.println("S3 Servlet registrations " + registrations.keySet());
    System.out.println("S3 Security filter order " + filters);
  }

  @Test
  void 실제_HTTP_SQL은_shared_인증과_업무_실행_뒤_shared_감사로_돌아온다() {
    com.meterengine.global.database.EnvironmentTestConfig.CONNECTION_TRACES.clear();
    var response = customer("test", "trace", "trace-key");
    assertThat(response.statusCode()).isEqualTo(201);
    var traces =
        com.meterengine.global.database.EnvironmentTestConfig.CONNECTION_TRACES.stream()
            .filter(trace -> trace.thread().contains("exec-"))
            .toList();
    var scopes = new java.util.ArrayList<com.meterengine.global.database.DatabaseScope>();
    for (var trace : traces) {
      assertThat(trace.role()).isEqualTo("meterengine_" + trace.schema());
      assertThat(trace.schema()).isEqualTo(trace.scope().name().toLowerCase(java.util.Locale.ROOT));
      if (scopes.isEmpty() || scopes.getLast() != trace.scope()) scopes.add(trace.scope());
    }
    assertThat(scopes)
        .containsExactly(
            com.meterengine.global.database.DatabaseScope.SHARED,
            com.meterengine.global.database.DatabaseScope.TEST,
            com.meterengine.global.database.DatabaseScope.SHARED);
    System.out.println("S3 actual HTTP SQL scope trace " + traces);
  }

  @Test
  void 시험_fixture는_일반_애플리케이션_scan에서_제외한다() {
    var filter =
        context.getBean(
            "org.springframework.boot.test.context.filter.TestTypeExcludeFilter",
            org.springframework.boot.context.TypeExcludeFilter.class);
    var scanner =
        new org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider(
            true);
    scanner.addExcludeFilter(filter);
    var candidates =
        scanner.findCandidateComponents("com.meterengine.global.database").stream()
            .map(org.springframework.beans.factory.config.BeanDefinition::getBeanClassName)
            .toList();
    assertThat(candidates)
        .contains(
            "com.meterengine.global.database.DatabaseConfig",
            "com.meterengine.global.database.DatabaseScopeFilter")
        .doesNotContain("com.meterengine.global.database.EnvironmentTestConfig");
    System.out.println("S3 actual Boot broad-scan fixture exclusion " + candidates);
  }

  @Test
  void 환경_조회의_예상하지_못한_오류도_기존_JSON_오류를_보존한다() {
    ownerJdbc().execute("REVOKE SELECT ON shared.organization_environment FROM meterengine_shared");
    try {
      var failure = get("/v1/customers", "test");
      assertThat(failure.statusCode()).isEqualTo(500);
      assertThat(json(failure).path("code").asString()).isEqualTo("internal_server_error");
      assertThat(get("/v1/sessions/current", null).statusCode()).isEqualTo(200);
    } finally {
      ownerJdbc().execute("GRANT SELECT ON shared.organization_environment TO meterengine_shared");
    }
    assertThat(get("/v1/customers", "test").statusCode()).isEqualTo(200);
  }
}
