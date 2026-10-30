package com.meterengine.global.validation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FourDigitYearLocalDateValidatorTest {

  private static final Validator validator =
      Validation.buildDefaultValidatorFactory().getValidator();

  record Probe(@FourDigitYear LocalDate date) {}

  @Test
  void 양끝은_받는다() {
    assertThat(isValid(LocalDate.of(1, 1, 1))).isTrue();
    assertThat(isValid(LocalDate.of(9999, 12, 31))).isTrue();
  }

  @Test
  void 양끝_밖은_거절한다() {
    assertThat(isValid(LocalDate.of(0, 12, 31))).isFalse();
    assertThat(isValid(LocalDate.of(10000, 1, 1))).isFalse();
  }

  @Test
  void null은_받는다() {
    assertThat(validator.validate(new Probe(null))).isEmpty();
  }

  @Test
  void 문구는_날짜_형식과_받는_범위를_알려준다() {
    assertThat(validator.validate(new Probe(LocalDate.of(10000, 1, 1))))
        .extracting(ConstraintViolation::getMessage)
        .containsExactly("yyyy-MM-dd 형식의 0001-01-01부터 9999-12-31까지만 받습니다");
  }

  private static boolean isValid(LocalDate date) {
    return validator.validate(new Probe(date)).isEmpty();
  }
}
