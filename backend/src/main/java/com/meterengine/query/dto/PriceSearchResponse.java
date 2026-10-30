package com.meterengine.query.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.pricing.dto.BillableMetricPricePriceRate;
import java.time.OffsetDateTime;
import java.util.List;

public record PriceSearchResponse(
    @JsonProperty("billable_metric_code") String billableMetricCode,
    @JsonProperty("billable_metric_name") String billableMetricName,
    @JsonProperty("dimension_properties") List<String> dimensionProperties,
    @JsonProperty("price_rates") List<BillableMetricPricePriceRate> priceRates,
    @JsonProperty("archived_at") OffsetDateTime archivedAt,
    @JsonProperty("rate_count") int rateCount) {}
