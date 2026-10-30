package com.meterengine.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.event.dto.UnbilledEventsResponse;
import com.meterengine.event.service.UnbilledEventService;
import com.meterengine.metric.service.BillableMetricUsageService;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = "meterengine.webhook.enabled=false")
@Import(TestcontainersConfiguration.class)
class UnbilledEventIntegrationTest {
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private UnbilledEventService unbilledEventService;
  @Autowired private WebApplicationContext webApplicationContext;
  private UUID organizationId;
  private UUID customerId;

  @BeforeEach
  void setUp() {
    organizationId = UUID.randomUUID();
    TestCredentials.issueApiKey(jdbcTemplate, organizationId, "read");
    customerId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO customer (id, organization_id, name) VALUES (?, ?, '고객')",
        customerId,
        organizationId);
    jdbcTemplate.update(
        "INSERT INTO billable_metric (organization_id, code, name, event_type, aggregation, target_property, dimension_properties) VALUES (?, 'quantity', '수량', 'usage', 'sum', 'quantity', '{}')",
        organizationId);
  }

  @Test
  void cutoff와_같은_시각은_미래이고_음수_수량으로_세지_않는다() {
    YearMonth month = BillableMetricUsageService.currentMonth();
    OffsetDateTime cutoffAt =
        month.atDay(15).atStartOfDay(BillableMetricUsageService.BILLING_ZONE).toOffsetDateTime();
    insert("unmatched", "other", "{}", cutoffAt.minusSeconds(1));
    insert("non-numeric", "usage", "{\"quantity\":\"text\"}", cutoffAt.minusSeconds(1));
    insert("negative", "usage", "{\"quantity\":-2}", cutoffAt.minusSeconds(1));
    insert("equal-cutoff", "usage", "{\"quantity\":-5}", cutoffAt);
    UnbilledEventsResponse summary =
        unbilledEventService.aggregate(organizationId, month, cutoffAt);
    assertThat(summary.unmatchedType().eventCount()).isEqualTo(1);
    assertThat(summary.nonNumericValue().eventCount()).isEqualTo(1);
    assertThat(summary.negativeQuantity().eventCount()).isEqualTo(1);
    assertThat(summary.futureTimestamp().eventCount()).isEqualTo(1);
    assertThat(summary.futureTimestamp().earliestOccurredAt()).isEqualTo(cutoffAt);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT properties->>'quantity' FROM event WHERE organization_id = ? AND transaction_id = 'negative'",
                String.class,
                organizationId))
        .isEqualTo("-2");
  }

  @Test
  void 같은_유형에_미터가_둘이어도_음수와_숫자_아닌_이벤트는_한_건으로_센다() {
    jdbcTemplate.update(
        "INSERT INTO billable_metric (organization_id, code, name, event_type, aggregation, target_property, dimension_properties) VALUES (?, 'peak', '최대', 'usage', 'max', 'quantity', '{}')",
        organizationId);
    YearMonth month = BillableMetricUsageService.currentMonth();
    OffsetDateTime cutoffAt =
        month.atDay(15).atStartOfDay(BillableMetricUsageService.BILLING_ZONE).toOffsetDateTime();
    insert("negative", "usage", "{\"quantity\":-2}", cutoffAt.minusSeconds(1));
    insert("non-numeric", "usage", "{\"quantity\":\"text\"}", cutoffAt.minusSeconds(1));
    UnbilledEventsResponse summary =
        unbilledEventService.aggregate(organizationId, month, cutoffAt);
    assertThat(summary.negativeQuantity().eventCount()).isEqualTo(1);
    assertThat(summary.negativeQuantity().billableMetrics()).hasSize(2);
    assertThat(summary.nonNumericValue().eventCount()).isEqualTo(1);
    assertThat(summary.nonNumericValue().billableMetrics()).hasSize(2);
  }

  @Test
  void 누락된_숫자와_null과_불리언을_숫자가_아닌_값으로_센다() {
    YearMonth month = BillableMetricUsageService.currentMonth();
    OffsetDateTime cutoffAt =
        month.atDay(15).atStartOfDay(BillableMetricUsageService.BILLING_ZONE).toOffsetDateTime();
    insert("missing", "usage", "{}", cutoffAt.minusSeconds(1));
    insert("null", "usage", "{\"quantity\":null}", cutoffAt.minusSeconds(1));
    insert("boolean", "usage", "{\"quantity\":true}", cutoffAt.minusSeconds(1));
    assertThat(
            unbilledEventService
                .aggregate(organizationId, month, cutoffAt)
                .nonNumericValue()
                .eventCount())
        .isEqualTo(3);
  }

  @Test
  void test와_live_read_키로_자기_도입사_요약을_읽는다() {
    MockMvcTester mvc =
        MockMvcTester.from(
            webApplicationContext, builder -> builder.apply(springSecurity()).build());
    for (String mode : new String[] {"test", "live"}) {
      String read = TestCredentials.issueApiKey(jdbcTemplate, organizationId, mode, "read");
      assertThat(mvc.get().uri("/v1/unbilled-events").header("Authorization", read).exchange())
          .hasStatusOk();
    }
    String ingest = TestCredentials.issueApiKey(jdbcTemplate, organizationId, "ingest");
    assertThat(mvc.get().uri("/v1/unbilled-events").header("Authorization", ingest).exchange())
        .hasStatus(403);
  }

  private void insert(
      String transactionId, String type, String properties, OffsetDateTime occurredAt) {
    jdbcTemplate.update(
        "INSERT INTO event (organization_id, transaction_id, customer_id, type, properties, occurred_at) VALUES (?, ?, ?, ?, ?::jsonb, ?)",
        organizationId,
        transactionId,
        customerId,
        type,
        properties,
        occurredAt);
  }
}
