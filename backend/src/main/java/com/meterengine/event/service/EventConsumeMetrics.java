package com.meterengine.event.service;

import com.meterengine.event.dto.EventDeadLetterReason;
import com.meterengine.global.error.ErrorCode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.springframework.stereotype.Component;

// 컨슈머가 메시지를 어떻게 끝냈는지 센다. 수집 서버의 accepted 건수와 견주면 중간에 사라진 것이 있는지 보인다.
@Component
public class EventConsumeMetrics {

  static final String CONSUME_COUNTER_NAME = "meterengine.event.consume";
  static final String RESULT_TAG = "result";
  static final String REASON_TAG = "reason";
  static final String STORED = "stored";
  static final String DUPLICATE = "duplicate";
  static final String REJECTED = "rejected";
  static final String DEAD_LETTER = "dead_letter";
  static final String NO_REASON = "none";

  private static final List<ErrorCode> REJECTION_REASONS =
      List.of(ErrorCode.CUSTOMER_NOT_FOUND, ErrorCode.CUSTOMER_ARCHIVED);

  private final MeterRegistry meterRegistry;

  EventConsumeMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    // 아직 세지 않은 조합도 0으로 내 두어야 첫 증가가 rate에 잡힌다.
    counter(STORED, NO_REASON);
    counter(DUPLICATE, NO_REASON);
    REJECTION_REASONS.forEach(reason -> counter(REJECTED, reason.getCode()));
    for (EventDeadLetterReason reason : EventDeadLetterReason.values()) {
      counter(DEAD_LETTER, reason.code());
    }
  }

  public void recordStored(int eventCount) {
    counter(STORED, NO_REASON).increment(eventCount);
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
