package com.meterengine.event.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(TestcontainersConfiguration.class)
@AutoConfigureMetrics
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class LocalIngestWarmupClientsIntegrationTest {

  @Autowired private IngestWarmupClients ingestWarmupClients;
  @Autowired private JdbcTemplate jdbcTemplate;

  private UUID organizationId;
  private UUID customerId;
  private String apiKey;

  @BeforeEach
  void setUp() {
    organizationId = UUID.randomUUID();
    apiKey =
        TestCredentials.rawKeyOf(
            TestCredentials.issueApiKey(jdbcTemplate, organizationId, "ingest"));
    customerId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO customer (id, organization_id, name) VALUES (?, ?, '예열 고객')",
        customerId,
        organizationId);
  }

  @Test
  void 여러_번_보내도_이벤트는_한_행만_남는다() {
    try (IngestWarmupClient client = ingestWarmupClients.open(apiKey, organizationId, customerId)) {
      client.sendEvent();
      client.sendEventBatch();
      client.sendEvent();
      client.sendEventBatch();
    }

    assertThat(countRows("event")).isEqualTo(1);
    assertThat(countRows("event_rejection")).isZero();
  }

  @Test
  void 묶음_원소가_거절되면_200이어도_예외를_던진다() {
    try (IngestWarmupClient client =
        ingestWarmupClients.open(apiKey, organizationId, UUID.randomUUID())) {
      assertThatThrownBy(client::sendEventBatch)
          .isInstanceOf(IngestWarmupException.class)
          .hasMessageContaining("/v1/event-batches")
          .hasMessageContaining("rejected");
    }
  }

  @Test
  void 도입사가_다른_키면_403으로_멈추고_아무것도_쓰지_않는다() {
    try (IngestWarmupClient client =
        ingestWarmupClients.open(apiKey, UUID.randomUUID(), customerId)) {
      assertThatThrownBy(client::sendEvent)
          .isInstanceOf(IngestWarmupException.class)
          .hasMessageContaining("/v1/events")
          .hasMessageContaining("403");
    }

    assertThat(countRows("event")).isZero();
    assertThat(countRows("event_rejection")).isZero();
    assertThat(countRows("audit_log")).isZero();
  }

  private long countRows(String table) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM " + table + " WHERE organization_id = ?", Long.class, organizationId);
  }
}
