package com.meterengine.metric.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.metric.entity.BillableMetric;
import java.util.List;

public record ListBillableMetricsResponse(
    @JsonProperty("metrics") List<BillableMetricResponse> billableMetrics) {

  public static ListBillableMetricsResponse from(List<BillableMetric> billableMetrics) {
    return new ListBillableMetricsResponse(
        billableMetrics.stream().map(BillableMetricResponse::from).toList());
  }
}
