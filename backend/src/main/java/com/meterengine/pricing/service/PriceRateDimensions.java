package com.meterengine.pricing.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

final class PriceRateDimensions {
  private PriceRateDimensions() {}

  static Map<String, Object> normalize(Map<String, Object> values) {
    Map<String, Object> normalizedValueByDimensionProperty = new LinkedHashMap<>();
    values.forEach(
        (key, value) ->
            normalizedValueByDimensionProperty.put(
                key,
                value instanceof Number number
                    ? new BigDecimal(number.toString()).stripTrailingZeros()
                    : value));
    return normalizedValueByDimensionProperty;
  }
}
