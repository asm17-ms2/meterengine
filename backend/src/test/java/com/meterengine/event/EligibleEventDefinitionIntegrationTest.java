package com.meterengine.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.jayway.jsonpath.JsonPath;
import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.event.dto.UnbilledEventsResponse;
import com.meterengine.event.service.UnbilledEventService;
import com.meterengine.global.time.BillingMonth;
import com.meterengine.metric.service.BillableMetricUsageService;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.context.WebApplicationContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class EligibleEventDefinitionIntegrationTest {
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private WebApplicationContext context;
  @Autowired private BillableMetricUsageService billableMetricUsageService;
  @Autowired private UnbilledEventService unbilledEventService;

  @Test
  void 청구_집계와_미청구_요약과_문제_진단이_같은_이벤트를_집계_가능으로_본다() {
    UUID organizationId = UUID.randomUUID();
    String read = TestCredentials.issueApiKey(jdbcTemplate, organizationId, "read");
    UUID customerId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO customer (id, organization_id, name) VALUES (?, ?, '고객')",
        customerId,
        organizationId);
    for (String[] metric :
        new String[][] {
          {"summed", "sum", "q"}, {"counted", "count", null}, {"distinct", "count_distinct", "q"}
        }) {
      jdbcTemplate.update(
          "INSERT INTO billable_metric (organization_id, code, name, event_type, aggregation, target_property, dimension_properties) VALUES (?, ?, ?, 'usage', ?, ?, '{}')",
          organizationId,
          metric[0],
          metric[0],
          metric[1],
          metric[2]);
    }
    YearMonth month = YearMonth.now(BillingMonth.ZONE);
    OffsetDateTime occurredAt = OffsetDateTime.now().minusMinutes(1);
    List<String> properties =
        List.of("{\"q\":3}", "{\"q\":\"text\"}", "{\"q\":true}", "{\"q\":null}", "{}");
    for (int index = 0; index < properties.size(); index++) {
      jdbcTemplate.update(
          "INSERT INTO event (organization_id, transaction_id, customer_id, type, properties, occurred_at) VALUES (?, ?, ?, 'usage', ?::jsonb, ?)",
          organizationId,
          "eligible-" + index,
          customerId,
          properties.get(index),
          occurredAt);
    }

    Map<String, Long> notAggregated =
        unbilledEventService
            .aggregate(organizationId, month, OffsetDateTime.now())
            .nonNumericValue()
            .billableMetrics()
            .stream()
            .collect(
                Collectors.toMap(
                    UnbilledEventsResponse.MetricCount::billableMetricCode,
                    UnbilledEventsResponse.MetricCount::eventCount));
    String issues =
        new String(
            MockMvcTester.from(context, builder -> builder.apply(springSecurity()).build())
                .get()
                .uri(
                    "/v1/event-issues/search?size=100&reason=invalid_target_property&month="
                        + month)
                .header(AUTHORIZATION, read)
                .exchange()
                .getResponse()
                .getContentAsByteArray(),
            StandardCharsets.UTF_8);
    Map<String, Long> invalid =
        JsonPath.<List<String>>read(issues, "$.issues[*].billable_metric_code").stream()
            .collect(Collectors.groupingBy(code -> code, Collectors.counting()));
    Map<String, java.math.BigDecimal> billed =
        billableMetricUsageService.aggregate(organizationId, month).stream()
            .collect(
                Collectors.toMap(
                    usage -> usage.billableMetric().getCode(),
                    usage -> usage.customers().getFirst().quantity()));

    assertThat(notAggregated).isEqualTo(Map.of("summed", 4L, "distinct", 2L));
    assertThat(invalid).isEqualTo(notAggregated);
    assertThat(billed.get("summed")).isEqualByComparingTo("3");
    assertThat(billed.get("counted")).isEqualByComparingTo("5");
    assertThat(billed.get("distinct")).isEqualByComparingTo("3");
  }
}
