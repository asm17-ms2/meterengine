package com.meterengine.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.MeteringEnvironmentTestSupport;
import com.meterengine.event.repository.EventRepository;
import com.meterengine.global.database.DatabaseScope;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SchemaWorkspaceQueryIntegrationTest extends MeteringEnvironmentTestSupport {
  @Autowired EventRepository events;

  @Autowired com.meterengine.query.repository.WorkspaceQueryRepository workspace;
  @Autowired com.meterengine.metric.repository.BillableMetricUsageRepository usage;

  @Test
  void 업무_역할은_선택한_스키마만_읽고_반대_스키마는_권한으로_거절된다() {
    var sharedIdentity = jdbc.queryForMap("SELECT current_user AS role,current_schema() AS schema");
    assertThat(sharedIdentity)
        .containsEntry("role", "meterengine_shared")
        .containsEntry("schema", "shared");
    System.out.println("S4 runtime identity: " + sharedIdentity);
    for (DatabaseScope scope : new DatabaseScope[] {DatabaseScope.TEST, DatabaseScope.LIVE}) {
      transactions.read(
          scope,
          () -> {
            String name = scope.name().toLowerCase(java.util.Locale.ROOT);
            var identity =
                jdbc.queryForMap("SELECT current_user AS role,current_schema() AS schema");
            assertThat(identity)
                .containsEntry("role", "meterengine_" + name)
                .containsEntry("schema", name);
            System.out.println("S4 runtime identity: " + identity);
            return null;
          });
      org.assertj.core.api.Assertions.assertThatThrownBy(
              () ->
                  transactions.read(
                      scope,
                      () ->
                          jdbc.queryForObject(
                              "SELECT count(*) FROM "
                                  + (scope == DatabaseScope.TEST ? "live" : "test")
                                  + ".event",
                              Long.class)))
          .isInstanceOf(org.springframework.dao.DataAccessException.class)
          .rootCause()
          .isInstanceOf(java.sql.SQLException.class)
          .extracting(error -> ((java.sql.SQLException) error).getSQLState())
          .isEqualTo("42501");
    }
  }

  @Test
  void 미터_멱등_재생과_가격은_실제_업무_역할마다_독립적이다() {
    String payload =
        "{\"code\":\"same\",\"name\":\"같은 미터\",\"event_type\":\"tokens\",\"aggregation\":\"sum\",\"target_property\":\"quantity\"}";
    for (String mode : java.util.List.of("test", "live")) {
      var created =
          request(
              "POST",
              "/v1/billable-metrics",
              session,
              mode,
              payload,
              "Idempotency-Key",
              "same-key");
      assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
      var replayed =
          request(
              "POST",
              "/v1/billable-metrics",
              session,
              mode,
              payload,
              "Idempotency-Key",
              "same-key");
      assertThat(replayed.statusCode()).isEqualTo(201);
      assertThat(replayed.body()).isEqualTo(created.body());
      assertThat(replayed.headers().firstValue("Idempotency-Replayed")).contains("true");
      assertThat(
              request(
                      "POST",
                      "/v1/billable-metrics",
                      session,
                      mode,
                      payload.replace("같은 미터", "다른 미터"),
                      "Idempotency-Key",
                      "same-key")
                  .statusCode())
          .isEqualTo(409);
      var price =
          request(
              "POST",
              "/v1/billable-metrics/same/price-rates",
              session,
              mode,
              "{\"dimension_values\":{},\"unit_price\":" + (mode.equals("test") ? "2" : "7") + "}");
      assertThat(price.statusCode()).as(price.body()).isEqualTo(201);
      var plan =
          request(
              "POST", "/v1/price-plans", session, mode, "{\"base_fee\":20,\"minimum_amount\":100}");
      assertThat(plan.statusCode()).as(plan.body()).isEqualTo(201);
    }
    transactions.read(
        DatabaseScope.TEST,
        () -> {
          assertThat(
                  jdbc.queryForObject(
                      "SELECT unit_price FROM price_rate WHERE organization_id=?",
                      java.math.BigDecimal.class,
                      organizationId))
              .isEqualByComparingTo("2");
          assertThat(
                  jdbc.queryForObject(
                      "SELECT count(*) FROM billable_metric WHERE organization_id=?",
                      Long.class,
                      organizationId))
              .isEqualTo(1);
          return null;
        });
    transactions.read(
        DatabaseScope.LIVE,
        () -> {
          assertThat(
                  jdbc.queryForObject(
                      "SELECT unit_price FROM price_rate WHERE organization_id=?",
                      java.math.BigDecimal.class,
                      organizationId))
              .isEqualByComparingTo("7");
          return null;
        });
    assertThat(
            jdbc.queryForList(
                "SELECT DISTINCT environment_id FROM shared.audit_log WHERE organization_id=? AND action='billable_metric_created'",
                UUID.class,
                organizationId))
        .containsExactlyInAnyOrder(environmentId("test"), environmentId("live"));
  }

  @Test
  void 검색은_선택한_업무_스키마의_고객만_읽는다() {
    transactions.read(
        DatabaseScope.TEST,
        () -> {
          assertThat(
                  workspace
                      .customers(
                          organizationId,
                          com.meterengine.query.service.SearchParameters.parse(java.util.Map.of()))
                      .total())
              .isZero();
          return null;
        });
  }

  @Test
  void 집계는_선택한_업무_스키마에서_환경_열_없이_실행된다() {
    transactions.read(
        DatabaseScope.TEST,
        () -> {
          var metric =
              new com.meterengine.metric.entity.BillableMetric(
                  organizationId,
                  "same",
                  "같은 미터",
                  "tokens",
                  "sum",
                  "quantity",
                  java.util.List.of(),
                  java.math.BigDecimal.ONE,
                  "none",
                  0,
                  null);
          var now = OffsetDateTime.now();
          assertThat(
                  usage
                      .aggregate(metric, now.minusDays(1), now.plusDays(1), now.plusDays(1))
                      .totalByCustomerId())
              .isEmpty();
          return null;
        });
  }

  @Test
  void 같은_고객과_거래는_선택한_업무_스키마에서_독립적으로_수집된다() {
    UUID customerId = UUID.randomUUID();
    for (DatabaseScope scope : new DatabaseScope[] {DatabaseScope.TEST, DatabaseScope.LIVE}) {
      transactions.write(
          scope,
          () -> {
            jdbc.update(
                "INSERT INTO customer(id,organization_id,name,external_id) VALUES (?,?,'같은 고객','same-external')",
                customerId,
                organizationId);
            assertThat(
                    events.insertIfAbsent(
                        organizationId,
                        "same-transaction",
                        customerId,
                        "tokens",
                        "{\"quantity\":3}",
                        OffsetDateTime.now()))
                .isEqualTo(1);
            assertThat(
                    events.insertIfAbsent(
                        organizationId,
                        "same-transaction",
                        customerId,
                        "tokens",
                        "{\"quantity\":7}",
                        OffsetDateTime.now()))
                .isZero();
            assertThat(events.find(organizationId, "same-transaction")).isPresent();
            return null;
          });
    }
  }

  @Test
  void 한쪽의_가격과_보관과_수집은_반대쪽의_집계와_검색과_CSV를_바꾸지_않는다() {
    UUID customerId = UUID.randomUUID();
    String month =
        java.time.YearMonth.now(
                com.meterengine.metric.service.BillableMetricUsageService.BILLING_ZONE)
            .toString();
    for (String mode : java.util.List.of("test", "live")) {
      createCustomer(mode, customerId, "same-external");
      assertThat(createMetric(mode, "same", "sum").statusCode()).isEqualTo(201);
      var price =
          request(
              "POST",
              "/v1/billable-metrics/same/price-rates",
              session,
              mode,
              "{\"dimension_values\":{},\"unit_price\":7}");
      assertThat(price.statusCode()).as(price.body()).isEqualTo(201);
      var event =
          request(
              "POST",
              "/v1/events",
              session,
              mode,
              eventJson("same", customerId, "{\"quantity\":3}"));
      assertThat(event.statusCode()).as(event.body()).isEqualTo(200);
    }
    var liveCsv = get("/v1/events/export?month=" + month, "live");
    assertThat(liveCsv.statusCode()).as(liveCsv.body()).isEqualTo(200);
    var usageService =
        context.getBean(com.meterengine.metric.service.BillableMetricUsageService.class);
    var chargeService = context.getBean(com.meterengine.pricing.service.ChargeService.class);
    var period = java.time.YearMonth.parse(month);
    transactions.read(
        DatabaseScope.LIVE,
        () -> {
          var usages = usageService.aggregate(organizationId, period);
          assertThat(usages.getFirst().customers().getFirst().quantity()).isEqualByComparingTo("3");
          assertThat(
                  chargeService.calculate(organizationId, period, usages).get(customerId).amount())
              .isEqualTo(20);
          var search =
              com.meterengine.query.service.SearchParameters.parse(
                  java.util.Map.of("month", month));
          assertThat(
                  workspace
                      .prices(organizationId, search)
                      .items()
                      .getFirst()
                      .priceRates()
                      .getFirst()
                      .unitPrice())
              .isEqualByComparingTo("7");
          return null;
        });
    assertThat(
            request(
                    "POST",
                    "/v1/events",
                    session,
                    "test",
                    eventJson("test-only", customerId, "{\"quantity\":9}"))
                .statusCode())
        .isEqualTo(200);
    assertThat(
            request(
                    "POST",
                    "/v1/billable-metrics/same/price-rates",
                    session,
                    "test",
                    "{\"dimension_values\":{},\"unit_price\":99}")
                .statusCode())
        .isEqualTo(201);
    assertThat(request("DELETE", "/v1/billable-metrics/same", session, "test", null).statusCode())
        .isEqualTo(204);
    assertThat(get("/v1/events/export?month=" + month, "live").body()).isEqualTo(liveCsv.body());
    assertThat(get("/v1/billable-metrics/same", "live").statusCode()).isEqualTo(200);
    assertThat(json(get("/v1/events?month=" + month, "live")).get("total").asLong()).isEqualTo(1);
    var liveUsage = get("/v1/usage?month=" + month, "live");
    assertThat(liveUsage.statusCode()).as(liveUsage.body()).isEqualTo(200);
    assertThat(
            json(liveUsage)
                .get("billable_metric_usages")
                .get(0)
                .get("customers")
                .get(0)
                .get("quantity")
                .decimalValue())
        .isEqualByComparingTo("3");
    var types = get("/v1/events/types?month=" + month, "live");
    assertThat(types.statusCode()).as(types.body()).isEqualTo(200);
    assertThat(json(types).get("types").get(0).asString()).isEqualTo("tokens");
    var months = get("/v1/events/months", "live");
    assertThat(months.statusCode()).as(months.body()).isEqualTo(200);
    assertThat(json(months).get("months").get(0).asString()).isEqualTo(month);
    transactions.read(
        DatabaseScope.LIVE,
        () -> {
          var usages = usageService.aggregate(organizationId, period);
          assertThat(usages.getFirst().customers().getFirst().quantity()).isEqualByComparingTo("3");
          assertThat(
                  chargeService.calculate(organizationId, period, usages).get(customerId).amount())
              .isEqualTo(20);
          var search =
              com.meterengine.query.service.SearchParameters.parse(
                  java.util.Map.of("q", "same", "month", month));
          assertThat(workspace.customers(organizationId, search).total()).isEqualTo(1);
          assertThat(workspace.metrics(organizationId, search).total()).isEqualTo(1);
          assertThat(
                  workspace
                      .prices(organizationId, search)
                      .items()
                      .getFirst()
                      .priceRates()
                      .getFirst()
                      .unitPrice())
              .isEqualByComparingTo("7");
          assertThat(workspace.months(organizationId, "usage")).containsExactly(month);
          assertThat(workspace.months(organizationId, "billable-metric-prices"))
              .containsExactly(month);
          return null;
        });
  }

  @Test
  void 실제_업무_FK는_선택한_스키마에_없는_참조와_다른_조직_참조를_거절한다() {
    UUID customerId = UUID.randomUUID();
    createCustomer("test", customerId, "test-only");
    expectForeignKey(
        DatabaseScope.LIVE,
        () ->
            jdbc.update(
                "INSERT INTO event(organization_id,transaction_id,customer_id,type,properties,occurred_at) VALUES (?,'missing',?,'tokens','{}',now())",
                organizationId,
                customerId));
    UUID otherOrganization = UUID.randomUUID();
    jdbc.update("INSERT INTO shared.organization(id,name) VALUES (?,'다른 조직')", otherOrganization);
    expectForeignKey(
        DatabaseScope.TEST,
        () ->
            jdbc.update(
                "INSERT INTO event(organization_id,transaction_id,customer_id,type,properties,occurred_at) VALUES (?,'wrong-org',?,'tokens','{}',now())",
                otherOrganization,
                customerId));
    assertThat(createMetric("test", "test-metric", "count").statusCode()).isEqualTo(201);
    expectForeignKey(
        DatabaseScope.LIVE,
        () ->
            jdbc.update(
                "INSERT INTO billable_metric_period(id,organization_id,billable_metric_code,active_from,active_until) VALUES (?,?,'test-metric','2026-01','2026-02')",
                UUID.randomUUID(),
                organizationId));
    expectForeignKey(
        DatabaseScope.TEST,
        () ->
            jdbc.update(
                "INSERT INTO price_rate(id,organization_id,billable_metric_code,dimension_values,unit_price,effective_from) VALUES (?,?,'test-metric','{}',1,'2026-01')",
                UUID.randomUUID(),
                otherOrganization));
  }

  @Test
  void 미터_감사_실패는_생성과_멱등_캐시까지_함께_롤백한다() {
    String payload =
        "{\"code\":\"rollback\",\"name\":\"미터\",\"event_type\":\"tokens\",\"aggregation\":\"count\"}";
    rejectAudit(
        "billable_metric_created",
        () -> {
          assertThat(
                  request(
                          "POST",
                          "/v1/billable-metrics",
                          session,
                          "test",
                          payload,
                          "Idempotency-Key",
                          "rollback-key")
                      .statusCode())
              .isEqualTo(500);
          transactions.read(
              DatabaseScope.TEST,
              () -> {
                assertThat(
                        jdbc.queryForObject(
                            "SELECT count(*) FROM billable_metric WHERE organization_id=?",
                            Long.class,
                            organizationId))
                    .isZero();
                assertThat(
                        jdbc.queryForObject(
                            "SELECT count(*) FROM idempotent_request WHERE organization_id=?",
                            Long.class,
                            organizationId))
                    .isZero();
                return null;
              });
        });
    assertThat(
            request(
                    "POST",
                    "/v1/billable-metrics",
                    session,
                    "test",
                    payload,
                    "Idempotency-Key",
                    "rollback-key")
                .statusCode())
        .isEqualTo(201);
  }

  @Test
  void 검색은_LIKE_문자와_한글과_null과_페이지의_안정된_순서를_보존한다() {
    transactions.write(
        DatabaseScope.TEST,
        () -> {
          jdbc.update(
              "INSERT INTO customer(id,organization_id,name,external_id) VALUES (?,?,'가나',null),(?,?,'가나','literal%_!'),(?,?,'나다','plain')",
              new UUID(0, 1),
              organizationId,
              new UUID(0, 2),
              organizationId,
              new UUID(0, 3),
              organizationId);
          var literal =
              com.meterengine.query.service.SearchParameters.parse(java.util.Map.of("q", "%_!"));
          assertThat(workspace.customers(organizationId, literal).items())
              .extracting(com.meterengine.customer.dto.CustomerResponse::id)
              .containsExactly(new UUID(0, 2));
          var paged =
              com.meterengine.query.service.SearchParameters.parse(
                  java.util.Map.of("page", "2", "size", "1", "sort", "name"));
          assertThat(workspace.customers(organizationId, paged).items())
              .extracting(com.meterengine.customer.dto.CustomerResponse::id)
              .containsExactly(new UUID(0, 2));
          var nulls =
              com.meterengine.query.service.SearchParameters.parse(
                  java.util.Map.of("sort", "external_id"));
          assertThat(workspace.customers(organizationId, nulls).items().getLast().externalId())
              .isNull();
          assertThat(workspace.customers(organizationId, paged).summary())
              .containsEntry("active_count", 3L)
              .containsEntry("archived_count", 0L);
          return null;
        });
    transactions.read(
        DatabaseScope.LIVE,
        () -> {
          assertThat(
                  workspace
                      .customers(
                          organizationId,
                          com.meterengine.query.service.SearchParameters.parse(java.util.Map.of()))
                      .total())
              .isZero();
          return null;
        });
  }

  private void expectForeignKey(DatabaseScope scope, Runnable insert) {
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                transactions.write(
                    scope,
                    () -> {
                      insert.run();
                      return null;
                    }))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class)
        .rootCause()
        .isInstanceOf(java.sql.SQLException.class)
        .extracting(error -> ((java.sql.SQLException) error).getSQLState())
        .isEqualTo("23503");
  }
}
