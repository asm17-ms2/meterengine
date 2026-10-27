package com.meterengine.global.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Import;
import org.springframework.mock.env.MockEnvironment;

class EncryptedStringConverterTest {

  private static final List<String> DEVELOPMENT_KEYS =
      List.of(CryptoProperties.LOCAL_KEY, CryptoProperties.TEST_KEY);
  private static final int IV_BYTES = 12;

  private final EncryptedStringConverter converter = converter(randomKey());

  @Test
  void 암호문은_v1_접두사로_시작하고_평문을_담지_않는다() {
    String stored = converter.convertToDatabaseColumn("test_sk_D0ECG1V1AzNAC91L");

    assertThat(stored).startsWith("v1:").doesNotContain("D0ECG1V1AzNAC91L");
  }

  @Test
  void 읽으면_평문으로_돌아온다() {
    String stored = converter.convertToDatabaseColumn("bk-plain");

    assertThat(converter.convertToEntityAttribute(stored)).isEqualTo("bk-plain");
  }

  @Test
  void 같은_평문도_매번_다른_암호문이_된다() {
    assertThat(converter.convertToDatabaseColumn("bk"))
        .isNotEqualTo(converter.convertToDatabaseColumn("bk"));
  }

  @Test
  void null은_null로_둔다() {
    assertThat(converter.convertToDatabaseColumn(null)).isNull();
    assertThat(converter.convertToEntityAttribute(null)).isNull();
  }

  @Test
  void 옛_빌링키_암호문을_읽는다() {
    EncryptedStringConverter zeroKeyConverter = converter(CryptoProperties.LOCAL_KEY, "test");
    String storedWithZeroKey = "v1:5rCE9gV5G0TxpFwUfjZ1rP7QEAUwptJENUT3ZCZLgN5hyGrYEsYJbinigA==";

    assertThat(zeroKeyConverter.convertToEntityAttribute(storedWithZeroKey))
        .isEqualTo("bk-known-answer");
  }

  @Test
  void 다른_키로는_읽을_수_없다() {
    String stored = converter.convertToDatabaseColumn("bk");
    EncryptedStringConverter other = converter(randomKey());

    assertThatThrownBy(() -> other.convertToEntityAttribute(stored))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageNotContaining("bk");
  }

  @Test
  void 접두사가_없으면_읽지_않는다() {
    String stored = converter.convertToDatabaseColumn("bk");

    assertThatThrownBy(() -> converter.convertToEntityAttribute(stored.substring("v1:".length())))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void 태그가_한_바이트라도_바뀌면_읽지_않는다() {
    String stored = converter.convertToDatabaseColumn("bk");
    String tampered = flipByte(stored, decodedLength(stored) - 1);

    assertThatThrownBy(() -> converter.convertToEntityAttribute(tampered))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void IV가_한_바이트라도_바뀌면_읽지_않는다() {
    String stored = converter.convertToDatabaseColumn("bk");
    String tampered = flipByte(stored, 0);

    assertThatThrownBy(() -> converter.convertToEntityAttribute(tampered))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void 암호문_본문이_한_바이트라도_바뀌면_읽지_않는다() {
    String stored = converter.convertToDatabaseColumn("bk");
    String tampered = flipByte(stored, IV_BYTES);

    assertThatThrownBy(() -> converter.convertToEntityAttribute(tampered))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void 키가_32바이트가_아니면_만들_수_없다() {
    String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

    assertThatThrownBy(() -> converter(shortKey))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageNotContaining(shortKey);
  }

  @Test
  void 키가_base64가_아니면_만들_수_없다() {
    String notBase64 = "not-base64-key!";

    assertThatThrownBy(() -> converter(notBase64))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageNotContaining(notBase64);
  }

  @Test
  void 개발용_고정_키는_local이나_test_프로파일이_없으면_받지_않는다() {
    for (String developmentKey : DEVELOPMENT_KEYS) {
      assertThatThrownBy(() -> converter(developmentKey)).isInstanceOf(IllegalStateException.class);
      assertThatThrownBy(() -> converter(developmentKey, "prod"))
          .isInstanceOf(IllegalStateException.class);
    }
  }

  @Test
  void 개발용_고정_키는_local이나_test_프로파일이_있으면_받는다() {
    for (String developmentKey : DEVELOPMENT_KEYS) {
      assertThat(converter(developmentKey, "local").convertToDatabaseColumn("bk"))
          .startsWith("v1:");
      assertThat(converter(developmentKey, "test").convertToDatabaseColumn("bk")).startsWith("v1:");
    }
  }

  @Test
  void 무작위_키는_프로파일_없이_받는다() {
    assertThat(converter(randomKey()).convertToDatabaseColumn("bk")).startsWith("v1:");
  }

  @Test
  void 암호화_키_설정이_없으면_기동하지_않는다() {
    new ApplicationContextRunner()
        .withUserConfiguration(CryptoTestConfig.class)
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .isInstanceOf(BindValidationException.class));
  }

  @Test
  void 프로파일_없이_개발용_고정_키를_설정하면_기동하지_않는다() {
    new ApplicationContextRunner()
        .withUserConfiguration(CryptoTestConfig.class)
        .withPropertyValues("crypto.encryption-key=" + CryptoProperties.LOCAL_KEY)
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .isInstanceOf(IllegalStateException.class));
  }

  @Test
  void 암호화_키_설정이_있으면_변환기가_만들어진다() {
    new ApplicationContextRunner()
        .withUserConfiguration(CryptoTestConfig.class)
        .withPropertyValues("crypto.encryption-key=" + randomKey())
        .run(context -> assertThat(context).hasSingleBean(EncryptedStringConverter.class));
  }

  private static EncryptedStringConverter converter(String key, String... activeProfiles) {
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles(activeProfiles);
    return new EncryptedStringConverter(new CryptoProperties(key), environment);
  }

  private static int decodedLength(String stored) {
    return Base64.getDecoder().decode(stored.substring("v1:".length())).length;
  }

  private static String flipByte(String stored, int index) {
    byte[] decoded = Base64.getDecoder().decode(stored.substring("v1:".length()));
    decoded[index] ^= 1;
    return "v1:" + Base64.getEncoder().encodeToString(decoded);
  }

  private static String randomKey() {
    byte[] key = new byte[32];
    new SecureRandom().nextBytes(key);
    return Base64.getEncoder().encodeToString(key);
  }

  @EnableConfigurationProperties(CryptoProperties.class)
  @Import(EncryptedStringConverter.class)
  static class CryptoTestConfig {}
}
