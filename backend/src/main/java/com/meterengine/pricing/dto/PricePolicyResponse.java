package com.meterengine.pricing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.pricing.entity.PricePolicy;
import java.util.List;

public record PricePolicyResponse(
    @JsonProperty("metric_code") String metricCode,
    @JsonProperty("dimension_properties") List<String> dimensionProperties) {

  public static PricePolicyResponse from(PricePolicy policy) {
    return new PricePolicyResponse(policy.getMetricCode(), policy.getDimensionProperties());
  }
}
