package com.meterengine.event.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.validation.Validation;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@ExtendWith(MockitoExtension.class)
class EventBatchServiceTest {

  private static final UUID ORGANIZATION_ID = UUID.randomUUID();
  private static final UUID CUSTOMER_ID = UUID.randomUUID();

  @Mock private EventRepository eventRepository;
  @Mock private CustomerDirectory customerDirectory;

  private final JsonMapper jsonMapper = JsonMapper.builder().build();

  @Test
  void 배치_이벤트는_요청_순서와_관계없이_transaction_id_순으로_저장을_요청한다() {
    when(customerDirectory.active(eq(ORGANIZATION_ID), anyList()))
        .thenReturn(Map.of(CUSTOMER_ID, true));
    EventBatchService batchService =
        new EventBatchService(
            eventRepository,
            customerDirectory,
            new EventIngestMetrics(new SimpleMeterRegistry()),
            Validation.buildDefaultValidatorFactory().getValidator(),
            jsonMapper);

    batchService.ingest(
        ORGANIZATION_ID, new IngestEventBatchRequest(List.of(event("tx-2"), event("tx-1"))));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<EventInsertion>> insertions = ArgumentCaptor.forClass(List.class);
    verify(eventRepository).insertAllIfAbsent(eq(ORGANIZATION_ID), insertions.capture());
    assertThat(insertions.getValue())
        .extracting(EventInsertion::transactionId)
        .containsExactly("tx-1", "tx-2");
  }

  @ParameterizedTest
  @ValueSource(strings = {"transactionId", "customerId", "type", "properties", "occurredAt"})
  void 검증_오류_필드는_단건_요청과_같은_JSON_이름이다(String javaField) throws Exception {
    String jsonField =
        IngestEventRequest.class.getDeclaredField(javaField).getAnnotation(JsonProperty.class)
                == null
            ? javaField
            : IngestEventRequest.class
                .getDeclaredField(javaField)
                .getAnnotation(JsonProperty.class)
                .value();
    EventBatchService batchService =
        new EventBatchService(
            eventRepository,
            customerDirectory,
            new EventIngestMetrics(new SimpleMeterRegistry()),
            Validation.buildDefaultValidatorFactory().getValidator(),
            jsonMapper);
    ObjectNode event = (ObjectNode) event("tx-missing");
    event.putNull(jsonField);

    IngestEventBatchResponse response =
        batchService.ingest(ORGANIZATION_ID, new IngestEventBatchRequest(List.of(event)));

    assertThat(response.results().getFirst().error().errors())
        .extracting(FieldError::field)
        .containsExactly("events[0]." + jsonField);
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
