package com.meterengine.metric.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.global.validation.StorableText;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record UpdateBillableMetricRequest(
    @NotBlank @StorableText String name,
    @NotBlank @StorableText @JsonProperty("event_type") String eventType,
    @NotBlank @StorableText String aggregation,
    @StorableText @JsonProperty("target_property") String targetProperty,
    @Schema(description = "단가를 가르는 이벤트 속성 키. 속성에 따라 단가가 갈리지 않으면 빈 배열")
        @JsonProperty("dimension_properties")
        List<@StorableText String> dimensionProperties) {}
