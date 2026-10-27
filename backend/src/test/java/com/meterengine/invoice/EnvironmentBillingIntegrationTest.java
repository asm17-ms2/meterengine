package com.meterengine.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.global.security.BusinessEnvironmentInterceptor;
import com.meterengine.metric.entity.BillableMetricId;
import com.meterengine.metric.repository.BillableMetricRepository;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class EnvironmentBillingIntegrationTest {
  private static final String HEADER = BusinessEnvironmentInterceptor.HEADER;
  private static final String CODE = "shared-meter";
  private static final YearMonth MONTH = YearMonth.now(ZoneId.of("Asia/Seoul"));
  @Autowired private WebApplicationContext context;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private JsonMapper json;
  @Autowired private BillableMetricRepository metricRepository;
  private MockMvcTester mvc;
  private UUID organizationId;
  private String session;

  @BeforeEach
  void setUp() {
    mvc = MockMvcTester.from(context, builder -> builder.apply(springSecurity()).build());
    organizationId = UUID.randomUUID();
    session = TestCredentials.issueMemberSession(jdbc, organizationId);
  }

  @Test
  void 세_환경의_동일_미터와_거래와_단가와_월별_원장을_각각_보존한다() {
    for (var entry : Map.of("legacy", 10, "test", 20, "live", 30).entrySet()) {
      String mode = entry.getKey();
      int quantity = entry.getValue();
      UUID customer = prepare(mode, quantity);
      JsonNode usage = ok(get(mode, "/v1/usage?month=" + MONTH));
      assertThat(usage.at("/billable_metric_usages/0/customers/0/customer_id").asString())
          .isEqualTo(customer.toString());
      assertThat(usage.at("/billable_metric_usages/0/customers/0/quantity").decimalValue())
          .isEqualByComparingTo(Integer.toString(quantity));
      long supply = quantity * (long) quantity + quantity * 10L;
      assertThat(ok(get(mode, "/v1/invoices/draft?month=" + MONTH)).get("total_amount").asLong())
          .isEqualTo(supply);
      assertThat(ok(post(mode, "/v1/invoices?month=" + MONTH, "{}")).get("finalized_count").asInt())
          .isEqualTo(1);
      JsonNode invoice = ok(get(mode, "/v1/invoices")).get("invoices").get(0);
      assertThat(invoice.get("supply_amount").asLong()).isEqualTo(supply);
      assertThat(invoice.get("total_amount").asLong()).isEqualTo(supply * 11 / 10);
      assertThat(ok(post(mode, "/v1/invoices?month=" + MONTH, "{}")).get("finalized_count").asInt())
          .isZero();
      for (String other : List.of("legacy", "test", "live")) {
        if (!other.equals(mode))
          assertThat(get(other, "/v1/invoices/" + invoice.get("id").asString())).hasStatus(404);
      }
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM invoice WHERE organization_id = ?",
                Integer.class,
                organizationId))
        .isEqualTo(3);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(DISTINCT environment_scope_id) FROM billable_metric WHERE"
                    + " organization_id = ? AND code = ?",
                Integer.class,
                organizationId,
                CODE))
        .isEqualTo(3);
    for (String mode : List.of("test", "live")) {
      var reloaded =
          metricRepository
              .findById(new BillableMetricId(organizationId, CODE, environment(mode)))
              .orElseThrow();
      assertThat(reloaded.getEnvironmentId()).isEqualTo(environment(mode));
      assertThat(reloaded.getName()).isEqualTo(mode + "-meter");
    }
    assertThat(
            mvc.put()
                .uri("/v1/billable-metrics/" + CODE)
                .header(AUTHORIZATION, session)
                .header(HEADER, "test")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"renamed-test\",\"event_type\":\"usage\",\"aggregation\":\"sum\",\"target_property\":\"quantity\"}")
                .exchange())
        .hasStatusOk();
    assertThat(ok(get("live", "/v1/billable-metrics/" + CODE)).get("name").asString())
        .isEqualTo("live-meter");
    assertThat(ok(get("test", "/v1/billable-metrics/" + CODE)).get("name").asString())
        .isEqualTo("renamed-test");
    assertThat(
            metricRepository
                .findById(new BillableMetricId(organizationId, CODE))
                .orElseThrow()
                .getEnvironmentId())
        .isNull();
  }

  @Test
  void 검색과_옵션과_내보내기와_기간목록은_현재_환경만_읽는다() {
    prepare("test", 20);
    UUID liveCustomer = prepare("live", 30);
    String previous = MONTH.minusMonths(1).toString();
    jdbc.update(
        "INSERT INTO price_rate"
            + " (id,organization_id,environment_id,billable_metric_code,dimension_values,unit_price,effective_from)"
            + " VALUES (?,?,?,?,'{}'::jsonb,10,?)",
        UUID.randomUUID(),
        organizationId,
        environment("live"),
        CODE,
        previous);
    jdbc.update(
        "INSERT INTO event"
            + " (organization_id,environment_id,transaction_id,customer_id,type,occurred_at,properties)"
            + " VALUES (?,?,'previous-live',?,'usage',?::timestamptz,'{\"quantity\":7}'::jsonb)",
        organizationId,
        environment("live"),
        liveCustomer,
        previous + "-01T00:00:00+09:00");
    assertThat(post("test", "/v1/invoices?month=" + MONTH, "{}")).hasStatusOk();
    for (String mode : List.of("test", "live")) {
      for (String resource :
          List.of("billable-metrics", "billable-metric-prices", "usage", "invoices/draft")) {
        JsonNode found =
            ok(
                get(
                    mode,
                    "/v1/"
                        + resource
                        + "/search"
                        + (resource.equals("billable-metrics") ? "" : "?month=" + MONTH)));
        assertThat(found.get("total_unfiltered").asInt())
            .isEqualTo(resource.equals("invoices/draft") && mode.equals("test") ? 0 : 1);
        var exported =
            get(
                mode,
                "/v1/"
                    + resource
                    + "/export"
                    + (resource.equals("billable-metrics") ? "" : "?month=" + MONTH));
        assertThat(exported).hasStatusOk();
        assertThat(body(exported))
            .doesNotContain(mode.equals("test") ? "live-meter" : "test-meter")
            .doesNotContain(mode.equals("test") ? "live-customer" : "test-customer");
      }
      JsonNode metrics = ok(get(mode, "/v1/billable-metrics/options"));
      assertThat(metrics.at("/items/0/label").asString()).isEqualTo(mode + "-meter");
      assertThat(ok(get(mode, "/v1/billable-metric-prices/months")).get("months").size())
          .isEqualTo(mode.equals("test") ? 1 : 2);
      assertThat(ok(get(mode, "/v1/usage/months")).get("months").size())
          .isEqualTo(mode.equals("test") ? 1 : 2);
    }
    assertThat(ok(get("test", "/v1/invoices/months")).get("months").size()).isEqualTo(1);
    assertThat(ok(get("live", "/v1/invoices/months")).get("months").size()).isZero();
    assertThat(ok(get("test", "/v1/invoices/search")).get("total_unfiltered").asInt()).isEqualTo(1);
    assertThat(ok(get("live", "/v1/invoices/search")).get("total_unfiltered").asInt()).isZero();
  }

  @Test
  void 반대_환경의_가격과_고객을_제출하거나_테스트키로_실제환경을_지정하면_거절한다() {
    UUID testCustomer = prepare("test", 20);
    UUID liveCustomer = prepare("live", 30);
    JsonNode rate =
        ok(get("live", "/v1/billable-metrics/" + CODE + "/price-rates")).get("price_rates").get(0);
    assertThat(
            mvc.delete()
                .uri("/v1/billable-metrics/" + CODE + "/price-rates/" + rate.get("id").asString())
                .header(AUTHORIZATION, session)
                .header(HEADER, "test")
                .exchange())
        .hasStatus(404);
    assertThat(get("test", "/v1/invoices/search?mode=live")).hasStatus(403);
    assertThat(get("test", "/v1/invoices/export?mode=live")).hasStatus(403);
    assertThat(get("test", "/v1/billable-metric-prices?customer_id=" + liveCustomer))
        .hasStatus(404);
    assertThat(
            post(
                "test",
                "/v1/price-plans",
                "{\"customer_id\":\"" + liveCustomer + "\",\"base_fee\":100,\"minimum_amount\":0}"))
        .hasStatus(404);
    JsonNode key =
        ok(
            post(
                "test",
                "/v1/api-keys",
                "{\"label\":\"test-reader\",\"mode\":\"test\",\"scopes\":[\"read\"]}"));
    assertThat(
            mvc.get()
                .uri("/v1/invoices")
                .header(AUTHORIZATION, "Bearer " + key.get("secret").asString())
                .header(HEADER, "live")
                .exchange())
        .hasStatus(403);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO price_plan"
                        + " (id,organization_id,environment_id,customer_id,effective_from,base_fee,minimum_amount)"
                        + " VALUES (?,?,?,?,?,100,0)",
                    UUID.randomUUID(),
                    organizationId,
                    environment("live"),
                    testCustomer,
                    MONTH.toString()))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(
            post(
                "test",
                "/v1/billable-metrics",
                "{\"code\":\"test-only\",\"name\":\"test-only\",\"event_type\":\"test-only\",\"aggregation\":\"count\"}"))
        .hasStatus(201);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO billable_metric_period"
                        + " (id,organization_id,environment_id,billable_metric_code,active_from,active_until)"
                        + " VALUES (?,?,?,?,?,?)",
                    UUID.randomUUID(),
                    organizationId,
                    environment("live"),
                    "test-only",
                    "2026-01",
                    "2026-02"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 같은_환경의_동시_확정은_하나만_생성하고_다른_환경은_별도로_확정한다() throws Exception {
    prepare("test", 20);
    prepare("live", 30);
    try (var executor = Executors.newFixedThreadPool(3)) {
      Callable<Integer> test =
          () ->
              ok(post("test", "/v1/invoices?month=" + MONTH, "{}")).get("finalized_count").asInt();
      Callable<Integer> live =
          () ->
              ok(post("live", "/v1/invoices?month=" + MONTH, "{}")).get("finalized_count").asInt();
      var results = executor.invokeAll(List.of(test, test, live));
      assertThat(results.get(0).get() + results.get(1).get()).isEqualTo(1);
      assertThat(results.get(2).get()).isEqualTo(1);
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM invoice WHERE organization_id = ?",
                Integer.class,
                organizationId))
        .isEqualTo(2);
  }

  @Test
  void 평균과_백분위와_건수_집계도_선택한_환경의_이벤트만_사용한다() {
    UUID testCustomer = prepare("test", 20);
    prepare("live", 30);
    assertThat(
            post(
                "test",
                "/v1/events",
                "{\"transaction_id\":\"test-second\",\"customer_id\":\""
                    + testCustomer
                    + "\",\"type\":\"usage\",\"timestamp\":\""
                    + MONTH
                    + "-01T00:00:00+09:00\",\"properties\":{\"quantity\":60}}"))
        .hasStatusOk();
    for (String mode : List.of("test", "live")) {
      for (String aggregation : List.of("avg", "percentile", "count")) {
        String fields = aggregation.equals("count") ? "" : ",\"target_property\":\"quantity\"";
        if (aggregation.equals("percentile")) fields += ",\"percentile\":50";
        assertThat(
                post(
                    mode,
                    "/v1/billable-metrics",
                    "{\"code\":\""
                        + aggregation
                        + "\",\"name\":\""
                        + aggregation
                        + "\",\"event_type\":\"usage\",\"aggregation\":\""
                        + aggregation
                        + "\""
                        + fields
                        + "}"))
            .hasStatus(201);
      }
      JsonNode usages = ok(get(mode, "/v1/usage?month=" + MONTH)).get("billable_metric_usages");
      for (JsonNode usage : usages) {
        String code = usage.get("code").asString();
        if (code.equals(CODE)) continue;
        String expected =
            code.equals("count")
                ? mode.equals("test") ? "2" : "1"
                : mode.equals("test") ? "40" : "30";
        assertThat(usage.at("/customers/0/quantity").decimalValue()).isEqualByComparingTo(expected);
      }
      assertThat(usages.size()).isEqualTo(4);
    }
  }

  @Test
  void 대시보드의_건수와_확정액과_결제환경은_요청환경에_고정된다() {
    prepare("test", 20);
    prepare("live", 30);
    assertThat(post("live", "/v1/customers", "{\"name\":\"live-extra\"}")).hasStatus(201);
    assertThat(
            post(
                "live",
                "/v1/billable-metrics",
                "{\"code\":\"live-extra\",\"name\":\"live-extra\",\"event_type\":\"other\",\"aggregation\":\"count\"}"))
        .hasStatus(201);
    assertThat(
            post(
                "test",
                "/v1/api-keys",
                "{\"label\":\"test-reader\",\"mode\":\"test\",\"scopes\":[\"read\"]}"))
        .hasStatus(201);
    assertThat(post("test", "/v1/invoices?month=" + MONTH, "{}")).hasStatusOk();
    jdbc.update(
        "UPDATE organization_environment SET business_registration_number = '1234567890',"
            + " representative_name = 'live owner', business_address = 'live address' WHERE"
            + " organization_id = ? AND id = ?",
        organizationId,
        environment("live"));
    JsonNode test = ok(get("test", "/v1/dashboard?month=" + MONTH));
    JsonNode live = ok(get("live", "/v1/dashboard?month=" + MONTH));
    assertThat(test.get("customer_count").asInt()).isEqualTo(1);
    assertThat(live.get("customer_count").asInt()).isEqualTo(2);
    assertThat(test.get("billable_metric_count").asInt()).isEqualTo(1);
    assertThat(live.get("billable_metric_count").asInt()).isEqualTo(2);
    assertThat(test.get("active_api_key_count").asInt()).isEqualTo(1);
    assertThat(live.get("active_api_key_count").asInt()).isZero();
    assertThat(test.get("event_count").asInt()).isEqualTo(1);
    assertThat(live.get("event_count").asInt()).isEqualTo(1);
    assertThat(test.get("supplier_configured").asBoolean()).isFalse();
    assertThat(live.get("supplier_configured").asBoolean()).isTrue();
    assertThat(test.get("payment_mode").asString()).isEqualTo("test");
    assertThat(live.get("payment_mode").asString()).isEqualTo("live");
    assertThat(test.get("finalized_total_amount").asLong()).isEqualTo(660);
    assertThat(live.get("finalized_total_amount").asLong()).isZero();
    assertThat(test.get("draft_supply_amount").asLong()).isZero();
    assertThat(live.get("draft_supply_amount").asLong()).isEqualTo(1200);
    assertThat(body(get("live", "/v1/dashboard?month=" + MONTH))).doesNotContain("test-customer");
    assertThat(ok(get("legacy", "/v1/dashboard?month=" + MONTH)).get("customer_count").asInt())
        .isZero();
  }

  @Test
  void 이벤트_매핑진단과_청구제외_경고는_다른환경의_미터와_이벤트를_섞지_않는다() {
    UUID testCustomer = prepare("test", 20);
    UUID liveCustomer = prepare("live", 30);
    assertThat(
            post(
                "live",
                "/v1/billable-metrics",
                "{\"code\":\"live-only\",\"name\":\"live-only\",\"event_type\":\"cross-usage\",\"aggregation\":\"sum\",\"target_property\":\"quantity\"}"))
        .hasStatus(201);
    assertThat(event("test", testCustomer, "unknown-test", "cross-usage", "{\"quantity\":10}"))
        .hasStatusOk();
    assertThat(event("test", testCustomer, "negative-test", "usage", "{\"quantity\":-5}"))
        .hasStatusOk();
    assertThat(event("live", liveCustomer, "invalid-live", "usage", "{\"quantity\":\"invalid\"}"))
        .hasStatusOk();
    assertThat(event("live", liveCustomer, "unpriced-live", "cross-usage", "{\"quantity\":7}"))
        .hasStatusOk();
    JsonNode test = ok(get("test", "/v1/event-issues/search?month=" + MONTH));
    JsonNode live = ok(get("live", "/v1/event-issues/search?month=" + MONTH));
    assertThat(test.at("/summary/unknown_event_type").asInt()).isEqualTo(1);
    assertThat(test.at("/summary/invalid_target_property").asInt()).isZero();
    assertThat(test.at("/summary/unpriced_usage").asInt()).isZero();
    assertThat(test.at("/warnings/negative_event_metric_count").asInt()).isEqualTo(1);
    assertThat(live.at("/summary/unknown_event_type").asInt()).isZero();
    assertThat(live.at("/summary/invalid_target_property").asInt()).isEqualTo(1);
    assertThat(live.at("/summary/unpriced_usage").asInt()).isEqualTo(1);
    assertThat(live.at("/warnings/negative_event_metric_count").asInt()).isZero();
    JsonNode testWarnings = ok(get("test", "/v1/unbilled-events?month=" + MONTH));
    JsonNode liveWarnings = ok(get("live", "/v1/unbilled-events?month=" + MONTH));
    assertThat(testWarnings.at("/unmatched_type/event_count").asInt()).isEqualTo(1);
    assertThat(testWarnings.at("/negative_quantity/event_count").asInt()).isEqualTo(1);
    assertThat(testWarnings.at("/non_numeric_value/event_count").asInt()).isZero();
    assertThat(liveWarnings.at("/unmatched_type/event_count").asInt()).isZero();
    assertThat(liveWarnings.at("/negative_quantity/event_count").asInt()).isZero();
    assertThat(liveWarnings.at("/non_numeric_value/event_count").asInt()).isEqualTo(1);
    assertThat(get("test", "/v1/event-issues/search?customer_id=" + liveCustomer)).hasStatus(404);
    assertThat(ok(get("legacy", "/v1/event-issues/search?month=" + MONTH)).get("total").asInt())
        .isZero();
  }

  @Test
  void 다른환경의_과거_활성기간은_현재환경의_미터를_활성화하지_않는다() {
    prepare("test", 20);
    prepare("live", 30);
    assertThat(
            mvc.delete()
                .uri("/v1/billable-metrics/" + CODE)
                .header(AUTHORIZATION, session)
                .header(HEADER, "live")
                .exchange())
        .hasStatus(204);
    jdbc.update(
        "UPDATE billable_metric SET active_from = ? WHERE organization_id = ? AND environment_id ="
            + " ? AND code = ?",
        MONTH.plusMonths(1).toString(),
        organizationId,
        environment("test"),
        CODE);
    assertThat(
            ok(get("test", "/v1/unbilled-events?month=" + MONTH))
                .at("/unmatched_type/event_count")
                .asInt())
        .isEqualTo(1);
    assertThat(
            ok(get("live", "/v1/unbilled-events?month=" + MONTH))
                .at("/unmatched_type/event_count")
                .asInt())
        .isZero();
    assertThat(
            ok(get("test", "/v1/event-issues/search?month=" + MONTH))
                .at("/summary/unknown_event_type")
                .asInt())
        .isEqualTo(1);
    assertThat(
            ok(get("live", "/v1/event-issues/search?month=" + MONTH))
                .at("/summary/unknown_event_type")
                .asInt())
        .isZero();
  }

  private MvcTestResult event(
      String mode, UUID customer, String transaction, String type, String properties) {
    return post(
        mode,
        "/v1/events",
        "{\"transaction_id\":\""
            + transaction
            + "\",\"customer_id\":\""
            + customer
            + "\",\"type\":\""
            + type
            + "\",\"timestamp\":\""
            + MONTH
            + "-01T00:00:00+09:00\",\"properties\":"
            + properties
            + "}");
  }

  private UUID prepare(String mode, int quantity) {
    JsonNode customer =
        ok(
            post(
                mode,
                "/v1/customers",
                "{\"name\":\"" + mode + "-customer\",\"external_id\":\"same-customer\"}"));
    UUID id = UUID.fromString(customer.get("id").asString());
    String metric =
        "{\"code\":\""
            + CODE
            + "\",\"name\":\""
            + mode
            + "-meter\",\"event_type\":\"usage\",\"aggregation\":\"sum\",\"target_property\":\"quantity\"}";
    var first =
        mvc.post()
            .uri("/v1/billable-metrics")
            .header(AUTHORIZATION, session)
            .header(HEADER, mode)
            .header("Idempotency-Key", "shared-metric-key")
            .contentType(MediaType.APPLICATION_JSON)
            .content(metric)
            .exchange();
    assertThat(first).hasStatus(201);
    assertThat(
            mvc.post()
                .uri("/v1/billable-metrics")
                .header(AUTHORIZATION, session)
                .header(HEADER, mode)
                .header("Idempotency-Key", "shared-metric-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(metric)
                .exchange())
        .hasStatus(201)
        .hasHeader("Idempotency-Replayed", "true");
    assertThat(
            post(
                mode,
                "/v1/billable-metrics/" + CODE + "/price-rates",
                "{\"dimension_values\":{},\"unit_price\":"
                    + quantity
                    + ",\"effective_from\":\""
                    + MONTH
                    + "\"}"))
        .hasStatus(201);
    assertThat(
            post(
                mode,
                "/v1/price-plans",
                "{\"base_fee\":"
                    + (quantity * 10)
                    + ",\"minimum_amount\":0,\"effective_from\":\""
                    + MONTH
                    + "\"}"))
        .hasStatus(201);
    assertThat(
            post(
                mode,
                "/v1/events",
                "{\"transaction_id\":\"shared-transaction\",\"customer_id\":\""
                    + id
                    + "\",\"type\":\"usage\",\"timestamp\":\""
                    + MONTH
                    + "-01T00:00:00+09:00\",\"properties\":{\"quantity\":"
                    + quantity
                    + "}}"))
        .hasStatusOk();
    return id;
  }

  private MvcTestResult get(String mode, String path) {
    return mvc.get().uri(path).header(AUTHORIZATION, session).header(HEADER, mode).exchange();
  }

  private MvcTestResult post(String mode, String path, String content) {
    return mvc.post()
        .uri(path)
        .header(AUTHORIZATION, session)
        .header(HEADER, mode)
        .contentType(MediaType.APPLICATION_JSON)
        .content(content)
        .exchange();
  }

  private JsonNode ok(MvcTestResult result) {
    assertThat(result).hasStatus2xxSuccessful();
    return json.readTree(body(result));
  }

  private static String body(MvcTestResult result) {
    return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
  }

  private UUID environment(String mode) {
    return jdbc.queryForObject(
        "SELECT id FROM organization_environment WHERE organization_id = ? AND mode = ?",
        UUID.class,
        organizationId,
        mode);
  }
}
