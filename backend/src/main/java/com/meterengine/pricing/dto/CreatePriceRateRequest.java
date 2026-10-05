package com.meterengine.pricing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.global.validation.StorableJson;
import com.meterengine.global.validation.StorableJsonValidator;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Map;

public record CreatePriceRateRequest(
    @Schema(description = "단가가 적용되는 속성 값 조합. 키 집합은 정책의 dimension_properties와 같아야 하고, 빈 객체는 기본 단가다")
        @NotNull @StorableJson
        @JsonProperty("dimension_values")
        Map<String, Object> dimensionValues,
    @NotNull @DecimalMin("0") @Digits(
            integer = StorableJsonValidator.MAX_INTEGER_DIGITS,
            fraction = StorableJsonValidator.MAX_FRACTION_DIGITS)
        @JsonProperty("unit_price")
        BigDecimal unitPrice) {}
