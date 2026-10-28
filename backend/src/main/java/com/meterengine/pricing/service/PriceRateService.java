package com.meterengine.pricing.service;

import com.meterengine.audit.service.AuditLogService;
import com.meterengine.customer.repository.CustomerRepository;
import com.meterengine.global.error.ConflictException;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.ErrorResponse.FieldError;
import com.meterengine.global.error.InvalidRequestException;
import com.meterengine.global.error.NotFoundException;
import com.meterengine.global.security.BusinessEnvironmentContext;
import com.meterengine.global.validation.ArchiveStatus;
import com.meterengine.metric.entity.BillableMetric;
import com.meterengine.metric.entity.BillableMetricId;
import com.meterengine.metric.repository.BillableMetricRepository;
import com.meterengine.metric.service.BillableMetricUsageService;
import com.meterengine.organization.repository.OrganizationRepository;
import com.meterengine.pricing.dto.CreatePriceRateRequest;
import com.meterengine.pricing.dto.ListPriceRatesResponse;
import com.meterengine.pricing.dto.PriceRateResponse;
import com.meterengine.pricing.entity.PriceRate;
import com.meterengine.pricing.repository.PriceRateRepository;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
public class PriceRateService {
  private static final TypeReference<Map<String, Object>> DIMENSION_VALUES_TYPE =
      new TypeReference<>() {};
  private final PriceRateRepository priceRateRepository;
  private final BillableMetricRepository billableMetricRepository;
  private final CustomerRepository customerRepository;
  private final OrganizationRepository organizationRepository;
  private final JsonMapper jsonMapper;
  private final AuditLogService auditLogService;

  PriceRateService(
      PriceRateRepository priceRateRepository,
      BillableMetricRepository billableMetricRepository,
      CustomerRepository customerRepository,
      OrganizationRepository organizationRepository,
      JsonMapper jsonMapper,
      AuditLogService auditLogService) {
    this.priceRateRepository = priceRateRepository;
    this.billableMetricRepository = billableMetricRepository;
    this.customerRepository = customerRepository;
    this.organizationRepository = organizationRepository;
    this.jsonMapper = jsonMapper;
    this.auditLogService = auditLogService;
  }

  @Transactional(readOnly = true)
  public boolean existsForBillableMetric(UUID organizationId, String code) {
    return BusinessEnvironmentContext.environmentId() == null
        ? priceRateRepository.existsByOrganizationIdAndBillableMetricCode(organizationId, code)
        : priceRateRepository
            .listScoped(organizationId, BusinessEnvironmentContext.environmentId())
            .stream()
            .anyMatch(rate -> rate.getBillableMetricCode().equals(code));
  }

  @Transactional(readOnly = true)
  public ListPriceRatesResponse list(UUID organizationId, String code, UUID customerId) {
    return list(organizationId, code, customerId, "active");
  }

  public ListPriceRatesResponse list(
      UUID organizationId, String code, UUID customerId, String status) {
    validateCustomer(organizationId, customerId);
    ArchiveStatus filter = ArchiveStatus.parse(status);
    return new ListPriceRatesResponse(
        rates(organizationId).stream()
            .filter(rate -> filter.includes(rate.getArchivedAt()))
            .filter(
                rate ->
                    rate.getBillableMetricCode().equals(code)
                        && Objects.equals(rate.getCustomerId(), customerId))
            .sorted(Comparator.comparing(PriceRate::getEffectiveFrom).reversed())
            .map(PriceRateResponse::from)
            .toList());
  }

