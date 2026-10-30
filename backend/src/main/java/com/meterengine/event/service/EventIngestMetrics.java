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
  private final Counter rejectedCounter;

  EventIngestMetrics(MeterRegistry meterRegistry) {
    this.storedCounter = ingestCounter(meterRegistry, STORED);
    this.duplicateCounter = ingestCounter(meterRegistry, DUPLICATE);
    this.rejectedCounter = ingestCounter(meterRegistry, "rejected");
  }

  private static Counter ingestCounter(MeterRegistry meterRegistry, String result) {
    return Counter.builder(INGEST_COUNTER_NAME)
        .description("이벤트 수집 결과 건수. result로 신규 저장, 중복, 거절을 구분한다")
        .tag(RESULT_TAG, result)
        .register(meterRegistry);
  }

  public void recordStored() {
    storedCounter.increment();
  }

  public void recordDuplicate() {
    duplicateCounter.increment();
  }

  public void recordRejected() {
    rejectedCounter.increment();
  }
}
