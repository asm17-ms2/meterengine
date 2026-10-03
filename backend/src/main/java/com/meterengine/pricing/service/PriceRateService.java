package com.meterengine.pricing.service;

import com.meterengine.global.error.ConflictException;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.NotFoundException;
import com.meterengine.pricing.dto.CreatePriceRateRequest;
import com.meterengine.pricing.dto.PriceRateResponse;
import com.meterengine.pricing.entity.PricePolicyId;
import com.meterengine.pricing.entity.PriceRate;
import com.meterengine.pricing.entity.PriceRateId;
import com.meterengine.pricing.repository.PricePolicyRepository;
import com.meterengine.pricing.repository.PriceRateRepository;
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
    if (!pricePolicyRepository.existsById(new PricePolicyId(organizationId, billableMetricCode))) {
      throw new NotFoundException(ErrorCode.PRICE_POLICY_NOT_FOUND);
    }

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
}
