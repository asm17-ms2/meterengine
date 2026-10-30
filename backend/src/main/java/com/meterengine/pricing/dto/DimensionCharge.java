package com.meterengine.pricing.dto;

import java.math.BigDecimal;
import java.util.Map;

public record DimensionCharge(
    String billableMetricCode,
    String targetProperty,
    Map<String, Object> dimensionValues,
    BigDecimal quantity,
    BigDecimal unitPrice,
    long amount,
    String lineType,
    int tierIndex,
    BigDecimal quantityNumerator,
    BigDecimal quantityDenominator) {
  public DimensionCharge(
      String billableMetricCode,
      String targetProperty,
      Map<String, Object> dimensionValues,
      BigDecimal quantity,
      BigDecimal unitPrice,
      long amount,
      String lineType,
      int tierIndex) {
    this(
        billableMetricCode,
        targetProperty,
        dimensionValues,
        quantity,
        unitPrice,
        amount,
        lineType,
        tierIndex,
        quantity,
        BigDecimal.ONE);
  }

  public DimensionCharge(
      String billableMetricCode,
      String targetProperty,
      Map<String, Object> dimensionValues,
      BigDecimal quantity,
      BigDecimal unitPrice,
      long amount) {
    this(
        billableMetricCode,
        targetProperty,
        dimensionValues,
        quantity,
        unitPrice,
        amount,
        "per_unit",
        0);
  }
}
