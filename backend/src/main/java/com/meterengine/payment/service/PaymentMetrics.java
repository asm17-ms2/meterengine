package com.meterengine.payment.service;

import com.meterengine.payment.entity.PaymentAttempt;
import com.meterengine.payment.entity.PaymentReconciliationMismatch;
import com.meterengine.payment.entity.PaymentSetting;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class PaymentMetrics {

  public static final String UNKNOWN = "unknown";
  public static final String PAYMENT_STATUS_CHANGED = "PAYMENT_STATUS_CHANGED";
  public static final String OTHER_EVENT_TYPE = "other";
  public static final String APPLIED = "applied";
  public static final String IGNORED = "ignored";
  static final List<String> MODES = List.of(PaymentSetting.TEST, PaymentSetting.LIVE);

  private static final String ATTEMPT = "meterengine.payment.attempt";
  private static final String RETRY_EXHAUSTED = "meterengine.payment.retry.exhausted";
  private static final String CANCELLATION = "meterengine.payment.cancellation";
  private static final String STALE_PENDING = "meterengine.payment.stale.pending";
  private static final String STALE_PENDING_OLDEST_REQUESTED =
      "meterengine.payment.stale.pending.oldest.requested";
  private static final long NO_STALE_PENDING = 0;
  private static final String RECONCILIATION_MISMATCH =
      "meterengine.payment.reconciliation.mismatch";
  private static final String WEBHOOK = "meterengine.toss.payments.webhook";
  private static final String FIRST_ATTEMPT = "first";
  private static final String RETRY_ATTEMPT = "retry";
  private static final List<String> ATTEMPTS = List.of(FIRST_ATTEMPT, RETRY_ATTEMPT);
  private static final String NO_FAILURE_REASON = "none";
  private static final List<String> RESULTS =
      List.of(PaymentAttempt.DONE, PaymentAttempt.FAILED, UNKNOWN);
  private static final List<String> MISMATCH_KINDS =
      List.of(
          PaymentReconciliationMismatch.MISSING_IN_METERENGINE,
          PaymentReconciliationMismatch.MISSING_IN_TOSS_PAYMENTS,
          PaymentReconciliationMismatch.AMOUNT_MISMATCH,
          PaymentReconciliationMismatch.STATUS_MISMATCH);
  private static final List<String> WEBHOOK_EVENT_TYPES =
      List.of(PAYMENT_STATUS_CHANGED, OTHER_EVENT_TYPE);
  private static final List<String> WEBHOOK_OUTCOMES = List.of(APPLIED, IGNORED, UNKNOWN);

  private final Map<String, Counter> countersByKey = new HashMap<>();
  private final Map<String, AtomicLong> stalePendingByMode = new HashMap<>();
  // 나이 대신 요청 시각을 내야 확인 작업이 멈춰도 대시보드의 경과가 계속 늘어난다.
  private final Map<String, AtomicLong> stalePendingOldestRequestedByMode = new HashMap<>();

  public PaymentMetrics(MeterRegistry meterRegistry) {
    for (String mode : MODES) {
      for (String attempt : ATTEMPTS) {
        registerAttempt(meterRegistry, mode, attempt, PaymentAttempt.DONE, NO_FAILURE_REASON);
        registerAttempt(meterRegistry, mode, attempt, UNKNOWN, NO_FAILURE_REASON);
        for (PaymentFailureReason reason : PaymentFailureReason.values()) {
          registerAttempt(meterRegistry, mode, attempt, PaymentAttempt.FAILED, reason.tag());
        }
      }
      for (String result : RESULTS) {
        register(meterRegistry, CANCELLATION, "mode", mode, "result", result);
      }
      register(meterRegistry, RETRY_EXHAUSTED, "mode", mode);
      for (String kind : MISMATCH_KINDS) {
        register(meterRegistry, RECONCILIATION_MISMATCH, "mode", mode, "kind", kind);
      }
      registerStalePending(meterRegistry, mode);
    }
    for (String eventType : WEBHOOK_EVENT_TYPES) {
      for (String outcome : WEBHOOK_OUTCOMES) {
        register(meterRegistry, WEBHOOK, "event_type", eventType, "outcome", outcome);
      }
    }
  }

  public void countAttempt(String mode, long attemptCount, String result) {
    counter(ATTEMPT, mode, attemptOf(attemptCount), result, NO_FAILURE_REASON).increment();
  }

  public void countFailedAttempt(String mode, long attemptCount, String failureCode) {
    counter(
            ATTEMPT,
            mode,
            attemptOf(attemptCount),
            PaymentAttempt.FAILED,
            PaymentFailureReason.of(failureCode).tag())
        .increment();
  }

  public void countRetryExhausted(String mode) {
    counter(RETRY_EXHAUSTED, mode).increment();
  }

  public void countCancellation(String mode, String result) {
    counter(CANCELLATION, mode, result).increment();
  }

  public void setStalePending(String mode, long count) {
    gaugeValue(stalePendingByMode, STALE_PENDING, mode).set(count);
  }

  public void setStalePendingOldestRequestedAt(String mode, OffsetDateTime requestedAt) {
    gaugeValue(stalePendingOldestRequestedByMode, STALE_PENDING_OLDEST_REQUESTED, mode)
        .set(requestedAt.toEpochSecond());
  }

  public void clearStalePendingOldestRequestedAt(String mode) {
    gaugeValue(stalePendingOldestRequestedByMode, STALE_PENDING_OLDEST_REQUESTED, mode)
        .set(NO_STALE_PENDING);
  }

  public void countReconciliationMismatch(String mode, String kind) {
    counter(RECONCILIATION_MISMATCH, mode, kind).increment();
  }

  public void countWebhook(String eventType, String outcome) {
    String eventTypeTag =
        PAYMENT_STATUS_CHANGED.equals(eventType) ? PAYMENT_STATUS_CHANGED : OTHER_EVENT_TYPE;
    counter(WEBHOOK, eventTypeTag, outcome).increment();
  }

  private static String attemptOf(long attemptCount) {
    return attemptCount == 1 ? FIRST_ATTEMPT : RETRY_ATTEMPT;
  }

  private void registerAttempt(
      MeterRegistry meterRegistry, String mode, String attempt, String result, String reason) {
    register(
        meterRegistry,
        ATTEMPT,
        "mode",
        mode,
        "attempt",
        attempt,
        "result",
        result,
        "reason",
        reason);
  }

  private void registerStalePending(MeterRegistry meterRegistry, String mode) {
    AtomicLong count = new AtomicLong();
    Gauge.builder(STALE_PENDING, count, AtomicLong::get).tag("mode", mode).register(meterRegistry);
    stalePendingByMode.put(mode, count);
    AtomicLong oldestRequestedEpochSeconds = new AtomicLong();
    Gauge.builder(STALE_PENDING_OLDEST_REQUESTED, oldestRequestedEpochSeconds, AtomicLong::get)
        .tag("mode", mode)
        .baseUnit("seconds")
        .register(meterRegistry);
    stalePendingOldestRequestedByMode.put(mode, oldestRequestedEpochSeconds);
  }

  private void register(MeterRegistry meterRegistry, String name, String... tags) {
    String[] tagValues = new String[tags.length / 2];
    for (int i = 0; i < tagValues.length; i++) {
      tagValues[i] = tags[i * 2 + 1];
    }
    countersByKey.put(
        key(name, tagValues), Counter.builder(name).tags(tags).register(meterRegistry));
  }

  private Counter counter(String name, String... tagValues) {
    Counter counter = countersByKey.get(key(name, tagValues));
    if (counter == null) {
      throw new IllegalArgumentException(
          "unregistered tag values for " + name + ": " + String.join(", ", tagValues));
    }
    return counter;
  }

  private static AtomicLong gaugeValue(
      Map<String, AtomicLong> valuesByMode, String name, String mode) {
    AtomicLong value = valuesByMode.get(mode);
    if (value == null) {
      throw new IllegalArgumentException("unregistered mode for " + name + ": " + mode);
    }
    return value;
  }

  private static String key(String name, String... tagValues) {
    return name + "/" + String.join("/", tagValues);
  }
}
