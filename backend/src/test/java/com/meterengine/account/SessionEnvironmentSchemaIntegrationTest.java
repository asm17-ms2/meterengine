package com.meterengine.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.EnvironmentTestSupport;
import com.meterengine.account.service.SessionService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class SessionEnvironmentSchemaIntegrationTest extends EnvironmentTestSupport {
  private static final String CONSENTS =
      "{\"new_password\":\"changed-password\",\"consents\":[{\"document\":\"terms_of_service\",\"document_version\":\"v1\"},{\"document\":\"privacy_policy\",\"document_version\":\"v1\"}]}";

  @Test
  void 실패한_로그인_카운터는_shared에_남고_앞_한도_초과는_뒤_한도를_소비하지_않는다() {
    String email = userId + "@example.com";
    String payload = "{\"email\":\"" + email + "\",\"password\":\"wrong-password\"}";
    for (int index = 0; index < 10; index++)
      assertThat(request("POST", "/v1/sessions", null, null, payload).statusCode()).isEqualTo(401);
    var blocked = request("POST", "/v1/sessions", null, null, payload);
    assertThat(blocked.statusCode()).isEqualTo(429);
    assertThat(blocked.headers().firstValue("Retry-After")).isPresent();
    var counts =
        jdbc.queryForList(
            "SELECT attempts FROM shared.auth_request_rate_limit WHERE scope='login_account' ORDER BY attempts",
            Long.class);
    assertThat(counts).containsExactly(10L, 11L);
    assertThat(request("POST", "/v1/sessions", null, null, payload).statusCode()).isEqualTo(429);
    assertThat(
            jdbc.queryForList(
                "SELECT attempts FROM shared.auth_request_rate_limit WHERE scope='login_account' ORDER BY attempts",
                Long.class))
        .isEqualTo(counts);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM shared.audit_log WHERE action='api_request_completed' AND detail->>'route'='/v1/sessions' AND detail->>'status' IN ('401','429')",
                Long.class))
        .isGreaterThanOrEqualTo(12);
  }

  @Test
  void 첫_동의는_비밀번호와_다른_세션을_감사와_함께_변경한다() {
    jdbc.update("UPDATE shared.user_account SET consent_required=true WHERE id=?", userId);
    String other = issueSession(userId);
    assertThat(get("/v1/customers", "test").statusCode()).isEqualTo(403);
    assertThat(get("/v1/sessions/current", null).statusCode()).isEqualTo(200);
    assertThat(
            request("POST", "/v1/sessions/current/consents", session, null, CONSENTS).statusCode())
        .isEqualTo(200);
    assertThat(
            passwordEncoder.matches(
                "changed-password",
                jdbc.queryForObject(
                    "SELECT password_hash FROM shared.user_account WHERE id=?",
                    String.class,
                    userId)))
        .isTrue();
    assertThat(request("GET", "/v1/sessions/current", other, null, null).statusCode())
        .isEqualTo(401);
    assertThat(get("/v1/sessions/current", null).statusCode()).isEqualTo(200);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM shared.consent WHERE user_account_id=?", Long.class, userId))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM shared.audit_log WHERE actor_id=? AND action IN ('user_account_password_updated','user_account_consents_accepted','session_deleted') AND environment_id IS NULL",
                Long.class,
                userId))
        .isEqualTo(3);
  }

  @Test
  void 첫_동의의_감사_실패는_비밀번호와_동의와_세션_폐기를_모두_롤백한다() {
    jdbc.update("UPDATE shared.user_account SET consent_required=true WHERE id=?", userId);
    String other = issueSession(userId);
    rejectAudit(
        "user_account_consents_accepted",
        () ->
            assertThat(
                    request("POST", "/v1/sessions/current/consents", session, null, CONSENTS)
                        .statusCode())
                .isEqualTo(500));
    assertThat(
            jdbc.queryForObject(
                "SELECT consent_required FROM shared.user_account WHERE id=?",
                Boolean.class,
                userId))
        .isTrue();
    assertThat(
            passwordEncoder.matches(
                "fixture-password",
                jdbc.queryForObject(
                    "SELECT password_hash FROM shared.user_account WHERE id=?",
                    String.class,
                    userId)))
        .isTrue();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM shared.consent WHERE user_account_id=?", Long.class, userId))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM shared.audit_log WHERE actor_id=? AND action='user_account_password_updated'",
                Long.class,
                userId))
        .isZero();
    assertThat(request("GET", "/v1/sessions/current", other, null, null).statusCode())
        .isEqualTo(200);
  }

  @Test
  void 비밀번호_비교_뒤_잠금_대기에서_바뀐_해시를_재검사한다() throws Exception {
    var sessionService = context.getBean(SessionService.class);
    long sessions =
        jdbc.queryForObject(
            "SELECT count(*) FROM shared.user_session WHERE user_account_id=?", Long.class, userId);
    var dataSource = context.getBean(javax.sql.DataSource.class);
    try (var connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      try (var statement =
          connection.prepareStatement(
              "UPDATE shared.user_account SET password_hash=? WHERE id=?")) {
        statement.setString(1, passwordEncoder.encode("changed-password"));
        statement.setObject(2, userId);
        statement.executeUpdate();
      }
      var login =
          CompletableFuture.supplyAsync(
              () -> sessionService.create(userId + "@example.com", "fixture-password"));
      try {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline
            && ownerJdbc()
                    .queryForObject(
                        "SELECT count(*) FROM pg_stat_activity WHERE usename='meterengine_shared' AND wait_event_type='Lock'",
                        Long.class)
                == 0) Thread.sleep(10);
        assertThat(
                ownerJdbc()
                    .queryForObject(
                        "SELECT count(*) FROM pg_stat_activity WHERE usename='meterengine_shared' AND wait_event_type='Lock'",
                        Long.class))
            .isGreaterThan(0);
      } finally {
        connection.commit();
      }
      assertThat(login)
          .failsWithin(10, TimeUnit.SECONDS)
          .withThrowableOfType(ExecutionException.class)
          .withCauseInstanceOf(com.meterengine.global.error.UnauthorizedException.class);
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM shared.user_session WHERE user_account_id=?",
                Long.class,
                userId))
        .isEqualTo(sessions);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM shared.audit_log WHERE actor_id=? AND action='session_created'",
                Long.class,
                userId))
        .isZero();
  }
}
