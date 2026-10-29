package com.meterengine.pricing.dto;

import com.fasterxml.jackson.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record BillableMetricPricePriceRate(
    @JsonProperty("dimension_values") Map<String, Object> dimensionValues,
    @Schema(description = "per_unit 모델의 단가. graduated, volume 모델에서는 null이다")
        @JsonProperty("unit_price")
        BigDecimal unitPrice,
    UUID id,
    @JsonProperty("customer_id") UUID customerId,
    @JsonProperty("effective_from") String effectiveFrom,
    @JsonProperty("effective_until") String effectiveUntil,
    @Schema(allowableValues = {"per_unit", "graduated", "volume"}) String model,
    @JsonRawValue
        @Schema(
            implementation = PriceRateTier[].class,
            description = "graduated, volume 모델의 구간. per_unit이면 null이다")
        String tiers,
    @JsonProperty("archived_at") java.time.OffsetDateTime archivedAt,
    @JsonProperty("archived_from") String archivedFrom) {}
