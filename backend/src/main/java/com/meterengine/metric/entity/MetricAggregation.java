package com.meterengine.metric.entity;

import com.meterengine.global.math.DecimalFraction;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

public final class MetricAggregation {
  public static final Set<String> SUPPORTED =
      Set.of(
          "sum",
          "count",
          "count_distinct",
          "min",
          "max",
          "avg",
          "first",
          "last",
          "median",
          "percentile");
  public static final Set<String> ROUNDING_MODES = Set.of("none", "ceil", "floor", "round");

  private MetricAggregation() {}

  public static BigDecimal transform(BigDecimal raw, BillableMetric metric) {
    return transform(raw, raw, BigDecimal.ONE, metric);
  }

  public static BigDecimal transform(
      BigDecimal raw, BigDecimal numerator, BigDecimal denominator, BillableMetric metric) {
    BigDecimal divisor = denominator.multiply(metric.getUnitSize());
    if (!"none".equals(metric.getRoundingMode())) {
      RoundingMode mode =
          switch (metric.getRoundingMode()) {
            case "ceil" -> RoundingMode.CEILING;
            case "floor" -> RoundingMode.FLOOR;
            case "round" -> RoundingMode.HALF_UP;
            default -> throw new IllegalStateException("Unsupported rounding mode");
          };
      return numerator.divide(divisor, metric.getRoundingPrecision(), mode);
    }
    if (metric.getUnitSize().compareTo(BigDecimal.ONE) == 0) return raw;
    return new DecimalFraction(numerator, divisor).decimalValue(raw.scale());
  }
}
