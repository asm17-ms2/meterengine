package com.meterengine.pricing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ListPriceRatesResponse(
    @JsonProperty("price_rates") List<PriceRateResponse> priceRates) {}
