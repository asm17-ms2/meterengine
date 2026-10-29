package com.meterengine.metric.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CustomerUsage(
    UUID customerId,
    String customerName,
    BigDecimal quantity,
    List<DimensionUsage> dimensionUsages,
    BigDecimal rawQuantity,
    BigDecimal rawQuantityNumerator,
    BigDecimal rawQuantityDenominator) {
  public CustomerUsage(
      UUID customerId,
      String customerName,
      BigDecimal quantity,
      List<DimensionUsage> dimensionUsages,
      BigDecimal rawQuantity) {
    this(
        customerId,
        customerName,
        quantity,
        dimensionUsages,
        rawQuantity,
        rawQuantity,
        BigDecimal.ONE);
  }

  public CustomerUsage(
      UUID customerId,
      String customerName,
      BigDecimal quantity,
      List<DimensionUsage> dimensionUsages) {
    this(customerId, customerName, quantity, dimensionUsages, quantity);
  }
}
