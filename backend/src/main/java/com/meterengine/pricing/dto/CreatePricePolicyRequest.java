package com.meterengine.pricing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreatePricePolicyRequest(
    @NotNull @JsonProperty("dimension_properties") List<String> dimensionProperties) {}
