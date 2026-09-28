package com.meterengine.global.validation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class FourDigitYearValidatorTest {

  private static final Validator validator =
      Validation.buildDefaultValidatorFactory().getValidator();

  record Probe(@FourDigitYear YearMonth month) {}

  @Test
  void 양끝은_받는다() {
    assertThat(isValid(YearMonth.of(1, 1))).isTrue();
    assertThat(isValid(YearMonth.of(9999, 12))).isTrue();
  }

  @Test
  void 양끝_밖은_거절한다() {
    assertThat(isValid(YearMonth.of(0, 12))).isFalse();
    assertThat(isValid(YearMonth.of(10000, 1))).isFalse();
    assertThat(isValid(YearMonth.of(-4714, 1))).isFalse();
    assertThat(isValid(YearMonth.of(300000, 1))).isFalse();
  }

  @Test
  void null은_받는다() {
    assertThat(validator.validate(new Probe(null))).isEmpty();
  }

  @Test
  void 문구는_받는_범위를_알려준다() {
    assertThat(validator.validate(new Probe(YearMonth.of(300000, 1))))
        .extracting(ConstraintViolation::getMessage)
        .containsExactly("yyyy-MM 형식의 0001-01부터 9999-12까지만 받습니다");
  }

  private static boolean isValid(YearMonth month) {
    return validator.validate(new Probe(month)).isEmpty();
  }
}
