package com.meterengine.global.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CryptoPropertiesTest {

  @Test
  void 문자열로_바꿔도_키를_드러내지_않는다() {
    String key = "c2VjcmV0LWtleS12YWx1ZS1mb3ItdG9zdHJpbmctdGU=";

    String text = new CryptoProperties(key).toString();

    assertThat(text).doesNotContain(key).doesNotContain(key.substring(0, 8));
  }
}
