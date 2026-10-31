package com.meterengine.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.MeteringEnvironmentTestSupport;
import com.meterengine.global.database.MeteringEnvironmentTestConfig;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class SchemaEventExportIntegrationTest extends MeteringEnvironmentTestSupport {
  @Test
  void 실제_CSV는_전체조건과_BOM과_CRLF와_수식방지와_JSON_숫자를_보존한다() {
    UUID customer = UUID.randomUUID();
    createCustomer("test", customer, "literal%_!");
    String json =
        "{\"quantity\":123456789012345678901234567890.123456789012345678,\"city\":\"서울\",\"secret\":\"original-only\"}";
    assertThat(
            request("POST", "/v1/events", session, "test", eventJson("=SUM(1)", customer, json))
                .statusCode())
        .isEqualTo(200);
    assertThat(
            request(
                    "POST",
                    "/v1/events",
                    session,
                    "test",
                    eventJson("other", customer, "{\"city\":\"부산\"}"))
                .statusCode())
        .isEqualTo(200);
    String suffix =
        "&customer_id="
            + customer
            + "&type=tokens&q="
            + encode("%_!")
            + "&properties="
            + encode("{\"city\":\"서울\"}")
            + "&sort=transaction_id&order=asc";
    var csv = get(path(suffix), "test");
    assertThat(csv.statusCode()).as(csv.body()).isEqualTo(200);
    assertThat(csv.body()).startsWith("\ufeff\"transaction_id\"").endsWith("\r\n");
    assertThat(csv.body().split("\r\n")).hasSize(2);
    assertThat(csv.body())
        .contains("'=SUM(1)", "같은 고객")
        .doesNotContain("original-only", "\"properties\"", "\"other\"");
    var original = get(path(suffix + "&include_properties=true"), "test");
    assertThat(original.statusCode()).as(original.body()).isEqualTo(200);
    assertThat(original.body())
        .contains(
            "123456789012345678901234567890.123456789012345678", "original-only", "\"properties\"");
    assertThat(get(path(""), "live").body().split("\r\n")).hasSize(1);
    assertThat(get(path("&sort=invalid"), "test").statusCode()).isEqualTo(400);
  }

  @Test
  void 만행까지_내보내고_초과나_원문_32MiB_초과는_잘린_CSV로_숨기지_않는다() {
    UUID customer = UUID.randomUUID();
    createCustomer("test", customer, "caps");
    transactions.write(
        scope("test"),
        () -> {
          jdbc.update(
              "INSERT INTO event(organization_id,transaction_id,customer_id,type,properties,occurred_at) SELECT ?, 'bulk-'||n, ?, 'tokens','{}',now() FROM generate_series(1,10000) n",
              organizationId,
              customer);
          return null;
        });
    var maximum = get(path(""), "test");
    assertThat(maximum.statusCode())
        .as(maximum.body().substring(0, Math.min(maximum.body().length(), 200)))
        .isEqualTo(200);
    assertThat(maximum.body().split("\r\n")).hasSize(10001);
    assertThat(
            request("POST", "/v1/events", session, "test", eventJson("extra", customer, "{}"))
                .statusCode())
        .isEqualTo(200);
    assertThat(get(path(""), "test").statusCode()).isEqualTo(400);
    transactions.write(
        scope("live"),
        () -> {
          jdbc.update(
              "INSERT INTO customer(id,organization_id,name) VALUES (?,?,'대형 원문')",
              customer,
              organizationId);
          jdbc.update(
              "INSERT INTO event(organization_id,transaction_id,customer_id,type,properties,occurred_at) SELECT ?, 'large-'||n, ?, 'tokens',jsonb_build_object('large',repeat('a',17000000)),now() FROM generate_series(1,2) n",
              organizationId,
              customer);
          return null;
        });
    assertThat(get(path("&include_properties=true"), "live").statusCode()).isEqualTo(400);
    var without = get(path(""), "live");
    assertThat(without.statusCode()).isEqualTo(200);
    assertThat(without.body().split("\r\n")).hasSize(3);
    assertThat(without.body()).doesNotContain("large\"\":");
  }

  @Test
  void 건수확인_뒤_실제_커밋된_이벤트는_같은_CSV_스냅샷에_섞이지_않는다() throws Exception {
    UUID customer = UUID.randomUUID();
    createCustomer("test", customer, "snapshot");
    assertThat(
            request("POST", "/v1/events", session, "test", eventJson("original", customer, "{}"))
                .statusCode())
        .isEqualTo(200);
    CountDownLatch counted = new CountDownLatch(1);
    CountDownLatch inserted = new CountDownLatch(1);
    AtomicBoolean once = new AtomicBoolean();
    MeteringEnvironmentTestConfig.AFTER_QUERY.set(
        sql -> {
          if (!sql.startsWith("SELECT count(*) FROM event e ") || !once.compareAndSet(false, true))
            return;
          counted.countDown();
          try {
            if (!inserted.await(10, TimeUnit.SECONDS))
              throw new IllegalStateException("insert timeout");
          } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
          }
        });
    CompletableFuture<java.net.http.HttpResponse<String>> response =
        CompletableFuture.supplyAsync(() -> get(path(""), "test"));
    try {
      assertThat(counted.await(10, TimeUnit.SECONDS)).isTrue();
      assertThat(
              request(
                      "POST",
                      "/v1/events",
                      session,
                      "test",
                      eventJson("committed-later", customer, "{}"))
                  .statusCode())
          .isEqualTo(200);
    } finally {
      inserted.countDown();
      MeteringEnvironmentTestConfig.AFTER_QUERY.set(sql -> {});
    }
    var csv = response.get(10, TimeUnit.SECONDS);
    assertThat(csv.statusCode()).as(csv.body()).isEqualTo(200);
    assertThat(csv.body()).contains("original").doesNotContain("committed-later");
    transactions.read(
        scope("test"),
        () -> {
          assertThat(
                  jdbc.queryForObject(
                      "SELECT count(*) FROM event WHERE organization_id=?",
                      Long.class,
                      organizationId))
              .isEqualTo(2);
          return null;
        });
  }

  private String path(String suffix) {
    return "/v1/events/export?month=" + YearMonth.now(ZoneId.of("Asia/Seoul")) + suffix;
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
