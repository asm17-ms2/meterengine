package com.meterengine.pricing.dto;

import java.math.BigDecimal;
import java.util.Map;

public record DimensionCharge(
    String billableMetricCode,
    String targetProperty,
    Map<String, Object> dimensionValues,
    BigDecimal quantity,
    BigDecimal unitPrice,
    long amount) {}
