package com.meterengine.metric.service;

import com.meterengine.event.service.EventService;
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
import com.meterengine.pricing.service.PricePolicyService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillableMetricService {

  private final BillableMetricRepository billableMetricRepository;
  private final EventService eventService;
  private final PricePolicyService pricePolicyService;

  BillableMetricService(
      BillableMetricRepository billableMetricRepository,
      EventService eventService,
      PricePolicyService pricePolicyService) {
    this.billableMetricRepository = billableMetricRepository;
    this.eventService = eventService;
    this.pricePolicyService = pricePolicyService;
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
    billableMetricRepository.saveAndFlush(billableMetric);

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
    billableMetricRepository.flush();

    return BillableMetricResponse.from(billableMetric);
  }

  @Transactional
  public void delete(UUID organizationId, String code) {
    BillableMetric billableMetric =
        billableMetricRepository
            .findById(new BillableMetricId(organizationId, code))
            .orElseThrow(() -> new NotFoundException(ErrorCode.BILLABLE_METRIC_NOT_FOUND));

    if (eventService.existsWithNumericProperty(
        organizationId, billableMetric.getEventType(), billableMetric.getTargetProperty())) {
      throw new ConflictException(ErrorCode.BILLABLE_METRIC_HAS_EVENTS);
    }
    if (pricePolicyService.existsForBillableMetric(organizationId, code)) {
      throw new ConflictException(ErrorCode.BILLABLE_METRIC_HAS_PRICE_POLICY);
    }

    billableMetricRepository.delete(billableMetric);
    billableMetricRepository.flush();
  }

  @Transactional(readOnly = true)
  public ListBillableMetricsResponse list(UUID organizationId) {
    return ListBillableMetricsResponse.from(
        billableMetricRepository.findByOrganizationIdOrderByCodeAsc(organizationId));
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
