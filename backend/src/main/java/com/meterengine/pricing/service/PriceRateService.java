package com.meterengine.pricing.service;

import com.meterengine.audit.service.AuditLogService;
import com.meterengine.customer.repository.CustomerRepository;
import com.meterengine.global.error.ConflictException;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.ErrorResponse.FieldError;
import com.meterengine.global.error.InvalidRequestException;
import com.meterengine.global.error.NotFoundException;
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
    return priceRateRepository.existsByOrganizationIdAndBillableMetricCode(organizationId, code);
  }

  @Transactional(readOnly = true)
  public ListPriceRatesResponse list(UUID organizationId, String code, UUID customerId) {
    validateCustomer(organizationId, customerId);
    return new ListPriceRatesResponse(
        rates(organizationId).stream()
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
    BillableMetric metric =
        billableMetricRepository
            .findById(new BillableMetricId(organizationId, code))
            .orElseThrow(() -> new NotFoundException(ErrorCode.BILLABLE_METRIC_NOT_FOUND));
    validate(request.dimensionValues(), metric.getDimensionProperties());
    if (!request.isValidPricing()) throw invalid("model", "요금 모델에 맞는 단가와 구간이 필요합니다");
    YearMonth current = YearMonth.now(BillableMetricUsageService.BILLING_ZONE);
    YearMonth start = request.effectiveFrom() == null ? current : request.effectiveFrom();
    if (start.isBefore(current))
      throw new ConflictException(ErrorCode.PRICE_RATE_HISTORY_IMMUTABLE);
    if (request.effectiveUntil() != null && !request.effectiveUntil().isAfter(start))
      throw invalid("effective_until", "시작 월보다 뒤여야 합니다");
    validateCustomer(organizationId, request.customerId());
    organizationRepository
        .findForUpdateById(organizationId)
        .orElseThrow(() -> new NotFoundException(ErrorCode.ORGANIZATION_NOT_FOUND));
    PriceRate rate =
        rates(organizationId).stream()
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
              tiers);
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
        priceRateRepository
            .findById(id)
            .filter(
                value ->
                    value.getOrganizationId().equals(organizationId)
                        && value.getBillableMetricCode().equals(code))
            .orElseThrow(() -> new NotFoundException(ErrorCode.PRICE_RATE_NOT_FOUND));
    if (rate.getEffectiveFrom()
            .compareTo(YearMonth.now(BillableMetricUsageService.BILLING_ZONE).toString())
        < 0) throw new ConflictException(ErrorCode.PRICE_RATE_HISTORY_IMMUTABLE);
    priceRateRepository.delete(rate);
    priceRateRepository.flush();
    auditLogService.record(
        organizationId, "price_rate_deleted", "price_rate", id.toString(), detail(rate));
  }

  @Transactional
  public void delete(UUID organizationId, String code, String dimensionValuesJson) {
    Map<String, Object> dimensionValues = parse(dimensionValuesJson);
    String current = YearMonth.now(BillableMetricUsageService.BILLING_ZONE).toString();
    organizationRepository
        .findForUpdateById(organizationId)
        .orElseThrow(() -> new NotFoundException(ErrorCode.ORGANIZATION_NOT_FOUND));
    List<PriceRate> active =
        rates(organizationId).stream()
            .filter(
                rate ->
                    rate.getBillableMetricCode().equals(code)
                        && rate.getCustomerId() == null
                        && parse(rate.getDimensionValues()).equals(dimensionValues)
                        && rate.getEffectiveFrom().compareTo(current) <= 0
                        && (rate.getEffectiveUntil() == null
                            || rate.getEffectiveUntil().compareTo(current) > 0))
            .toList();
    if (active.isEmpty()) throw new NotFoundException(ErrorCode.PRICE_RATE_NOT_FOUND);
    for (PriceRate rate : active) {
      Map<String, Object> detail = detail(rate);
      String action;
      if (rate.getEffectiveFrom().equals(current)) {
        priceRateRepository.delete(rate);
        action = "price_rate_deleted";
      } else {
        rate.close(current);
        action = "price_rate_closed";
        detail.put("previous_effective_until", detail.get("effective_until"));
        detail.put("effective_until", current);
      }
      auditLogService.record(organizationId, action, "price_rate", rate.getId().toString(), detail);
    }
    priceRateRepository.flush();
  }

  private Map<String, Object> detail(PriceRate rate) {
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("billable_metric_code", rate.getBillableMetricCode());
    detail.put("dimension_values", parse(rate.getDimensionValues()));
    detail.put("customer_id", rate.getCustomerId());
    detail.put("effective_from", rate.getEffectiveFrom());
    detail.put("effective_until", rate.getEffectiveUntil());
    detail.put("model", rate.getModel());
    detail.put("unit_price", rate.getUnitPrice());
    detail.put(
        "tiers",
        rate.getTiers() == null ? null : jsonMapper.readValue(rate.getTiers(), Object.class));
    return detail;
  }

  private List<PriceRate> rates(UUID organizationId) {
    return priceRateRepository.findByOrganizationIdOrderByBillableMetricCodeAscDimensionValuesAsc(
        organizationId);
  }

  private void validateCustomer(UUID organizationId, UUID customerId) {
    if (customerId != null
        && !customerRepository.existsByOrganizationIdAndId(organizationId, customerId))
      throw new NotFoundException(ErrorCode.CUSTOMER_NOT_FOUND);
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
