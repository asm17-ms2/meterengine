package com.meterengine.pricing.service;

import com.meterengine.audit.service.AuditLogService;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.ErrorResponse.FieldError;
import com.meterengine.global.error.InvalidRequestException;
import com.meterengine.global.error.NotFoundException;
import com.meterengine.metric.entity.BillableMetric;
import com.meterengine.metric.entity.BillableMetricId;
import com.meterengine.metric.repository.BillableMetricRepository;
import com.meterengine.pricing.dto.CreatePriceRateRequest;
import com.meterengine.pricing.dto.PriceRateResponse;
import com.meterengine.pricing.entity.PriceRate;
import com.meterengine.pricing.entity.PriceRateId;
import com.meterengine.pricing.repository.PriceRateRepository;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

  private static final String PRICE_RATE_TARGET_TYPE = "price_rate";

  private static final String DIMENSION_VALUES_DETAIL = "dimension_values";

  private static final String UNIT_PRICE_DETAIL = "unit_price";

  private static final String PREVIOUS_UNIT_PRICE_DETAIL = "previous_unit_price";

  private final PriceRateRepository priceRateRepository;
  private final BillableMetricRepository billableMetricRepository;
  private final JsonMapper jsonMapper;
  private final AuditLogService auditLogService;

  PriceRateService(
      PriceRateRepository priceRateRepository,
      BillableMetricRepository billableMetricRepository,
      JsonMapper jsonMapper,
      AuditLogService auditLogService) {
    this.priceRateRepository = priceRateRepository;
    this.billableMetricRepository = billableMetricRepository;
    this.jsonMapper = jsonMapper;
    this.auditLogService = auditLogService;
  }

  @Transactional(readOnly = true)
  public boolean existsForBillableMetric(UUID organizationId, String billableMetricCode) {
    return priceRateRepository.existsByOrganizationIdAndBillableMetricCode(
        organizationId, billableMetricCode);
  }

  @Transactional
  public PriceRateResponse create(
      UUID organizationId, String billableMetricCode, CreatePriceRateRequest request) {
    BillableMetric billableMetric =
        billableMetricRepository
            .findById(new BillableMetricId(organizationId, billableMetricCode))
            .orElseThrow(() -> new NotFoundException(ErrorCode.BILLABLE_METRIC_NOT_FOUND));

    validate(request.dimensionValues(), billableMetric.getDimensionProperties());

    String dimensionValues = jsonMapper.writeValueAsString(request.dimensionValues());
    PriceRateId priceRateId = new PriceRateId(organizationId, billableMetricCode, dimensionValues);
    Optional<PriceRate> found = priceRateRepository.findById(priceRateId);
    Optional<BigDecimal> previousUnitPrice = found.map(PriceRate::getUnitPrice);
    PriceRate priceRate;
    if (found.isPresent()) {
      priceRate = found.get();
      priceRate.updateUnitPrice(request.unitPrice());
    } else {
      priceRate =
          new PriceRate(organizationId, billableMetricCode, dimensionValues, request.unitPrice());
    }
    priceRateRepository.saveAndFlush(priceRate);
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put(DIMENSION_VALUES_DETAIL, request.dimensionValues());
    detail.put(UNIT_PRICE_DETAIL, request.unitPrice());
    previousUnitPrice.ifPresent(unitPrice -> detail.put(PREVIOUS_UNIT_PRICE_DETAIL, unitPrice));
    auditLogService.record(
        organizationId, "price_rate_created", PRICE_RATE_TARGET_TYPE, billableMetricCode, detail);

    return PriceRateResponse.from(priceRate);
  }

  @Transactional
  public void delete(UUID organizationId, String billableMetricCode, String dimensionValuesJson) {
    Map<String, Object> parsedDimensionValues = parse(dimensionValuesJson);
    String dimensionValues = jsonMapper.writeValueAsString(parsedDimensionValues);
    PriceRate priceRate =
        priceRateRepository
            .findById(new PriceRateId(organizationId, billableMetricCode, dimensionValues))
            .orElseThrow(() -> new NotFoundException(ErrorCode.PRICE_RATE_NOT_FOUND));
    priceRateRepository.delete(priceRate);
    priceRateRepository.flush();
    auditLogService.record(
        organizationId,
        "price_rate_deleted",
        PRICE_RATE_TARGET_TYPE,
        billableMetricCode,
        Map.of(DIMENSION_VALUES_DETAIL, parsedDimensionValues));
  }

  private Map<String, Object> parse(String dimensionValuesJson) {
    try {
      return jsonMapper.readValue(dimensionValuesJson, DIMENSION_VALUES_TYPE);
    } catch (JacksonException exception) {
      throw new InvalidRequestException(
          ErrorCode.VALIDATION_ERROR, List.of(new FieldError("dimension_values", "JSON 객체여야 합니다")));
    }
  }

  private void validate(Map<String, Object> dimensionValues, List<String> dimensionProperties) {
    if (!dimensionValues.keySet().equals(new HashSet<>(dimensionProperties))) {
      throw new InvalidRequestException(
          ErrorCode.INVALID_PRICE_RATE,
          List.of(new FieldError("dimension_values", "키 집합이 미터의 dimension_properties와 같아야 합니다")));
    }
    if (dimensionValues.values().stream().anyMatch(value -> !isScalar(value))) {
      throw new InvalidRequestException(
          ErrorCode.INVALID_PRICE_RATE,
          List.of(new FieldError("dimension_values", "값은 문자열, 숫자, 불리언만 됩니다")));
    }
  }

  private static boolean isScalar(Object value) {
    return value instanceof String || value instanceof Number || value instanceof Boolean;
  }
}
