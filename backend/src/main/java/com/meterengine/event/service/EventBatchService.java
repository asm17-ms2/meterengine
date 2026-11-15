package com.meterengine.event.service;

import com.meterengine.customer.api.CustomerDirectory;
import com.meterengine.event.dto.EventInsertion;
import com.meterengine.event.dto.IngestEventBatchRequest;
import com.meterengine.event.dto.IngestEventBatchResponse;
import com.meterengine.event.dto.IngestEventBatchResponse.EventResult;
import com.meterengine.event.dto.IngestEventRequest;
import com.meterengine.event.repository.EventRepository;
import com.meterengine.event.service.EventBatchParser.ParsedEventBatch;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.ErrorResponse.FieldError;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@Service
public class EventBatchService {
  private static final String STORED = "stored";
  private static final String DUPLICATE = "duplicate";
  private static final String REJECTED = EventBatchParser.REJECTED;

  private final EventRepository eventRepository;
  private final CustomerDirectory customerDirectory;
  private final EventIngestMetrics eventIngestMetrics;
  private final EventBatchParser eventBatchParser;
  private final JsonMapper jsonMapper;
  private final TransactionTemplate transactionTemplate;

  EventBatchService(
      EventRepository eventRepository,
      CustomerDirectory customerDirectory,
      EventIngestMetrics eventIngestMetrics,
      EventBatchParser eventBatchParser,
      JsonMapper jsonMapper,
      PlatformTransactionManager platformTransactionManager) {
    this.eventRepository = eventRepository;
    this.customerDirectory = customerDirectory;
    this.eventIngestMetrics = eventIngestMetrics;
    this.eventBatchParser = eventBatchParser;
    this.jsonMapper = jsonMapper;
    this.transactionTemplate = new TransactionTemplate(platformTransactionManager);
  }

  // 커밋 실패는 트랜잭션을 닫을 때 나므로 결과는 트랜잭션 밖에서 센다.
  public IngestEventBatchResponse ingest(UUID organizationId, IngestEventBatchRequest batch) {
    Instant receivedAt = Instant.now();
    IngestedBatch ingested;
    try {
      ingested = transactionTemplate.execute(status -> store(organizationId, batch, receivedAt));
    } catch (RuntimeException failure) {
      eventIngestMetrics.recordFailed(batch.events().size());
      throw failure;
    }
    recordResults(ingested, receivedAt);
    return ingested.response();
  }

  private void recordResults(IngestedBatch ingested, Instant receivedAt) {
    for (EventResult result : ingested.response().results()) {
      switch (result.result()) {
        case STORED -> {
          IngestEventRequest stored = ingested.requestsByIndex().get(result.index());
          eventIngestMetrics.recordStored(
              Duration.between(stored.occurredAt().toInstant(), receivedAt));
        }
        case DUPLICATE -> eventIngestMetrics.recordDuplicate();
        case REJECTED -> eventIngestMetrics.recordRejected(result.error().code());
      }
    }
  }

  private IngestedBatch store(
      UUID organizationId, IngestEventBatchRequest batch, Instant receivedAt) {
    ParsedEventBatch parsed = eventBatchParser.parse(batch);
    List<EventResult> results = parsed.results();
    Map<Integer, IngestEventRequest> requestsByIndex = parsed.requestsByIndex();
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
      } else if (EventIngestionService.isTooOld(request.occurredAt().toInstant(), receivedAt)) {
        results.set(index, EventBatchParser.tooOld(index, request.transactionId()));
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
    insertions.sort(Comparator.comparing(EventInsertion::transactionId));
    Set<String> inserted =
        new HashSet<>(eventRepository.insertAllIfAbsent(organizationId, insertions));
    for (Map.Entry<Integer, IngestEventRequest> entry : requestsByIndex.entrySet()) {
      if (results.get(entry.getKey()) != null) {
        continue;
      }
      String transactionId = entry.getValue().transactionId();
      if (inserted.contains(transactionId)) {
        results.set(entry.getKey(), new EventResult(entry.getKey(), transactionId, STORED, null));
      } else {
        results.set(entry.getKey(), duplicate(entry.getKey(), transactionId));
      }
    }
    return new IngestedBatch(new IngestEventBatchResponse(results), requestsByIndex);
  }

  private EventResult rejected(
      int index, String transactionId, ErrorCode code, List<FieldError> errors) {
    return EventBatchParser.rejected(index, transactionId, code, errors);
  }

  private EventResult duplicate(int index, String transactionId) {
    return new EventResult(index, transactionId, DUPLICATE, null);
  }

  private record IngestedBatch(
      IngestEventBatchResponse response, Map<Integer, IngestEventRequest> requestsByIndex) {}
}
