package com.meterengine.pricing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.pricing.entity.PricePlan;
import java.util.UUID;

public record PricePlanResponse(
    UUID id,
    @JsonProperty("customer_id") UUID customerId,
    @JsonProperty("effective_from") String effectiveFrom,
    @JsonProperty("effective_until") String effectiveUntil,
    @JsonProperty("base_fee") long baseFee,
    @JsonProperty("minimum_amount") long minimumAmount,
    @JsonProperty("archived_at") java.time.OffsetDateTime archivedAt,
    @JsonProperty("archived_from") String archivedFrom) {
  public static PricePlanResponse from(PricePlan plan) {
    return new PricePlanResponse(
        plan.getId(),
        plan.getCustomerId(),
        plan.getEffectiveFrom(),
        plan.getEffectiveUntil(),
        plan.getBaseFee(),
        plan.getMinimumAmount(),
        plan.getArchivedAt(),
        plan.getArchivedFrom());
  }
}
