package com.meterengine.metric.dto;

import java.math.BigDecimal;
import java.util.Map;

public record DimensionUsage(Map<String, Object> dimensionValues, BigDecimal quantity) {}
