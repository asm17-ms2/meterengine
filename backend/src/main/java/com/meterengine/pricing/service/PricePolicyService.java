package com.meterengine.pricing.service;

import com.meterengine.metric.entity.BillableMetric;
import com.meterengine.metric.entity.BillableMetricId;
import com.meterengine.metric.exception.MetricNotFoundException;
import com.meterengine.metric.repository.BillableMetricRepository;
import com.meterengine.pricing.dto.MetricPricePolicyResponse;
import com.meterengine.pricing.dto.PricePolicyListResponse;
import com.meterengine.pricing.dto.PricePolicyResponse;
import com.meterengine.pricing.dto.SavePricePolicyRequest;
import com.meterengine.pricing.entity.PricePolicy;
import com.meterengine.pricing.entity.PricePolicyId;
import com.meterengine.pricing.exception.InvalidPricePolicyException;
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

  private final PricePolicyRepository policies;
  private final BillableMetricRepository metrics;
  private final PriceRateRepository rates;

  PricePolicyService(
      PricePolicyRepository policies, BillableMetricRepository metrics, PriceRateRepository rates) {
    this.policies = policies;
    this.metrics = metrics;
    this.rates = rates;
  }

  @Transactional(readOnly = true)
  public PricePolicyListResponse list(UUID organizationId) {
    Map<String, PricePolicy> policyByMetricCode =
        policies.findByOrganizationId(organizationId).stream()
            .collect(Collectors.toMap(PricePolicy::getMetricCode, Function.identity()));
    Map<String, BigDecimal> unitPriceByMetricCode = rates.findBaseUnitPrices(organizationId);

    return new PricePolicyListResponse(
        metrics.findByOrganizationIdOrderByCodeAsc(organizationId).stream()
            .map(metric -> toResponse(metric, policyByMetricCode, unitPriceByMetricCode))
            .toList());
  }

  @Transactional
  public PricePolicyResponse register(
      UUID organizationId, String metricCode, SavePricePolicyRequest request) {
    if (!metrics.existsById(new BillableMetricId(organizationId, metricCode))) {
      throw new MetricNotFoundException(organizationId, metricCode);
    }

    validate(request.dimensionProperties());

    if (policies.existsById(new PricePolicyId(organizationId, metricCode))) {
      throw new PricePolicyAlreadyExistsException(metricCode);
    }

    PricePolicy policy = new PricePolicy(organizationId, metricCode, request.dimensionProperties());
    try {
      policies.saveAndFlush(policy);
    } catch (DataIntegrityViolationException exception) {
      throw new PricePolicyAlreadyExistsException(metricCode);
    }

    return PricePolicyResponse.from(policy);
  }

  private static MetricPricePolicyResponse toResponse(
      BillableMetric metric,
      Map<String, PricePolicy> policyByMetricCode,
      Map<String, BigDecimal> unitPriceByMetricCode) {
    return MetricPricePolicyResponse.of(
        metric.getCode(),
        policyByMetricCode.get(metric.getCode()),
        unitPriceByMetricCode.get(metric.getCode()));
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
