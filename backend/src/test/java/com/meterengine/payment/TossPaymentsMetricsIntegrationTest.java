package com.meterengine.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.TestcontainersConfiguration;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.autoconfigure.PropertiesMeterFilter;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class TossPaymentsMetricsIntegrationTest {

  private static final Duration TOSS_READ_TIMEOUT = Duration.ofSeconds(60);

  @Autowired private PropertiesMeterFilter propertiesMeterFilter;

  // 테스트 레지스트리는 히스토그램 구간을 내지 않아 운영 설정이 타이머에 붙이는 분포 설정을 직접 본다.
  @Test
  void 토스_호출_타이머는_읽기_제한_60초를_넘는_구간까지_히스토그램을_낸다() {
    Meter.Id timer =
        new Meter.Id(
            "meterengine.toss.payments.request",
            Tags.of("operation", "approve_billing", "outcome", "success"),
            null,
            null,
            Meter.Type.TIMER);

    DistributionStatisticConfig config =
        propertiesMeterFilter.configure(timer, DistributionStatisticConfig.NONE);

    assertThat(config.isPercentileHistogram()).isTrue();
    assertThat(config.getMaximumExpectedValueAsDouble())
        .isGreaterThan((double) TOSS_READ_TIMEOUT.toNanos());
  }
}
