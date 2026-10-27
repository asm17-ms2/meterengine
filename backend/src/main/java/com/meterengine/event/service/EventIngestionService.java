package com.meterengine.event.service;

import com.meterengine.customer.api.CustomerDirectory;
import com.meterengine.event.dto.IngestEventRequest;
import com.meterengine.event.dto.IngestEventResponse;
import com.meterengine.event.repository.EventRepository;
import com.meterengine.global.error.BusinessException;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.ErrorResponse.FieldError;
import com.meterengine.global.error.InvalidRequestException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class EventIngestionService {

  private static final Duration MAX_EVENT_AGE = Duration.ofDays(35);

  private final EventRepository eventRepository;
  private final CustomerDirectory customerDirectory;
  private final JsonMapper jsonMapper;
  private final EventIngestMetrics eventIngestMetrics;

  EventIngestionService(
      EventRepository eventRepository,
      CustomerDirectory customerDirectory,
      JsonMapper jsonMapper,
      EventIngestMetrics eventIngestMetrics) {
    this.eventRepository = eventRepository;
    this.customerDirectory = customerDirectory;
    this.jsonMapper = jsonMapper;
    this.eventIngestMetrics = eventIngestMetrics;
  }

  @Transactional
  public IngestEventResponse ingest(UUID organizationId, IngestEventRequest request) {
    try {
      return store(organizationId, request);
    } catch (BusinessException rejection) {
      eventIngestMetrics.recordRejected();
      throw rejection;
    }
  }

  private IngestEventResponse store(UUID organizationId, IngestEventRequest request) {
    try {
      customerDirectory.check(organizationId, request.customerId());
    } catch (com.meterengine.global.error.ConflictException exception) {
      if (exception.getErrorCode() != ErrorCode.CUSTOMER_ARCHIVED
          || !eventRepository.exists(organizationId, request.transactionId())) throw exception;
      eventIngestMetrics.recordDuplicate();
      return IngestEventResponse.alreadyStored(request.transactionId());
    }

    if (isTooOld(request.occurredAt().toInstant(), Instant.now())) {
      if (eventRepository.exists(organizationId, request.transactionId())) {
        eventIngestMetrics.recordDuplicate();
        return IngestEventResponse.alreadyStored(request.transactionId());
      }
      throw new InvalidRequestException(
          ErrorCode.EVENT_TOO_OLD,
          List.of(new FieldError("timestamp", "수집 판정 시각보다 35일 넘게 과거인 시각입니다")));
    }

    String propertiesJson = jsonMapper.writeValueAsString(request.properties());

    int inserted =
        eventRepository.insertIfAbsent(
            organizationId,
            request.transactionId(),
            request.customerId(),
            request.type(),
            propertiesJson,
            request.occurredAt());
    if (inserted == 1) {
      eventIngestMetrics.recordStored();
      return IngestEventResponse.stored(request.transactionId());
    }
    eventIngestMetrics.recordDuplicate();
    return IngestEventResponse.alreadyStored(request.transactionId());
  }

  static boolean isTooOld(Instant occurredAt, Instant now) {
    return occurredAt.isBefore(now.minus(MAX_EVENT_AGE));
  }
}
