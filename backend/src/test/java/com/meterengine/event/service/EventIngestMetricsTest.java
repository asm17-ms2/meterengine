package com.meterengine.event.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EventIngestMetricsTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-10-14T10:00:00Z");

  private SimpleMeterRegistry meterRegistry;
  private EventIngestMetrics eventIngestMetrics;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    eventIngestMetrics = new EventIngestMetrics(meterRegistry);
  }

  @Test
  void 저장되면_발생_시각부터_저장까지_걸린_시간을_기록한다() {
    eventIngestMetrics.recordStored(OCCURRED_AT, OCCURRED_AT.plusSeconds(5));

    Timer timer = storageDelayTimer();
    assertThat(timer.count()).isEqualTo(1);
    assertThat(timer.totalTime(TimeUnit.SECONDS)).isEqualTo(5.0);
  }

  @Test
  void 저장_지연_히스토그램은_1초_10초_60초_칸을_가진다() {
    eventIngestMetrics.recordStored(OCCURRED_AT, OCCURRED_AT.plusSeconds(5));

    assertThat(countAtBucket(1)).isZero();
    assertThat(countAtBucket(10)).isEqualTo(1.0);
    assertThat(countAtBucket(60)).isEqualTo(1.0);
  }

  @Test
  void 저장되면_stored_건수를_올린다() {
    eventIngestMetrics.recordStored(OCCURRED_AT, OCCURRED_AT.plusSeconds(1));

    assertThat(ingestCounter("stored").count()).isEqualTo(1.0);
    assertThat(ingestCounter("duplicate").count()).isZero();
  }

  @Test
  void 중복이면_duplicate_건수만_올리고_지연은_기록하지_않는다() {
    eventIngestMetrics.recordDuplicate();

    assertThat(ingestCounter("duplicate").count()).isEqualTo(1.0);
    assertThat(ingestCounter("stored").count()).isZero();
    assertThat(storageDelayTimer().count()).isZero();
  }

  @Test
  void 발생_시각이_저장_시각보다_뒤면_지연을_기록하지_않고_건수만_센다() {
    eventIngestMetrics.recordStored(OCCURRED_AT, OCCURRED_AT.minusSeconds(30));

    assertThat(storageDelayTimer().count()).isZero();
    assertThat(ingestCounter("stored").count()).isEqualTo(1.0);
  }

  private Timer storageDelayTimer() {
    return meterRegistry.get(EventIngestMetrics.STORAGE_DELAY_TIMER_NAME).timer();
  }

  private Counter ingestCounter(String result) {
    return meterRegistry
        .get(EventIngestMetrics.INGEST_COUNTER_NAME)
        .tag(EventIngestMetrics.RESULT_TAG, result)
        .counter();
  }

  private double countAtBucket(long seconds) {
    return Arrays.stream(storageDelayTimer().takeSnapshot().histogramCounts())
        .filter(bucket -> bucket.bucket(TimeUnit.SECONDS) == seconds)
        .mapToDouble(CountAtBucket::count)
        .findFirst()
        .orElseThrow();
  }
}
