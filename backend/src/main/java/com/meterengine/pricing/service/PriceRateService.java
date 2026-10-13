package com.meterengine.pricing.service;

import com.meterengine.global.error.ConflictException;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class PriceRateService {

  private final PriceRateRepository priceRateRepository;
  private final BillableMetricRepository billableMetricRepository;
  private final JsonMapper jsonMapper;

  PriceRateService(
      PriceRateRepository priceRateRepository,
      BillableMetricRepository billableMetricRepository,
      JsonMapper jsonMapper) {
    this.priceRateRepository = priceRateRepository;
    this.billableMetricRepository = billableMetricRepository;
    this.jsonMapper = jsonMapper;
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
    if (priceRateRepository.existsById(
        new PriceRateId(organizationId, billableMetricCode, dimensionValues))) {
      throw new ConflictException(ErrorCode.PRICE_RATE_ALREADY_EXISTS);
    }

    PriceRate priceRate =
        new PriceRate(organizationId, billableMetricCode, dimensionValues, request.unitPrice());
    priceRateRepository.saveAndFlush(priceRate);

    return PriceRateResponse.from(priceRate);
  }

  private void validate(Map<String, Object> dimensionValues, List<String> dimensionProperties) {
    if (!dimensionValues.isEmpty()
        && !dimensionValues.keySet().equals(new HashSet<>(dimensionProperties))) {
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
