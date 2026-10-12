package com.meterengine.pricing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.Map;

public record BillableMetricPricePriceRate(
    @Schema(description = "단가가 적용되는 속성 값 조합. 빈 객체는 속성 조건 없이 적용되는 기본 단가다")
        @JsonProperty("dimension_values")
        Map<String, Object> dimensionValues,
    @Schema(description = "그 조합의 단가. 0은 무료라는 뜻이다") @JsonProperty("unit_price")
        BigDecimal unitPrice) {}
