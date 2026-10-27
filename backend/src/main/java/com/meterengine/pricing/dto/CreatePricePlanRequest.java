package com.meterengine.pricing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.global.validation.FourDigitYear;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.YearMonth;
import java.util.UUID;

public record CreatePricePlanRequest(
    @JsonProperty("customer_id") UUID customerId,
    @FourDigitYear @JsonProperty("effective_from") YearMonth effectiveFrom,
    @FourDigitYear @JsonProperty("effective_until") YearMonth effectiveUntil,
    @NotNull @Min(0) @JsonProperty("base_fee") Long baseFee,
    @NotNull @Min(0) @JsonProperty("minimum_amount") Long minimumAmount) {}
