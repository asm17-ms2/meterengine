package com.meterengine.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.meterengine.KafkaTestcontainersConfiguration;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.event.client.EventDeadLetterException;
import com.meterengine.event.client.EventDeadLetterPublisher;
import com.meterengine.event.dto.EventIngestMessage;
import com.meterengine.event.dto.EventIngestRecord;
import com.meterengine.event.service.EventConsumeService;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

// 리스너를 끄고 저장 서비스를 직접 불러, 한 묶음에 무엇이 들었는지를 시험이 정한다.
@Import({TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class})
@SpringBootTest(
    properties = {
      "meterengine.event.write-path=kafka",
      "meterengine.role=ingest",
      "meterengine.event.kafka.create-topics=true",
      "meterengine.event.kafka.replicas=1"
    })
class EventConsumeIntegrationTest {

  private static final String DEAD_LETTER_TOPIC = "event.dlq";
  private static final Duration POLL_TIMEOUT = Duration.ofMillis(200);

  @Autowired private EventConsumeService eventConsumeService;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private JsonMapper jsonMapper;
  @Autowired private KafkaContainer kafkaContainer;
  @Autowired private MeterRegistry meterRegistry;
  @MockitoSpyBean private EventDeadLetterPublisher eventDeadLetterPublisher;

  private UUID organizationId;
  private UUID customerId;

  @BeforeEach
  void setUp() {
    organizationId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO organization (id, name) VALUES (?, '테스트 도입사')", organizationId);
    customerId =
        jdbcTemplate.queryForObject(
            "INSERT INTO customer (organization_id, name) VALUES (?, 'acme') RETURNING id",
            UUID.class,
            organizationId);
  }

