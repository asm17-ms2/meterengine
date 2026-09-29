package com.meterengine.global.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.YearMonth;

public class FourDigitYearValidator implements ConstraintValidator<FourDigitYear, YearMonth> {

  @Override
  public boolean isValid(YearMonth value, ConstraintValidatorContext context) {
    if (value == null) {
      return true;
    }
    return value.getYear() >= 1 && value.getYear() <= 9999;
  }
}
