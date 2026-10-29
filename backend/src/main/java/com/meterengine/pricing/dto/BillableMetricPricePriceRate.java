package com.meterengine.pricing.dto;

import com.fasterxml.jackson.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record BillableMetricPricePriceRate(
    @JsonProperty("dimension_values") Map<String, Object> dimensionValues,
    @JsonProperty("unit_price") BigDecimal unitPrice,
    UUID id,
    @JsonProperty("customer_id") UUID customerId,
    @JsonProperty("effective_from") String effectiveFrom,
    @JsonProperty("effective_until") String effectiveUntil,
    String model,
    @JsonRawValue @Schema(type = "array", implementation = PriceRateTier.class) String tiers,
    @JsonProperty("archived_at") java.time.OffsetDateTime archivedAt,
    @JsonProperty("archived_from") String archivedFrom) {}
