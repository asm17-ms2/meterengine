package com.meterengine.metric.dto;

import java.math.BigDecimal;
import java.util.Map;

public record DimensionUsage(
    Map<String, Object> dimensionValues,
    BigDecimal quantity,
    BigDecimal rawQuantity,
    BigDecimal rawQuantityNumerator,
    BigDecimal rawQuantityDenominator) {
  public DimensionUsage(Map<String, Object> dimensionValues, BigDecimal quantity) {
    this(dimensionValues, quantity, quantity);
  }

  public DimensionUsage(
      Map<String, Object> dimensionValues, BigDecimal quantity, BigDecimal rawQuantity) {
    this(dimensionValues, quantity, rawQuantity, rawQuantity, BigDecimal.ONE);
  }
}
