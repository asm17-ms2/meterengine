package com.meterengine.metric.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.YearMonth;
import java.util.List;

public record ListBillableMetricUsagesResponse(
    @Schema(description = "집계한 달(yyyy-MM, KST)") String month,
    @Schema(description = "미터별 사용량입니다. 그 달에 집계하는 미터가 미터 코드 오름차순으로 나옵니다.")
        @JsonProperty("billable_metric_usages")
        List<BillableMetricUsageResponse> billableMetricUsages) {

  public static ListBillableMetricUsagesResponse of(
      YearMonth month, List<BillableMetricUsage> billableMetricUsages) {
    return new ListBillableMetricUsagesResponse(
        month.toString(),
        billableMetricUsages.stream().map(BillableMetricUsageResponse::from).toList());
  }
}
