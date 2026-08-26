package com.meterengine.metric.service;

import com.meterengine.metric.dto.BillableMetricListResponse;
import com.meterengine.metric.dto.BillableMetricResponse;
import com.meterengine.metric.dto.SaveBillableMetricRequest;
import com.meterengine.metric.entity.BillableMetric;
import com.meterengine.metric.entity.BillableMetricId;
import com.meterengine.metric.exception.InvalidBillableMetricException;
import com.meterengine.metric.exception.MetricAlreadyExistsException;
import com.meterengine.metric.repository.BillableMetricRepository;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillableMetricService {

  private static final String DUPLICATE_CODE_CONSTRAINT = "billable_metric_pkey";

  private final BillableMetricRepository metrics;

  BillableMetricService(BillableMetricRepository metrics) {
    this.metrics = metrics;
  }

  @Transactional
  public BillableMetricResponse register(UUID organizationId, SaveBillableMetricRequest request) {
    validate(request);

    BillableMetricId id = new BillableMetricId(organizationId, request.code());
    if (metrics.existsById(id)) {
      throw new MetricAlreadyExistsException(request.code());
    }

    BillableMetric metric =
        new BillableMetric(
            organizationId,
            request.code(),
            request.name(),
            request.eventType(),
            request.aggregation(),
            request.targetProperty());
    try {
      metrics.saveAndFlush(metric);
    } catch (DataIntegrityViolationException exception) {
      if (exception.getCause() instanceof ConstraintViolationException cause
          && DUPLICATE_CODE_CONSTRAINT.equals(cause.getConstraintName())) {
        throw new MetricAlreadyExistsException(request.code());
      }
      throw exception;
    }

    return BillableMetricResponse.from(metric);
  }

  @Transactional(readOnly = true)
  public BillableMetricListResponse list(UUID organizationId) {
    return new BillableMetricListResponse(
        metrics.findByOrganizationIdOrderByCodeAsc(organizationId).stream()
            .map(BillableMetricResponse::from)
            .toList());
  }

  private void validate(SaveBillableMetricRequest request) {
    if (!BillableMetric.SUM.equals(request.aggregation())) {
      throw new InvalidBillableMetricException(
          "aggregation %s is not supported; only SUM is available"
              .formatted(request.aggregation()));
    }
    if (request.targetProperty() == null || request.targetProperty().isBlank()) {
      throw new InvalidBillableMetricException("SUM aggregation requires target_property");
    }
  }
}
