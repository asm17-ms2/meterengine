package com.meterengine.metric.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record SaveBillableMetricRequest(
    @NotBlank String code,
    @NotBlank String name,
    @NotBlank @JsonProperty("event_type") String eventType,
    @NotBlank String aggregation,
    @JsonProperty("target_property") String targetProperty) {}
