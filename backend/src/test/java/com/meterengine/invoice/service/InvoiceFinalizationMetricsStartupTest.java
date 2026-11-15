package com.meterengine.invoice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.meterengine.invoice.repository.InvoiceFinalizationAttemptRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

// 예약 작업을 돌지 않는 서버는 기동 뒤에 다시 세지 못한다. 기동 때 값이 굳으면 서버를 바꿀 때까지 경보가 풀리지 않는다.
class InvoiceFinalizationMetricsStartupTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(
              InvoiceFinalizationMetrics.class, InvoiceFinalizationMetricsStartup.class)
          .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
          .withBean(
              InvoiceFinalizationAttemptRepository.class,
              () -> mock(InvoiceFinalizationAttemptRepository.class));

  @Test
  void 역할을_주지_않으면_기동_때_세어_게이지를_낸다() {
    assertThat(unfinalizedGaugesAfterStartup()).isNotEmpty();
  }

  @Test
  void 역할이_all이면_기동_때_세어_게이지를_낸다() {
    assertThat(unfinalizedGaugesAfterStartup("meterengine.role=all")).isNotEmpty();
  }

  @Test
  void 역할이_api면_기동_때_세어_게이지를_낸다() {
    assertThat(unfinalizedGaugesAfterStartup("meterengine.role=api")).isNotEmpty();
  }

  @Test
  void 역할이_ingest면_게이지를_내지_않는다() {
    assertThat(unfinalizedGaugesAfterStartup("meterengine.role=ingest")).isEmpty();
  }

  @Test
  void 역할이_consumer면_게이지를_내지_않는다() {
    assertThat(unfinalizedGaugesAfterStartup("meterengine.role=consumer")).isEmpty();
  }

  private Collection<Gauge> unfinalizedGaugesAfterStartup(String... propertyValues) {
    List<Gauge> gauges = new ArrayList<>();
    contextRunner
        .withPropertyValues(propertyValues)
        .run(
            context -> {
              context.publishEvent(
                  new ApplicationReadyEvent(
                      new SpringApplication(), new String[0], context, Duration.ZERO));
              gauges.addAll(
                  context
                      .getBean(MeterRegistry.class)
                      .find(InvoiceFinalizationMetrics.UNFINALIZED_GAUGE_NAME)
                      .gauges());
            });
    return gauges;
  }
}
