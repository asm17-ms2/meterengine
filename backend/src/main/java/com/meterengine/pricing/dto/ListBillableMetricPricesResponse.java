package com.meterengine.pricing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ListBillableMetricPricesResponse(
    @Schema(description = "미터별 단가입니다. 보관한 미터를 포함해 미터 코드 오름차순입니다.")
        @JsonProperty("billable_metric_prices")
        List<BillableMetricPriceResponse> billableMetricPrices) {}
