package com.meterengine.invoice;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.MeteringEnvironmentTestSupport;
import com.meterengine.global.database.BillingEnvironmentTestConfig;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = BillingEnvironmentTestConfig.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"spring.docker.compose.enabled=false", "spring.jpa.open-in-view=false"})
class SchemaBillingTransactionIntegrationTest extends MeteringEnvironmentTestSupport {
  @Test
  void 같은_고객과_월의_청구는_실제_역할마다_금액과_이름을_보존한다() {
    UUID customer = UUID.randomUUID();
    for (String mode : List.of("test", "live")) {
      prepareBilling(mode, customer, mode.equals("test") ? 10 : 20);
      var response = request("POST", "/v1/invoices?month=" + month(), session, mode, "{}");
      assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
      assertThat(json(response).get("finalized_count").asInt()).isEqualTo(1);
      var invoice = get("/v1/invoices?month=" + month(), mode);
      assertThat(invoice.statusCode()).as(invoice.body()).isEqualTo(200);
      assertThat(json(invoice).at("/invoices/0/supply_amount").asLong())
          .isEqualTo(mode.equals("test") ? 100 : 200);
      assertThat(json(invoice).at("/invoices/0/tax_amount").asLong())
          .isEqualTo(mode.equals("test") ? 10 : 20);
      assertThat(json(invoice).at("/invoices/0/lines/0/billable_metric_name").asString())
          .isEqualTo("같은 미터");
      transactions.read(
          scope(mode),
          () -> {
            assertThat(
                    jdbc.queryForObject(
                        "SELECT count(*) FROM outbound_email WHERE organization_id=?",
                        Long.class,
                        organizationId))
                .isEqualTo(1);
            return null;
          });
    }
  }

  @Test
  void 확정_감사가_실패하면_청구와_라인과_메일도_모두_롤백된다() {
    UUID customer = UUID.randomUUID();
    prepareBilling("test", customer, 10);
    rejectAudit(
        "invoice_finalized",
        () -> {
          var response = request("POST", "/v1/invoices?month=" + month(), session, "test", "{}");
          assertThat(response.statusCode()).as(response.body()).isEqualTo(500);
        });
    transactions.read(
        scope("test"),
        () -> {
          for (String table : List.of("invoice", "invoice_line", "outbound_email")) {
            assertThat(
                    jdbc.queryForObject(
                        "SELECT count(*) FROM " + table + " WHERE organization_id=?",
                        Long.class,
                        organizationId))
                .isZero();
          }
          return null;
        });
    assertThat(request("POST", "/v1/invoices?month=" + month(), session, "test", "{}").statusCode())
        .isEqualTo(200);
  }

  private static YearMonth month() {
    return YearMonth.now(com.meterengine.metric.service.BillableMetricUsageService.BILLING_ZONE);
  }

  @Test
  void 수량0은_제외하고_금액0_라인은_남기며_공급가액0_고객은_확정하지_않는다() {
    UUID billable = UUID.randomUUID();
    UUID zero = UUID.randomUUID();
    createCustomer("test", billable, "billable");
    createCustomer("test", zero, "zero");
    for (String code : List.of("billable", "free", "zero")) {
      var metric =
          request(
              "POST",
              "/v1/billable-metrics",
              session,
              "test",
              "{\"code\":\""
                  + code
                  + "\",\"name\":\""
                  + code
                  + "\",\"event_type\":\"tokens\",\"aggregation\":\"sum\",\"target_property\":\""
                  + (code.equals("zero") ? "zero" : "quantity")
                  + "\"}");
      assertThat(metric.statusCode()).as(metric.body()).isEqualTo(201);
      var rate =
          request(
              "POST",
              "/v1/billable-metrics/" + code + "/price-rates",
              session,
              "test",
              "{\"dimension_values\":{},\"unit_price\":" + (code.equals("free") ? 0 : 1) + "}");
      assertThat(rate.statusCode()).as(rate.body()).isEqualTo(201);
    }
    var specific =
        request(
            "POST",
            "/v1/billable-metrics/billable/price-rates",
            session,
            "test",
            "{\"dimension_values\":{},\"unit_price\":0,\"customer_id\":\"" + zero + "\"}");
    assertThat(specific.statusCode()).as(specific.body()).isEqualTo(201);
    for (UUID customer : List.of(billable, zero))
      assertThat(
              request(
                      "POST",
                      "/v1/events",
                      session,
                      "test",
                      eventJson(customer.toString(), customer, "{\"quantity\":10,\"zero\":0}"))
                  .statusCode())
          .isEqualTo(200);
    var finalized = request("POST", "/v1/invoices?month=" + month(), session, "test", "{}");
    assertThat(finalized.statusCode()).as(finalized.body()).isEqualTo(200);
    assertThat(json(finalized).get("finalized_count").asInt()).isEqualTo(1);
    var invoice = json(get("/v1/invoices?customer_id=" + billable, "test")).at("/invoices/0");
    assertThat(invoice.get("supply_amount").asLong()).isEqualTo(10);
    assertThat(invoice.get("tax_amount").asLong()).isEqualTo(1);
    assertThat(invoice.get("total_amount").asLong()).isEqualTo(11);
    assertThat(invoice.get("lines").size()).isEqualTo(2);
    assertThat(invoice.at("/lines/0/billable_metric_code").asString()).isEqualTo("billable");
    assertThat(invoice.at("/lines/1/billable_metric_code").asString()).isEqualTo("free");
    assertThat(invoice.at("/lines/1/quantity").decimalValue()).isEqualByComparingTo("10");
    assertThat(invoice.at("/lines/1/amount").asLong()).isZero();
    assertThat(json(get("/v1/invoices?customer_id=" + zero, "test")).get("invoices").size())
        .isZero();
  }

