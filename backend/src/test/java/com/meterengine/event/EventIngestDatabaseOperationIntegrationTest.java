package com.meterengine.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.context.WebApplicationContext;

// 커밋을 재려면 요청이 실제로 커밋해야 하므로 시험 트랜잭션을 두지 않는다.
// 테스트 기본값은 지표 내보내기를 꺼서 Prometheus 스크레이프와 히스토그램 버킷이 없으므로 켠다.
@AutoConfigureMetrics
class EventIngestDatabaseOperationIntegrationTest
    extends com.meterengine.MeteringEnvironmentTestSupport {

  private static final String TIMER_NAME = "meterengine.db.operation";

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private MeterRegistry meterRegistry;
  private MockMvcTester mvc;
  private UUID environmentId;
  private String authorization;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcTester.from(
            webApplicationContext, builder -> builder.apply(springSecurity()).build());
    authorization = issueKey("test", "ingest", true, null);
    environmentId = environmentId("test");
  }

  @Test
  void 환경_키로_이벤트를_저장하면_인증부터_커밋까지_DB_왕복마다_한_번씩_잰다() {
    assertRoundTrips("test");
  }

  @Test
  void LIVE_환경_키도_인증부터_커밋까지_실제_schema의_DB_왕복을_잰다() {
    assertRoundTrips("live");
  }

  private void assertRoundTrips(String mode) {
    authorization = issueKey(mode, "ingest", true, null);
    environmentId = environmentId(mode);
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
  void 환경이_없는_키는_거부하고_환경_조회와_업무_저장과_커밋을_재지_않는다() {
    String unscopedAuthorization = issueKey("test", "ingest", false, null);
    long environmentSelects = count("select", "organization_environment");
    long customerSelects = count("select", "customer");
    long inserts = count("insert", "event");
    long commits = count("commit", "event");
    assertThat(post(unscopedAuthorization, UUID.randomUUID())).hasStatus(403);
    assertThat(count("select", "organization_environment")).isEqualTo(environmentSelects);
    assertThat(count("select", "customer")).isEqualTo(customerSelects);
    assertThat(count("insert", "event")).isEqualTo(inserts);
    assertThat(count("commit", "event")).isEqualTo(commits);
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
    String mode =
        jdbcTemplate.queryForObject(
            "SELECT mode FROM shared.organization_environment WHERE id=? AND organization_id=?",
            String.class,
            customerEnvironmentId,
            organizationId);
    return transactions.write(
        scope(mode),
        () ->
            jdbcTemplate.queryForObject(
                "INSERT INTO customer (organization_id, name) VALUES (?, '고객') RETURNING id",
                UUID.class,
                organizationId));
  }

  private MvcTestResult post(UUID customerId) {
    return post(authorization, customerId);
  }

  private MvcTestResult post(String requestAuthorization, UUID customerId) {
    return post(requestAuthorization, customerId, UUID.randomUUID().toString());
  }

  private MvcTestResult post(String requestAuthorization, UUID customerId, String transactionId) {
    return mvc.post()
        .uri("/v1/events")
        .header(AUTHORIZATION, requestAuthorization)
        .contentType(MediaType.APPLICATION_JSON)
        .content(eventBody(transactionId, customerId))
        .exchange();
  }

  @Test
  void TEST_배치는_실제_커밋_뒤에만_stored를_센다() {
    assertBatchCommit("test");
  }

  @Test
  void LIVE_배치는_실제_커밋_뒤에만_stored를_센다() {
    assertBatchCommit("live");
  }

  @Test
  void 단건의_실제_커밋이_롤백되면_stored와_commit_대신_failed만_센다() {
    assertCommitRollback("test", false);
  }

  @Test
  void 배치의_실제_커밋이_롤백되면_원소_전체를_failed로_센다() {
    assertCommitRollback("live", true);
  }

  private void assertBatchCommit(String mode) {
    String key = issueKey(mode, "ingest", true, null);
    UUID customer = insertCustomer(environmentId(mode));
    String prefix = "mi1-success-" + UUID.randomUUID();
    double stored = ingestCount(mode, "stored");
    double failed = ingestCount(mode, "failed");
    assertThat(batch(key, customer, prefix)).hasStatusOk();
    assertThat(ingestCount(mode, "stored")).isEqualTo(stored + 2);
    assertThat(ingestCount(mode, "failed")).isEqualTo(failed);
    assertThat(storedRows(mode, prefix)).isEqualTo(2);
  }

  private void assertCommitRollback(String mode, boolean batch) {
    String key = issueKey(mode, "ingest", true, null);
    UUID customer = insertCustomer(environmentId(mode));
    String prefix = "mi1-rollback-" + UUID.randomUUID();
    double stored = ingestCount(mode, "stored");
    double failed = ingestCount(mode, "failed");
    long commits = count("commit", "event");
    var owner = ownerJdbc();
    owner.execute(
        "CREATE FUNCTION "
            + mode
            + ".mi1_reject_commit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF"
            + " NEW.transaction_id LIKE 'mi1-rollback-%' THEN RAISE EXCEPTION"
            + " 'mi1-event-commit-rejected'; END IF; RETURN NEW; END $$");
    owner.execute(
        "CREATE CONSTRAINT TRIGGER mi1_reject_commit AFTER INSERT ON "
            + mode
            + ".event DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION "
            + mode
            + ".mi1_reject_commit()");
    try {
      if (batch) assertThat(batch(key, customer, prefix)).hasStatus(500);
      else assertThat(post(key, customer, prefix)).hasStatus(500);
      assertThat(ingestCount(mode, "stored")).isEqualTo(stored);
      assertThat(ingestCount(mode, "failed")).isEqualTo(failed + (batch ? 2 : 1));
      assertThat(count("commit", "event")).isEqualTo(commits);
      assertThat(storedRows(mode, prefix)).isZero();
    } finally {
      owner.execute("DROP TRIGGER mi1_reject_commit ON " + mode + ".event");
      owner.execute("DROP FUNCTION " + mode + ".mi1_reject_commit()");
    }
  }

  private long storedRows(String mode, String prefix) {
    return transactions.read(
        scope(mode),
        () ->
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM event WHERE organization_id=? AND transaction_id LIKE ?",
                Long.class,
                organizationId,
                prefix + "%"));
  }

  private double ingestCount(String mode, String result) {
    return meterRegistry
        .get("meterengine.event.ingest")
        .tags("environment", mode, "result", result)
        .counter()
        .count();
  }

  private MvcTestResult batch(String key, UUID customer, String prefix) {
    return mvc.post()
        .uri("/v1/event-batches")
        .header(AUTHORIZATION, key)
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            "{\"events\":["
                + eventBody(prefix + "-1", customer)
                + ","
                + eventBody(prefix + "-2", customer)
                + "]}")
        .exchange();
  }

  private static String eventBody(String transactionId, UUID customerId) {
    return """
    {"transaction_id":"%s","customer_id":"%s","type":"chat_completion",
     "properties":{"token":1},"timestamp":"%s"}
    """
        .formatted(transactionId, customerId, OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS));
  }

  private long count(String operation, String table) {
    Timer timer =
        meterRegistry.find(TIMER_NAME).tag("operation", operation).tag("table", table).timer();
    return timer == null ? 0 : timer.count();
  }
}
