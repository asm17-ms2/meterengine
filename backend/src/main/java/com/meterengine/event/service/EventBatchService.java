package com.meterengine.event.service;

import com.meterengine.customer.api.CustomerDirectory;
import com.meterengine.event.dto.EventInsertion;
import com.meterengine.event.dto.IngestEventBatchRequest;
import com.meterengine.event.dto.IngestEventBatchResponse;
import com.meterengine.event.dto.IngestEventBatchResponse.EventResult;
import com.meterengine.event.dto.IngestEventRequest;
import com.meterengine.event.repository.EventRepository;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.ErrorResponse;
import com.meterengine.global.error.ErrorResponse.FieldError;
import jakarta.validation.Validator;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class EventBatchService {
  private final EventRepository eventRepository;
  private final CustomerDirectory customerDirectory;
  private final EventIngestMetrics eventIngestMetrics;
  private final Validator validator;
  private final JsonMapper jsonMapper;

  EventBatchService(
      EventRepository eventRepository,
      CustomerDirectory customerDirectory,
      EventIngestMetrics eventIngestMetrics,
      Validator validator,
      JsonMapper jsonMapper) {
    this.eventRepository = eventRepository;
    this.customerDirectory = customerDirectory;
    this.eventIngestMetrics = eventIngestMetrics;
    this.validator = validator;
    this.jsonMapper =
        jsonMapper
            .rebuild()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
            .build();
  }

  @Transactional
  public IngestEventBatchResponse ingest(UUID organizationId, IngestEventBatchRequest batch) {
    Instant now = Instant.now();
    List<EventResult> results =
        new ArrayList<>(java.util.Collections.nCopies(batch.events().size(), null));
    Map<Integer, IngestEventRequest> requestsByIndex = new LinkedHashMap<>();
    for (int index = 0; index < batch.events().size(); index++) {
      JsonNode node = batch.events().get(index);
      String transactionId =
          node != null && node.isObject() && node.path("transaction_id").isString()
              ? node.path("transaction_id").asString()
              : null;
      IngestEventRequest request;
      try {
        if (node == null || !node.isObject()) {
          throw new IllegalArgumentException();
        }
        if (node.hasNonNull("timestamp")) {
          OffsetDateTime.parse(node.path("timestamp").asString());
        }
        request = jsonMapper.treeToValue(node, IngestEventRequest.class);
      } catch (RuntimeException exception) {
        String field =
            node != null
                    && node.isObject()
                    && node.has("timestamp")
                    && !validTimestamp(node.path("timestamp"))
                ? ".timestamp"
                : "";
        results.set(
            index,
            rejected(
                index,
                transactionId,
                ErrorCode.VALIDATION_ERROR,
                List.of(new FieldError("events[" + index + "]" + field, "이벤트 필드의 형식이 올바르지 않습니다"))));
        continue;
      }
      final int resultIndex = index;
      List<FieldError> errors =
          validator.validate(request).stream()
              .map(
                  violation ->
                      new FieldError(
                          "events["
                              + resultIndex
                              + "]."
                              + jsonField(violation.getPropertyPath().toString()),
                          violation.getMessage()))
              .sorted(java.util.Comparator.comparing(FieldError::field))
              .toList();
      if (!errors.isEmpty()) {
        results.set(index, rejected(index, transactionId, ErrorCode.VALIDATION_ERROR, errors));
      } else {
        requestsByIndex.put(index, request);
      }
    }
    List<String> transactionIds =
        requestsByIndex.values().stream()
            .map(IngestEventRequest::transactionId)
            .distinct()
            .toList();
    Set<String> existing =
        new HashSet<>(eventRepository.findExistingTransactionIds(organizationId, transactionIds));
    Map<UUID, Boolean> customers =
        customerDirectory.active(
            organizationId,
            requestsByIndex.values().stream()
                .map(IngestEventRequest::customerId)
                .distinct()
                .toList());
    Set<String> accepted = new HashSet<>();
    List<EventInsertion> insertions = new ArrayList<>();
    for (Map.Entry<Integer, IngestEventRequest> entry : requestsByIndex.entrySet()) {
      int index = entry.getKey();
      IngestEventRequest request = entry.getValue();
      if (!customers.containsKey(request.customerId())) {
        results.set(
            index,
            rejected(index, request.transactionId(), ErrorCode.CUSTOMER_NOT_FOUND, List.of()));
      } else if (existing.contains(request.transactionId())
          || accepted.contains(request.transactionId())) {
        results.set(index, duplicate(index, request.transactionId()));
      } else if (!customers.get(request.customerId())) {
        results.set(
            index,
            rejected(index, request.transactionId(), ErrorCode.CUSTOMER_ARCHIVED, List.of()));
      } else if (EventIngestionService.isTooOld(request.occurredAt().toInstant(), now)) {
        results.set(
            index,
            rejected(
                index,
                request.transactionId(),
                ErrorCode.EVENT_TOO_OLD,
                List.of(
                    new FieldError(
                        "events[" + index + "].timestamp", "수집 판정 시각보다 35일 넘게 과거인 시각입니다"))));
      } else {
        accepted.add(request.transactionId());
        insertions.add(
            new EventInsertion(
                request.transactionId(),
                request.customerId(),
                request.type(),
                jsonMapper.writeValueAsString(request.properties()),
                request.occurredAt()));
      }
    }
    Set<String> inserted =
        new HashSet<>(eventRepository.insertAllIfAbsent(organizationId, insertions));
    for (Map.Entry<Integer, IngestEventRequest> entry : requestsByIndex.entrySet()) {
      if (results.get(entry.getKey()) != null) {
        continue;
      }
      String transactionId = entry.getValue().transactionId();
      if (inserted.contains(transactionId)) {
        results.set(entry.getKey(), new EventResult(entry.getKey(), transactionId, "stored", null));
        eventIngestMetrics.recordStored();
      } else {
        results.set(entry.getKey(), duplicate(entry.getKey(), transactionId));
      }
    }
    return new IngestEventBatchResponse(results);
  }

  private EventResult rejected(
      int index, String transactionId, ErrorCode code, List<FieldError> errors) {
    eventIngestMetrics.recordRejected();
    return new EventResult(index, transactionId, "rejected", ErrorResponse.of(code, errors));
  }

  private EventResult duplicate(int index, String transactionId) {
    eventIngestMetrics.recordDuplicate();
    return new EventResult(index, transactionId, "duplicate", null);
  }

  private static String jsonField(String field) {
    return switch (field) {
      case "transactionId" -> "transaction_id";
      case "customerId" -> "customer_id";
      case "occurredAt" -> "timestamp";
      default -> field;
    };
  }

  private static boolean validTimestamp(JsonNode node) {
    try {
      OffsetDateTime.parse(node.asString());
      return true;
    } catch (RuntimeException exception) {
      return false;
    }
  }
}
