package com.meterengine.pricing.service;

import com.meterengine.global.error.ConflictException;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.ErrorResponse.FieldError;
import com.meterengine.global.error.InvalidRequestException;
import com.meterengine.global.error.NotFoundException;
import com.meterengine.metric.entity.BillableMetricId;
import com.meterengine.metric.repository.BillableMetricRepository;
import com.meterengine.pricing.dto.CreatePricePolicyRequest;
import com.meterengine.pricing.dto.PricePolicyResponse;
import com.meterengine.pricing.entity.PricePolicy;
import com.meterengine.pricing.entity.PricePolicyId;
import com.meterengine.pricing.repository.PricePolicyRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PricePolicyService {

  private final PricePolicyRepository pricePolicyRepository;
  private final BillableMetricRepository billableMetricRepository;

  PricePolicyService(
      PricePolicyRepository pricePolicyRepository,
      BillableMetricRepository billableMetricRepository) {
    this.pricePolicyRepository = pricePolicyRepository;
    this.billableMetricRepository = billableMetricRepository;
  }

  @Transactional
  public PricePolicyResponse create(
      UUID organizationId, String billableMetricCode, CreatePricePolicyRequest request) {
    if (!billableMetricRepository.existsById(
        new BillableMetricId(organizationId, billableMetricCode))) {
      throw new NotFoundException(ErrorCode.BILLABLE_METRIC_NOT_FOUND);
    }

    validate(request.dimensionProperties());

    if (pricePolicyRepository.existsById(new PricePolicyId(organizationId, billableMetricCode))) {
      throw new ConflictException(ErrorCode.PRICE_POLICY_ALREADY_EXISTS);
    }

    PricePolicy pricePolicy =
        new PricePolicy(organizationId, billableMetricCode, request.dimensionProperties());
    try {
      pricePolicyRepository.saveAndFlush(pricePolicy);
    } catch (DataIntegrityViolationException exception) {
      throw new ConflictException(ErrorCode.PRICE_POLICY_ALREADY_EXISTS);
    }

    return PricePolicyResponse.from(pricePolicy);
  }

  private void validate(List<String> dimensionProperties) {
    Set<String> declared = new HashSet<>(dimensionProperties);
    if (declared.size() < dimensionProperties.size()) {
      throw new InvalidRequestException(
          ErrorCode.INVALID_PRICE_POLICY,
          List.of(new FieldError("dimension_properties", "같은 키가 두 번 있습니다")));
    }
    if (dimensionProperties.stream().anyMatch(key -> key == null || key.isBlank())) {
      throw new InvalidRequestException(
          ErrorCode.INVALID_PRICE_POLICY,
          List.of(new FieldError("dimension_properties", "빈 키를 담을 수 없습니다")));
    }
  }
}
