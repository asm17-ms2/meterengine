package com.meterengine.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.meterengine.KafkaTestcontainersConfiguration;
import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.global.error.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Import({TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class})
@SpringBootTest(
    properties = {
      "meterengine.event.write-path=kafka",
      "meterengine.role=ingest",
      "meterengine.event.kafka.create-topics=true",
      "meterengine.event.kafka.partitions=3",
      "meterengine.event.kafka.replicas=1",
      "meterengine.event.kafka.producer-name=test-producer"
    })
class KafkaEventIngestIntegrationTest {

  private static final String TOPIC = "event.ingest";
  private static final String TRACEPARENT =
      "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
  private static final Duration POLL_TIMEOUT = Duration.ofMillis(200);

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private JsonMapper jsonMapper;
  @Autowired private KafkaContainer kafkaContainer;

  private MockMvcTester mvc;
  private UUID organizationId;
  private String authorization;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcTester.from(
            webApplicationContext, builder -> builder.apply(springSecurity()).build());
    organizationId = UUID.randomUUID();
    authorization = TestCredentials.issueApiKey(jdbcTemplate, organizationId);
  }

  @Test
  void 유효한_이벤트는_202이고_duplicate_없이_transaction_id만_돌려준다() {
    MvcTestResult result = postEvent(event("tx-1", UUID.randomUUID(), now()));

    assertThat(result).hasStatus(202);
    JsonNode body = body(result);
    assertThat(body.path("transaction_id").asString()).isEqualTo("tx-1");
    assertThat(body.has("duplicate")).isFalse();
  }

  @Test
  void 받은_이벤트는_요청_안에서_DB에_저장하지_않는다() {
    postEvent(event("tx-1", UUID.randomUUID(), now()));

    assertThat(count("event")).isZero();
  }

  @Test
  void 발행한_메시지는_예시_파일과_같은_필드를_싣는다() throws IOException {
    UUID customerId = UUID.randomUUID();
    OffsetDateTime occurredAt = now();

    postEvent(event("tx-1", customerId, occurredAt));

    JsonNode message = jsonMapper.readTree(published("tx-1").value());
    assertThat(message.propertyNames()).containsExactlyElementsOf(example().propertyNames());
    assertThat(message.path("schema_version").asInt()).isEqualTo(1);
    assertThat(message.path("organization_id").asString()).isEqualTo(organizationId.toString());
    assertThat(message.path("customer_id").asString()).isEqualTo(customerId.toString());
    assertThat(message.path("type").asString()).isEqualTo("api_call");
    assertThat(message.path("properties").path("tokens").asInt()).isEqualTo(1520);
    assertThat(OffsetDateTime.parse(message.path("occurred_at").asString())).isEqualTo(occurredAt);
    assertThat(OffsetDateTime.parse(message.path("accepted_at").asString()))
        .isBetween(occurredAt, OffsetDateTime.now(ZoneOffset.UTC));
    assertThat(message.path("actor_type").asString()).isEqualTo("api_key");
    assertThat(message.path("actor_id").isNull()).isFalse();
    assertThat(message.path("request_id").isNull()).isFalse();
  }

  @Test
  void 메시지_키는_도입사와_transaction_id를_잇는다() {
    postEvent(event("tx-1", UUID.randomUUID(), now()));

    assertThat(published("tx-1").key()).isEqualTo(organizationId + ":tx-1");
  }

  @Test
  void 헤더에는_보낸_서버와_추적_문맥만_싣고_자바_클래스_이름은_싣지_않는다() {
    postEvent(event("tx-1", UUID.randomUUID(), now()));

    Map<String, String> headers = headers(published("tx-1"));
    assertThat(headers)
        .containsOnly(
            Map.entry("producer", "test-producer"), Map.entry("traceparent", TRACEPARENT));
  }

  @Test
  void 발생한_지_36일인_이벤트는_400이고_발행도_거절_기록도_하지_않는다() {
    MvcTestResult result = postEvent(event("tx-old", UUID.randomUUID(), now().minusDays(36)));

    assertThat(result)
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCode.EVENT_TOO_OLD.getCode());
    assertThat(publishedKeys()).doesNotContain(organizationId + ":tx-old");
    assertThat(count("event_rejection")).isZero();
  }

  @Test
  void 배치는_원소마다_accepted와_rejected를_돌려주고_받은_원소만_발행한다() {
    String batch =
        "{\"events\":["
            + event("tx-ok", UUID.randomUUID(), now())
            + ",{\"transaction_id\":\"tx-broken\"},"
            + event("tx-old", UUID.randomUUID(), now().minusDays(36))
            + "]}";

    MvcTestResult result = postBatch(batch);

    assertThat(result).hasStatus(202);
    JsonNode results = body(result).path("results");
    assertThat(results.get(0).path("result").asString()).isEqualTo("accepted");
    assertThat(results.get(1).path("result").asString()).isEqualTo("rejected");
    assertThat(results.get(1).path("error").path("code").asString()).isEqualTo("validation_error");
    assertThat(results.get(2).path("result").asString()).isEqualTo("rejected");
    assertThat(results.get(2).path("error").path("code").asString()).isEqualTo("event_too_old");
    assertThat(publishedKeys())
        .contains(organizationId + ":tx-ok")
        .doesNotContain(organizationId + ":tx-broken", organizationId + ":tx-old");
    assertThat(count("event_rejection")).isZero();
  }

  @Test
  void 배치에_받은_원소가_없으면_200이다() {
    MvcTestResult result = postBatch("{\"events\":[{\"transaction_id\":\"tx-broken\"}]}");

    assertThat(result).hasStatusOk();
  }

  private MvcTestResult postEvent(String json) {
    return post("/v1/events", json);
  }

  private MvcTestResult postBatch(String json) {
    return post("/v1/event-batches", json);
  }

  private MvcTestResult post(String uri, String json) {
    return mvc.post()
        .uri(uri)
        .header(AUTHORIZATION, authorization)
        .header("traceparent", TRACEPARENT)
        .contentType(MediaType.APPLICATION_JSON)
        .content(json)
        .exchange();
  }

  private static String event(String transactionId, UUID customerId, OffsetDateTime occurredAt) {
    return """
        {"transaction_id":"%s","customer_id":"%s","type":"api_call",
         "properties":{"tokens":1520,"region":"ap-northeast-2"},"timestamp":"%s"}
        """
        .formatted(transactionId, customerId, occurredAt);
  }

  private static OffsetDateTime now() {
    return OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
  }

  private JsonNode body(MvcTestResult result) {
    return jsonMapper.readTree(
        new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8));
  }

  private JsonNode example() throws IOException {
    try (InputStream input =
        getClass().getResourceAsStream(EventIngestMessageContractTest.EXAMPLE)) {
      return jsonMapper.readTree(input);
    }
  }

  private int count(String table) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM " + table + " WHERE organization_id = ?",
            Integer.class,
            organizationId);
    return count == null ? 0 : count;
  }

  private ConsumerRecord<String, byte[]> published(String transactionId) {
    String key = organizationId + ":" + transactionId;
    return publishedRecords().stream()
        .filter(consumerRecord -> key.equals(consumerRecord.key()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("발행된 메시지가 없다: " + key));
  }

  private List<String> publishedKeys() {
    return publishedRecords().stream().map(ConsumerRecord::key).toList();
  }

  // 발행 확인 뒤에 응답하므로, 응답을 받은 시점의 토픽 끝까지만 읽으면 그 요청의 메시지가 다 들어 있다.
  private List<ConsumerRecord<String, byte[]>> publishedRecords() {
    Map<String, Object> config =
        Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers(),
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
    try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(config)) {
      List<TopicPartition> partitions =
          consumer.partitionsFor(TOPIC).stream()
              .map(partition -> new TopicPartition(TOPIC, partition.partition()))
              .toList();
      consumer.assign(partitions);
      consumer.seekToBeginning(partitions);
      Map<TopicPartition, Long> endOffsets = consumer.endOffsets(partitions);
      List<ConsumerRecord<String, byte[]>> records = new ArrayList<>();
      while (partitions.stream()
          .anyMatch(partition -> consumer.position(partition) < endOffsets.get(partition))) {
        consumer.poll(POLL_TIMEOUT).forEach(records::add);
      }
      return records;
    }
  }

  private static Map<String, String> headers(ConsumerRecord<String, byte[]> consumerRecord) {
    Map<String, String> headers = new java.util.LinkedHashMap<>();
    for (Header header : consumerRecord.headers()) {
      headers.put(header.key(), new String(header.value(), StandardCharsets.UTF_8));
    }
    return headers;
  }
}
