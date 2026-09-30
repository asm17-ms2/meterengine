package com.meterengine.payment.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meterengine.payment.config.PaymentProperties;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class BillingKeyConverterTest {

  private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

  private final BillingKeyConverter converter = new BillingKeyConverter(new PaymentProperties(KEY));

  @Test
  void 암호문은_버전_접두사로_시작하고_평문을_담지_않는다() {
    String stored = converter.convertToDatabaseColumn("D0ECG1V1AzNAC91L_7oeRLSG+/=");

    assertThat(stored).startsWith("v1:").doesNotContain("D0ECG1V1AzNAC91L");
  }

  @Test
  void 읽으면_평문으로_돌아온다() {
    String stored = converter.convertToDatabaseColumn("bk-plain");

    assertThat(converter.convertToEntityAttribute(stored)).isEqualTo("bk-plain");
  }

  @Test
  void 이전에_저장한_암호문을_읽는다() {
    String storedWithZeroKey = "v1:5rCE9gV5G0TxpFwUfjZ1rP7QEAUwptJENUT3ZCZLgN5hyGrYEsYJbinigA==";

    assertThat(converter.convertToEntityAttribute(storedWithZeroKey)).isEqualTo("bk-known-answer");
  }

  @Test
  void 같은_값도_매번_다른_암호문이_된다() {
    assertThat(converter.convertToDatabaseColumn("bk"))
        .isNotEqualTo(converter.convertToDatabaseColumn("bk"));
  }

  @Test
  void 다른_키로는_읽을_수_없다() {
    String stored = converter.convertToDatabaseColumn("bk");
    byte[] otherKey = new byte[32];
    otherKey[0] = 1;
    BillingKeyConverter other =
        new BillingKeyConverter(
            new PaymentProperties(Base64.getEncoder().encodeToString(otherKey)));

    assertThatThrownBy(() -> other.convertToEntityAttribute(stored))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void 키가_32바이트가_아니면_만들_수_없다() {
    String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

    assertThatThrownBy(() -> new BillingKeyConverter(new PaymentProperties(shortKey)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void 버전_접두사가_없으면_읽지_않는다() {
    assertThatThrownBy(() -> converter.convertToEntityAttribute("plain"))
        .isInstanceOf(IllegalStateException.class);
  }
}
