package com.meterengine.webhook.service;

import com.meterengine.webhook.dto.WebhookBacklog;
import com.meterengine.webhook.dto.WebhookOutcome;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class WebhookDeliveryMetrics {

  static final String DELIVERY_COUNTER_NAME = "meterengine.webhook.delivery";
  static final String RESPONSE_TIMER_NAME = "meterengine.webhook.receiver.response";
  static final String LATENCY_TIMER_NAME = "meterengine.webhook.delivery.latency";
  static final String BACKLOG_GAUGE_NAME = "meterengine.webhook.delivery.backlog";
  static final String BACKLOG_AGE_GAUGE_NAME = "meterengine.webhook.delivery.backlog.age";
  static final String RETRY_WAITING_GAUGE_NAME = "meterengine.webhook.delivery.retry.waiting";
  static final String PENDING_OLDEST_AGE_GAUGE_NAME =
      "meterengine.webhook.delivery.pending.oldest.age";

  private static final String ENVIRONMENT_TAG = "environment";
  private static final List<String> ENVIRONMENTS = List.of("legacy", "test", "live");
  private static final String NONE = "none";
  private static final String UNEXPECTED_STATUS = "unexpected_status";
  private static final WebhookBacklog NO_BACKLOG = new WebhookBacklog(0, 0, 0, 0);

  // 수신 주소에 요청을 보내기 전에 끝난 사유라 걸린 시간이 수신 측 응답 시간이 아니다.
  private static final Set<String> NOT_SENT_REASONS =
      Set.of("endpoint_disabled", "secret_unavailable", "blocked_address");

  // 전달 소요 시간은 재시도 간격만큼 길어지므로 히스토그램 범위를 기본값(30초까지)보다 넓게 잡는다.
  private static final Duration LATENCY_MINIMUM = Duration.ofSeconds(1);
  private static final Duration LATENCY_MAXIMUM = Duration.ofDays(1);

  private final MeterRegistry meterRegistry;
  private final Map<String, BacklogGauges> backlogGaugesByEnvironment = new HashMap<>();

  WebhookDeliveryMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    for (String environment : ENVIRONMENTS) {
      backlogGaugesByEnvironment.put(environment, registerBacklogGauges(environment));
      registerDeliveredCounters(environment);
    }
  }

  public void countAttempt(
      String result, String environment, boolean firstAttempt, WebhookOutcome outcome) {
    String reason = outcome.failureReason() == null ? NONE : outcome.failureReason();
    deliveryCounter(
            result, reason, environment, Boolean.toString(firstAttempt), statusGroupOf(outcome))
        .increment();
  }

  // 선점 만료는 시도 상한에 닿은 마지막 시도에서만 생긴다.
  public void countExpiredLeases(String environment, int count) {
    deliveryCounter("failed", "lease_expired", environment, Boolean.toString(false), NONE)
        .increment(count);
  }

  public void recordResponse(String environment, WebhookOutcome outcome) {
    if (outcome.failureReason() != null && NOT_SENT_REASONS.contains(outcome.failureReason())) {
      return;
    }
    Timer.builder(RESPONSE_TIMER_NAME)
        .description("웹훅 수신 주소가 답하기까지 걸린 시간. 시간 초과와 연결 실패도 든다")
        .publishPercentileHistogram()
        .tag(ENVIRONMENT_TAG, environment)
        .register(meterRegistry)
        .record(Duration.ofMillis(outcome.durationMs()));
  }

  public void recordDelivered(String environment, Duration latency) {
    Timer.builder(LATENCY_TIMER_NAME)
        .description("웹훅 이벤트가 생긴 때부터 전달에 성공하기까지 걸린 시간")
        .publishPercentileHistogram()
        .minimumExpectedValue(LATENCY_MINIMUM)
        .maximumExpectedValue(LATENCY_MAXIMUM)
        .tag(ENVIRONMENT_TAG, environment)
        .register(meterRegistry)
        .record(latency);
  }

  public void updateBacklog(Map<String, WebhookBacklog> backlogByEnvironment) {
    backlogGaugesByEnvironment.forEach(
        (environment, gauges) ->
            gauges.update(backlogByEnvironment.getOrDefault(environment, NO_BACKLOG)));
  }

  private Counter deliveryCounter(
      String result, String reason, String environment, String firstAttempt, String statusGroup) {
    return Counter.builder(DELIVERY_COUNTER_NAME)
        .tag("result", result)
        .tag("reason", reason)
        .tag(ENVIRONMENT_TAG, environment)
        .tag("first_attempt", firstAttempt)
        .tag("status_group", statusGroup)
        .register(meterRegistry);
  }

  // DB의 실패 사유는 그대로 두고, 응답 코드로 실패한 경우만 지표에서 코드 묶음으로 나눠 본다.
  private static String statusGroupOf(WebhookOutcome outcome) {
    if (!UNEXPECTED_STATUS.equals(outcome.failureReason()) || outcome.responseStatus() == null) {
      return NONE;
    }
    int status = outcome.responseStatus();
    if (status == HttpStatus.GONE.value() || status == HttpStatus.TOO_MANY_REQUESTS.value()) {
      return Integer.toString(status);
    }
    return switch (status / 100) {
      case 3 -> "3xx";
      case 4 -> "4xx";
      case 5 -> "5xx";
      default -> "other";
    };
  }

  // 성공 시계열이 첫 성공 전에 없으면 실패만 있는 구간의 성공률이 0%가 아니라 빈칸으로 보인다.
  private void registerDeliveredCounters(String environment) {
    for (boolean firstAttempt : new boolean[] {true, false}) {
      deliveryCounter("done", NONE, environment, Boolean.toString(firstAttempt), NONE);
    }
  }

  private BacklogGauges registerBacklogGauges(String environment) {
    BacklogGauges gauges =
        new BacklogGauges(new AtomicLong(), new AtomicLong(), new AtomicLong(), new AtomicLong());
    Gauge.builder(BACKLOG_GAUGE_NAME, gauges.dueCount(), AtomicLong::get)
        .description("보낼 때가 지났는데 아직 보내지 않은 웹훅 전송 수")
        .tag(ENVIRONMENT_TAG, environment)
        .register(meterRegistry);
    Gauge.builder(BACKLOG_AGE_GAUGE_NAME, gauges.oldestDueAgeSeconds(), AtomicLong::get)
        .description("보낼 때가 지난 웹훅 전송이 다음 시도 예정 시각부터 가장 오래 밀린 시간")
        .baseUnit("seconds")
        .tag(ENVIRONMENT_TAG, environment)
        .register(meterRegistry);
    Gauge.builder(RETRY_WAITING_GAUGE_NAME, gauges.waitingRetryCount(), AtomicLong::get)
        .description("다음 시도 예정 시각을 기다리는 웹훅 재시도 수")
        .tag(ENVIRONMENT_TAG, environment)
        .register(meterRegistry);
    Gauge.builder(PENDING_OLDEST_AGE_GAUGE_NAME, gauges.oldestPendingAgeSeconds(), AtomicLong::get)
        .description("아직 전달하지 못한 웹훅 전송 가운데 가장 먼저 생긴 것이 생긴 뒤 지난 시간. 재시도 대기도 든다")
        .baseUnit("seconds")
        .tag(ENVIRONMENT_TAG, environment)
        .register(meterRegistry);
    return gauges;
  }

  private record BacklogGauges(
      AtomicLong dueCount,
      AtomicLong oldestDueAgeSeconds,
      AtomicLong waitingRetryCount,
      AtomicLong oldestPendingAgeSeconds) {
    void update(WebhookBacklog backlog) {
      dueCount.set(backlog.dueCount());
      oldestDueAgeSeconds.set(backlog.oldestDueAgeSeconds());
      waitingRetryCount.set(backlog.waitingRetryCount());
      oldestPendingAgeSeconds.set(backlog.oldestPendingAgeSeconds());
    }
  }
}
