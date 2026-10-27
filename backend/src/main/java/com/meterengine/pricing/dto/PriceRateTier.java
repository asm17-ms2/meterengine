package com.meterengine.pricing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.global.validation.StorableJsonValidator;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record PriceRateTier(
    @Positive @Digits(
            integer = StorableJsonValidator.MAX_INTEGER_DIGITS,
            fraction = StorableJsonValidator.MAX_FRACTION_DIGITS)
        @JsonProperty("up_to")
        BigDecimal upTo,
    @NotNull @DecimalMin("0") @Digits(
            integer = StorableJsonValidator.MAX_INTEGER_DIGITS,
            fraction = StorableJsonValidator.MAX_FRACTION_DIGITS)
        @JsonProperty("unit_price")
        BigDecimal unitPrice) {}
