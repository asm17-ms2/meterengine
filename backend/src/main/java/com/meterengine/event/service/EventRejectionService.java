package com.meterengine.event.service;

import com.meterengine.event.dto.IngestEventBatchRequest;
import com.meterengine.event.dto.IngestEventBatchResponse;
import com.meterengine.event.dto.IngestEventRequest;
import com.meterengine.global.error.BusinessException;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.ErrorResponse;
import com.meterengine.global.security.AuthenticatedPrincipal;
import com.meterengine.global.security.RequestAuditFilter;
import com.meterengine.global.validation.StorableTextValidator;
import com.meterengine.global.validation.StorableTimestampValidator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import tools.jackson.databind.JsonNode;

@Service
public class EventRejectionService {
  private static final Logger LOG = LoggerFactory.getLogger(EventRejectionService.class);
  static final Set<ErrorCode> REASONS =
      Set.of(
          ErrorCode.CUSTOMER_NOT_FOUND,
          ErrorCode.CUSTOMER_ARCHIVED,
          ErrorCode.EVENT_TOO_OLD,
          ErrorCode.VALIDATION_ERROR);
  private static final Set<String> FIELDS =
      Set.of("transaction_id", "customer_id", "type", "timestamp", "properties", "events");
  private final EventRejectionStorageService eventRejectionStorageService;
  private final Counter counter;

  public EventRejectionService(
      EventRejectionStorageService eventRejectionStorageService, MeterRegistry registry) {
    this.eventRejectionStorageService = eventRejectionStorageService;
    this.counter =
        Counter.builder("meterengine.event.rejection.recording.failures")
            .description("수집 거절 메타데이터 기록 실패 건수")
            .register(registry);
  }

  public void record(UUID organizationId, IngestEventRequest request, BusinessException exception) {
    if (!REASONS.contains(exception.getErrorCode())) return;
    save(
        organizationId,
        List.of(
            new EventRejection(
                text(request.transactionId()),
                request.customerId(),
                text(request.type()),
                timestamp(request.occurredAt()),
                exception.getErrorCode(),
                fields(exception.getErrors()),
                null)));
  }

  public void record(
      UUID organizationId, IngestEventBatchRequest request, IngestEventBatchResponse response) {
    List<EventRejection> rejections = new ArrayList<>();
    for (var result : response.results()) {
      if (!"rejected".equals(result.result())
          || result.error() == null
          || !REASONS.contains(result.error().code())) continue;
      JsonNode node = request.events().get(result.index());
      String customer = string(node, "customer_id");
      UUID customerId = null;
      try {
        if (customer != null) customerId = UUID.fromString(customer);
      } catch (IllegalArgumentException ignored) {
      }
      OffsetDateTime occurred = null;
      try {
        String value = string(node, "timestamp");
        if (value != null) occurred = OffsetDateTime.parse(value);
      } catch (RuntimeException ignored) {
      }
      rejections.add(
          new EventRejection(
              text(result.transactionId()),
              customerId,
              text(string(node, "type")),
              timestamp(occurred),
              result.error().code(),
              fields(result.error().errors()),
              result.index()));
    }
    save(organizationId, rejections);
  }

  private void save(UUID organizationId, List<EventRejection> rejections) {
    if (rejections.isEmpty()) return;
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    AuthenticatedPrincipal principal =
        authentication != null
                && authentication.getPrincipal() instanceof AuthenticatedPrincipal actor
            ? actor
            : null;
    RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
    UUID requestId =
        attributes == null
            ? null
            : (UUID)
                attributes.getAttribute(
                    RequestAuditFilter.REQUEST_ID_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
    try {
      eventRejectionStorageService.record(organizationId, principal, requestId, rejections);
    } catch (RuntimeException exception) {
      counter.increment();
      LOG.error(
          "Event rejection recording failed organization_id={} request_id={} exception_type={}",
          organizationId,
          requestId,
          exception.getClass().getSimpleName());
    }
  }

  private static String string(JsonNode node, String field) {
    return node != null && node.isObject() && node.path(field).isString()
        ? node.path(field).asString()
        : null;
  }

  private static String text(String value) {
    return value == null
            || value.length() > 255
            || !new StorableTextValidator().isValid(value, null)
        ? null
        : value;
  }

  private static OffsetDateTime timestamp(OffsetDateTime value) {
    return new StorableTimestampValidator().isValid(value, null) ? value : null;
  }

  private static List<String> fields(List<ErrorResponse.FieldError> errors) {
    return errors.stream()
        .map(error -> error.field().replaceFirst("^events\\[[0-9]+]\\.?", ""))
        .map(field -> field.isEmpty() ? "events" : field.split("[.\\[]", 2)[0])
        .filter(FIELDS::contains)
        .distinct()
        .sorted()
        .toList();
  }
}