  @Transactional
  public PriceRateResponse create(
      UUID organizationId, String code, CreatePriceRateRequest request) {
    if (!request.isValidPricing())
      throw new InvalidRequestException(
          ErrorCode.VALIDATION_ERROR,
          List.of(new FieldError("model", "요금 모델에 맞는 단가와 마지막 한도 없는 오름차순 구간이 필요합니다")));
    organizationRepository
        .findForUpdateById(organizationId)
        .orElseThrow(() -> new NotFoundException(ErrorCode.ORGANIZATION_NOT_FOUND));
    BillableMetric metric =
        billableMetricRepository
            .findById(
                new BillableMetricId(
                    organizationId, code, BusinessEnvironmentContext.environmentId()))
            .orElseThrow(() -> new NotFoundException(ErrorCode.BILLABLE_METRIC_NOT_FOUND));
    if (metric.getArchivedAt() != null) throw new ConflictException(ErrorCode.RESOURCE_ARCHIVED);
    validate(request.dimensionValues(), metric.getDimensionProperties());
    YearMonth current = YearMonth.now(BillableMetricUsageService.BILLING_ZONE);
    YearMonth start = request.effectiveFrom() == null ? current : request.effectiveFrom();
    if (start.isBefore(current))
      throw new ConflictException(ErrorCode.PRICE_RATE_HISTORY_IMMUTABLE);
    if (request.effectiveUntil() != null && !request.effectiveUntil().isAfter(start))
      throw new InvalidRequestException(
          ErrorCode.VALIDATION_ERROR, List.of(new FieldError("effective_until", "시작 월보다 뒤여야 합니다")));
    validateCustomer(organizationId, request.customerId());
    if (request.customerId() != null) {
      var customer =
          (BusinessEnvironmentContext.environmentId() == null
                  ? customerRepository.lock(organizationId, java.util.List.of(request.customerId()))
                  : customerRepository.lockScoped(
                      organizationId,
                      BusinessEnvironmentContext.environmentId(),
                      java.util.List.of(request.customerId())))
              .stream()
                  .findFirst()
                  .orElseThrow(() -> new NotFoundException(ErrorCode.CUSTOMER_NOT_FOUND));
      if (customer.getArchivedAt() != null)
        throw new ConflictException(ErrorCode.CUSTOMER_ARCHIVED);
    }
    boolean retainedSlot =
        rates(organizationId).stream()
            .anyMatch(
                existing ->
                    existing.getArchivedAt() != null
                        && existing.applies(current.toString())
                        && existing.getBillableMetricCode().equals(code)
                        && Objects.equals(existing.getCustomerId(), request.customerId())
                        && existing.getEffectiveFrom().equals(start.toString())
                        && parse(existing.getDimensionValues())
                            .equals(PriceRateDimensions.normalize(request.dimensionValues())));
    if (retainedSlot) throw new ConflictException(ErrorCode.ARCHIVE_RESTORATION_CONFLICT);
    PriceRate rate =
        rates(organizationId).stream()
            .filter(existing -> existing.getArchivedAt() == null)
            .filter(
                existing ->
                    existing.getBillableMetricCode().equals(code)
                        && Objects.equals(existing.getCustomerId(), request.customerId())
                        && existing.getEffectiveFrom().equals(start.toString())
                        && parse(existing.getDimensionValues())
                            .equals(PriceRateDimensions.normalize(request.dimensionValues())))
            .findFirst()
            .orElse(null);
    String model = request.model() == null ? "per_unit" : request.model();
    String until = request.effectiveUntil() == null ? null : request.effectiveUntil().toString();
    String tiers = request.tiers() == null ? null : jsonMapper.writeValueAsString(request.tiers());
    Map<String, Object> previous = rate == null ? null : detail(rate);
    if (rate == null)
      rate =
          new PriceRate(
              UUID.randomUUID(),
              organizationId,
              code,
              jsonMapper.writeValueAsString(request.dimensionValues()),
              request.unitPrice(),
              request.customerId(),
              start.toString(),
              until,
              model,
              tiers,
              BusinessEnvironmentContext.environmentId());
    else rate.update(request.unitPrice(), until, model, tiers);
    priceRateRepository.saveAndFlush(rate);
    Map<String, Object> detail = detail(rate);
    if (previous != null) detail.put("previous", previous);
    auditLogService.record(
        organizationId, "price_rate_created", "price_rate", rate.getId().toString(), detail);
    return PriceRateResponse.from(rate);
  }

  @Transactional
  public void delete(UUID organizationId, String code, UUID id) {
    organizationRepository
        .findForUpdateById(organizationId)
        .orElseThrow(() -> new NotFoundException(ErrorCode.ORGANIZATION_NOT_FOUND));
    PriceRate rate =
        (BusinessEnvironmentContext.environmentId() == null
                ? priceRateRepository.findById(id)
                : priceRateRepository.findScoped(
                    organizationId, BusinessEnvironmentContext.environmentId(), id))
            .filter(
                value ->
                    value.getOrganizationId().equals(organizationId)
                        && BusinessEnvironmentContext.matches(value.getEnvironmentId())
                        && value.getBillableMetricCode().equals(code))
            .orElseThrow(() -> new NotFoundException(ErrorCode.PRICE_RATE_NOT_FOUND));
    archive(organizationId, rate);
  }

  @Transactional
  public void delete(UUID organizationId, String code, String dimensionValuesJson) {
    Map<String, Object> dimensionValues = parse(dimensionValuesJson);
    organizationRepository
        .findForUpdateById(organizationId)
        .orElseThrow(() -> new NotFoundException(ErrorCode.ORGANIZATION_NOT_FOUND));
    List<PriceRate> matched =
        rates(organizationId).stream()
            .filter(
                rate ->
                    rate.getBillableMetricCode().equals(code)
                        && rate.getCustomerId() == null
                        && parse(rate.getDimensionValues()).equals(dimensionValues)
                        && rate.applies(BillableMetricUsageService.currentMonth().toString()))
            .toList();
    if (matched.isEmpty()) throw new NotFoundException(ErrorCode.PRICE_RATE_NOT_FOUND);
    matched.forEach(rate -> archive(organizationId, rate));
  }

