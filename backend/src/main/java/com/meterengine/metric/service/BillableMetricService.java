package com.meterengine.metric.service;

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

  private static final String DUPLICATE_CODE_CONSTRAINT = "billable_metric_pkey";

  private final BillableMetricRepository billableMetricRepository;

  BillableMetricService(BillableMetricRepository billableMetricRepository) {
    this.billableMetricRepository = billableMetricRepository;
  }

  @Transactional
  public BillableMetricResponse create(UUID organizationId, CreateBillableMetricRequest request) {
    validate(request);

    BillableMetricId id = new BillableMetricId(organizationId, request.code());
    if (billableMetricRepository.existsById(id)) {
      throw new ConflictException(ErrorCode.METRIC_ALREADY_EXISTS);
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
      if (exception.getCause() instanceof ConstraintViolationException cause
          && DUPLICATE_CODE_CONSTRAINT.equals(cause.getConstraintName())) {
        throw new ConflictException(ErrorCode.METRIC_ALREADY_EXISTS);
      }
      throw new InvalidRequestException(ErrorCode.UNKNOWN_ORGANIZATION);
    }

    return BillableMetricResponse.from(billableMetric);
  }

  @Transactional(readOnly = true)
  public ListBillableMetricsResponse list(UUID organizationId) {
    return ListBillableMetricsResponse.from(
        billableMetricRepository.findByOrganizationIdOrderByCodeAsc(organizationId));
  }

  private void validate(CreateBillableMetricRequest request) {
    if (!BillableMetric.SUM.equals(request.aggregation())) {
      throw new InvalidRequestException(
          ErrorCode.INVALID_BILLABLE_METRIC, List.of(new FieldError("aggregation", "SUM만 지원합니다")));
    }
    if (request.targetProperty() == null || request.targetProperty().isBlank()) {
      throw new InvalidRequestException(
          ErrorCode.INVALID_BILLABLE_METRIC,
          List.of(new FieldError("target_property", "SUM 집계에는 target_property가 필요합니다")));
    }
  }
}
