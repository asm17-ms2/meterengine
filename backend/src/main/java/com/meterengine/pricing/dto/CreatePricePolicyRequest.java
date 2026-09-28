package com.meterengine.pricing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.global.validation.StorableText;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreatePricePolicyRequest(
    @NotNull @JsonProperty("dimension_properties")
        List<@StorableText String> dimensionProperties) {}
