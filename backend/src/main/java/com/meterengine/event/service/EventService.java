package com.meterengine.event.service;

import com.meterengine.customer.repository.CustomerRepository;
import com.meterengine.event.dto.Event;
import com.meterengine.event.dto.IngestEventRequest;
import com.meterengine.event.dto.IngestEventResponse;
import com.meterengine.event.dto.ListEventsResponse;
import com.meterengine.event.repository.EventRepository;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.ErrorResponse.FieldError;
import com.meterengine.global.error.InvalidRequestException;
import com.meterengine.global.error.NotFoundException;
import com.meterengine.metric.service.BillableMetricUsageService;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class EventService {

  private static final Duration MAX_EVENT_AGE = Duration.ofDays(35);

  private final EventRepository eventRepository;
  private final CustomerRepository customerRepository;
  private final JsonMapper jsonMapper;

  EventService(
      EventRepository eventRepository,
      CustomerRepository customerRepository,
      JsonMapper jsonMapper) {
    this.eventRepository = eventRepository;
    this.customerRepository = customerRepository;
    this.jsonMapper = jsonMapper;
  }

  public boolean existsWithNumericProperty(UUID organizationId, String type, String property) {
    return eventRepository.existsWithNumericProperty(organizationId, type, property);
  }

  public IngestEventResponse ingest(UUID organizationId, IngestEventRequest request) {
    if (!customerRepository.existsByOrganizationIdAndId(organizationId, request.customerId())) {
      throw new NotFoundException(ErrorCode.CUSTOMER_NOT_FOUND);
    }

    if (isTooOld(request.occurredAt().toInstant(), Instant.now())) {
      if (eventRepository.exists(organizationId, request.transactionId())) {
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
    return inserted == 1
        ? IngestEventResponse.stored(request.transactionId())
        : IngestEventResponse.alreadyStored(request.transactionId());
  }

  static boolean isTooOld(Instant occurredAt, Instant now) {
    return occurredAt.isBefore(now.minus(MAX_EVENT_AGE));
  }

  @Transactional(readOnly = true)
  public ListEventsResponse list(
      UUID organizationId, UUID customerId, YearMonth month, String type, int page, int size) {
    if (customerId != null
        && !customerRepository.existsByOrganizationIdAndId(organizationId, customerId)) {
      throw new NotFoundException(ErrorCode.CUSTOMER_NOT_FOUND);
    }

    OffsetDateTime start =
        month.atDay(1).atStartOfDay(BillableMetricUsageService.BILLING_ZONE).toOffsetDateTime();
    OffsetDateTime end =
        month
            .plusMonths(1)
            .atDay(1)
            .atStartOfDay(BillableMetricUsageService.BILLING_ZONE)
            .toOffsetDateTime();

    long total = eventRepository.count(organizationId, customerId, type, start, end);
    List<Event> events =
        eventRepository.findPage(organizationId, customerId, type, start, end, page, size);

    return new ListEventsResponse(
        month.toString(),
        page,
        size,
        total,
        events.stream().map(EventService::toEventResponse).toList());
  }

  private static ListEventsResponse.EventResponse toEventResponse(Event event) {
    return new ListEventsResponse.EventResponse(
        event.transactionId(),
        event.customerId(),
        event.customerName(),
        event.type(),
        event.propertiesJson(),
        event.occurredAt(),
        event.receivedAt());
  }
}
