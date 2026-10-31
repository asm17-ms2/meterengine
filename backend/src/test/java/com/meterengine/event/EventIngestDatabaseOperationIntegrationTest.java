package com.meterengine.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.context.WebApplicationContext;

// 커밋을 재려면 요청이 실제로 커밋해야 하므로 시험 트랜잭션을 두지 않는다.
// 테스트 기본값은 지표 내보내기를 꺼서 Prometheus 스크레이프와 히스토그램 버킷이 없으므로 켠다.
@AutoConfigureMetrics
@SpringBootTest(properties = "meterengine.webhook.enabled=false")
@Import(TestcontainersConfiguration.class)
class EventIngestDatabaseOperationIntegrationTest {

  private static final String TIMER_NAME = "meterengine.db.operation";

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private MeterRegistry meterRegistry;
  private MockMvcTester mvc;
  private UUID organizationId;
  private UUID environmentId;
  private String authorization;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcTester.from(
            webApplicationContext, builder -> builder.apply(springSecurity()).build());
    organizationId = UUID.randomUUID();
    authorization = TestCredentials.issueApiKey(jdbcTemplate, organizationId, "test", "ingest");
    environmentId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM organization_environment WHERE organization_id = ? AND mode = 'test'",
            UUID.class,
            organizationId);
    jdbcTemplate.update(
        "UPDATE api_key SET environment_id = ? WHERE key_hash = sha256(convert_to(?, 'UTF8'))",
        environmentId,
        TestCredentials.rawKeyOf(authorization));
  }

  @Test
  void 환경_키로_이벤트를_저장하면_인증부터_커밋까지_DB_왕복마다_한_번씩_잰다() {
    UUID customerId = insertCustomer(environmentId);
    long apiKeySelects = count("select", "api_key");
    long environmentSelects = count("select", "organization_environment");
    long organizationSelects = count("select", "organization");
    long customerSelects = count("select", "customer");
    long eventInserts = count("insert", "event");
    long eventCommits = count("commit", "event");

    assertThat(post(customerId)).hasStatusOk();

    assertThat(count("select", "api_key")).isEqualTo(apiKeySelects + 1);
    assertThat(count("select", "organization_environment")).isEqualTo(environmentSelects + 1);
    assertThat(count("select", "organization")).isEqualTo(organizationSelects + 1);
    assertThat(count("select", "customer")).isEqualTo(customerSelects + 1);
    assertThat(count("insert", "event")).isEqualTo(eventInserts + 1);
    assertThat(count("commit", "event")).isEqualTo(eventCommits + 1);
  }

  // SimpleMeterRegistry는 백분위 히스토그램을 내지 않아 실제 스크레이프로 버킷을 본다.
  @Test
  void p99를_구하도록_기본_하한_1ms보다_잘게_나눈_히스토그램_버킷을_낸다() throws Exception {
    assertThat(post(insertCustomer(environmentId))).hasStatusOk();

    String scrape =
        mvc.get().uri("/actuator/prometheus").exchange().getResponse().getContentAsString();
    double smallestBucket =
        scrape
            .lines()
            .filter(line -> line.startsWith("meterengine_db_operation_seconds_bucket{"))
            .filter(
                line -> line.contains("operation=\"insert\"") && line.contains("table=\"event\""))
            .map(line -> line.replaceAll(".*le=\"([^\"]+)\".*", "$1"))
            .filter(bound -> !bound.equals("+Inf"))
            .mapToDouble(Double::parseDouble)
            .min()
            .orElseThrow();
    assertThat(smallestBucket).isLessThan(0.001);
  }

  @Test
  void 환경이_없는_키로_저장하면_환경_조회는_재지_않는다() {
    String legacyAuthorization =
        TestCredentials.issueApiKey(jdbcTemplate, organizationId, "ingest");
    UUID customerId = insertCustomer(null);
    long environmentSelects = count("select", "organization_environment");

    assertThat(post(legacyAuthorization, customerId)).hasStatusOk();

    assertThat(count("select", "organization_environment")).isEqualTo(environmentSelects);
  }

  @Test
  void 고객이_없어_거절한_이벤트는_고객_조회만_재고_커밋은_재지_않는다() {
    long customerSelects = count("select", "customer");
    long eventInserts = count("insert", "event");
    long eventCommits = count("commit", "event");

    assertThat(post(UUID.randomUUID())).hasStatus(404);

    assertThat(count("select", "customer")).isEqualTo(customerSelects + 1);
    assertThat(count("insert", "event")).isEqualTo(eventInserts);
    assertThat(count("commit", "event")).isEqualTo(eventCommits);
  }

  private UUID insertCustomer(UUID customerEnvironmentId) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO customer (organization_id, environment_id, name) VALUES (?, ?, '고객') RETURNING id",
        UUID.class,
        organizationId,
        customerEnvironmentId);
  }

  private MvcTestResult post(UUID customerId) {
    return post(authorization, customerId);
  }

  private MvcTestResult post(String requestAuthorization, UUID customerId) {
    String occurredAt = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString();
    return mvc.post()
        .uri("/v1/events")
        .header(AUTHORIZATION, requestAuthorization)
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            """
            {"transaction_id":"%s","customer_id":"%s","type":"chat_completion",
             "properties":{"token":1},"timestamp":"%s"}
            """
                .formatted(UUID.randomUUID(), customerId, occurredAt))
        .exchange();
  }

  private long count(String operation, String table) {
    Timer timer =
        meterRegistry.find(TIMER_NAME).tag("operation", operation).tag("table", table).timer();
    return timer == null ? 0 : timer.count();
  }
}
