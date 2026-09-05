package com.meterengine.pricing.service;

import com.meterengine.metric.entity.BillableMetric;
import com.meterengine.metric.entity.BillableMetricId;
import com.meterengine.metric.repository.BillableMetricRepository;
import com.meterengine.pricing.dto.BillableMetricPricePolicyResponse;
import com.meterengine.pricing.dto.CreatePricePolicyRequest;
import com.meterengine.pricing.dto.ListPricePoliciesResponse;
import com.meterengine.pricing.dto.PricePolicyResponse;
import com.meterengine.pricing.entity.PricePolicy;
import com.meterengine.pricing.entity.PricePolicyId;
import com.meterengine.pricing.exception.InvalidPricePolicyException;
import com.meterengine.pricing.exception.MetricNotFoundException;
import com.meterengine.pricing.exception.PricePolicyAlreadyExistsException;
import com.meterengine.pricing.repository.PricePolicyRepository;
import com.meterengine.pricing.repository.PriceRateRepository;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PricePolicyService {

  private final PricePolicyRepository pricePolicyRepository;
  private final BillableMetricRepository billableMetricRepository;
  private final PriceRateRepository priceRateRepository;

  PricePolicyService(
      PricePolicyRepository pricePolicyRepository,
      BillableMetricRepository billableMetricRepository,
      PriceRateRepository priceRateRepository) {
    this.pricePolicyRepository = pricePolicyRepository;
    this.billableMetricRepository = billableMetricRepository;
    this.priceRateRepository = priceRateRepository;
  }

  @Transactional(readOnly = true)
  public ListPricePoliciesResponse list(UUID organizationId) {
    Map<String, PricePolicy> policyByBillableMetricCode =
        pricePolicyRepository.findByOrganizationId(organizationId).stream()
            .collect(Collectors.toMap(PricePolicy::getBillableMetricCode, Function.identity()));
    Map<String, BigDecimal> unitPriceByBillableMetricCode =
        priceRateRepository.findBaseUnitPrices(organizationId);

    return new ListPricePoliciesResponse(
        billableMetricRepository.findByOrganizationIdOrderByCodeAsc(organizationId).stream()
            .map(
                billableMetric ->
                    toResponse(
                        billableMetric, policyByBillableMetricCode, unitPriceByBillableMetricCode))
            .toList());
  }

  @Transactional
  public PricePolicyResponse create(
      UUID organizationId, String billableMetricCode, CreatePricePolicyRequest request) {
    if (!billableMetricRepository.existsById(
        new BillableMetricId(organizationId, billableMetricCode))) {
      throw new MetricNotFoundException(organizationId, billableMetricCode);
    }

    validate(request.dimensionProperties());

    if (pricePolicyRepository.existsById(new PricePolicyId(organizationId, billableMetricCode))) {
      throw new PricePolicyAlreadyExistsException(billableMetricCode);
    }

    PricePolicy pricePolicy =
        new PricePolicy(organizationId, billableMetricCode, request.dimensionProperties());
    try {
      pricePolicyRepository.saveAndFlush(pricePolicy);
    } catch (DataIntegrityViolationException exception) {
      throw new PricePolicyAlreadyExistsException(billableMetricCode);
    }

    return PricePolicyResponse.from(pricePolicy);
  }

  private static BillableMetricPricePolicyResponse toResponse(
      BillableMetric billableMetric,
      Map<String, PricePolicy> policyByBillableMetricCode,
      Map<String, BigDecimal> unitPriceByBillableMetricCode) {
    return BillableMetricPricePolicyResponse.of(
        billableMetric.getCode(),
        policyByBillableMetricCode.get(billableMetric.getCode()),
        unitPriceByBillableMetricCode.get(billableMetric.getCode()));
  }

  private void validate(List<String> dimensionProperties) {
    Set<String> declared = new HashSet<>(dimensionProperties);
    if (declared.size() < dimensionProperties.size()) {
      throw new InvalidPricePolicyException("dimension_properties has duplicate keys");
    }
    if (dimensionProperties.stream().anyMatch(key -> key == null || key.isBlank())) {
      throw new InvalidPricePolicyException("dimension_properties has a blank key");
    }
  }
}
