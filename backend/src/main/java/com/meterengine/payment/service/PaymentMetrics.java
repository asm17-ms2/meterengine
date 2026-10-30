package com.meterengine.payment.service;

import com.meterengine.payment.entity.PaymentAttempt;
import com.meterengine.payment.entity.PaymentReconciliationMismatch;
import com.meterengine.payment.entity.PaymentSetting;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
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

  private static final String ATTEMPT = "meterengine.payment.attempt";
  private static final String RETRY_EXHAUSTED = "meterengine.payment.retry.exhausted";
  private static final String CANCELLATION = "meterengine.payment.cancellation";
  private static final String STALE_PENDING = "meterengine.payment.stale.pending";
  private static final String RECONCILIATION_MISMATCH =
      "meterengine.payment.reconciliation.mismatch";
  private static final String WEBHOOK = "meterengine.toss.payments.webhook";
  private static final List<String> MODES = List.of(PaymentSetting.TEST, PaymentSetting.LIVE);
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
  private final AtomicLong stalePending = new AtomicLong();

  public PaymentMetrics(MeterRegistry meterRegistry) {
    for (String mode : MODES) {
      for (String result : RESULTS) {
        register(meterRegistry, ATTEMPT, "mode", mode, "result", result);
        register(meterRegistry, CANCELLATION, "mode", mode, "result", result);
      }
      register(meterRegistry, RETRY_EXHAUSTED, "mode", mode);
      for (String kind : MISMATCH_KINDS) {
        register(meterRegistry, RECONCILIATION_MISMATCH, "mode", mode, "kind", kind);
      }
    }
    for (String eventType : WEBHOOK_EVENT_TYPES) {
      for (String outcome : WEBHOOK_OUTCOMES) {
        register(meterRegistry, WEBHOOK, "event_type", eventType, "outcome", outcome);
      }
    }
    Gauge.builder(STALE_PENDING, stalePending, AtomicLong::get).register(meterRegistry);
  }

  public void countAttempt(String mode, String result) {
    counter(ATTEMPT, mode, result).increment();
  }

  public void countRetryExhausted(String mode) {
    counter(RETRY_EXHAUSTED, mode).increment();
  }

  public void countCancellation(String mode, String result) {
    counter(CANCELLATION, mode, result).increment();
  }

  public void setStalePending(long count) {
    stalePending.set(count);
  }

  public void countReconciliationMismatch(String mode, String kind) {
    counter(RECONCILIATION_MISMATCH, mode, kind).increment();
  }

  public void countWebhook(String eventType, String outcome) {
    String eventTypeTag =
        PAYMENT_STATUS_CHANGED.equals(eventType) ? PAYMENT_STATUS_CHANGED : OTHER_EVENT_TYPE;
    counter(WEBHOOK, eventTypeTag, outcome).increment();
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

  private static String key(String name, String... tagValues) {
    return name + "/" + String.join("/", tagValues);
  }
}
