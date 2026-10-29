package com.meterengine.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.global.security.BusinessEnvironment;
import com.meterengine.global.security.BusinessEnvironmentContext;
import com.meterengine.global.security.OrganizationEnvironments;
import com.meterengine.notification.service.UnbilledUsageReportService;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = "meterengine.webhook.enabled=false")
@Import(TestcontainersConfiguration.class)
class UnbilledUsageNotificationIntegrationTest {
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private UnbilledUsageReportService unbilledUsageReportService;
  @Autowired private JsonMapper jsonMapper;
  @Autowired private OrganizationEnvironments environments;

  @Test
  void 음수_이벤트_건수와_음수_합_잘림_조합을_서로_다른_칸으로_발행한다() {
    UUID organizationId = UUID.randomUUID();
    TestCredentials.issueApiKey(jdbcTemplate, organizationId, "admin");
    UUID customerId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO customer (id, organization_id, name) VALUES (?, ?, '고객')",
        customerId,
        organizationId);
    jdbcTemplate.update(
        "INSERT INTO billable_metric (organization_id, code, name, event_type, aggregation,"
            + " target_property, dimension_properties) VALUES (?, 'quantity', '수량', 'usage', 'sum',"
            + " 'quantity', '{}')",
        organizationId);
    jdbcTemplate.update(
        "INSERT INTO event (organization_id, transaction_id, customer_id, type, properties,"
            + " occurred_at) VALUES (?, 'negative', ?, 'usage', '{\"quantity\":-2}', ?)",
        organizationId,
        customerId,
        OffsetDateTime.now().minusSeconds(1));
    subscribe(organizationId, null);
    assertThat(unbilledUsageReportService.report(organizationId)).isTrue();
    JsonNode payload =
        jsonMapper.readTree(
            jdbcTemplate.queryForObject(
                "SELECT payload::text FROM webhook_event WHERE organization_id = ?",
                String.class,
                organizationId));
    assertThat(payload.path("type").asString()).isEqualTo("unbilled_usage_detected");
    assertThat(payload.path("data").path("negative_quantity").path("event_count").asInt())
        .isEqualTo(1);
    assertThat(payload.path("data").path("negative_usages").size()).isEqualTo(1);
    assertThat(payload.path("data").path("negative_usages").get(0).path("customer_id").asString())
        .isEqualTo(customerId.toString());
    assertThat(payload.path("data").path("negative_usages").get(0).path("quantity").asInt())
        .isEqualTo(-2);
  }

  @Test
  void 알릴_이벤트가_없으면_발행하지_않는다() {
    UUID organizationId = UUID.randomUUID();
    TestCredentials.issueApiKey(jdbcTemplate, organizationId, "admin");
    assertThat(unbilledUsageReportService.report(organizationId)).isFalse();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM webhook_event WHERE organization_id = ?",
                Integer.class,
                organizationId))
        .isZero();
  }

  @Test
  void 환경별_작업은_해당환경의_진단을_웹훅원장에_고정한다() {
    UUID organizationId = UUID.randomUUID();
    TestCredentials.issueApiKey(jdbcTemplate, organizationId, "admin");
    BusinessEnvironment test = environments.resolve(organizationId, "test");
    BusinessEnvironment live = environments.resolve(organizationId, "live");
    UUID customer = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO customer (id,organization_id,environment_id,name) VALUES (?,?,?,'test"
            + " customer')",
        customer,
        organizationId,
        test.environmentId());
    jdbcTemplate.update(
        "INSERT INTO event"
            + " (organization_id,environment_id,transaction_id,customer_id,type,properties,occurred_at)"
            + " VALUES (?,?,'test-only',?,'unmapped','{}',?)",
        organizationId,
        test.environmentId(),
        customer,
        OffsetDateTime.now().minusSeconds(1));
    subscribe(organizationId, test.environmentId());
    subscribe(organizationId, live.environmentId());
    subscribe(organizationId, null);
    assertThat(
            BusinessEnvironmentContext.run(
                test, () -> unbilledUsageReportService.report(organizationId)))
        .isTrue();
    assertThat(
            BusinessEnvironmentContext.run(
                live, () -> unbilledUsageReportService.report(organizationId)))
        .isFalse();
    assertThat(unbilledUsageReportService.report(organizationId)).isFalse();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM webhook_event WHERE organization_id = ?",
                Integer.class,
                organizationId))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT environment_id FROM webhook_event WHERE organization_id = ?",
                UUID.class,
                organizationId))
        .isEqualTo(test.environmentId());
    assertThat(BusinessEnvironmentContext.current()).isNull();
  }

  @Test
  void 미청구_통보를_구독한_수신_주소가_없으면_웹훅_원장에_남기지_않는다() {
    UUID organizationId = UUID.randomUUID();
    TestCredentials.issueApiKey(jdbcTemplate, organizationId, "admin");
    insertEvent(organizationId, null, "unmapped", "{}", OffsetDateTime.now().minusSeconds(1));
    assertThat(unbilledUsageReportService.report(organizationId)).isFalse();
    assertThat(webhookEventCount(organizationId)).isZero();
  }

  @Test
  void 단가_없는_사용량만_있어도_통보에_싣는다() {
    UUID organizationId = UUID.randomUUID();
    TestCredentials.issueApiKey(jdbcTemplate, organizationId, "admin");
    UUID customerId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO customer (id, organization_id, name) VALUES (?, ?, '고객')",
        customerId,
        organizationId);
    jdbcTemplate.update(
        "INSERT INTO billable_metric (organization_id, code, name, event_type, aggregation,"
            + " target_property, dimension_properties) VALUES (?, 'quantity', '수량', 'usage', 'sum',"
            + " 'quantity', '{}')",
        organizationId);
    insertEvent(
        organizationId, customerId, "usage", "{\"quantity\":3}", OffsetDateTime.now().minusSeconds(1));
    subscribe(organizationId, null);
    assertThat(unbilledUsageReportService.report(organizationId)).isTrue();
    JsonNode unpriced = payloads(organizationId).get(0).path("data").path("unpriced_usages");
    assertThat(unpriced.size()).isEqualTo(1);
    assertThat(unpriced.get(0).path("customer_id").asString()).isEqualTo(customerId.toString());
    assertThat(unpriced.get(0).path("quantity").asInt()).isEqualTo(3);
  }

  @Test
  void 지난달_사용량은_지난달_통보로_따로_발행한다() {
    UUID organizationId = UUID.randomUUID();
    TestCredentials.issueApiKey(jdbcTemplate, organizationId, "admin");
    YearMonth current = YearMonth.now(ZoneId.of("Asia/Seoul"));
    OffsetDateTime lastHourOfPreviousMonth =
        current.atDay(1).atStartOfDay(ZoneId.of("Asia/Seoul")).toOffsetDateTime().minusHours(1);
    insertEvent(organizationId, null, "unmapped", "{}", lastHourOfPreviousMonth);
    subscribe(organizationId, null);
    assertThat(unbilledUsageReportService.report(organizationId)).isTrue();
    assertThat(payloads(organizationId))
        .extracting(payload -> payload.path("data").path("month").asString())
        .containsExactly(current.minusMonths(1).toString());
  }

  private void subscribe(UUID organizationId, UUID environmentId) {
    jdbcTemplate.update(
        "INSERT INTO webhook_endpoint (id, organization_id, environment_id, url, secret,"
            + " event_types) VALUES (?, ?, ?, 'https://hooks.example.com/meterengine', 'whsec_test',"
            + " ARRAY['unbilled_usage_detected'])",
        UUID.randomUUID(),
        organizationId,
        environmentId);
  }

  private void insertEvent(
      UUID organizationId,
      UUID customerId,
      String type,
      String properties,
      OffsetDateTime occurredAt) {
    UUID customer = customerId;
    if (customer == null) {
      customer = UUID.randomUUID();
      jdbcTemplate.update(
          "INSERT INTO customer (id, organization_id, name) VALUES (?, ?, '고객')",
          customer,
          organizationId);
    }
    jdbcTemplate.update(
        "INSERT INTO event (organization_id, transaction_id, customer_id, type, properties,"
            + " occurred_at) VALUES (?, ?, ?, ?, ?::jsonb, ?)",
        organizationId,
        UUID.randomUUID().toString(),
        customer,
        type,
        properties,
        occurredAt);
  }

  private int webhookEventCount(UUID organizationId) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM webhook_event WHERE organization_id = ?", Integer.class, organizationId);
  }

  private List<JsonNode> payloads(UUID organizationId) {
    return jdbcTemplate.queryForList(
            "SELECT payload::text FROM webhook_event WHERE organization_id = ? ORDER BY created_at",
            String.class,
            organizationId)
        .stream()
        .map(jsonMapper::readTree)
        .toList();
  }
}
