package com.meterengine.metric.service;

import com.meterengine.audit.service.AuditLogService;
import com.meterengine.event.api.EventPresence;
import com.meterengine.global.error.ConflictException;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.ErrorResponse.FieldError;
import com.meterengine.global.error.InvalidRequestException;
import com.meterengine.global.error.NotFoundException;
import com.meterengine.global.security.BusinessEnvironmentContext;
import com.meterengine.global.validation.ArchiveStatus;
import com.meterengine.global.validation.VersionPrecondition;
import com.meterengine.metric.dto.BillableMetricResponse;
import com.meterengine.metric.dto.CreateBillableMetricRequest;
import com.meterengine.metric.dto.ListBillableMetricsResponse;
import com.meterengine.metric.dto.UpdateBillableMetricRequest;
import com.meterengine.metric.entity.BillableMetric;
import com.meterengine.metric.entity.BillableMetricId;
import com.meterengine.metric.entity.BillableMetricPeriod;
import com.meterengine.metric.entity.MetricAggregation;
import com.meterengine.metric.repository.BillableMetricPeriodRepository;
import com.meterengine.metric.repository.BillableMetricRepository;
import com.meterengine.organization.repository.OrganizationRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillableMetricService {

  private final BillableMetricRepository billableMetricRepository;
  private final EventPresence eventPresence;
  private final BillableMetricPeriodRepository billableMetricPeriodRepository;
  private final OrganizationRepository organizationRepository;
  private final AuditLogService auditLogService;

  BillableMetricService(
      BillableMetricRepository billableMetricRepository,
      EventPresence eventPresence,
      BillableMetricPeriodRepository billableMetricPeriodRepository,
      OrganizationRepository organizationRepository,
      AuditLogService auditLogService) {
    this.billableMetricRepository = billableMetricRepository;
    this.eventPresence = eventPresence;
    this.billableMetricPeriodRepository = billableMetricPeriodRepository;
    this.organizationRepository = organizationRepository;
    this.auditLogService = auditLogService;
  }

  @Transactional
  public BillableMetricResponse create(UUID organizationId, CreateBillableMetricRequest request) {
    lock(organizationId);
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

    BillableMetricId id =
        new BillableMetricId(
            organizationId, request.code(), BusinessEnvironmentContext.environmentId());
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
            calculation.percentile(),
            BusinessEnvironmentContext.environmentId());
    billableMetricRepository.saveAndFlush(billableMetric);
    auditLogService.record(
        organizationId,
        "billable_metric_created",
        "billable_metric",
        request.code(),
        Map.of(
            "changed_fields",
            List.of(
                "code",
                "name",
                "event_type",
                "aggregation",
                "target_property",
                "dimension_properties",
                "unit_size",
                "rounding_mode",
                "rounding_precision",
                "percentile")));

    return BillableMetricResponse.from(billableMetric);
  }

  @Transactional
  public BillableMetricResponse update(
      UUID organizationId, String code, UpdateBillableMetricRequest request) {
    return update(organizationId, code, request, null);
  }

  @Transactional
  public BillableMetricResponse update(
      UUID organizationId, String code, UpdateBillableMetricRequest request, Long expectedVersion) {
    lock(organizationId);
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
            .findById(
                new BillableMetricId(
                    organizationId, code, BusinessEnvironmentContext.environmentId()))
            .orElseThrow(() -> new NotFoundException(ErrorCode.BILLABLE_METRIC_NOT_FOUND));

    VersionPrecondition.requireMatch(expectedVersion, billableMetric.getVersion());
    if (billableMetric.getArchivedAt() != null)
      throw new ConflictException(ErrorCode.RESOURCE_ARCHIVED);

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
        && eventPresence.exists(organizationId, billableMetric.getEventType()))
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
    if (!fields.isEmpty())
      auditLogService.record(
          organizationId,
          "billable_metric_updated",
          "billable_metric",
          code,
          Map.of("changed_fields", fields));

    return BillableMetricResponse.from(billableMetric);
  }

  @Transactional
  public void delete(UUID organizationId, String code) {
    lock(organizationId);
    BillableMetric billableMetric =
        billableMetricRepository
            .findById(
                new BillableMetricId(
                    organizationId, code, BusinessEnvironmentContext.environmentId()))
            .orElseThrow(() -> new NotFoundException(ErrorCode.BILLABLE_METRIC_NOT_FOUND));

    if (billableMetric.getArchivedAt() != null) return;
    String cutoff = BillableMetricUsageService.currentMonth().plusMonths(1).toString();
    if (billableMetric.getActiveFrom().compareTo(cutoff) < 0)
      billableMetricPeriodRepository.save(
          new BillableMetricPeriod(
              organizationId,
              code,
              billableMetric.getActiveFrom(),
              cutoff,
              BusinessEnvironmentContext.environmentId()));
    billableMetric.archive(cutoff);
    auditLogService.record(
        organizationId,
        "billable_metric_archived",
        "billable_metric",
        code,
        Map.of("inactive_from", cutoff));
  }

  @Transactional
  public BillableMetricResponse restore(UUID organizationId, String code) {
    lock(organizationId);
    BillableMetric metric = metric(organizationId, code);
    if (metric.getArchivedAt() != null) {
      String start = BillableMetricUsageService.currentMonth().plusMonths(1).toString();
      metric.restore(start);
      auditLogService.record(
          organizationId,
          "billable_metric_restored",
          "billable_metric",
          code,
          Map.of("active_from", start));
    }
    billableMetricRepository.flush();
    return BillableMetricResponse.from(metric);
  }

  @Transactional(readOnly = true)
  public BillableMetricResponse get(UUID organizationId, String code) {
    return BillableMetricResponse.from(metric(organizationId, code));
  }

  @Transactional(readOnly = true)
  public ListBillableMetricsResponse list(UUID organizationId) {
    return list(organizationId, "active");
  }

  @Transactional(readOnly = true)
  public ListBillableMetricsResponse list(UUID organizationId, String status) {
    ArchiveStatus filter = ArchiveStatus.parse(status);
    return ListBillableMetricsResponse.from(
        (BusinessEnvironmentContext.environmentId() == null
                ? billableMetricRepository.findByOrganizationIdOrderByCodeAsc(organizationId)
                : billableMetricRepository.listScoped(
                    organizationId, BusinessEnvironmentContext.environmentId()))
            .stream().filter(metric -> filter.includes(metric.getArchivedAt())).toList());
  }

  private BillableMetric metric(UUID organizationId, String code) {
    return billableMetricRepository
        .findById(
            new BillableMetricId(organizationId, code, BusinessEnvironmentContext.environmentId()))
        .orElseThrow(() -> new NotFoundException(ErrorCode.BILLABLE_METRIC_NOT_FOUND));
  }

  private void lock(UUID organizationId) {
    organizationRepository
        .findForUpdateById(organizationId)
        .orElseThrow(() -> new NotFoundException(ErrorCode.ORGANIZATION_NOT_FOUND));
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
