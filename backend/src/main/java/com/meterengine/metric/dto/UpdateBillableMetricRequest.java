package com.meterengine.metric.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.global.validation.StorableText;
import jakarta.validation.constraints.NotBlank;

public record UpdateBillableMetricRequest(
    @NotBlank @StorableText String name,
    @NotBlank @StorableText @JsonProperty("event_type") String eventType,
    @NotBlank @StorableText String aggregation,
    @StorableText @JsonProperty("target_property") String targetProperty) {}
