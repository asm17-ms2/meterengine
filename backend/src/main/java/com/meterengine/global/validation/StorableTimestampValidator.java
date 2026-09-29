package com.meterengine.global.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public class StorableTimestampValidator
    implements ConstraintValidator<StorableTimestamp, OffsetDateTime> {

  // 받는 가장 이른 순간.
  static final Instant EARLIEST =
      OffsetDateTime.of(-4712, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC).toInstant();

  // 받는 가장 늦은 순간.
  static final Instant LATEST =
      OffsetDateTime.of(294276, 12, 31, 23, 59, 59, 999_999_000, ZoneOffset.UTC).toInstant();

  @Override
  public boolean isValid(OffsetDateTime value, ConstraintValidatorContext context) {
    if (value == null) {
      return true;
    }
    Instant instant = value.toInstant();
    return !instant.isBefore(EARLIEST) && !instant.isAfter(LATEST);
  }
}
