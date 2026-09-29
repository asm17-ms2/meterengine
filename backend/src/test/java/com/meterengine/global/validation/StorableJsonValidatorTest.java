package com.meterengine.global.validation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StorableJsonValidatorTest {

  private static final Validator validator =
      Validation.buildDefaultValidatorFactory().getValidator();

  record Probe(@StorableJson Map<String, Object> properties) {}

  @Test
  void 키나_문자열_값에_받지_않는_문자가_있으면_거절한다() {
    assertThat(isValid(Map.of("a\u0000b", 1))).isFalse();
    assertThat(isValid(Map.of("prompt", "a\ud800b"))).isFalse();
  }

  @Test
  void 중첩된_객체와_배열_안쪽까지_본다() {
    assertThat(isValid(Map.of("meta", Map.of("tags", List.of("ok", "a\udc00"))))).isFalse();
    assertThat(isValid(Map.of("rows", List.of(Map.of("k\u0000", true))))).isFalse();
  }

  @Test
  void 소수점_앞이나_뒤_자릿수가_numeric_범위를_넘는_숫자는_거절한다() {
    assertThat(isValid(Map.of("x", new BigDecimal("1E+131072")))).isFalse();
    assertThat(isValid(Map.of("x", new BigDecimal("1E-16384")))).isFalse();
    assertThat(isValid(Map.of("x", new BigDecimal("1.0E-16383")))).isFalse();
    assertThat(isValid(Map.of("rows", List.of(1, new BigDecimal("1E-16384"))))).isFalse();
  }

  @Test
  void numeric_범위_끝의_숫자는_받는다() {
    assertThat(isValid(Map.of("x", new BigDecimal("1E+131071")))).isTrue();
    assertThat(isValid(Map.of("x", new BigDecimal("1E-16383")))).isTrue();
    assertThat(
            isValid(
                Map.of("rows", List.of(new BigDecimal("1E+131071"), new BigDecimal("1E-16383")))))
        .isTrue();
  }

  @Test
  void null과_불리언과_숫자와_한국어와_이모지는_받는다() {
    Map<String, Object> properties = new HashMap<>();
    properties.put("none", null);
    properties.put("flag", true);
    properties.put("count", 1.5);
    properties.put("label", "한국어 😀");
    properties.put("items", new ArrayList<>(Arrays.asList(1, null, "x", Map.of("f", false))));

    assertThat(isValid(properties)).isTrue();
    assertThat(isValid(null)).isTrue();
  }

  private static boolean isValid(Map<String, Object> properties) {
    return validator.validate(new Probe(properties)).isEmpty();
  }
}
