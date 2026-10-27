package com.meterengine.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.global.security.BusinessEnvironment;
import com.meterengine.global.security.BusinessEnvironmentContext;
import com.meterengine.global.security.OrganizationEnvironments;
import com.meterengine.notification.service.UnbilledUsageReportService;
import java.time.OffsetDateTime;
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
}
