package com.meterengine.global.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class StorableTextValidator implements ConstraintValidator<StorableText, String> {

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    return value == null || isStorable(value);
  }

  // 문자열에 NUL 문자도 짝이 없는 서로게이트도 없는지 본다.
  static boolean isStorable(String value) {
    return value.codePoints().noneMatch(StorableTextValidator::isUnstorable);
  }

  private static boolean isUnstorable(int codePoint) {
    return codePoint == 0
        || (codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE);
  }
}
