package com.meterengine.pricing.service;

import com.meterengine.global.error.ConflictException;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.ErrorResponse.FieldError;
import com.meterengine.global.error.InvalidRequestException;
import com.meterengine.global.error.NotFoundException;
import com.meterengine.pricing.dto.CreatePriceRateRequest;
import com.meterengine.pricing.dto.PriceRateResponse;
import com.meterengine.pricing.entity.PricePolicy;
import com.meterengine.pricing.entity.PricePolicyId;
import com.meterengine.pricing.entity.PriceRate;
import com.meterengine.pricing.entity.PriceRateId;
import com.meterengine.pricing.repository.PricePolicyRepository;
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
  private final PricePolicyRepository pricePolicyRepository;
  private final JsonMapper jsonMapper;

  PriceRateService(
      PriceRateRepository priceRateRepository,
      PricePolicyRepository pricePolicyRepository,
      JsonMapper jsonMapper) {
    this.priceRateRepository = priceRateRepository;
    this.pricePolicyRepository = pricePolicyRepository;
    this.jsonMapper = jsonMapper;
  }

  @Transactional
  public PriceRateResponse create(
      UUID organizationId, String billableMetricCode, CreatePriceRateRequest request) {
    PricePolicy pricePolicy =
        pricePolicyRepository
            .findById(new PricePolicyId(organizationId, billableMetricCode))
            .orElseThrow(() -> new NotFoundException(ErrorCode.PRICE_POLICY_NOT_FOUND));

    validate(request.dimensionValues(), pricePolicy.getDimensionProperties());

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
          List.of(new FieldError("dimension_values", "키 집합이 정책의 dimension_properties와 같아야 합니다")));
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
