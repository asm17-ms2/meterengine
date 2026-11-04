package com.meterengine.invoice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meterengine.invoice.repository.InvoiceFinalizationAttemptRepository;
import com.meterengine.invoice.repository.InvoiceFinalizationAttemptRepository.OpenCount;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;

@ExtendWith(MockitoExtension.class)
class InvoiceFinalizationMetricsTest {

  @Mock private InvoiceFinalizationAttemptRepository invoiceFinalizationAttemptRepository;

  private SimpleMeterRegistry meterRegistry;
  private InvoiceFinalizationMetrics invoiceFinalizationMetrics;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    invoiceFinalizationMetrics =
        new InvoiceFinalizationMetrics(meterRegistry, invoiceFinalizationAttemptRepository);
  }

  @Test
  void 게이지를_읽을_때는_DB를_조회하지_않고_센_적_없는_환경과_결과는_0이다() {
    when(invoiceFinalizationAttemptRepository.countOpen())
        .thenReturn(List.of(openCount("live", "failed", 2), openCount("test", "held", 3)));
    invoiceFinalizationMetrics.refresh();

    double liveFailed = unfinalized("live", "failed");
    double testHeld = unfinalized("test", "held");
    double legacyFailed = unfinalized("legacy", "failed");

    assertThat(liveFailed).isEqualTo(2);
    assertThat(testHeld).isEqualTo(3);
    assertThat(legacyFailed).isZero();
    verify(invoiceFinalizationAttemptRepository, times(1)).countOpen();
  }

  @Test
  void 세지_못하면_앞서_센_값을_그대로_낸다() {
    when(invoiceFinalizationAttemptRepository.countOpen())
        .thenReturn(List.of(openCount("legacy", "failed", 1)))
        .thenThrow(new QueryTimeoutException("timeout"));
    invoiceFinalizationMetrics.refresh();

    invoiceFinalizationMetrics.refresh();

    assertThat(unfinalized("legacy", "failed")).isEqualTo(1);
  }

  @Test
  void 처음_세기_전에는_게이지가_없다() {
    when(invoiceFinalizationAttemptRepository.countOpen())
        .thenThrow(new QueryTimeoutException("timeout"));

    invoiceFinalizationMetrics.refresh();

    assertThat(meterRegistry.find(InvoiceFinalizationMetrics.UNFINALIZED_GAUGE_NAME).gauge())
        .isNull();
  }

  private double unfinalized(String environment, String result) {
    return meterRegistry
        .get(InvoiceFinalizationMetrics.UNFINALIZED_GAUGE_NAME)
        .tag(InvoiceFinalizationMetrics.ENVIRONMENT_TAG, environment)
        .tag(InvoiceFinalizationMetrics.RESULT_TAG, result)
        .gauge()
        .value();
  }

  private static OpenCount openCount(String environment, String result, long count) {
    return new OpenCount() {
      @Override
      public String getEnvironment() {
        return environment;
      }

      @Override
      public String getResult() {
        return result;
      }

      @Override
      public long getCount() {
        return count;
      }
    };
  }
}
