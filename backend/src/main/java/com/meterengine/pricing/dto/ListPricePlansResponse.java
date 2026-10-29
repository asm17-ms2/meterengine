package com.meterengine.pricing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ListPricePlansResponse(
    @JsonProperty("price_plans") List<PricePlanResponse> pricePlans) {}
