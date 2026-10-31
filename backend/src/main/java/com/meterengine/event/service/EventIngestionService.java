package com.meterengine.event.service;

import com.meterengine.customer.api.CustomerDirectory;
import com.meterengine.event.dto.IngestEventRequest;
import com.meterengine.event.dto.IngestEventResponse;
import com.meterengine.event.repository.EventRepository;
import com.meterengine.global.error.BusinessException;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.ErrorResponse.FieldError;
import com.meterengine.global.error.InvalidRequestException;
import com.meterengine.global.metrics.DatabaseOperationTimer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@Service
public class EventIngestionService {

  static final Duration MAX_EVENT_AGE = Duration.ofDays(35);

  private static final String CUSTOMER_TABLE = "customer";
  private static final String EVENT_TABLE = "event";

  private final EventRepository eventRepository;
  private final CustomerDirectory customerDirectory;
  private final JsonMapper jsonMapper;
  private final EventIngestMetrics eventIngestMetrics;
  private final DatabaseOperationTimer databaseOperationTimer;
  private final TransactionTemplate transactionTemplate;

  EventIngestionService(
      EventRepository eventRepository,
      CustomerDirectory customerDirectory,
      JsonMapper jsonMapper,
      EventIngestMetrics eventIngestMetrics,
      DatabaseOperationTimer databaseOperationTimer,
      PlatformTransactionManager platformTransactionManager) {
    this.eventRepository = eventRepository;
    this.customerDirectory = customerDirectory;
    this.jsonMapper = jsonMapper;
    this.eventIngestMetrics = eventIngestMetrics;
    this.databaseOperationTimer = databaseOperationTimer;
    this.transactionTemplate = new TransactionTemplate(platformTransactionManager);
  }

  // 커밋 실패는 트랜잭션을 닫을 때 나므로 결과는 트랜잭션 밖에서 센다.
  public IngestEventResponse ingest(UUID organizationId, IngestEventRequest request) {
    Instant receivedAt = Instant.now();
    IngestEventResponse response;
    try {
      response =
          transactionTemplate.execute(
              status -> {
                databaseOperationTimer.timeCommit(EVENT_TABLE);
                return store(organizationId, request, receivedAt);
              });
    } catch (BusinessException rejection) {
      eventIngestMetrics.recordRejected(rejection.getErrorCode());
      throw rejection;
    } catch (RuntimeException failure) {
      eventIngestMetrics.recordFailed(1);
      throw failure;
    }
    if (response.duplicate()) {
      eventIngestMetrics.recordDuplicate();
    } else {
      eventIngestMetrics.recordStored(
          Duration.between(request.occurredAt().toInstant(), receivedAt));
    }
    return response;
  }

  private IngestEventResponse store(
      UUID organizationId, IngestEventRequest request, Instant receivedAt) {
    try {
      databaseOperationTimer.timeSelect(
          CUSTOMER_TABLE, () -> customerDirectory.check(organizationId, request.customerId()));
    } catch (com.meterengine.global.error.ConflictException exception) {
      if (exception.getErrorCode() != ErrorCode.CUSTOMER_ARCHIVED
          || !eventRepository.exists(organizationId, request.transactionId())) throw exception;
      return IngestEventResponse.alreadyStored(request.transactionId());
    }

    if (isTooOld(request.occurredAt().toInstant(), receivedAt)) {
      if (eventRepository.exists(organizationId, request.transactionId())) {
        return IngestEventResponse.alreadyStored(request.transactionId());
      }
      throw new InvalidRequestException(
          ErrorCode.EVENT_TOO_OLD,
          List.of(new FieldError("timestamp", "수집 판정 시각보다 35일 넘게 과거인 시각입니다")));
    }

    String propertiesJson = jsonMapper.writeValueAsString(request.properties());

    int inserted =
        databaseOperationTimer.timeInsert(
            EVENT_TABLE,
            () ->
                eventRepository.insertIfAbsent(
                    organizationId,
                    request.transactionId(),
                    request.customerId(),
                    request.type(),
                    propertiesJson,
                    request.occurredAt()));
    if (inserted == 1) {
      return IngestEventResponse.stored(request.transactionId());
    }
    return IngestEventResponse.alreadyStored(request.transactionId());
  }

  static boolean isTooOld(Instant occurredAt, Instant now) {
    return occurredAt.isBefore(now.minus(MAX_EVENT_AGE));
  }
}
