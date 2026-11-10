package com.meterengine.event.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

import com.meterengine.event.client.EventIngestPublisher;
import com.meterengine.event.config.EventKafkaProperties;
import com.meterengine.event.dto.EventIngestMessage;
import com.meterengine.event.dto.IngestEventBatchRequest;
import com.meterengine.event.dto.IngestEventBatchResponse;
import com.meterengine.event.dto.IngestEventBatchResponse.EventResult;
import com.meterengine.event.dto.IngestEventRequest;
import com.meterengine.global.error.BusinessException;
import com.meterengine.global.error.ErrorCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.validation.Validation;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class KafkaEventIngestorTest {

  private static final UUID ORGANIZATION_ID = UUID.randomUUID();
  private static final UUID CUSTOMER_ID = UUID.randomUUID();

  @Mock private EventIngestPublisher eventIngestPublisher;

  private final JsonMapper jsonMapper = JsonMapper.builder().build();

  private SimpleMeterRegistry meterRegistry;
  private KafkaEventIngestor kafkaEventIngestor;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    kafkaEventIngestor =
        new KafkaEventIngestor(
            eventIngestPublisher,
            new EventBatchParser(
                Validation.buildDefaultValidatorFactory().getValidator(), jsonMapper),
            new EventIngestMetrics(meterRegistry),
            new EventKafkaProperties(
                "event.ingest",
                6,
                (short) 3,
                false,
                Duration.ofSeconds(1),
                "test",
                "event.dlq",
                Duration.ofSeconds(1)));
  }

  @Test
  void 발행이_확인되지_않으면_503이고_실패로_센다() {
    when(eventIngestPublisher.publish(any(), any())).thenReturn(failedPublishing());

    assertThatThrownBy(() -> kafkaEventIngestor.ingest(ORGANIZATION_ID, request("tx-1")))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EVENT_INGEST_UNAVAILABLE));
    assertThat(ingestCount("failed")).isEqualTo(1);
  }

  @Test
  void 발행이_끝나지_않으면_기다리는_상한에서_503이다() {
    when(eventIngestPublisher.publish(any(), any())).thenReturn(new CompletableFuture<>());

    assertThatThrownBy(() -> kafkaEventIngestor.ingest(ORGANIZATION_ID, request("tx-1")))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EVENT_INGEST_UNAVAILABLE));
  }

  @Test
  void 배치에서_일부만_발행에_실패하면_그_원소만_failed다() {
    when(eventIngestPublisher.publish(argThat(message("tx-ok")), any()))
        .thenReturn(CompletableFuture.completedFuture(null));
    when(eventIngestPublisher.publish(argThat(message("tx-lost")), any()))
        .thenReturn(failedPublishing());

    IngestEventBatchResponse response =
        kafkaEventIngestor.ingestBatch(ORGANIZATION_ID, batch("tx-ok", "tx-lost"));

    assertThat(response.results())
        .extracting(EventResult::transactionId, EventResult::result)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("tx-ok", "accepted"),
            org.assertj.core.groups.Tuple.tuple("tx-lost", "failed"));
    assertThat(response.results().get(1).error().code())
        .isEqualTo(ErrorCode.EVENT_INGEST_UNAVAILABLE);
    assertThat(ingestCount("accepted")).isEqualTo(1);
    assertThat(ingestCount("failed")).isEqualTo(1);
  }

  @Test
  void 배치에서_한_원소도_발행하지_못하면_503이다() {
    when(eventIngestPublisher.publish(any(), any())).thenReturn(failedPublishing());

    assertThatThrownBy(() -> kafkaEventIngestor.ingestBatch(ORGANIZATION_ID, batch("tx-1", "tx-2")))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EVENT_INGEST_UNAVAILABLE));
    assertThat(ingestCount("failed")).isEqualTo(2);
  }

  private static CompletableFuture<Void> failedPublishing() {
    return CompletableFuture.failedFuture(new IllegalStateException("broker unavailable"));
  }

  private static org.mockito.ArgumentMatcher<EventIngestMessage> message(String transactionId) {
    return message -> message != null && transactionId.equals(message.transactionId());
  }

  private IngestEventRequest request(String transactionId) {
    return new IngestEventRequest(
        transactionId, CUSTOMER_ID, "api_call", Map.of("tokens", 1), OffsetDateTime.now());
  }

  private IngestEventBatchRequest batch(String... transactionIds) {
    List<JsonNode> events =
        java.util.Arrays.stream(transactionIds)
            .map(transactionId -> (JsonNode) jsonMapper.valueToTree(request(transactionId)))
            .toList();
    return new IngestEventBatchRequest(events);
  }

  private double ingestCount(String result) {
    return meterRegistry
        .get("meterengine.event.ingest")
        .tag("result", result)
        .tag("environment", "legacy")
        .counter()
        .count();
  }
}
