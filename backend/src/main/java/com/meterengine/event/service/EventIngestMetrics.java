package com.meterengine.event.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class EventIngestMetrics {

  static final String STORAGE_DELAY_TIMER_NAME = "meterengine.event.storage.delay";
  static final String INGEST_COUNTER_NAME = "meterengine.event.ingest";
  static final String RESULT_TAG = "result";

  private static final String STORED = "stored";
  private static final String DUPLICATE = "duplicate";
  private static final Duration[] STORAGE_DELAY_OBJECTIVES = {
    Duration.ofSeconds(1), Duration.ofSeconds(10), Duration.ofSeconds(60)
  };

  private final Timer storageDelayTimer;
  private final Counter storedCounter;
  private final Counter duplicateCounter;

  EventIngestMetrics(MeterRegistry meterRegistry) {
    this.storageDelayTimer =
        Timer.builder(STORAGE_DELAY_TIMER_NAME)
            .description("이벤트가 발생한 시각부터 DB에 저장되기까지 걸린 시간")
            .serviceLevelObjectives(STORAGE_DELAY_OBJECTIVES)
            .publishPercentileHistogram()
            .register(meterRegistry);
    this.storedCounter = ingestCounter(meterRegistry, STORED);
    this.duplicateCounter = ingestCounter(meterRegistry, DUPLICATE);
  }

  private static Counter ingestCounter(MeterRegistry meterRegistry, String result) {
    return Counter.builder(INGEST_COUNTER_NAME)
        .description("수집 요청에 200으로 답한 건수. result가 duplicate면 이미 저장된 이벤트다")
        .tag(RESULT_TAG, result)
        .register(meterRegistry);
  }

  public void recordStored(Instant occurredAt, Instant storedAt) {
    storedCounter.increment();
    Duration storageDelay = Duration.between(occurredAt, storedAt);
    if (!storageDelay.isNegative()) {
      storageDelayTimer.record(storageDelay);
    }
  }

  public void recordDuplicate() {
    duplicateCounter.increment();
  }
}
