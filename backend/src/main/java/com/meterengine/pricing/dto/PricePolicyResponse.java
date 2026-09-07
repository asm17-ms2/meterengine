package com.meterengine.pricing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.pricing.entity.PricePolicy;
import java.util.List;

public record PricePolicyResponse(
    @JsonProperty("billable_metric_code") String billableMetricCode,
    @JsonProperty("dimension_properties") List<String> dimensionProperties) {

  public static PricePolicyResponse from(PricePolicy pricePolicy) {
    return new PricePolicyResponse(
        pricePolicy.getBillableMetricCode(), pricePolicy.getDimensionProperties());
  }
}