  @Test
  void 동시_확정은_고객과_월마다_한번이고_다른_환경은_별도로_확정한다() throws Exception {
    UUID customer = UUID.randomUUID();
    prepareBilling("test", customer, 10);
    prepareBilling("live", customer, 20);
    try (var executor = java.util.concurrent.Executors.newFixedThreadPool(3)) {
      var results =
          executor.invokeAll(
              List.<java.util.concurrent.Callable<java.net.http.HttpResponse<String>>>of(
                  () -> request("POST", "/v1/invoices?month=" + month(), session, "test", "{}"),
                  () -> request("POST", "/v1/invoices?month=" + month(), session, "test", "{}"),
                  () -> request("POST", "/v1/invoices?month=" + month(), session, "live", "{}")));
      int total = 0;
      for (var future : results) {
        var r = future.get();
        assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
        total += json(r).get("finalized_count").asInt();
      }
      assertThat(total).isEqualTo(2);
    }
    for (String mode : List.of("test", "live")) {
      assertThat(
              ownerJdbc()
                  .queryForObject(
                      "SELECT count(*) FROM " + mode + ".invoice WHERE organization_id=?",
                      Long.class,
                      organizationId))
          .isEqualTo(1);
      assertThat(
              ownerJdbc()
                  .queryForObject(
                      "SELECT count(*) FROM " + mode + ".outbound_email WHERE organization_id=?",
                      Long.class,
                      organizationId))
          .isEqualTo(1);
    }
  }

  @Test
  void 확정금액과_미터이름과_메일은_추가사용량과_가격변경으로_변하지_않는다() {
    UUID customer = UUID.randomUUID();
    prepareBilling("test", customer, 100);
    var draft = get("/v1/invoices/draft?month=" + month(), "test");
    assertThat(draft.statusCode()).as(draft.body()).isEqualTo(200);
    var finalized = request("POST", "/v1/invoices?month=" + month(), session, "test", "{}");
    assertThat(finalized.statusCode()).as(finalized.body()).isEqualTo(200);
    String before = get("/v1/invoices?month=" + month(), "test").body();
    var first = mapper.readTree(before).at("/invoices/0");
    assertThat(first.get("supply_amount").asLong())
        .isEqualTo(json(draft).get("total_amount").asLong());
    String mail =
        ownerJdbc()
            .queryForObject(
                "SELECT html_body FROM test.outbound_email WHERE organization_id=?",
                String.class,
                organizationId);
    assertThat(mail).contains("S3 fixture", month().toString(), "1,000", "1,100");
    assertThat(
            ownerJdbc()
                .queryForObject(
                    "SELECT recipient_email FROM test.outbound_email WHERE organization_id=?",
                    String.class,
                    organizationId))
        .isNull();
    transactions.write(
        scope("test"),
        () -> {
          jdbc.update(
              "UPDATE billable_metric SET name='changed' WHERE organization_id=?", organizationId);
          jdbc.update(
              "UPDATE price_rate SET unit_price=999 WHERE organization_id=?", organizationId);
          jdbc.update("UPDATE customer SET name='changed customer' WHERE id=?", customer);
          return null;
        });
    assertThat(
            request(
                    "POST",
                    "/v1/events",
                    session,
                    "test",
                    eventJson("after-finalization", customer, "{\"quantity\":100}"))
                .statusCode())
        .isEqualTo(200);
    assertThat(
            json(request("POST", "/v1/invoices?month=" + month(), session, "test", "{}"))
                .get("finalized_count")
                .asInt())
        .isZero();
    assertThat(get("/v1/invoices?month=" + month(), "test").body()).isEqualTo(before);
    assertThat(
            ownerJdbc()
                .queryForObject(
                    "SELECT html_body FROM test.outbound_email WHERE organization_id=?",
                    String.class,
                    organizationId))
        .isEqualTo(mail);
    assertThat(
            ownerJdbc()
                .queryForObject(
                    "SELECT count(*) FROM test.outbound_email WHERE organization_id=?",
                    Long.class,
                    organizationId))
        .isEqualTo(1);
  }

