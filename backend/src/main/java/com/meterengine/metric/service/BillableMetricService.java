package com.meterengine.metric.service;

import com.meterengine.event.service.EventService;
import com.meterengine.global.error.BusinessException;
import com.meterengine.global.error.ConflictException;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.ErrorResponse.FieldError;
import com.meterengine.global.error.InvalidRequestException;
import com.meterengine.global.error.NotFoundException;
import com.meterengine.metric.dto.BillableMetricResponse;
import com.meterengine.metric.dto.CreateBillableMetricRequest;
import com.meterengine.metric.dto.ListBillableMetricsResponse;
import com.meterengine.metric.dto.UpdateBillableMetricRequest;
import com.meterengine.metric.entity.BillableMetric;
import com.meterengine.metric.entity.BillableMetricId;
import com.meterengine.metric.repository.BillableMetricRepository;
import java.util.List;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillableMetricService {

  private static final String DUPLICATE_CODE_CONSTRAINT = "billable_metric_pk";
  private static final String DUPLICATE_EVENT_TYPE_TARGET_PROPERTY_CONSTRAINT =
      "billable_metric_organization_event_type_target_property_unique";

  private final BillableMetricRepository billableMetricRepository;
  private final EventService eventService;

  BillableMetricService(
      BillableMetricRepository billableMetricRepository, EventService eventService) {
    this.billableMetricRepository = billableMetricRepository;
    this.eventService = eventService;
  }

  @Transactional
  public BillableMetricResponse create(UUID organizationId, CreateBillableMetricRequest request) {
    validate(request.aggregation(), request.targetProperty());

    BillableMetricId id = new BillableMetricId(organizationId, request.code());
    if (billableMetricRepository.existsById(id)) {
      throw new ConflictException(ErrorCode.BILLABLE_METRIC_ALREADY_EXISTS);
    }
    if (billableMetricRepository.existsByOrganizationIdAndEventTypeAndTargetProperty(
        organizationId, request.eventType(), request.targetProperty())) {
      throw new ConflictException(
          ErrorCode.BILLABLE_METRIC_EVENT_TYPE_TARGET_PROPERTY_ALREADY_EXISTS);
    }

    BillableMetric billableMetric =
        new BillableMetric(
            organizationId,
            request.code(),
            request.name(),
            request.eventType(),
            request.aggregation(),
            request.targetProperty());
    try {
      billableMetricRepository.saveAndFlush(billableMetric);
    } catch (DataIntegrityViolationException exception) {
      throw toBusinessException(exception);
    }

    return BillableMetricResponse.from(billableMetric);
  }

  @Transactional
  public BillableMetricResponse update(
      UUID organizationId, String code, UpdateBillableMetricRequest request) {
    validate(request.aggregation(), request.targetProperty());

    BillableMetric billableMetric =
        billableMetricRepository
            .findById(new BillableMetricId(organizationId, code))
            .orElseThrow(() -> new NotFoundException(ErrorCode.BILLABLE_METRIC_NOT_FOUND));

    if (!billableMetric.hasEventTypeAndTargetProperty(
        request.eventType(), request.targetProperty())) {
      if (eventService.existsWithNumericProperty(
          organizationId, billableMetric.getEventType(), billableMetric.getTargetProperty())) {
        throw new ConflictException(ErrorCode.BILLABLE_METRIC_HAS_EVENTS);
      }
      if (billableMetricRepository.existsByOrganizationIdAndEventTypeAndTargetProperty(
          organizationId, request.eventType(), request.targetProperty())) {
        throw new ConflictException(
            ErrorCode.BILLABLE_METRIC_EVENT_TYPE_TARGET_PROPERTY_ALREADY_EXISTS);
      }
    }

    billableMetric.update(
        request.name(), request.eventType(), request.aggregation(), request.targetProperty());
    try {
      billableMetricRepository.flush();
    } catch (DataIntegrityViolationException exception) {
      throw toBusinessException(exception);
    }

    return BillableMetricResponse.from(billableMetric);
  }

  @Transactional(readOnly = true)
  public ListBillableMetricsResponse list(UUID organizationId) {
    return ListBillableMetricsResponse.from(
        billableMetricRepository.findByOrganizationIdOrderByCodeAsc(organizationId));
  }

  private BusinessException toBusinessException(DataIntegrityViolationException exception) {
    if (exception.getCause() instanceof ConstraintViolationException cause) {
      if (DUPLICATE_CODE_CONSTRAINT.equals(cause.getConstraintName())) {
        return new ConflictException(ErrorCode.BILLABLE_METRIC_ALREADY_EXISTS);
      }
      if (DUPLICATE_EVENT_TYPE_TARGET_PROPERTY_CONSTRAINT.equals(cause.getConstraintName())) {
        return new ConflictException(
            ErrorCode.BILLABLE_METRIC_EVENT_TYPE_TARGET_PROPERTY_ALREADY_EXISTS);
      }
    }
    return new InvalidRequestException(ErrorCode.UNKNOWN_ORGANIZATION);
  }

  private void validate(String aggregation, String targetProperty) {
    if (!BillableMetric.SUM.equals(aggregation)) {
      throw new InvalidRequestException(
          ErrorCode.INVALID_BILLABLE_METRIC, List.of(new FieldError("aggregation", "sum만 지원합니다")));
    }
    if (targetProperty == null || targetProperty.isBlank()) {
      throw new InvalidRequestException(
          ErrorCode.INVALID_BILLABLE_METRIC,
          List.of(new FieldError("target_property", "sum 집계에는 target_property가 필요합니다")));
    }
  }
}
