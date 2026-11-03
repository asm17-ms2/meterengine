package com.meterengine.event.service;

import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.security.BusinessEnvironmentContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class EventIngestMetrics {

  static final String INGEST_COUNTER_NAME = "meterengine.event.ingest";
  static final String ARRIVAL_DELAY_TIMER_NAME = "meterengine.event.arrival.delay";
  static final String RESULT_TAG = "result";
  static final String REASON_TAG = "reason";
  static final String ENVIRONMENT_TAG = "environment";

  private static final String STORED = "stored";
  private static final String DUPLICATE = "duplicate";
  private static final String REJECTED = "rejected";
  private static final String FAILED = "failed";
  private static final String NO_REASON = "none";
  private static final String OTHER_REASON = "other";
  // 웹훅 전달 지표의 environment 라벨과 같은 값을 쓴다.
  private static final String LEGACY_ENVIRONMENT = "legacy";
  private static final List<String> ENVIRONMENTS = List.of(LEGACY_ENVIRONMENT, "test", "live");
  private static final Set<ErrorCode> REJECTION_REASONS =
      EnumSet.of(
          ErrorCode.VALIDATION_ERROR,
          ErrorCode.MALFORMED_REQUEST_BODY,
          ErrorCode.EVENT_TOO_OLD,
          ErrorCode.CUSTOMER_NOT_FOUND,
          ErrorCode.CUSTOMER_ARCHIVED);
  // 바로 보내는 연동, 모아 보내는 연동, 35일 거절 기준에 다가간 이벤트를 가를 만큼만 경계를 둔다.
  private static final Duration[] ARRIVAL_DELAY_BOUNDARIES = {
    Duration.ofSeconds(1),
    Duration.ofSeconds(5),
    Duration.ofSeconds(30),
    Duration.ofMinutes(1),
    Duration.ofMinutes(5),
    Duration.ofMinutes(15),
    Duration.ofHours(1),
    Duration.ofHours(6),
    Duration.ofDays(1),
    Duration.ofDays(7),
    Duration.ofDays(30),
    EventIngestionService.MAX_EVENT_AGE
  };

  private final MeterRegistry meterRegistry;
  private final Map<IngestCounterKey, Counter> ingestCountersByKey = new ConcurrentHashMap<>();
  private final Map<String, Timer> arrivalDelayTimersByEnvironment = new ConcurrentHashMap<>();

  EventIngestMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    // 아직 받지 않은 조합도 0으로 내 두어야 첫 증가가 rate에 잡힌다.
    for (String environment : ENVIRONMENTS) {
      for (String result : List.of(STORED, DUPLICATE, FAILED)) {
        ingestCounter(result, NO_REASON, environment);
      }
      for (ErrorCode reason : REJECTION_REASONS) {
        ingestCounter(REJECTED, reason.getCode(), environment);
      }
      ingestCounter(REJECTED, OTHER_REASON, environment);
      arrivalDelayTimer(environment);
    }
  }

  public void recordStored(Duration arrivalDelay) {
    String environment = currentEnvironment();
    ingestCounter(STORED, NO_REASON, environment).increment();
    // 도입사 시계가 조금 빨라 지연이 음수면 바로 도착한 것으로 보고 0을 넣는다.
    Duration nonNegativeDelay = arrivalDelay.isNegative() ? Duration.ZERO : arrivalDelay;
    arrivalDelayTimer(environment).record(nonNegativeDelay);
  }

  public void recordDuplicate() {
    ingestCounter(DUPLICATE, NO_REASON, currentEnvironment()).increment();
  }

  public void recordRejected(ErrorCode errorCode) {
    String reason = REJECTION_REASONS.contains(errorCode) ? errorCode.getCode() : OTHER_REASON;
    ingestCounter(REJECTED, reason, currentEnvironment()).increment();
  }

  public void recordFailed(int eventCount) {
    ingestCounter(FAILED, NO_REASON, currentEnvironment()).increment(eventCount);
  }

  private Counter ingestCounter(String result, String reason, String environment) {
    return ingestCountersByKey.computeIfAbsent(
        new IngestCounterKey(result, reason, environment), this::registerIngestCounter);
  }

  private Counter registerIngestCounter(IngestCounterKey key) {
    return Counter.builder(INGEST_COUNTER_NAME)
        .description("이벤트 수집 결과 건수. result는 저장, 중복, 거절, 저장 실패이고 reason은 거절한 오류 code다")
        .tag(RESULT_TAG, key.result())
        .tag(REASON_TAG, key.reason())
        .tag(ENVIRONMENT_TAG, key.environment())
        .register(meterRegistry);
  }

  private Timer arrivalDelayTimer(String environment) {
    return arrivalDelayTimersByEnvironment.computeIfAbsent(
        environment, this::registerArrivalDelayTimer);
  }

  private Timer registerArrivalDelayTimer(String environment) {
    return Timer.builder(ARRIVAL_DELAY_TIMER_NAME)
        .description("저장한 이벤트의 발생 시각부터 받은 시각까지 걸린 시간")
        .serviceLevelObjectives(ARRIVAL_DELAY_BOUNDARIES)
        .tag(ENVIRONMENT_TAG, environment)
        .register(meterRegistry);
  }

  private static String currentEnvironment() {
    String mode = BusinessEnvironmentContext.mode();
    return mode == null ? LEGACY_ENVIRONMENT : mode;
  }

  private record IngestCounterKey(String result, String reason, String environment) {}
}
