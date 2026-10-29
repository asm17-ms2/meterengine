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
import com.meterengine.metric.entity.MetricAggregation;
import com.meterengine.metric.repository.BillableMetricRepository;
import com.meterengine.pricing.service.PriceRateService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillableMetricService {

  private final BillableMetricRepository billableMetricRepository;
  private final EventService eventService;
  private final PriceRateService priceRateService;

  BillableMetricService(
      BillableMetricRepository billableMetricRepository,
      EventService eventService,
      PriceRateService priceRateService) {
    this.billableMetricRepository = billableMetricRepository;
    this.eventService = eventService;
    this.priceRateService = priceRateService;
  }

  @Transactional
  public BillableMetricResponse create(UUID organizationId, CreateBillableMetricRequest request) {
    Calculation calculation =
        validate(
            request.aggregation(),
            request.targetProperty(),
            request.unitSize(),
            request.roundingMode(),
            request.roundingPrecision(),
            request.percentile());
    List<String> dimensionProperties = dimensionPropertiesOrEmpty(request.dimensionProperties());
    validateDimensionProperties(dimensionProperties);

    BillableMetricId id = new BillableMetricId(organizationId, request.code());
    if (billableMetricRepository.existsById(id)) {
      throw new ConflictException(ErrorCode.BILLABLE_METRIC_ALREADY_EXISTS);
    }

    BillableMetric billableMetric =
        new BillableMetric(
            organizationId,
            request.code(),
            request.name(),
            request.eventType(),
            calculation.aggregation(),
            request.targetProperty(),
            dimensionProperties,
            calculation.unitSize(),
            calculation.roundingMode(),
            calculation.roundingPrecision(),
            calculation.percentile());
    billableMetricRepository.saveAndFlush(billableMetric);

    return BillableMetricResponse.from(billableMetric);
  }

  @Transactional
  public BillableMetricResponse update(
      UUID organizationId, String code, UpdateBillableMetricRequest request) {
    Calculation calculation =
        validate(
            request.aggregation(),
            request.targetProperty(),
            request.unitSize(),
            request.roundingMode(),
            request.roundingPrecision(),
            request.percentile());
    List<String> dimensionProperties = dimensionPropertiesOrEmpty(request.dimensionProperties());
    validateDimensionProperties(dimensionProperties);

    BillableMetric billableMetric =
        billableMetricRepository
            .findById(new BillableMetricId(organizationId, code))
            .orElseThrow(() -> new NotFoundException(ErrorCode.BILLABLE_METRIC_NOT_FOUND));

    var fields = new ArrayList<String>();
    if (!Objects.equals(billableMetric.getName(), request.name())) fields.add("name");
    if (!Objects.equals(billableMetric.getEventType(), request.eventType()))
      fields.add("event_type");
    if (!Objects.equals(billableMetric.getAggregation(), calculation.aggregation()))
      fields.add("aggregation");
    if (!Objects.equals(billableMetric.getTargetProperty(), request.targetProperty()))
      fields.add("target_property");
    if (!Objects.equals(billableMetric.getDimensionProperties(), dimensionProperties))
      fields.add("dimension_properties");
    if (!decimalEquals(billableMetric.getUnitSize(), calculation.unitSize()))
      fields.add("unit_size");
    if (!Objects.equals(billableMetric.getRoundingMode(), calculation.roundingMode()))
      fields.add("rounding_mode");
    if (billableMetric.getRoundingPrecision() != calculation.roundingPrecision())
      fields.add("rounding_precision");
    if (!decimalEquals(billableMetric.getPercentile(), calculation.percentile()))
      fields.add("percentile");
    if (fields.stream().anyMatch(field -> !"name".equals(field))
        && eventService.exists(organizationId, billableMetric.getEventType()))
      throw new ConflictException(ErrorCode.BILLABLE_METRIC_HAS_EVENTS);
    billableMetric.update(
        request.name(),
        request.eventType(),
        calculation.aggregation(),
        request.targetProperty(),
        dimensionProperties,
        calculation.unitSize(),
        calculation.roundingMode(),
        calculation.roundingPrecision(),
        calculation.percentile());
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
    if (priceRateService.existsForBillableMetric(organizationId, code)) {
      throw new ConflictException(ErrorCode.BILLABLE_METRIC_HAS_PRICE_RATE);
    }

    billableMetricRepository.delete(billableMetric);
    billableMetricRepository.flush();
  }

  @Transactional(readOnly = true)
  public ListBillableMetricsResponse list(UUID organizationId) {
    return ListBillableMetricsResponse.from(
        billableMetricRepository.findByOrganizationIdOrderByCodeAsc(organizationId));
  }

  @Transactional(readOnly = true)
  public BillableMetricResponse get(UUID organizationId, String code) {
    return BillableMetricResponse.from(
        billableMetricRepository
            .findById(new BillableMetricId(organizationId, code))
            .orElseThrow(() -> new NotFoundException(ErrorCode.BILLABLE_METRIC_NOT_FOUND)));
  }

  private record Calculation(
      String aggregation,
      BigDecimal unitSize,
      String roundingMode,
      int roundingPrecision,
      BigDecimal percentile) {}

  private Calculation validate(
      String aggregation,
      String targetProperty,
      BigDecimal unitSize,
      String roundingMode,
      Integer roundingPrecision,
      BigDecimal percentile) {
    String normalized = aggregation == null ? "" : aggregation.toLowerCase(Locale.ROOT);
    if (!MetricAggregation.SUPPORTED.contains(normalized))
      throw invalid("aggregation", "지원하는 집계 방식을 지정해야 합니다");
    if ("count".equals(normalized) && targetProperty != null)
      throw invalid("target_property", "count 집계에는 target_property를 지정하지 않습니다");
    if (!"count".equals(normalized) && (targetProperty == null || targetProperty.isBlank()))
      throw invalid("target_property", "이 집계에는 target_property가 필요합니다");
    BigDecimal selectedUnit = unitSize == null ? BigDecimal.ONE : unitSize;
    if (selectedUnit.signum() <= 0) throw invalid("unit_size", "집계 단위는 양수여야 합니다");
    String selectedRounding = roundingMode == null ? "none" : roundingMode.toLowerCase(Locale.ROOT);
    if (!MetricAggregation.ROUNDING_MODES.contains(selectedRounding))
      throw invalid("rounding_mode", "none, ceil, floor, round 중 하나를 지정해야 합니다");
    int selectedPrecision = roundingPrecision == null ? 0 : roundingPrecision;
    if (selectedPrecision < 0 || selectedPrecision > 12)
      throw invalid("rounding_precision", "소수 자릿수는 0~12여야 합니다");
    if ("percentile".equals(normalized)) {
      if (percentile == null
          || percentile.signum() < 0
          || percentile.compareTo(new BigDecimal("100")) > 0)
        throw invalid("percentile", "백분위는 0~100으로 지정해야 합니다");
    } else if (percentile != null) throw invalid("percentile", "percentile 집계에만 백분위를 지정합니다");
    return new Calculation(
        normalized,
        selectedUnit,
        selectedRounding,
        "none".equals(selectedRounding) ? 0 : selectedPrecision,
        percentile);
  }

  private static boolean decimalEquals(BigDecimal first, BigDecimal second) {
    return first == null ? second == null : second != null && first.compareTo(second) == 0;
  }

  private static InvalidRequestException invalid(String field, String message) {
    return new InvalidRequestException(
        ErrorCode.INVALID_BILLABLE_METRIC, List.of(new FieldError(field, message)));
  }

  private static List<String> dimensionPropertiesOrEmpty(List<String> dimensionProperties) {
    return dimensionProperties == null ? List.of() : dimensionProperties;
  }

  private void validateDimensionProperties(List<String> dimensionProperties) {
    Set<String> declared = new HashSet<>(dimensionProperties);
    if (declared.size() < dimensionProperties.size()) {
      throw new InvalidRequestException(
          ErrorCode.INVALID_BILLABLE_METRIC,
          List.of(new FieldError("dimension_properties", "같은 키가 두 번 있습니다")));
    }
    if (dimensionProperties.stream().anyMatch(key -> key == null || key.isBlank())) {
      throw new InvalidRequestException(
          ErrorCode.INVALID_BILLABLE_METRIC,
          List.of(new FieldError("dimension_properties", "빈 키를 담을 수 없습니다")));
    }
  }
}
