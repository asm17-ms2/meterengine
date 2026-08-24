package com.meterengine.metric.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.metric.entity.BillableMetric;

public record BillableMetricResponse(
    String code,
    String name,
    @JsonProperty("event_type") String eventType,
    String aggregation,
    @JsonProperty("target_property") String targetProperty) {

  public static BillableMetricResponse from(BillableMetric metric) {
    return new BillableMetricResponse(
        metric.getCode(),
        metric.getName(),
        metric.getEventType(),
        metric.getAggregation(),
        metric.getTargetProperty());
  }
}
