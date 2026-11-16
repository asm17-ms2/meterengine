package com.meterengine.event.service;

import com.meterengine.event.dto.EventDeadLetterReason;
import com.meterengine.global.error.ErrorCode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Component;

// 컨슈머가 메시지를 어떻게 끝냈는지 센다. 수집 서버의 accepted 건수와 견주면 중간에 사라진 것이 있는지 보인다.
@Component
public class EventConsumeMetrics {

  static final String CONSUME_COUNTER_NAME = "meterengine.event.consume";
  static final String STORAGE_DELAY_TIMER_NAME = "meterengine.event.storage.delay";
  static final String THROTTLE_TIMER_NAME = "meterengine.event.consume.throttle";
  static final String RESULT_TAG = "result";
  static final String REASON_TAG = "reason";
  static final String STORED = "stored";
  static final String DUPLICATE = "duplicate";
  static final String REJECTED = "rejected";
  static final String DEAD_LETTER = "dead_letter";
  static final String NO_REASON = "none";

  private static final List<ErrorCode> REJECTION_REASONS =
      List.of(ErrorCode.CUSTOMER_NOT_FOUND, ErrorCode.CUSTOMER_ARCHIVED);
  // 평소(1초 안), 반영 목표(60초), 장애 뒤 밀린 것을 비우는 목표(10분)를 가를 만큼만 경계를 둔다.
  private static final Duration[] STORAGE_DELAY_BOUNDARIES = {
    Duration.ofMillis(100),
    Duration.ofMillis(500),
    Duration.ofSeconds(1),
    Duration.ofSeconds(5),
    Duration.ofSeconds(15),
    Duration.ofSeconds(30),
    Duration.ofMinutes(1),
    Duration.ofMinutes(2),
    Duration.ofMinutes(5),
    Duration.ofMinutes(10),
    Duration.ofMinutes(30),
    Duration.ofHours(1)
  };

  private final MeterRegistry meterRegistry;
  private final Timer storageDelay;
  private final Timer throttle;

  EventConsumeMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    this.storageDelay =
        Timer.builder(STORAGE_DELAY_TIMER_NAME)
            .description("저장한 이벤트의 수집 서버가 받은 시각부터 DB에 들어간 시각까지 걸린 시간")
            .serviceLevelObjectives(STORAGE_DELAY_BOUNDARIES)
            .register(meterRegistry);
    this.throttle =
        Timer.builder(THROTTLE_TIMER_NAME)
            .description("컨슈머가 초당 저장 건수의 상한을 지키려고 묶음을 넣기 전에 기다린 시간")
            .register(meterRegistry);
    // 아직 세지 않은 조합도 0으로 내 두어야 첫 증가가 rate에 잡힌다.
    counter(STORED, NO_REASON);
    counter(DUPLICATE, NO_REASON);
    REJECTION_REASONS.forEach(reason -> counter(REJECTED, reason.getCode()));
    for (EventDeadLetterReason reason : EventDeadLetterReason.values()) {
      counter(DEAD_LETTER, reason.code());
    }
  }

  public void recordStored(Duration storageDelay) {
    counter(STORED, NO_REASON).increment();
    // 수집 서버와 컨슈머 서버의 시계가 조금 어긋나 음수가 나오면 바로 저장한 것으로 본다.
    this.storageDelay.record(storageDelay.isNegative() ? Duration.ZERO : storageDelay);
  }

  public void recordThrottled(Duration wait) {
    throttle.record(wait);
  }

  public void recordDuplicate(int eventCount) {
    counter(DUPLICATE, NO_REASON).increment(eventCount);
  }

  public void recordRejected(ErrorCode errorCode) {
    counter(REJECTED, errorCode.getCode()).increment();
  }

  public void recordDeadLetter(EventDeadLetterReason reason) {
    counter(DEAD_LETTER, reason.code()).increment();
  }

  private Counter counter(String result, String reason) {
    return Counter.builder(CONSUME_COUNTER_NAME)
        .description("컨슈머가 끝낸 메시지 건수. result는 저장, 중복, 거절, DLQ이고 reason은 거절이나 DLQ의 이유다")
        .tag(RESULT_TAG, result)
        .tag(REASON_TAG, reason)
        .register(meterRegistry);
  }
}
