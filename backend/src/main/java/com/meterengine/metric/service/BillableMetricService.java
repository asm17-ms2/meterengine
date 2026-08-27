package com.meterengine.metric.service;

import com.meterengine.event.repository.EventRepository;
import com.meterengine.metric.dto.BillableMetricListResponse;
import com.meterengine.metric.dto.BillableMetricResponse;
import com.meterengine.metric.dto.SaveBillableMetricRequest;
import com.meterengine.metric.dto.UpdateBillableMetricRequest;
import com.meterengine.metric.entity.BillableMetric;
import com.meterengine.metric.entity.BillableMetricId;
import com.meterengine.metric.exception.InvalidBillableMetricException;
import com.meterengine.metric.exception.MetricAlreadyExistsException;
import com.meterengine.metric.exception.MetricBasisHasEventsException;
import com.meterengine.metric.exception.MetricHasEventsException;
import com.meterengine.metric.exception.MetricHasPricePolicyException;
import com.meterengine.metric.exception.MetricNotFoundException;
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
  private final EventRepository events;

  BillableMetricService(BillableMetricRepository metrics, EventRepository events) {
    this.metrics = metrics;
    this.events = events;
  }

  @Transactional
  public BillableMetricResponse register(UUID organizationId, SaveBillableMetricRequest request) {
    validate(request.aggregation(), request.targetProperty());

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
    return BillableMetricListResponse.from(
        metrics.findByOrganizationIdOrderByCodeAsc(organizationId));
  }

  @Transactional
  public BillableMetricResponse update(
      UUID organizationId, String code, UpdateBillableMetricRequest request) {
    validate(request.aggregation(), request.targetProperty());

    BillableMetric metric =
        metrics
            .findById(new BillableMetricId(organizationId, code))
            .orElseThrow(() -> new MetricNotFoundException(organizationId, code));

    rejectIfBasisHasEvents(organizationId, code, metric, request);

    metric.update(
        request.name(), request.eventType(), request.aggregation(), request.targetProperty());
    return BillableMetricResponse.from(metric);
  }

  @Transactional
  public void delete(UUID organizationId, String code) {
    BillableMetric metric =
        metrics
            .findById(new BillableMetricId(organizationId, code))
            .orElseThrow(() -> new MetricNotFoundException(organizationId, code));

    if (events.existsForBasis(organizationId, metric.getEventType(), metric.getTargetProperty())) {
      throw new MetricHasEventsException(code);
    }

    try {
      metrics.delete(metric);
      metrics.flush();
    } catch (DataIntegrityViolationException exception) {
      throw new MetricHasPricePolicyException(code);
    }
  }

  private void rejectIfBasisHasEvents(
      UUID organizationId,
      String code,
      BillableMetric metric,
      UpdateBillableMetricRequest request) {
    if (metric.hasSameBasis(request.eventType(), request.aggregation(), request.targetProperty())) {
      return;
    }
    if (events.existsForBasis(organizationId, metric.getEventType(), metric.getTargetProperty())) {
      throw new MetricBasisHasEventsException(
          code, metric.getEventType(), metric.getTargetProperty());
    }
    if (events.existsForBasis(organizationId, request.eventType(), request.targetProperty())) {
      throw new MetricBasisHasEventsException(code, request.eventType(), request.targetProperty());
    }
  }

  private void validate(String aggregation, String targetProperty) {
    if (!BillableMetric.SUM.equals(aggregation)) {
      throw new InvalidBillableMetricException(
          "aggregation %s is not supported; only SUM is available".formatted(aggregation));
    }
    if (targetProperty == null || targetProperty.isBlank()) {
      throw new InvalidBillableMetricException("SUM aggregation requires target_property");
    }
  }
}
