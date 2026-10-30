package com.meterengine.global.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SecretMaskTest {
  @Test
  void 아홉_자_이상이면_가린_표시_뒤에_마지막_네_자만_보인다() {
    assertThat(SecretMask.mask("test_sk_123456789")).isEqualTo("****6789");
  }

  @Test
  void 여덟_자_이하면_마지막_네_자도_보이지_않는다() {
    assertThat(SecretMask.mask("12345678")).isEqualTo("****");
  }
}
