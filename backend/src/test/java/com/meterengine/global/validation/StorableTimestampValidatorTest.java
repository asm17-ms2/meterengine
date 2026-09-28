package com.meterengine.global.validation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class StorableTimestampValidatorTest {

  private static final Validator validator =
      Validation.buildDefaultValidatorFactory().getValidator();

  record Probe(@StorableTimestamp OffsetDateTime at) {}

  @Test
  void 양끝은_받는다() {
    assertThat(isValid("-4712-01-01T00:00:00Z")).isTrue();
    assertThat(isValid("+294276-12-31T23:59:59.999999Z")).isTrue();
    assertThat(isValid("+294276-12-31T23:59:59.9999985Z")).isTrue();
  }

  @Test
  void 양끝_밖은_거절한다() {
    assertThat(isValid("-4713-12-31T23:59:59.999999999Z")).isFalse();
    assertThat(isValid("+294276-12-31T23:59:59.999999001Z")).isFalse();
    assertThat(isValid("+294277-01-01T00:00:00Z")).isFalse();
  }

  @Test
  void UTC가_아닌_오프셋은_같은_순간으로_판정한다() {
    assertThat(isValid("-4712-01-01T09:00:00+09:00")).isTrue();
    assertThat(isValid("-4712-01-01T08:59:59+09:00")).isFalse();
    assertThat(isValid("+294276-12-31T14:59:59.999999-09:00")).isTrue();
    assertThat(isValid("+294276-12-31T15:00:00-09:00")).isFalse();
  }

  @Test
  void null은_받는다() {
    assertThat(validator.validate(new Probe(null))).isEmpty();
  }

  private static boolean isValid(String timestamp) {
    return validator.validate(new Probe(OffsetDateTime.parse(timestamp))).isEmpty();
  }
}
