package com.meterengine.invoice.service;

import com.meterengine.invoice.entity.InvoiceFinalizationAttempt;
import com.meterengine.invoice.repository.InvoiceFinalizationAttemptRepository;
import com.meterengine.invoice.repository.InvoiceFinalizationAttemptRepository.OpenCount;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// 수집 때 DB를 읽지 않도록 기동 때와 매 정시 실행 뒤에 센 값을 낸다. 정시 슬롯을 놓친 인스턴스는 한 시간 늦을 수 있다.
@Component
public class InvoiceFinalizationMetrics {

  static final String UNFINALIZED_GAUGE_NAME = "meterengine.invoice.unfinalized";
  static final String ENVIRONMENT_TAG = "environment";
  static final String RESULT_TAG = "result";

  private static final Logger logger = LoggerFactory.getLogger(InvoiceFinalizationMetrics.class);
  private static final List<String> ENVIRONMENTS = List.of("legacy", "test", "live");
  private static final List<String> RESULTS =
      List.of(InvoiceFinalizationAttempt.HELD, InvoiceFinalizationAttempt.FAILED);

  private final MeterRegistry meterRegistry;
  private final InvoiceFinalizationAttemptRepository invoiceFinalizationAttemptRepository;
  private final Map<String, AtomicLong> unfinalizedByEnvironmentAndResult =
      new ConcurrentHashMap<>();

  InvoiceFinalizationMetrics(
      MeterRegistry meterRegistry,
      InvoiceFinalizationAttemptRepository invoiceFinalizationAttemptRepository) {
    this.meterRegistry = meterRegistry;
    this.invoiceFinalizationAttemptRepository = invoiceFinalizationAttemptRepository;
  }

  // 세지 못하면 앞서 센 값을 그대로 둔다. 처음 센 뒤에 등록해 세기 전에는 0으로 보이지 않는다.
  @EventListener(ApplicationReadyEvent.class)
  public void refresh() {
    Map<String, Long> counts = new HashMap<>();
    try {
      for (OpenCount openCount : invoiceFinalizationAttemptRepository.countOpen()) {
        counts.put(key(openCount.getEnvironment(), openCount.getResult()), openCount.getCount());
      }
    } catch (RuntimeException exception) {
      logger.warn("unfinalized invoice count failed", exception);
      return;
    }
    for (String environment : ENVIRONMENTS) {
      for (String result : RESULTS) {
        unfinalizedOf(environment, result).set(counts.getOrDefault(key(environment, result), 0L));
      }
    }
  }

  private AtomicLong unfinalizedOf(String environment, String result) {
    return unfinalizedByEnvironmentAndResult.computeIfAbsent(
        key(environment, result),
        ignored -> {
          AtomicLong count = new AtomicLong();
          Gauge.builder(UNFINALIZED_GAUGE_NAME, count, AtomicLong::get)
              .description("자동 확정의 마지막 시도가 보류나 실패로 끝나 아직 확정되지 않은 (고객, 달)의 수")
              .tag(ENVIRONMENT_TAG, environment)
              .tag(RESULT_TAG, result)
              .register(meterRegistry);
          return count;
        });
  }

  private static String key(String environment, String result) {
    return environment + "/" + result;
  }
}