  @Test
  void 인보이스와_라인은_불변이고_금액합과_월유일성과_음수허용을_유지한다() {
    UUID customer = UUID.randomUUID();
    createCustomer("test", customer, "negative-fixture");
    assertThat(createMetric("test", "signed", "sum").statusCode()).isEqualTo(201);
    UUID invoice = UUID.randomUUID();
    UUID line = UUID.randomUUID();
    transactions.write(
        scope("test"),
        () -> {
          jdbc.update(
              "INSERT INTO invoice(id,organization_id,customer_id,customer_name,period,supply_amount,tax_amount,total_amount,finalized_at) VALUES (?,?,?,'signed',?,-100,-10,-110,now())",
              invoice,
              organizationId,
              customer,
              month().toString());
          jdbc.update(
              "INSERT INTO invoice_line(id,organization_id,invoice_id,billable_metric_code,dimension_values,quantity,unit_price,amount) VALUES (?,?,?,'signed','{}',-10,10,-100)",
              line,
              organizationId,
              invoice);
          return null;
        });
    assertThat(
            ownerJdbc()
                .queryForObject(
                    "SELECT quantity FROM test.invoice_line WHERE id=?",
                    java.math.BigDecimal.class,
                    line))
        .isEqualByComparingTo("-10");
    for (String table : List.of("invoice", "invoice_line")) {
      for (String sql :
          List.of(
              "UPDATE test." + table + " SET id=id",
              "DELETE FROM test." + table,
              "TRUNCATE test." + table + " CASCADE")) {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> ownerJdbc().execute(sql))
            .isInstanceOf(org.springframework.dao.DataAccessException.class);
      }
    }
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                transactions.write(
                    scope("test"),
                    () ->
                        jdbc.update(
                            "INSERT INTO invoice(organization_id,customer_id,customer_name,period,supply_amount,tax_amount,total_amount,finalized_at) VALUES (?,?,'duplicate',?,1,0,1,now())",
                            organizationId,
                            customer,
                            month().toString())))
        .isInstanceOf(org.springframework.dao.DataAccessException.class)
        .rootCause()
        .extracting(e -> ((java.sql.SQLException) e).getSQLState())
        .isEqualTo("23505");
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                transactions.write(
                    scope("test"),
                    () ->
                        jdbc.update(
                            "INSERT INTO invoice(organization_id,customer_id,customer_name,period,supply_amount,tax_amount,total_amount,finalized_at) VALUES (?,?,'bad-total',?,100,10,111,now())",
                            organizationId,
                            customer,
                            month().plusMonths(1).toString())))
        .isInstanceOf(org.springframework.dao.DataAccessException.class)
        .rootCause()
        .extracting(e -> ((java.sql.SQLException) e).getSQLState())
        .isEqualTo("23514");
  }

  private void prepareBilling(String mode, UUID customer, int price) {
    createCustomer(mode, customer, "same-customer");
    assertThat(createMetric(mode, "same", "sum").statusCode()).isEqualTo(201);
    var rate =
        request(
            "POST",
            "/v1/billable-metrics/same/price-rates",
            session,
            mode,
            "{\"dimension_values\":{},\"unit_price\":" + price + "}");
    assertThat(rate.statusCode()).as(rate.body()).isEqualTo(201);
    assertThat(
            request(
                    "POST",
                    "/v1/events",
                    session,
                    mode,
                    eventJson("same-transaction", customer, "{\"quantity\":10}"))
                .statusCode())
        .isEqualTo(200);
  }
}
