package com.meterengine.global.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class StorableJsonValidator implements ConstraintValidator<StorableJson, Map<String, ?>> {

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
          number.precision() - number.scale() <= 131072 && number.scale() <= 16383;
      case null, default -> true;
    };
  }
}
