package com.meterengine.event.service;

import com.meterengine.customer.api.CustomerDirectory;
import com.meterengine.event.api.EventPresence;
import com.meterengine.event.dto.Event;
import com.meterengine.event.dto.ListEventsResponse;
import com.meterengine.event.repository.EventRepository;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.NotFoundException;
import com.meterengine.global.validation.StorableJsonValidator;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
public class EventQueryService implements EventPresence {
  private static final TypeReference<Map<String, Object>> PROPERTIES = new TypeReference<>() {};
  private static final int MAX_EXPORT_ROWS = 10000;

  // 원본 속성은 백엔드와 프론트 프록시가 응답 본문으로 여러 벌 들고 있어 합계를 묶는다.
  private static final long MAX_EXPORT_PROPERTIES_BYTES = 32L * 1024 * 1024;

  private final EventRepository eventRepository;
  private final CustomerDirectory customerDirectory;
  private final JsonMapper jsonMapper;

  EventQueryService(
      EventRepository eventRepository, CustomerDirectory customerDirectory, JsonMapper jsonMapper) {
    this.eventRepository = eventRepository;
    this.customerDirectory = customerDirectory;
    this.jsonMapper = jsonMapper;
  }

  public boolean exists(UUID organizationId, String type, String property) {
    return eventRepository.existsWithNumericProperty(organizationId, type, property);
  }

  public boolean exists(UUID organizationId, String type) {
    return eventRepository.existsWithType(organizationId, type);
  }

  @Transactional(readOnly = true)
  public ListEventsResponse list(
      UUID organizationId, UUID customerId, YearMonth month, String type, int page, int size) {
    return list(
        organizationId,
        customerId,
        EventSearch.resolve(month, null, null, null, null, null, null),
        type,
        page,
        size);
  }

  @Transactional(readOnly = true)
  public ListEventsResponse list(
      UUID organizationId, UUID customerId, EventSearch search, String type, int page, int size) {
    validate(organizationId, customerId, search);
    long total = eventRepository.count(organizationId, customerId, type, search);
    List<Event> events =
        eventRepository.findPage(organizationId, customerId, type, search, page, size);

    return new ListEventsResponse(
        search.month(),
        page,
        size,
        total,
        events.stream().map(EventQueryService::toEventResponse).toList(),
        search.fromAt(),
        search.toAt());
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public List<Event> export(
      UUID organizationId,
      UUID customerId,
      EventSearch search,
      String type,
      boolean includeProperties) {
    validate(organizationId, customerId, search);
    if (eventRepository.count(organizationId, customerId, type, search) > MAX_EXPORT_ROWS) {
      throw EventSearch.invalid("export", "10000행 이하가 되도록 검색 범위를 좁혀 주세요");
    }
    if (includeProperties
        && eventRepository.sumPropertiesBytes(organizationId, customerId, type, search)
            > MAX_EXPORT_PROPERTIES_BYTES) {
      throw EventSearch.invalid("export", "원본 속성이 32MB 이하가 되도록 검색 범위를 좁혀 주세요");
    }
    return includeProperties
        ? eventRepository.findPage(organizationId, customerId, type, search, 0, MAX_EXPORT_ROWS)
        : eventRepository.findPageWithoutProperties(
            organizationId, customerId, type, search, 0, MAX_EXPORT_ROWS);
  }

  @Transactional(readOnly = true)
  public List<String> types(UUID organizationId, UUID customerId, EventSearch search) {
    if (customerId != null && !customerDirectory.exists(organizationId, customerId)) {
      throw new NotFoundException(ErrorCode.CUSTOMER_NOT_FOUND);
    }
    validateProperties(search.properties());
    return eventRepository.findTypes(organizationId, customerId, search);
  }

  @Transactional(readOnly = true)
  public List<String> months(UUID organizationId) {
    return eventRepository.findMonths(organizationId);
  }

  @Transactional(readOnly = true)
  public ListEventsResponse.EventResponse find(UUID organizationId, String transactionId) {
    return toEventResponse(
        eventRepository
            .find(organizationId, transactionId)
            .orElseThrow(() -> new NotFoundException(ErrorCode.EVENT_NOT_FOUND)));
  }

  private void validateProperties(String properties) {
    if (properties == null || properties.isBlank()) return;
    try {
      if (!jsonMapper.readTree(properties).isObject())
        throw EventSearch.invalid("properties", "속성 조건은 JSON 객체여야 합니다");
      if (!new StorableJsonValidator().isValid(jsonMapper.readValue(properties, PROPERTIES), null))
        throw EventSearch.invalid("properties", "속성 조건에 저장할 수 없는 문자나 숫자가 있습니다");
    } catch (tools.jackson.core.JacksonException exception) {
      throw EventSearch.invalid("properties", "속성 조건을 JSON 객체로 읽을 수 없습니다");
    }
  }

  private void validate(UUID organizationId, UUID customerId, EventSearch search) {
    if (customerId != null && !customerDirectory.exists(organizationId, customerId)) {
      throw new NotFoundException(ErrorCode.CUSTOMER_NOT_FOUND);
    }
    validateProperties(search.properties());
  }

  private static ListEventsResponse.EventResponse toEventResponse(Event event) {
    return new ListEventsResponse.EventResponse(
        event.transactionId(),
        event.customerId(),
        event.customerName(),
        event.type(),
        event.propertiesJson(),
        event.occurredAt(),
        event.receivedAt(),
        event.requestId(),
        event.actorType(),
        event.actorId());
  }
}
