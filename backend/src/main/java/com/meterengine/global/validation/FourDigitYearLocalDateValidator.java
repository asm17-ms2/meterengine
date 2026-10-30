package com.meterengine.global.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.LocalDate;

public class FourDigitYearLocalDateValidator
    implements ConstraintValidator<FourDigitYear, LocalDate> {

  private static final String MESSAGE = "yyyy-MM-dd 형식의 0001-01-01부터 9999-12-31까지만 받습니다";

  @Override
  public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
    if (value == null || (value.getYear() >= 1 && value.getYear() <= 9999)) {
      return true;
    }
    context.disableDefaultConstraintViolation();
    context.buildConstraintViolationWithTemplate(MESSAGE).addConstraintViolation();
    return false;
  }
}
