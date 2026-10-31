package com.meterengine.metric;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.MeteringEnvironmentTestSupport;
import com.meterengine.metric.entity.BillableMetric;
import com.meterengine.metric.repository.BillableMetricUsageRepository;
import com.meterengine.metric.service.BillableMetricUsageService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SchemaMetricAggregationIntegrationTest extends MeteringEnvironmentTestSupport {
  @Autowired BillableMetricUsageRepository usage;

  @Test
  void 모든_집계는_서울_월과_수신_마감에서_유효한_원본만_계산한다() {
    UUID customerId = UUID.randomUUID();
    createCustomer("test", customerId, "aggregation");
    YearMonth month = YearMonth.now(BillableMetricUsageService.BILLING_ZONE);
    OffsetDateTime start =
        month.atDay(1).atStartOfDay(BillableMetricUsageService.BILLING_ZONE).toOffsetDateTime();
    OffsetDateTime end = start.plusMonths(1);
    OffsetDateTime cutoff = BillableMetricUsageService.cutoffAt(month);
    String[] values = {"11", "21", "21", "40", "\"21\"", "true", "{\"nested\":21}", null};
    for (int i = 0; i < values.length; i++) {
      pastEvent(
          "test",
          "tx-" + i,
          customerId,
          "tokens",
          values[i] == null ? "{}" : "{\"quantity\":" + values[i] + "}",
          start.plusSeconds(i),
          cutoff.minusNanos(1000));
    }
    pastEvent(
        "test",
        "before",
        customerId,
        "tokens",
        "{\"quantity\":999}",
        start.minusNanos(1000),
        cutoff.minusSeconds(1));
    pastEvent(
        "test", "end", customerId, "tokens", "{\"quantity\":999}", end, cutoff.minusSeconds(1));
    pastEvent("test", "late", customerId, "tokens", "{\"quantity\":999}", start, cutoff);
    for (String sample :
        List.of(
            "sum,93",
            "count,8",
            "count_distinct,5",
            "min,11",
            "max,40",
            "avg,23.25",
            "first,11",
            "last,40",
            "median,21",
            "percentile,37.15")) {
      String[] fields = sample.split(",");
      var metric =
          metric(
              fields[0],
              "tokens",
              List.of(),
              fields[0].equals("percentile") ? new BigDecimal("95") : null);
      transactions.read(
          scope("test"),
          () -> {
            var total =
                usage.aggregate(metric, start, end, cutoff).totalByCustomerId().get(customerId);
            assertThat(total.quantity()).as(fields[0]).isEqualByComparingTo(fields[1]);
            if (fields[0].equals("avg")) {
              assertThat(total.rawQuantityNumerator()).isEqualByComparingTo("93");
              assertThat(total.rawQuantityDenominator()).isEqualByComparingTo("4");
            }
            return null;
          });
    }
    transactions.read(
        scope("live"),
        () -> {
          assertThat(
                  usage
                      .aggregate(metric("sum", "tokens", List.of(), null), start, end, cutoff)
                      .totalByCustomerId())
              .isEmpty();
          return null;
        });
    assertThat(
            ownerJdbc()
                .queryForObject(
                    "SELECT tgenabled FROM pg_trigger WHERE tgrelid='test.event'::regclass AND tgname='event_set_received_at'",
                    String.class))
        .isEqualTo("O");
  }

  @Test
  void 동시_발생의_첫값과_끝값은_C_거래순서이고_평균과_백분위는_정확하다() {
    UUID customerId = UUID.randomUUID();
    createCustomer("test", customerId, "exact");
    OffsetDateTime start = OffsetDateTime.parse("2026-08-01T00:00:00+09:00");
    OffsetDateTime end = start.plusMonths(1);
    OffsetDateTime received = end.plusHours(12);
    pastEvent("test", "a", customerId, "tie", "{\"quantity\":11}", start, received);
    pastEvent("test", "Z", customerId, "tie", "{\"quantity\":31}", start, received);
    pastEvent(
        "test", "1", customerId, "fraction", "{\"quantity\":1,\"group\":\"a\"}", start, received);
    pastEvent(
        "test", "2", customerId, "fraction", "{\"quantity\":0,\"group\":\"b\"}", start, received);
    pastEvent(
        "test", "3", customerId, "fraction", "{\"quantity\":0,\"group\":\"b\"}", start, received);
    pastEvent(
        "test",
        "p1",
        customerId,
        "precision",
        "{\"quantity\":9007199254740993.000000000000000001}",
        start,
        received);
    pastEvent(
        "test",
        "p2",
        customerId,
        "precision",
        "{\"quantity\":9007199254740993.000000000000000003}",
        start,
        received);
    transactions.read(
        scope("test"),
        () -> {
          assertThat(
                  usage
                      .aggregate(
                          metric("first", "tie", List.of(), null), start, end, end.plusHours(13))
                      .totalByCustomerId()
                      .get(customerId)
                      .quantity())
              .isEqualByComparingTo("31");
          assertThat(
                  usage
                      .aggregate(
                          metric("last", "tie", List.of(), null), start, end, end.plusHours(13))
                      .totalByCustomerId()
                      .get(customerId)
                      .quantity())
              .isEqualByComparingTo("11");
          var fractions =
              usage.aggregate(
                  metric("avg", "fraction", List.of("group"), null), start, end, end.plusHours(13));
          assertThat(fractions.totalByCustomerId().get(customerId).rawQuantityNumerator())
              .isEqualByComparingTo("1");
          assertThat(fractions.totalByCustomerId().get(customerId).rawQuantityDenominator())
              .isEqualByComparingTo("3");
          assertThat(fractions.dimensionUsagesByCustomerId().get(customerId)).hasSize(2);
          assertThat(
                  usage
                      .aggregate(
                          metric("percentile", "precision", List.of(), new BigDecimal("50")),
                          start,
                          end,
                          end.plusHours(13))
                      .totalByCustomerId()
                      .get(customerId)
                      .quantity())
              .isEqualByComparingTo("9007199254740993.000000000000000002");
          return null;
        });
  }

  @Test
  void 실제_수집의_수신시각은_DB가_덮어쓰고_보관기간_겹침은_한번만_집계한다() {
    UUID customerId = UUID.randomUUID();
    createCustomer("test", customerId, "period");
    assertThat(createMetric("test", "period", "sum").statusCode()).isEqualTo(201);
    var month = YearMonth.now(BillableMetricUsageService.BILLING_ZONE);
    var service = context.getBean(BillableMetricUsageService.class);
    transactions.write(
        scope("test"),
        () -> {
          jdbc.update(
              "INSERT INTO event(organization_id,transaction_id,customer_id,type,properties,occurred_at,received_at) VALUES (?,'trigger',?,'tokens','{\"quantity\":4}',now()-interval '1 second','2000-01-01')",
              organizationId,
              customerId);
          assertThat(
                  jdbc.queryForObject(
                      "SELECT received_at > now()-interval '1 minute' FROM event WHERE organization_id=?",
                      Boolean.class,
                      organizationId))
              .isTrue();
          jdbc.update(
              "INSERT INTO billable_metric_period(id,organization_id,billable_metric_code,active_from,active_until) VALUES (?,?,'period',?,?),(?,?,'period',?,?)",
              UUID.randomUUID(),
              organizationId,
              month.minusMonths(1).toString(),
              month.plusMonths(1).toString(),
              UUID.randomUUID(),
              organizationId,
              month.toString(),
              month.plusMonths(2).toString());
          assertThat(
                  service
                      .aggregate(organizationId, month)
                      .getFirst()
                      .customers()
                      .getFirst()
                      .quantity())
              .isEqualByComparingTo("4");
          return null;
        });
    assertThat(request("DELETE", "/v1/billable-metrics/period", session, "test", null).statusCode())
        .isEqualTo(204);
    transactions.read(
        scope("test"),
        () -> {
          assertThat(
                  service
                      .aggregate(organizationId, month)
                      .getFirst()
                      .customers()
                      .getFirst()
                      .quantity())
              .isEqualByComparingTo("4");
          assertThat(
                  service.aggregate(
                      organizationId, month.plusMonths(3), OffsetDateTime.now().plusMonths(4)))
              .isEmpty();
          return null;
        });
    assertThat(
            request("POST", "/v1/billable-metrics/period/restorations", session, "test", null)
                .statusCode())
        .isEqualTo(200);
  }

  private BillableMetric metric(
      String aggregation, String type, List<String> dimensions, BigDecimal percentile) {
    return new BillableMetric(
        organizationId,
        "metric",
        "미터",
        type,
        aggregation,
        aggregation.equals("count") ? null : "quantity",
        dimensions,
        BigDecimal.ONE,
        "none",
        0,
        percentile);
  }
}
