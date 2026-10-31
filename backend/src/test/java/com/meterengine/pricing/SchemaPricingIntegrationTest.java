package com.meterengine.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.MeteringEnvironmentTestSupport;
import com.meterengine.metric.service.BillableMetricUsageService;
import com.meterengine.pricing.service.ChargeService;
import com.meterengine.query.repository.WorkspaceQueryRepository;
import com.meterengine.query.service.SearchParameters;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SchemaPricingIntegrationTest extends MeteringEnvironmentTestSupport {
  @Autowired WorkspaceQueryRepository workspace;
  @Autowired BillableMetricUsageService usage;
  @Autowired ChargeService charges;

  @Test
  void 최신_가격의_보관은_구버전을_되살리지_않고_복원은_원래_종료월을_유지한다() {
    assertThat(createMetric("test", "version", "sum").statusCode()).isEqualTo(201);
    YearMonth month = YearMonth.now(BillableMetricUsageService.BILLING_ZONE);
    var first =
        price(
            "test",
            "version",
            "{\"dimension_values\":{},\"unit_price\":10,\"effective_from\":\"" + month + "\"}");
    assertThat(first.statusCode()).as(first.body()).isEqualTo(201);
    ownerJdbc()
        .update(
            "UPDATE test.price_rate SET effective_from=? WHERE id=?",
            month.minusMonths(1).toString(),
            UUID.fromString(json(first).get("id").asString()));
    var next =
        price(
            "test",
            "version",
            "{\"dimension_values\":{},\"unit_price\":20,\"effective_from\":\""
                + month
                + "\",\"effective_until\":\""
                + month.plusMonths(3)
                + "\"}");
    assertThat(next.statusCode()).as(next.body()).isEqualTo(201);
    String id = json(next).get("id").asString();
    assertThat(
            request(
                    "DELETE",
                    "/v1/billable-metrics/version/price-rates/" + id,
                    session,
                    "test",
                    null)
                .statusCode())
        .isEqualTo(204);
    transactions.read(
        scope("test"),
        () -> {
          var future =
              workspace.prices(
                  organizationId,
                  SearchParameters.parse(Map.of("month", month.plusMonths(1).toString())));
          assertThat(future.items().getFirst().priceRates()).isEmpty();
          assertThat(future.summary()).containsEntry("no_rate_count", 1L);
          var current =
              workspace.prices(
                  organizationId, SearchParameters.parse(Map.of("month", month.toString())));
          assertThat(current.items().getFirst().priceRates().getFirst().unitPrice())
              .isEqualByComparingTo("20");
          return null;
        });
    assertThat(
            price(
                    "test",
                    "version",
                    "{\"dimension_values\":{},\"unit_price\":30,\"effective_from\":\""
                        + month
                        + "\"}")
                .statusCode())
        .isEqualTo(409);
    var restored =
        request(
            "POST",
            "/v1/billable-metrics/version/price-rates/" + id + "/restorations",
            session,
            "test",
            null);
    assertThat(restored.statusCode()).as(restored.body()).isEqualTo(200);
    assertThat(json(restored).get("effective_until").asString())
        .isEqualTo(month.plusMonths(3).toString());
    transactions.read(
        scope("test"),
        () -> {
          assertThat(
                  workspace
                      .prices(
                          organizationId,
                          SearchParameters.parse(Map.of("month", month.plusMonths(1).toString())))
                      .items()
                      .getFirst()
                      .priceRates()
                      .getFirst()
                      .unitPrice())
              .isEqualByComparingTo("20");
          assertThat(
                  workspace
                      .prices(
                          organizationId,
                          SearchParameters.parse(Map.of("month", month.plusMonths(3).toString())))
                      .items()
                      .getFirst()
                      .priceRates()
                      .getFirst()
                      .unitPrice())
              .isEqualByComparingTo("10");
          return null;
        });
    assertThat(
            price(
                    "test",
                    "version",
                    "{\"dimension_values\":{},\"unit_price\":1,\"effective_from\":\""
                        + month.minusMonths(1)
                        + "\"}")
                .statusCode())
        .isEqualTo(409);
    assertThat(
            request(
                    "DELETE",
                    "/v1/billable-metrics/version/price-rates/" + id,
                    session,
                    "live",
                    null)
                .statusCode())
        .isEqualTo(404);
  }

  @Test
  void 저장된_구간과_고객우선가격과_최소사용료와_기본료는_선택한_환경에서_계산한다() {
    UUID customer = UUID.randomUUID();
    for (String mode : List.of("test", "live")) {
      createCustomer(mode, customer, "same-priced-customer");
      assertThat(createMetric(mode, "tiered", "sum").statusCode()).isEqualTo(201);
      var common =
          price(
              mode,
              "tiered",
              "{\"dimension_values\":{},\"model\":\"graduated\",\"tiers\":[{\"up_to\":10,\"unit_price\":2},{\"up_to\":null,\"unit_price\":3}]}");
      assertThat(common.statusCode()).as(common.body()).isEqualTo(201);
      assertThat(
              request(
                      "POST",
                      "/v1/price-plans",
                      session,
                      mode,
                      "{\"base_fee\":20,\"minimum_amount\":100}")
                  .statusCode())
          .isEqualTo(201);
      assertThat(
              request(
                      "POST",
                      "/v1/events",
                      session,
                      mode,
                      eventJson("same", customer, "{\"quantity\":25}"))
                  .statusCode())
          .isEqualTo(200);
    }
    YearMonth month = YearMonth.now(BillableMetricUsageService.BILLING_ZONE);
    transactions.read(
        scope("live"),
        () -> {
          var charge =
              charges
                  .calculate(organizationId, month, usage.aggregate(organizationId, month))
                  .get(customer);
          assertThat(charge.amount()).isEqualTo(120);
          assertThat(charge.dimensionCharges())
              .extracting(com.meterengine.pricing.dto.DimensionCharge::lineType)
              .containsExactly(
                  "graduated_tier", "graduated_tier", "minimum_adjustment", "base_fee");
          return null;
        });
    var specific =
        price(
            "test",
            "tiered",
            "{\"dimension_values\":{},\"customer_id\":\""
                + customer
                + "\",\"model\":\"volume\",\"tiers\":[{\"up_to\":10,\"unit_price\":4},{\"up_to\":null,\"unit_price\":5}]}");
    assertThat(specific.statusCode()).as(specific.body()).isEqualTo(201);
    transactions.read(
        scope("test"),
        () -> {
          var charge =
              charges
                  .calculate(organizationId, month, usage.aggregate(organizationId, month))
                  .get(customer);
          assertThat(charge.amount()).isEqualTo(140);
          assertThat(charge.dimensionCharges())
              .extracting(com.meterengine.pricing.dto.DimensionCharge::lineType)
              .containsExactly("volume", "base_fee");
          return null;
        });
    transactions.read(
        scope("live"),
        () -> {
          assertThat(
                  charges
                      .calculate(organizationId, month, usage.aggregate(organizationId, month))
                      .get(customer)
                      .amount())
              .isEqualTo(120);
          return null;
        });
    var plan =
        request(
            "POST",
            "/v1/price-plans",
            session,
            "test",
            "{\"customer_id\":\""
                + customer
                + "\",\"base_fee\":30,\"minimum_amount\":200,\"effective_from\":\""
                + month.plusMonths(1)
                + "\"}");
    assertThat(plan.statusCode()).as(plan.body()).isEqualTo(201);
    String id = json(plan).get("id").asString();
    assertThat(request("DELETE", "/v1/price-plans/" + id, session, "test", null).statusCode())
        .isEqualTo(204);
    assertThat(
            request("POST", "/v1/price-plans/" + id + "/restorations", session, "test", null)
                .statusCode())
        .isEqualTo(200);
    assertThat(request("DELETE", "/v1/price-plans/" + id, session, "live", null).statusCode())
        .isEqualTo(404);
  }

  private java.net.http.HttpResponse<String> price(String mode, String code, String payload) {
    return request("POST", "/v1/billable-metrics/" + code + "/price-rates", session, mode, payload);
  }
}
