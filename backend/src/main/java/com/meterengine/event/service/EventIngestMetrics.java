package com.meterengine.event.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class EventIngestMetrics {

  static final String INGEST_COUNTER_NAME = "meterengine.event.ingest";
  static final String RESULT_TAG = "result";

  private static final String STORED = "stored";
  private static final String DUPLICATE = "duplicate";

  private final Counter storedCounter;
  private final Counter duplicateCounter;

  EventIngestMetrics(MeterRegistry meterRegistry) {
    this.storedCounter = ingestCounter(meterRegistry, STORED);
    this.duplicateCounter = ingestCounter(meterRegistry, DUPLICATE);
  }

  private static Counter ingestCounter(MeterRegistry meterRegistry, String result) {
    return Counter.builder(INGEST_COUNTER_NAME)
        .description("수집 요청에 200으로 답한 건수. result가 duplicate면 이미 저장된 이벤트다")
        .tag(RESULT_TAG, result)
        .register(meterRegistry);
  }

  public void recordStored() {
    storedCounter.increment();
  }

  public void recordDuplicate() {
    duplicateCounter.increment();
  }
}
