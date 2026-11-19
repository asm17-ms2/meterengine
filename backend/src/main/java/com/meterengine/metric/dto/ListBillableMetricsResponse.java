package com.meterengine.metric.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.metric.entity.BillableMetric;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ListBillableMetricsResponse(
    @Schema(description = "과금 미터 목록입니다. 과금 미터 코드 오름차순입니다.") @JsonProperty("billable_metrics")
        List<BillableMetricResponse> billableMetrics) {

  public static ListBillableMetricsResponse from(List<BillableMetric> billableMetrics) {
    return new ListBillableMetricsResponse(
        billableMetrics.stream().map(BillableMetricResponse::from).toList());
  }
}
