package com.meterengine.pricing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ListPricePoliciesResponse(
    @JsonProperty("price_policies") List<BillableMetricPricePolicyResponse> pricePolicies) {}
