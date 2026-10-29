package com.meterengine.metric.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.metric.entity.BillableMetric;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

public record BillableMetricResponse(
    String code,
    String name,
    @JsonProperty("event_type") String eventType,
    String aggregation,
    @JsonProperty("target_property") String targetProperty,
    @Schema(description = "단가를 가르는 이벤트 속성 키. 속성에 따라 단가가 갈리지 않으면 빈 배열")
        @JsonProperty("dimension_properties")
        List<String> dimensionProperties,
    @JsonProperty("unit_size") BigDecimal unitSize,
    @JsonProperty("rounding_mode") String roundingMode,
    @JsonProperty("rounding_precision") int roundingPrecision,
    BigDecimal percentile) {

  public static BillableMetricResponse from(BillableMetric billableMetric) {
    return new BillableMetricResponse(
        billableMetric.getCode(),
        billableMetric.getName(),
        billableMetric.getEventType(),
        billableMetric.getAggregation(),
        billableMetric.getTargetProperty(),
        billableMetric.getDimensionProperties(),
        billableMetric.getUnitSize(),
        billableMetric.getRoundingMode(),
        billableMetric.getRoundingPrecision(),
        billableMetric.getPercentile());
  }
}
