package com.meterengine.pricing.dto;

import com.fasterxml.jackson.annotation.*;
import com.meterengine.pricing.entity.PriceRate;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record PriceRateResponse(
    UUID id,
    @JsonProperty("billable_metric_code") String billableMetricCode,
    @JsonRawValue @Schema(implementation = Map.class) @JsonProperty("dimension_values")
        String dimensionValues,
    @JsonProperty("unit_price") BigDecimal unitPrice,
    @JsonProperty("customer_id") UUID customerId,
    @JsonProperty("effective_from") String effectiveFrom,
    @JsonProperty("effective_until") String effectiveUntil,
    String model,
    @JsonRawValue @Schema(type = "array", implementation = PriceRateTier.class) String tiers,
    @JsonProperty("archived_at") java.time.OffsetDateTime archivedAt,
    @JsonProperty("archived_from") String archivedFrom) {
  public static PriceRateResponse from(PriceRate rate) {
    return new PriceRateResponse(
        rate.getId(),
        rate.getBillableMetricCode(),
        rate.getDimensionValues(),
        rate.getUnitPrice(),
        rate.getCustomerId(),
        rate.getEffectiveFrom(),
        rate.getEffectiveUntil(),
        rate.getModel(),
        rate.getTiers(),
        rate.getArchivedAt(),
        rate.getArchivedFrom());
  }
}
