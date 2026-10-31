package com.meterengine.event.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.customer.api.CustomerDirectory;
import com.meterengine.event.dto.EventInsertion;
import com.meterengine.event.dto.IngestEventBatchRequest;
import com.meterengine.event.dto.IngestEventBatchResponse;
import com.meterengine.event.dto.IngestEventRequest;
import com.meterengine.event.repository.EventRepository;
import com.meterengine.global.error.ErrorResponse.FieldError;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.validation.Validation;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@ExtendWith(MockitoExtension.class)
class EventBatchServiceTest {

  private static final UUID ORGANIZATION_ID = UUID.randomUUID();
  private static final UUID CUSTOMER_ID = UUID.randomUUID();

  @Mock private EventRepository eventRepository;
  @Mock private CustomerDirectory customerDirectory;
  @Mock private PlatformTransactionManager platformTransactionManager;

  private final JsonMapper jsonMapper = JsonMapper.builder().build();

  private SimpleMeterRegistry meterRegistry;
  private EventBatchService batchService;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    batchService =
        new EventBatchService(
            eventRepository,
            customerDirectory,
            new EventIngestMetrics(meterRegistry),
            Validation.buildDefaultValidatorFactory().getValidator(),
            jsonMapper,
            platformTransactionManager);
  }

  @Test
  void 배치_이벤트는_요청_순서와_관계없이_transaction_id_순으로_저장을_요청한다() {
    when(customerDirectory.active(eq(ORGANIZATION_ID), anyList()))
        .thenReturn(Map.of(CUSTOMER_ID, true));

    batchService.ingest(
        ORGANIZATION_ID, new IngestEventBatchRequest(List.of(event("tx-2"), event("tx-1"))));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<EventInsertion>> insertions = ArgumentCaptor.forClass(List.class);
    verify(eventRepository).insertAllIfAbsent(eq(ORGANIZATION_ID), insertions.capture());
    assertThat(insertions.getValue())
        .extracting(EventInsertion::transactionId)
        .containsExactly("tx-1", "tx-2");
  }

  @Test
  void 검증_오류_필드는_단건_요청과_같은_필드_이름이다() throws Exception {
    for (String javaField :
        List.of("transactionId", "customerId", "type", "properties", "occurredAt")) {
      JsonProperty property =
          IngestEventRequest.class.getDeclaredField(javaField).getAnnotation(JsonProperty.class);
      String jsonField = property == null ? javaField : property.value();
      ObjectNode event = (ObjectNode) event("tx-missing");
      event.putNull(jsonField);

      IngestEventBatchResponse response =
          batchService.ingest(ORGANIZATION_ID, new IngestEventBatchRequest(List.of(event)));

      assertThat(response.results().getFirst().error().errors())
          .as(javaField)
          .extracting(FieldError::field)
          .containsExactly("events[0]." + jsonField);
    }
  }

  @Test
  void 거절한_이벤트는_오류_code를_reason으로_센다() {
    ObjectNode withoutType = (ObjectNode) event("tx-2");
    withoutType.remove("type");

    batchService.ingest(
        ORGANIZATION_ID, new IngestEventBatchRequest(List.of(event("tx-1"), withoutType)));

    assertThat(ingestCount("rejected", "customer_not_found")).isEqualTo(1.0);
    assertThat(ingestCount("rejected", "validation_error")).isEqualTo(1.0);
  }

  @Test
  void 커밋이_실패하면_거절할_원소까지_배치의_이벤트_수만큼_failed로_센다() {
    when(customerDirectory.active(eq(ORGANIZATION_ID), anyList()))
        .thenReturn(Map.of(CUSTOMER_ID, true));
    when(eventRepository.insertAllIfAbsent(eq(ORGANIZATION_ID), anyList()))
        .thenReturn(List.of("tx-1"));
    doThrow(new TransactionSystemException("커밋 실패")).when(platformTransactionManager).commit(any());
    ObjectNode withoutType = (ObjectNode) event("tx-2");
    withoutType.remove("type");

    assertThatThrownBy(
            () ->
                batchService.ingest(
                    ORGANIZATION_ID,
                    new IngestEventBatchRequest(List.of(event("tx-1"), withoutType))))
        .isInstanceOf(TransactionSystemException.class);

    assertThat(ingestCount("failed", "none")).isEqualTo(2.0);
    assertThat(ingestCount("stored", "none")).isZero();
    assertThat(ingestCount("rejected", "validation_error")).isZero();
  }

  @Test
  void 저장된_이벤트만_도착_지연을_기록한다() {
    when(customerDirectory.active(eq(ORGANIZATION_ID), anyList()))
        .thenReturn(Map.of(CUSTOMER_ID, true));
    when(eventRepository.insertAllIfAbsent(eq(ORGANIZATION_ID), anyList()))
        .thenReturn(List.of("tx-1"));

    batchService.ingest(
        ORGANIZATION_ID, new IngestEventBatchRequest(List.of(event("tx-1"), event("tx-2"))));

    assertThat(arrivalDelayCount()).isEqualTo(1);
  }

  private double ingestCount(String result, String reason) {
    return meterRegistry
        .get(EventIngestMetrics.INGEST_COUNTER_NAME)
        .tag(EventIngestMetrics.RESULT_TAG, result)
        .tag(EventIngestMetrics.REASON_TAG, reason)
        .counters()
        .stream()
        .mapToDouble(Counter::count)
        .sum();
  }

  private long arrivalDelayCount() {
    return meterRegistry.get(EventIngestMetrics.ARRIVAL_DELAY_TIMER_NAME).timers().stream()
        .mapToLong(Timer::count)
        .sum();
  }

  private JsonNode event(String transactionId) {
    return jsonMapper.valueToTree(
        Map.of(
            "transaction_id", transactionId,
            "customer_id", CUSTOMER_ID.toString(),
            "type", "chat_completion",
            "properties", Map.of("token", 1),
            "timestamp", OffsetDateTime.now().toString()));
  }
}
