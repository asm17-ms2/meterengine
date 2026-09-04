package com.meterengine.metric.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.YearMonth;
import java.util.List;

public record ListBillableMetricUsagesResponse(
    String month,
    @JsonProperty("billable_metric_usages")
        List<BillableMetricUsageResponse> billableMetricUsages) {

  public static ListBillableMetricUsagesResponse of(
      YearMonth month, List<BillableMetricUsage> billableMetricUsages) {
    return new ListBillableMetricUsagesResponse(
        month.toString(),
        billableMetricUsages.stream().map(BillableMetricUsageResponse::from).toList());
  }
}
