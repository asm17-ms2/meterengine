package com.meterengine.global.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class StorableJsonValidator implements ConstraintValidator<StorableJson, Map<String, ?>> {

  // PostgreSQL NUMERIC이 받는 소수점 앞 자릿수.
  public static final int MAX_INTEGER_DIGITS = 131072;

  // PostgreSQL NUMERIC이 받는 소수점 뒤 자릿수.
  public static final int MAX_FRACTION_DIGITS = 16383;

  @Override
  public boolean isValid(Map<String, ?> value, ConstraintValidatorContext context) {
    return value == null || isStorable(value);
  }

  private static boolean isStorable(Object node) {
    return switch (node) {
      case String text -> StorableTextValidator.isStorable(text);
      case Map<?, ?> object ->
          object.entrySet().stream()
              .allMatch(entry -> isStorable(entry.getKey()) && isStorable(entry.getValue()));
      case List<?> array -> array.stream().allMatch(StorableJsonValidator::isStorable);
      case BigDecimal number ->
          number.precision() - number.scale() <= MAX_INTEGER_DIGITS
              && number.scale() <= MAX_FRACTION_DIGITS;
      case null, default -> true;
    };
  }
}
