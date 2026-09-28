package com.meterengine.metric.service;

import com.meterengine.global.error.BusinessException;
import com.meterengine.global.error.ConflictException;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.ErrorResponse.FieldError;
import com.meterengine.global.error.InvalidRequestException;
import com.meterengine.metric.dto.BillableMetricResponse;
import com.meterengine.metric.dto.CreateBillableMetricRequest;
import com.meterengine.metric.dto.ListBillableMetricsResponse;
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

  BillableMetricService(BillableMetricRepository billableMetricRepository) {
    this.billableMetricRepository = billableMetricRepository;
  }

  @Transactional
  public BillableMetricResponse create(UUID organizationId, CreateBillableMetricRequest request) {
    validate(request);

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

  private void validate(CreateBillableMetricRequest request) {
    if (!BillableMetric.SUM.equals(request.aggregation())) {
      throw new InvalidRequestException(
          ErrorCode.INVALID_BILLABLE_METRIC, List.of(new FieldError("aggregation", "sum만 지원합니다")));
    }
    if (request.targetProperty() == null || request.targetProperty().isBlank()) {
      throw new InvalidRequestException(
          ErrorCode.INVALID_BILLABLE_METRIC,
          List.of(new FieldError("target_property", "sum 집계에는 target_property가 필요합니다")));
    }
  }
}
