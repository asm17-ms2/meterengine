package com.meterengine.pricing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record PricePolicyListResponse(
    @JsonProperty("price_policies") List<MetricPricePolicyResponse> pricePolicies) {}
