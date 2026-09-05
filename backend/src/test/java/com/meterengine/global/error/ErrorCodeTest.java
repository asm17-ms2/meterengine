package com.meterengine.global.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class ErrorCodeTest {

  @Test
  void code는_상수_이름을_소문자로_내린_snake_case다() {
    for (ErrorCode errorCode : ErrorCode.values()) {
      assertThat(errorCode.getCode())
          .isEqualTo(errorCode.name().toLowerCase(Locale.ROOT))
          .matches("[a-z]+(_[a-z]+)*");
    }
  }

  @Test
  void 문구는_비어_있지_않다() {
    for (ErrorCode errorCode : ErrorCode.values()) {
      assertThat(errorCode.getMessage()).isNotBlank();
    }
  }
}
