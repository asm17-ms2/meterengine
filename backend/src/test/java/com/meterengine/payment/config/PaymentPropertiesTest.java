package com.meterengine.payment.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PaymentPropertiesTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(PaymentPropertiesTestConfig.class);

  @Test
  void 기본값으로_기동하고_없음_실패_지연은_10분이다() {
    contextRunner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context.getBean(PaymentProperties.class).notFoundFailureDelay())
              .isEqualTo(Duration.ofMinutes(10));
        });
  }

  @Test
  void 없음_실패_지연이_확정_지연보다_짧으면_기동하지_않는다() {
    contextRunner
        .withPropertyValues(
            "payment.confirmation-delay=PT5M", "payment.not-found-failure-delay=PT3M")
        .run(this::assertRejected);
  }

  @Test
  void 없음_실패_지연이_2분보다_짧으면_확정_지연보다_길어도_기동하지_않는다() {
    contextRunner
        .withPropertyValues(
            "payment.confirmation-delay=PT30S", "payment.not-found-failure-delay=PT1M59S")
        .run(this::assertRejected);
  }

  @Test
  void 없음_실패_지연이_2분이고_확정_지연과_같으면_기동한다() {
    contextRunner
        .withPropertyValues(
            "payment.confirmation-delay=PT2M", "payment.not-found-failure-delay=PT2M")
        .run(context -> assertThat(context).hasNotFailed());
  }

  private void assertRejected(AssertableApplicationContext context) {
    assertThat(context)
        .getFailure()
        .rootCause()
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("payment.not-found-failure-delay");
  }

  @EnableConfigurationProperties(PaymentProperties.class)
  static class PaymentPropertiesTestConfig {}
}