  @Transactional
  public PriceRateResponse restore(UUID organizationId, String code, UUID id) {
    organizationRepository
        .findForUpdateById(organizationId)
        .orElseThrow(() -> new NotFoundException(ErrorCode.ORGANIZATION_NOT_FOUND));
    PriceRate rate =
        (BusinessEnvironmentContext.environmentId() == null
                ? priceRateRepository.findById(id)
                : priceRateRepository.findScoped(
                    organizationId, BusinessEnvironmentContext.environmentId(), id))
            .filter(
                value ->
                    value.getOrganizationId().equals(organizationId)
                        && BusinessEnvironmentContext.matches(value.getEnvironmentId())
                        && value.getBillableMetricCode().equals(code))
            .orElseThrow(() -> new NotFoundException(ErrorCode.PRICE_RATE_NOT_FOUND));
    if (rate.getArchivedAt() == null) return PriceRateResponse.from(rate);
    boolean collision =
        rates(organizationId).stream()
            .anyMatch(
                other ->
                    other.getArchivedAt() == null
                        && other.getBillableMetricCode().equals(code)
                        && Objects.equals(other.getCustomerId(), rate.getCustomerId())
                        && other.getEffectiveFrom().equals(rate.getEffectiveFrom())
                        && parse(other.getDimensionValues())
                            .equals(parse(rate.getDimensionValues())));
    if (collision
        || rate.getArchivedFrom().compareTo(BillableMetricUsageService.currentMonth().toString())
            <= 0) throw new ConflictException(ErrorCode.ARCHIVE_RESTORATION_CONFLICT);
    rate.restore();
    auditLogService.record(
        organizationId, "price_rate_restored", "price_rate", id.toString(), detail(rate));
    return PriceRateResponse.from(rate);
  }

  private void archive(UUID organizationId, PriceRate rate) {
    if (rate.getArchivedAt() != null) return;
    String next = BillableMetricUsageService.currentMonth().plusMonths(1).toString();
    rate.archive(rate.getEffectiveFrom().compareTo(next) > 0 ? rate.getEffectiveFrom() : next);
    auditLogService.record(
        organizationId, "price_rate_archived", "price_rate", rate.getId().toString(), detail(rate));
  }

  private Map<String, Object> detail(PriceRate rate) {
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("billable_metric_code", rate.getBillableMetricCode());
    detail.put("dimension_values", parse(rate.getDimensionValues()));
    detail.put("customer_id", rate.getCustomerId());
    detail.put("effective_from", rate.getEffectiveFrom());
    detail.put("effective_until", rate.getEffectiveUntil());
    detail.put("model", rate.getModel());
    detail.put("archived_from", rate.getArchivedFrom());
    detail.put("unit_price", rate.getUnitPrice());
    detail.put(
        "tiers",
        rate.getTiers() == null ? null : jsonMapper.readValue(rate.getTiers(), Object.class));
    return detail;
  }

  private List<PriceRate> rates(UUID organizationId) {
    return (BusinessEnvironmentContext.environmentId() == null
        ? priceRateRepository.findByOrganizationIdOrderByBillableMetricCodeAscDimensionValuesAsc(
            organizationId)
        : priceRateRepository.listScoped(
            organizationId, BusinessEnvironmentContext.environmentId()));
  }

  private void validateCustomer(UUID organizationId, UUID customerId) {
    if (customerId != null
        && !(BusinessEnvironmentContext.environmentId() == null
            ? customerRepository.existsByOrganizationIdAndId(organizationId, customerId)
            : customerRepository
                .findScoped(organizationId, BusinessEnvironmentContext.environmentId(), customerId)
                .isPresent())) throw new NotFoundException(ErrorCode.CUSTOMER_NOT_FOUND);
  }

  private Map<String, Object> parse(String json) {
    try {
      Map<String, Object> values = jsonMapper.readValue(json, DIMENSION_VALUES_TYPE);
      if (values == null)
        throw new InvalidRequestException(
            ErrorCode.VALIDATION_ERROR,
            List.of(new FieldError("dimension_values", "JSON 객체여야 합니다")));
      return PriceRateDimensions.normalize(values);
    } catch (JacksonException exception) {
      throw new InvalidRequestException(
          ErrorCode.VALIDATION_ERROR, List.of(new FieldError("dimension_values", "JSON 객체여야 합니다")));
    }
  }

  private void validate(Map<String, Object> values, List<String> dimensions) {
    if (values == null || !values.keySet().equals(new HashSet<>(dimensions)))
      throw invalid("dimension_values", "키 집합이 미터의 dimension_properties와 같아야 합니다");
    if (values.values().stream()
        .anyMatch(
            value ->
                !(value instanceof String || value instanceof Number || value instanceof Boolean)))
      throw invalid("dimension_values", "값은 문자열, 숫자, 불리언만 됩니다");
  }

  private static InvalidRequestException invalid(String field, String message) {
    return new InvalidRequestException(
        ErrorCode.INVALID_PRICE_RATE, List.of(new FieldError(field, message)));
  }
}
