package com.meterengine.apikey;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.EnvironmentTestSupport;
import org.junit.jupiter.api.Test;

class ApiKeyScopesSchemaIntegrationTest extends EnvironmentTestSupport {
  @Test
  void 키_관리와_감사_조회는_환경이_맞아도_세션만_허용한다() {
    String key = issueKey("test", "admin", true, null);
    assertThat(request("GET", "/v1/api-keys", key, null, null).statusCode()).isEqualTo(403);
    assertThat(request("GET", "/v1/audit-logs", key, null, null).statusCode()).isEqualTo(403);
    assertThat(get("/v1/api-keys", null).statusCode()).isEqualTo(400);
    assertThat(get("/v1/api-keys", "test").statusCode()).isEqualTo(200);
    assertThat(get("/v1/audit-logs", null).statusCode()).isEqualTo(400);
  }

  @Test
  void 키_권한_변경은_shared에_저장되고_다음_인증부터_적용된다() {
    var issued =
        request(
            "POST",
            "/v1/api-keys",
            session,
            "test",
            "{\"label\":\"s3\",\"mode\":\"test\",\"scopes\":[\"admin\"]}");
    assertThat(issued.statusCode()).isEqualTo(201);
    String id = json(issued).path("id").asString();
    String key = "Bearer " + json(issued).path("secret").asString();
    assertThat(
            request(
                    "PUT",
                    "/v1/api-keys/" + id + "/scopes",
                    session,
                    "test",
                    "{\"scopes\":[\"read\"]}")
                .statusCode())
        .isEqualTo(200);
    assertThat(request("GET", "/v1/customers", key, null, null).statusCode()).isEqualTo(200);
    assertThat(request("POST", "/v1/customers", key, null, "{\"name\":\"denied\"}").statusCode())
        .isEqualTo(403);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM shared.audit_log WHERE organization_id=? AND action='api_key_scopes_updated'",
                Long.class,
                organizationId))
        .isEqualTo(1);
    assertThat(
            request(
                    "PUT",
                    "/v1/api-keys/" + id + "/scopes",
                    session,
                    "live",
                    "{\"scopes\":[\"admin\"]}")
                .statusCode())
        .isEqualTo(404);
  }

  @Test
  void 수집_키의_재등록과_멱등_재생은_개인정보를_노출하지_않는다() {
    assertThat(customer("test", "same", "admin-create").statusCode()).isEqualTo(201);
    String key = issueKey("test", "ingest", true, null);
    for (int index = 0; index < 2; index++) {
      var result =
          request(
              "POST",
              "/v1/customers",
              key,
              null,
              "{\"name\":\"hidden\",\"external_id\":\"same\"}",
              "Idempotency-Key",
              "ingest-retry");
      assertThat(result.statusCode()).isEqualTo(200);
      assertThat(json(result).path("name").isNull()).isTrue();
      assertThat(json(result).path("billing_email").isNull()).isTrue();
      if (index == 1)
        assertThat(result.headers().firstValue("Idempotency-Replayed")).contains("true");
    }
  }

  @Test
  void 키_발급과_권한_변경은_감사와_같은_shared_트랜잭션이다() {
    rejectAudit(
        "api_key_created",
        () ->
            assertThat(
                    request(
                            "POST",
                            "/v1/api-keys",
                            session,
                            "test",
                            "{\"label\":\"rollback\",\"mode\":\"test\",\"scopes\":[\"admin\"]}")
                        .statusCode())
                .isEqualTo(500));
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM shared.api_key WHERE organization_id=?",
                Long.class,
                organizationId))
        .isZero();
    var issued =
        request(
            "POST",
            "/v1/api-keys",
            session,
            "test",
            "{\"label\":\"atomic\",\"mode\":\"test\",\"scopes\":[\"admin\"]}");
    String id = json(issued).path("id").asString();
    rejectAudit(
        "api_key_scopes_updated",
        () ->
            assertThat(
                    request(
                            "PUT",
                            "/v1/api-keys/" + id + "/scopes",
                            session,
                            "test",
                            "{\"scopes\":[\"read\"]}")
                        .statusCode())
                .isEqualTo(500));
    assertThat(
            jdbc.queryForObject(
                "SELECT scopes=ARRAY['admin']::varchar[] FROM shared.api_key WHERE id=?",
                Boolean.class,
                java.util.UUID.fromString(id)))
        .isTrue();
  }

  @Test
  void 키_권한_변경과_폐기는_실제_shared_행_잠금으로_직렬화한다() throws Exception {
    var issued =
        request(
            "POST",
            "/v1/api-keys",
            session,
            "test",
            "{\"label\":\"lock\",\"mode\":\"test\",\"scopes\":[\"admin\"]}");
    var id = java.util.UUID.fromString(json(issued).path("id").asString());
    var selected =
        new com.meterengine.global.security.BusinessEnvironment(
            organizationId, environmentId("test"), "test");
    var service = context.getBean(com.meterengine.apikey.service.ApiKeyService.class);
    var locked = new java.util.concurrent.CountDownLatch(1);
    var release = new java.util.concurrent.CountDownLatch(1);
    var update =
        java.util.concurrent.CompletableFuture.runAsync(
            () ->
                transactions.write(
                    com.meterengine.global.database.DatabaseScope.SHARED,
                    () ->
                        com.meterengine.global.security.BusinessEnvironmentContext.run(
                            selected,
                            () -> {
                              service.update(organizationId, id, java.util.List.of("read"));
                              locked.countDown();
                              try {
                                if (!release.await(10, java.util.concurrent.TimeUnit.SECONDS))
                                  throw new IllegalStateException("lock release timed out");
                              } catch (InterruptedException exception) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException(exception);
                              }
                              return null;
                            })));
    assertThat(locked.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
    var revoke =
        java.util.concurrent.CompletableFuture.runAsync(
            () ->
                com.meterengine.global.security.BusinessEnvironmentContext.run(
                    selected, () -> service.delete(organizationId, id)));
    try {
      long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
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
      assertThat(revoke).isNotDone();
      System.out.println("S3 key locking: actual meterengine_shared transaction waits on row lock");
    } finally {
      release.countDown();
    }
    update.get(10, java.util.concurrent.TimeUnit.SECONDS);
    revoke.get(10, java.util.concurrent.TimeUnit.SECONDS);
    assertThat(
            jdbc.queryForObject(
                "SELECT revoked_at IS NOT NULL AND scopes=ARRAY['read']::varchar[] FROM shared.api_key WHERE id=?",
                Boolean.class,
                id))
        .isTrue();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM shared.audit_log WHERE organization_id=? AND action IN ('api_key_scopes_updated','api_key_deleted')",
                Long.class,
                organizationId))
        .isEqualTo(2);
  }
}
