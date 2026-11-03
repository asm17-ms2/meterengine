package com.meterengine.event.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.security.BusinessEnvironment;
import com.meterengine.global.security.BusinessEnvironmentContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EventIngestMetricsTest {

  private static final Duration ARRIVAL_DELAY = Duration.ofMinutes(3);

  private SimpleMeterRegistry meterRegistry;
  private EventIngestMetrics eventIngestMetrics;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    eventIngestMetrics = new EventIngestMetrics(meterRegistry);
  }

  @Test
  void 저장되면_stored_건수를_올린다() {
    eventIngestMetrics.recordStored(ARRIVAL_DELAY);

    assertThat(ingestCounter("stored", "none", "legacy").count()).isEqualTo(1.0);
    assertThat(ingestCounter("duplicate", "none", "legacy").count()).isZero();
  }

  @Test
  void 중복이면_duplicate_건수를_올린다() {
    eventIngestMetrics.recordDuplicate();

    assertThat(ingestCounter("duplicate", "none", "legacy").count()).isEqualTo(1.0);
    assertThat(ingestCounter("stored", "none", "legacy").count()).isZero();
  }

  @Test
  void 거절하면_오류_code를_reason으로_rejected_건수를_올린다() {
    eventIngestMetrics.recordRejected(ErrorCode.EVENT_TOO_OLD);

    assertThat(ingestCounter("rejected", "event_too_old", "legacy").count()).isEqualTo(1.0);
  }

  @Test
  void 수집이_내지_않는_오류_code의_거절은_reason을_other로_센다() {
    eventIngestMetrics.recordRejected(ErrorCode.PERMISSION_DENIED);

    assertThat(ingestCounter("rejected", "other", "legacy").count()).isEqualTo(1.0);
  }

  @Test
  void 저장에_실패하면_이벤트_수만큼_failed_건수를_올린다() {
    eventIngestMetrics.recordFailed(3);

    assertThat(ingestCounter("failed", "none", "legacy").count()).isEqualTo(3.0);
  }

  @Test
  void 업무_환경의_mode를_environment_라벨로_쓴다() {
    BusinessEnvironment live =
        new BusinessEnvironment(UUID.randomUUID(), UUID.randomUUID(), "live");

    BusinessEnvironmentContext.run(live, eventIngestMetrics::recordDuplicate);

    assertThat(ingestCounter("duplicate", "none", "live").count()).isEqualTo(1.0);
    assertThat(ingestCounter("duplicate", "none", "legacy").count()).isZero();
  }

  @Test
  void 받기_전에_모든_결과와_사유와_환경_조합을_0으로_낸다() {
    assertThat(ingestCounter("rejected", "customer_archived", "test").count()).isZero();
    assertThat(ingestCounter("failed", "none", "live").count()).isZero();
    assertThat(arrivalDelayTimer("test").count()).isZero();
  }

  @Test
  void 저장된_이벤트의_도착_지연을_분포로_기록한다() {
    eventIngestMetrics.recordStored(ARRIVAL_DELAY);

    Timer arrivalDelay = arrivalDelayTimer("legacy");
    assertThat(arrivalDelay.count()).isEqualTo(1);
    assertThat(arrivalDelay.totalTime(TimeUnit.SECONDS)).isEqualTo(180.0);
  }

  @Test
  void 발생_시각이_미래인_이벤트는_도착_지연_0으로_기록한다() {
    eventIngestMetrics.recordStored(Duration.ofSeconds(-5));

    Timer arrivalDelay = arrivalDelayTimer("legacy");
    assertThat(arrivalDelay.count()).isEqualTo(1);
    assertThat(arrivalDelay.totalTime(TimeUnit.SECONDS)).isZero();
  }

  @Test
  void 도착_지연_분포는_받는_기간_35일을_마지막_경계로_둔다() {
    double[] boundariesInSeconds =
        Arrays.stream(arrivalDelayTimer("legacy").takeSnapshot().histogramCounts())
            .mapToDouble(bucket -> bucket.bucket(TimeUnit.SECONDS))
            .toArray();

    assertThat(boundariesInSeconds)
        .endsWith((double) EventIngestionService.MAX_EVENT_AGE.toSeconds());
  }

  private Counter ingestCounter(String result, String reason, String environment) {
    return meterRegistry
        .get(EventIngestMetrics.INGEST_COUNTER_NAME)
        .tag(EventIngestMetrics.RESULT_TAG, result)
        .tag(EventIngestMetrics.REASON_TAG, reason)
        .tag(EventIngestMetrics.ENVIRONMENT_TAG, environment)
        .counter();
  }

  private Timer arrivalDelayTimer(String environment) {
    return meterRegistry
        .get(EventIngestMetrics.ARRIVAL_DELAY_TIMER_NAME)
        .tag(EventIngestMetrics.ENVIRONMENT_TAG, environment)
        .timer();
  }
}
