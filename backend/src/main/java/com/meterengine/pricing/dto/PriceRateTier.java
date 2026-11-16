package com.meterengine.pricing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.global.validation.StorableJsonValidator;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record PriceRateTier(
    @Schema(description = "구간 상한(과금 수량, 이 값 포함)입니다. 0보다 크고 앞 구간보다 커야 하며, 마지막 구간은 null로 둡니다.")
        @Positive @Digits(
            integer = StorableJsonValidator.MAX_INTEGER_DIGITS,
            fraction = StorableJsonValidator.MAX_FRACTION_DIGITS)
        @JsonProperty("up_to")
        BigDecimal upTo,
    @NotNull @DecimalMin("0") @Digits(
            integer = StorableJsonValidator.MAX_INTEGER_DIGITS,
            fraction = StorableJsonValidator.MAX_FRACTION_DIGITS)
        @JsonProperty("unit_price")
        @Schema(description = "이 구간의 과금 수량 1당 단가(원)입니다. 0 이상입니다.")
        BigDecimal unitPrice) {}
