package com.meterengine.pricing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;
import com.meterengine.pricing.entity.PriceRate;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.Map;

public record PriceRateResponse(
    @JsonProperty("billable_metric_code") String billableMetricCode,
    @JsonRawValue @Schema(implementation = Map.class) @JsonProperty("dimension_values")
        String dimensionValues,
    @JsonProperty("unit_price") BigDecimal unitPrice) {

  public static PriceRateResponse from(PriceRate priceRate) {
    return new PriceRateResponse(
        priceRate.getBillableMetricCode(),
        priceRate.getDimensionValues(),
        priceRate.getUnitPrice());
  }
}
