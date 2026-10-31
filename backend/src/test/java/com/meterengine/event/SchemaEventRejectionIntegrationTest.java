package com.meterengine.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meterengine.MeteringEnvironmentTestSupport;
import com.meterengine.event.service.EventRejection;
import com.meterengine.event.service.EventRejectionStorageService;
import com.meterengine.global.error.ErrorCode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SchemaEventRejectionIntegrationTest extends MeteringEnvironmentTestSupport {
  @Test
  void 실제_단건과_배치는_고객_경계를_검사하고_부분성공과_거절_원문_비기록을_보존한다() {
    UUID customer = UUID.randomUUID();
    createCustomer("test", customer, "only-test");
    String key = issueKey("test", "ingest", true, null);
    String event = eventJson("same", customer, "{\"secret\":\"never-store-rejected-properties\"}");
    var first = request("POST", "/v1/events", key, null, event);
    assertThat(first.statusCode()).as(first.body()).isEqualTo(200);
    assertThat(json(first).get("duplicate").asBoolean()).isFalse();
    assertThat(json(request("POST", "/v1/events", key, null, event)).get("duplicate").asBoolean())
        .isTrue();
    UUID missing = UUID.randomUUID();
    String invalid =
        eventJson("missing", missing, "{\"secret\":\"never-store-rejected-properties\"}");
    var batch =
        request(
            "POST",
            "/v1/event-batches",
            key,
            null,
            "{\"events\":["
                + event
                + ","
                + eventJson("new", customer, "{}")
                + ","
                + invalid
                + "]}");
    assertThat(batch.statusCode()).as(batch.body()).isEqualTo(200);
    assertThat(json(batch).get("results").get(0).get("result").asString()).isEqualTo("duplicate");
    assertThat(json(batch).get("results").get(1).get("result").asString()).isEqualTo("stored");
    assertThat(json(batch).get("results").get(2).get("result").asString()).isEqualTo("rejected");
    var live = request("POST", "/v1/events", session, "live", event);
    assertThat(live.statusCode()).isEqualTo(404);
    var rejections = get("/v1/event-rejections/search", "test");
    assertThat(rejections.statusCode()).as(rejections.body()).isEqualTo(200);
    assertThat(json(rejections).get("total").asLong()).isEqualTo(1);
    assertThat(rejections.body())
        .contains("customer_not_found")
        .doesNotContain("never-store-rejected-properties", "properties", "actor_id", key);
    transactions.read(
        scope("test"),
        () -> {
          assertThat(
                  jdbc.queryForObject(
                      "SELECT count(*) FROM event WHERE organization_id=?",
                      Long.class,
                      organizationId))
              .isEqualTo(2);
          assertThat(
                  jdbc.queryForObject(
                      "SELECT actor_type FROM event_rejection WHERE organization_id=?",
                      String.class,
                      organizationId))
              .isEqualTo("api_key");
          assertThat(
                  jdbc.queryForObject(
                      "SELECT request_id FROM event_rejection WHERE organization_id=?",
                      UUID.class,
                      organizationId))
              .isEqualTo(UUID.fromString(batch.headers().firstValue("X-Request-ID").orElseThrow()));
          return null;
        });
    transactions.read(
        scope("live"),
        () -> {
          assertThat(
                  jdbc.queryForObject(
                      "SELECT count(*) FROM event WHERE organization_id=?",
                      Long.class,
                      organizationId))
              .isZero();
          assertThat(
                  jdbc.queryForObject(
                      "SELECT count(*) FROM event_rejection WHERE organization_id=?",
                      Long.class,
                      organizationId))
              .isEqualTo(1);
          return null;
        });
    assertThat(get("/v1/customers", "test").statusCode()).isEqualTo(200);
    assertThat(request("GET", "/v1/customers", key, null, null).statusCode()).isEqualTo(403);
    jdbc.update(
        "UPDATE shared.api_key SET scopes=ARRAY['ingest','read']::varchar[] WHERE key_hash=?",
        com.meterengine.global.security.SecureTokens.hash(key.substring("Bearer ".length())));
    assertThat(request("GET", "/v1/customers", key, null, null).statusCode()).isEqualTo(200);
    assertThat(
            request("POST", "/v1/events", key, null, eventJson("after-scopes", customer, "{}"))
                .statusCode())
        .isEqualTo(200);
  }

  @Test
  void 거절_기록은_외부_트랜잭션의_롤백에도_남는다() {
    var storage = context.getBean(EventRejectionStorageService.class);
    assertThatThrownBy(
            () ->
                transactions.write(
                    scope("test"),
                    () -> {
                      storage.record(
                          organizationId,
                          null,
                          UUID.randomUUID(),
                          List.of(
                              new EventRejection(
                                  "independent",
                                  UUID.randomUUID(),
                                  "tokens",
                                  OffsetDateTime.now(),
                                  ErrorCode.CUSTOMER_NOT_FOUND,
                                  List.of(),
                                  null)));
                      throw new IllegalStateException("outer rollback");
                    }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("outer rollback");
    transactions.read(
        scope("test"),
        () -> {
          assertThat(
                  jdbc.queryForObject(
                      "SELECT count(*) FROM event_rejection WHERE organization_id=?",
                      Long.class,
                      organizationId))
              .isEqualTo(1);
          return null;
        });
  }

  @Test
  void 거절_저장_실패가_원래_오류와_배치의_정상_저장을_가리지_않는다() {
    UUID customer = UUID.randomUUID();
    createCustomer("test", customer, "failure");
    var owner = ownerJdbc();
    owner.execute(
        "CREATE FUNCTION test.s4_reject_record() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 's4-record-failure'; END $$");
    owner.execute(
        "CREATE TRIGGER s4_reject_record BEFORE INSERT ON test.event_rejection FOR EACH ROW EXECUTE FUNCTION test.s4_reject_record()");
    try {
      assertThat(
              request(
                      "POST",
                      "/v1/events",
                      session,
                      "test",
                      eventJson("missing", UUID.randomUUID(), "{}"))
                  .statusCode())
          .isEqualTo(404);
      var batch =
          request(
              "POST",
              "/v1/event-batches",
              session,
              "test",
              "{\"events\":["
                  + eventJson("valid", customer, "{}")
                  + ","
                  + eventJson("invalid", UUID.randomUUID(), "{}")
                  + "]}");
      assertThat(batch.statusCode()).isEqualTo(200);
      assertThat(json(batch).get("results").get(0).get("result").asString()).isEqualTo("stored");
      transactions.read(
          scope("test"),
          () -> {
            assertThat(
                    jdbc.queryForObject(
                        "SELECT count(*) FROM event WHERE organization_id=?",
                        Long.class,
                        organizationId))
                .isEqualTo(1);
            assertThat(
                    jdbc.queryForObject(
                        "SELECT count(*) FROM event_rejection WHERE organization_id=?",
                        Long.class,
                        organizationId))
                .isZero();
            return null;
          });
    } finally {
      owner.execute("DROP TRIGGER s4_reject_record ON test.event_rejection");
      owner.execute("DROP FUNCTION test.s4_reject_record()");
    }
  }

  @Test
  void 진단과_문제검색은_선택한_스키마에서만_유형과_수치와_가격을_판정한다() {
    UUID customer = UUID.randomUUID();
    createCustomer("test", customer, "diagnostics");
    assertThat(createMetric("test", "sum", "sum").statusCode()).isEqualTo(201);
    assertThat(
            request(
                    "POST",
                    "/v1/events",
                    session,
                    "test",
                    eventJson("invalid", customer, "{\"quantity\":\"text\"}"))
                .statusCode())
        .isEqualTo(200);
    assertThat(
            request(
                    "POST",
                    "/v1/events",
                    session,
                    "test",
                    eventJson("negative", customer, "{\"quantity\":-3}"))
                .statusCode())
        .isEqualTo(200);
    assertThat(
            request(
                    "POST",
                    "/v1/events",
                    session,
                    "test",
                    eventJson("unpriced", customer, "{\"quantity\":9}"))
                .statusCode())
        .isEqualTo(200);
    assertThat(
            request(
                    "POST",
                    "/v1/events",
                    session,
                    "test",
                    eventJson("unknown", customer, "{}").replace("tokens", "unknown"))
                .statusCode())
        .isEqualTo(200);
    var diagnostics = get("/v1/unbilled-events", "test");
    assertThat(diagnostics.statusCode()).as(diagnostics.body()).isEqualTo(200);
    assertThat(json(diagnostics).get("unmatched_type").get("event_count").asLong()).isEqualTo(1);
    assertThat(json(diagnostics).get("non_numeric_value").get("event_count").asLong()).isEqualTo(1);
    assertThat(json(diagnostics).get("negative_quantity").get("event_count").asLong()).isEqualTo(1);
    assertThat(
            json(get("/v1/unbilled-events", "live"))
                .get("unmatched_type")
                .get("event_count")
                .asLong())
        .isZero();
    var issues = get("/v1/event-issues/search", "test");
    assertThat(issues.statusCode()).as(issues.body()).isEqualTo(200);
    assertThat(json(issues).get("summary").get("unknown_event_type").asLong()).isEqualTo(1);
    assertThat(json(issues).get("summary").get("invalid_target_property").asLong()).isEqualTo(1);
    assertThat(json(issues).get("summary").get("unpriced_usage").asLong()).isEqualTo(2);
    assertThat(json(get("/v1/event-issues/search", "live")).get("total").asLong()).isZero();
  }

  @Test
  void 실제_업무_역할로도_이벤트와_거절_원문은_수정하거나_삭제할_수_없다() {
    UUID customer = UUID.randomUUID();
    createCustomer("test", customer, "immutable");
    assertThat(
            request("POST", "/v1/events", session, "test", eventJson("immutable", customer, "{}"))
                .statusCode())
        .isEqualTo(200);
    assertThat(
            request(
                    "POST",
                    "/v1/events",
                    session,
                    "test",
                    eventJson("missing", UUID.randomUUID(), "{}"))
                .statusCode())
        .isEqualTo(404);
    for (String table : List.of("event", "event_rejection")) {
      for (String sql :
          List.of(
              "DELETE FROM " + table + " WHERE organization_id=?",
              "UPDATE " + table + " SET type='changed' WHERE organization_id=?")) {
        assertThatThrownBy(
                () -> transactions.write(scope("test"), () -> jdbc.update(sql, organizationId)))
            .isInstanceOf(org.springframework.dao.DataAccessException.class);
      }
      transactions.read(
          scope("test"),
          () -> {
            assertThat(
                    jdbc.queryForObject(
                        "SELECT count(*) FROM " + table + " WHERE organization_id=?",
                        Long.class,
                        organizationId))
                .isEqualTo(1);
            return null;
          });
    }
  }
}
