package com.meterengine.event.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EventIngestMetricsTest {

  private SimpleMeterRegistry meterRegistry;
  private EventIngestMetrics eventIngestMetrics;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    eventIngestMetrics = new EventIngestMetrics(meterRegistry);
  }

  @Test
  void 저장되면_stored_건수를_올린다() {
    eventIngestMetrics.recordStored();

    assertThat(ingestCounter("stored").count()).isEqualTo(1.0);
    assertThat(ingestCounter("duplicate").count()).isZero();
  }

  @Test
  void 중복이면_duplicate_건수를_올린다() {
    eventIngestMetrics.recordDuplicate();

    assertThat(ingestCounter("duplicate").count()).isEqualTo(1.0);
    assertThat(ingestCounter("stored").count()).isZero();
  }

  private Counter ingestCounter(String result) {
    return meterRegistry
        .get(EventIngestMetrics.INGEST_COUNTER_NAME)
        .tag(EventIngestMetrics.RESULT_TAG, result)
        .counter();
  }
}
