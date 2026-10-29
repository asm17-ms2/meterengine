package com.meterengine.global.math;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record DecimalFraction(BigDecimal numerator, BigDecimal denominator) {
  public DecimalFraction {
    Objects.requireNonNull(numerator);
    Objects.requireNonNull(denominator);
    if (denominator.signum() <= 0)
      throw new IllegalArgumentException("Denominator must be positive");
  }

  public static DecimalFraction of(BigDecimal value) {
    return new DecimalFraction(value, BigDecimal.ONE);
  }

  public int signum() {
    return numerator.signum();
  }

  public int compareTo(BigDecimal value) {
    return numerator.compareTo(value.multiply(denominator));
  }

  public DecimalFraction min(BigDecimal value) {
    return compareTo(value) <= 0 ? this : of(value);
  }

  public DecimalFraction subtract(BigDecimal value) {
    return new DecimalFraction(numerator.subtract(value.multiply(denominator)), denominator);
  }

  public BigDecimal decimalValue(int minimumScale) {
    try {
      return numerator.divide(denominator);
    } catch (ArithmeticException exception) {
      long numeratorIntegerDigits = (long) numerator.precision() - numerator.scale();
      long denominatorIntegerDigits = (long) denominator.precision() - denominator.scale();
      long scale =
          Math.max(
              Math.max(18L, minimumScale),
              Math.max(
                  (long) numerator.scale(),
                  18L + denominatorIntegerDigits - numeratorIntegerDigits));
      return numerator.divide(denominator, Math.toIntExact(scale), RoundingMode.HALF_UP);
    }
  }

  public long charge(BigDecimal unitPrice) {
    return numerator
        .multiply(unitPrice)
        .divide(denominator, -1, RoundingMode.DOWN)
        .longValueExact();
  }
}