  @Test
  void 메시지를_event에_넣고_received_at은_수집_서버가_받은_시각이다() {
    EventIngestMessage message = message("tx-1", customerId);

    eventConsumeService.consume(List.of(eventIngestRecord(message)));

    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            """
            SELECT received_at, request_id, actor_type, actor_id, properties::text AS properties
            FROM event WHERE organization_id = ? AND transaction_id = 'tx-1'
            """,
            organizationId);
    assertThat(((java.sql.Timestamp) row.get("received_at")).toInstant())
        .isEqualTo(message.acceptedAt());
    assertThat(row.get("request_id")).isEqualTo(message.requestId());
    assertThat(row.get("actor_type")).isEqualTo("api_key");
    assertThat(row.get("actor_id")).isEqualTo(message.actorId());
    assertThat(jsonMapper.readTree((String) row.get("properties")).path("unit_cost").asString())
        .isEqualTo("0.25");
  }

  @Test
  void 같은_메시지를_다시_읽어도_한_행만_남는다() {
    EventIngestRecord eventIngestRecord = eventIngestRecord(message("tx-1", customerId));

    eventConsumeService.consume(List.of(eventIngestRecord, eventIngestRecord));
    eventConsumeService.consume(List.of(eventIngestRecord));

    assertThat(count("event")).isEqualTo(1);
  }

  @Test
  void 없는_고객의_메시지는_저장하지_않고_거절_기록에_남긴다() {
    EventIngestMessage message = message("tx-1", UUID.randomUUID());

    eventConsumeService.consume(List.of(eventIngestRecord(message)));

    assertThat(count("event")).isZero();
    assertThat(rejectionCodes()).containsExactly("customer_not_found");
  }

  @Test
  void 보관한_고객의_메시지는_저장하지_않고_거절_기록에_남긴다() {
    jdbcTemplate.update("UPDATE customer SET archived_at = now() WHERE id = ?", customerId);

    eventConsumeService.consume(List.of(eventIngestRecord(message("tx-1", customerId))));

    assertThat(count("event")).isZero();
    assertThat(rejectionCodes()).containsExactly("customer_archived");
  }

  @Test
  void 읽을_수_없는_레코드는_DLQ로_보내고_같은_묶음의_나머지는_저장한다() {
    String brokenKey = organizationId + ":broken";
    EventIngestRecord broken =
        new EventIngestRecord(brokenKey, "not json".getBytes(StandardCharsets.UTF_8), Map.of());

    eventConsumeService.consume(List.of(broken, eventIngestRecord(message("tx-1", customerId))));

    assertThat(count("event")).isEqualTo(1);
    ConsumerRecord<String, byte[]> deadLetter = deadLetter(brokenKey);
    assertThat(new String(deadLetter.value(), StandardCharsets.UTF_8)).isEqualTo("not json");
    assertThat(header(deadLetter, "failure_reason")).isEqualTo("invalid_message");
    assertThat(Instant.parse(header(deadLetter, "failed_at"))).isNotNull();
  }

  @Test
  void 모르는_schema_version은_DLQ로_보낸다() {
    String key = organizationId + ":tx-future";
    byte[] future =
        jsonMapper.writeValueAsBytes(
            Map.of("schema_version", 2, "organization_id", organizationId.toString()));

    eventConsumeService.consume(List.of(new EventIngestRecord(key, future, Map.of())));

    assertThat(header(deadLetter(key), "failure_reason")).isEqualTo("unknown_schema_version");
  }

  @Test
  void DB가_받지_않는_메시지만_DLQ로_보내고_같은_묶음의_나머지는_저장한다() {
    EventIngestMessage unstorable =
        new EventIngestMessage(
            EventIngestMessage.CURRENT_SCHEMA_VERSION,
            organizationId,
            null,
            "tx-unstorable",
            customerId,
            "api_call",
            Map.of("note", "a\u0000b"),
            now(),
            now().toInstant(),
            UUID.randomUUID(),
            "api_key",
            UUID.randomUUID());

    eventConsumeService.consume(
        List.of(eventIngestRecord(unstorable), eventIngestRecord(message("tx-1", customerId))));

    assertThat(storedTransactionIds()).containsExactly("tx-1");
    assertThat(header(deadLetter(unstorable.key()), "failure_reason"))
        .isEqualTo("constraint_violation");
  }

  @Test
  void DLQ_발행이_실패해_묶음을_다시_읽어도_거절_기록은_한_행만_남는다() {
    EventIngestRecord rejected = eventIngestRecord(message("tx-1", UUID.randomUUID()));

    consumeAgainAfterDeadLetterFailure(List.of(rejected, unreadableRecord()));

    assertThat(rejectionCodes()).containsExactly("customer_not_found");
  }

  // 도입사가 거절된 이벤트를 다시 보내면 수집 서버가 받은 시각이 달라 다른 거절이다.
  @Test
  void 다시_보낸_메시지의_거절은_기록을_새로_남긴다() {
    UUID missingCustomerId = UUID.randomUUID();
    EventIngestMessage first = message("tx-1", missingCustomerId);
    EventIngestMessage resent =
        message("tx-1", missingCustomerId, null, first.acceptedAt().plusSeconds(1));

    eventConsumeService.consume(List.of(eventIngestRecord(first)));
    eventConsumeService.consume(List.of(eventIngestRecord(resent)));

    assertThat(rejectionCodes()).containsExactly("customer_not_found", "customer_not_found");
  }

  @Test
  void 환경이_있는_메시지도_다시_읽으면_거절_기록은_한_행만_남는다() {
    EventIngestRecord rejected =
        eventIngestRecord(
            message("tx-1", UUID.randomUUID(), testEnvironmentId(), now().toInstant()));

    eventConsumeService.consume(List.of(rejected));
    eventConsumeService.consume(List.of(rejected));

    assertThat(rejectionCodes()).containsExactly("customer_not_found");
  }

  @Test
  void DLQ_발행이_실패해_묶음을_다시_읽어도_저장_건수는_한_번만_센다() {
    double storedBefore = consumed("stored", "none");

    consumeAgainAfterDeadLetterFailure(
        List.of(eventIngestRecord(message("tx-1", customerId)), unreadableRecord()));

    assertThat(consumed("stored", "none") - storedBefore).isEqualTo(1);
  }

  @Test
  void DLQ_발행이_실패해_묶음을_다시_읽어도_거절_건수는_한_번만_센다() {
    double rejectedBefore = consumed("rejected", "customer_not_found");

    consumeAgainAfterDeadLetterFailure(
        List.of(eventIngestRecord(message("tx-1", UUID.randomUUID())), unreadableRecord()));

    assertThat(consumed("rejected", "customer_not_found") - rejectedBefore).isEqualTo(1);
  }

  // 첫 번째는 DLQ 발행이 던져 오프셋을 넘기지 못한 것이고, 두 번째는 컨테이너가 같은 묶음을 다시 준 것이다.
  private void consumeAgainAfterDeadLetterFailure(List<EventIngestRecord> eventIngestRecords) {
    doThrow(new EventDeadLetterException(DEAD_LETTER_TOPIC, new IllegalStateException("broker")))
        .doCallRealMethod()
        .when(eventDeadLetterPublisher)
        .publish(any(), any());

    assertThatThrownBy(() -> eventConsumeService.consume(eventIngestRecords))
        .isInstanceOf(EventDeadLetterException.class);
    eventConsumeService.consume(eventIngestRecords);
  }

  private EventIngestRecord unreadableRecord() {
    return new EventIngestRecord(
        organizationId + ":broken", "not json".getBytes(StandardCharsets.UTF_8), Map.of());
  }

  private UUID testEnvironmentId() {
    return jdbcTemplate.queryForObject(
        "SELECT id FROM organization_environment WHERE organization_id = ? AND mode = 'test'",
        UUID.class,
        organizationId);
  }

  private double consumed(String result, String reason) {
    return meterRegistry
        .get("meterengine.event.consume")
        .tag("result", result)
        .tag("reason", reason)
        .counter()
        .count();
  }

  private EventIngestMessage message(String transactionId, UUID messageCustomerId) {
    return message(transactionId, messageCustomerId, null, now().toInstant().minusSeconds(30));
  }

  private EventIngestMessage message(
      String transactionId, UUID messageCustomerId, UUID environmentId, Instant acceptedAt) {
    return new EventIngestMessage(
        EventIngestMessage.CURRENT_SCHEMA_VERSION,
        organizationId,
        environmentId,
        transactionId,
        messageCustomerId,
        "api_call",
        Map.of("tokens", 1520, "unit_cost", new java.math.BigDecimal("0.25")),
        now(),
        acceptedAt,
        UUID.randomUUID(),
        "api_key",
        UUID.randomUUID());
  }

  private EventIngestRecord eventIngestRecord(EventIngestMessage message) {
    return new EventIngestRecord(
        message.key(),
        jsonMapper.writeValueAsBytes(message),
        Map.of("producer", "test".getBytes(StandardCharsets.UTF_8)));
  }

  private static OffsetDateTime now() {
    return OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
  }

  private int count(String table) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM " + table + " WHERE organization_id = ?",
            Integer.class,
            organizationId);
    return count == null ? 0 : count;
  }

  private List<String> storedTransactionIds() {
    return jdbcTemplate.queryForList(
        "SELECT transaction_id FROM event WHERE organization_id = ? ORDER BY transaction_id",
        String.class,
        organizationId);
  }

  private List<String> rejectionCodes() {
    return jdbcTemplate.queryForList(
        "SELECT error_code FROM event_rejection WHERE organization_id = ?",
        String.class,
        organizationId);
  }

  private static String header(ConsumerRecord<String, byte[]> consumerRecord, String name) {
    return new String(consumerRecord.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
  }

  // DLQ 발행은 브로커 확인 뒤에 돌아오므로, 지금 토픽 끝까지만 읽으면 방금 보낸 레코드가 들어 있다.
  private ConsumerRecord<String, byte[]> deadLetter(String key) {
    Map<String, Object> config =
        Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers(),
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
    try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(config)) {
      List<TopicPartition> partitions = List.of(new TopicPartition(DEAD_LETTER_TOPIC, 0));
      consumer.assign(partitions);
      consumer.seekToBeginning(partitions);
      long end = consumer.endOffsets(partitions).get(partitions.get(0));
      List<ConsumerRecord<String, byte[]>> records = new ArrayList<>();
      while (consumer.position(partitions.get(0)) < end) {
        consumer.poll(POLL_TIMEOUT).forEach(records::add);
      }
      return records.stream()
          .filter(consumerRecord -> key.equals(consumerRecord.key()))
          .findFirst()
          .orElseThrow(() -> new AssertionError("DLQ에 레코드가 없다: " + key));
    }
  }
}
