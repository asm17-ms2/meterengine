package com.meterengine.global.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.mock.env.MockEnvironment;

class CryptoPropertiesTest {

  @Test
  void 문자열로_바꿔도_키를_드러내지_않는다() {
    String key = "c2VjcmV0LWtleS12YWx1ZS1mb3ItdG9zdHJpbmctdGU=";

    String text = new CryptoProperties(key).toString();

    assertThat(text).doesNotContain(key).doesNotContain(key.substring(0, 8));
  }

  @Test
  void local_프로파일_설정만으로_암호화_키가_바인딩되고_사설망_수신이_허용된다() throws Exception {
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles("local");
    PropertiesLoaderUtils.loadProperties(new ClassPathResource("application-local.properties"))
        .forEach((key, value) -> environment.setProperty((String) key, (String) value));

    CryptoProperties properties =
        Binder.get(environment).bind("crypto", CryptoProperties.class).get();

    assertThat(
            new EncryptedStringConverter(properties, environment).convertToDatabaseColumn("secret"))
        .startsWith("v1:");
    assertThat(environment.getProperty("meterengine.webhook.allow-private-network", Boolean.class))
        .isTrue();
  }
}
