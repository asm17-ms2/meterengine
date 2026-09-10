package com.meterengine.pricing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ListBillableMetricPricesResponse(
    @JsonProperty("billable_metric_prices")
        List<BillableMetricPriceResponse> billableMetricPrices) {}
