package com.meterengine.pricing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ListPriceRatesResponse(
    @Schema(description = "단가 버전 목록입니다. 시작 월 내림차순입니다.") @JsonProperty("price_rates")
        List<PriceRateResponse> priceRates) {}
