package com.meterengine.metric.dto;

import com.meterengine.metric.entity.BillableMetric;
import java.util.List;

public record BillableMetricListResponse(List<BillableMetricResponse> metrics) {

  public static BillableMetricListResponse from(List<BillableMetric> metrics) {
    return new BillableMetricListResponse(
        metrics.stream().map(BillableMetricResponse::from).toList());
  }
}
