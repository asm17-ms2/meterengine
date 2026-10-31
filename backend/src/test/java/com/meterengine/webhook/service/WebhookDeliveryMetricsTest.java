package com.meterengine.webhook.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.webhook.dto.WebhookBacklog;
import com.meterengine.webhook.dto.WebhookOutcome;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WebhookDeliveryMetricsTest {

  private SimpleMeterRegistry meterRegistry;
  private WebhookDeliveryMetrics webhookDeliveryMetrics;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    webhookDeliveryMetrics = new WebhookDeliveryMetrics(meterRegistry);
  }

  @Test
  void 응답_코드로_실패한_전송은_상태_코드_묶음을_붙인다() {
    Map<Integer, String> statusGroups =
        Map.of(301, "3xx", 404, "4xx", 410, "410", 429, "429", 503, "5xx", 102, "other");

    statusGroups
        .keySet()
        .forEach(
            status ->
                webhookDeliveryMetrics.countAttempt(
                    "retry", "live", true, new WebhookOutcome(status, "unexpected_status", 10)));

    statusGroups.forEach(
        (status, statusGroup) ->
            assertThat(deliveryCount("status_group", statusGroup)).as("%d", status).isEqualTo(1.0));
  }

  @Test
  void 응답_코드가_없는_실패는_상태_코드_묶음을_none으로_둔다() {
    webhookDeliveryMetrics.countAttempt(
        "retry", "live", true, new WebhookOutcome(null, "timeout", 10));

    assertThat(deliveryCount("status_group", "none")).isEqualTo(1.0);
  }

  @Test
  void 첫_시도와_재시도를_first_attempt로_나눠_센다() {
    webhookDeliveryMetrics.countAttempt("done", "live", true, new WebhookOutcome(200, null, 10));
    webhookDeliveryMetrics.countAttempt("done", "live", false, new WebhookOutcome(200, null, 10));
    webhookDeliveryMetrics.countAttempt("done", "live", false, new WebhookOutcome(200, null, 10));

    assertThat(deliveryCount("first_attempt", "true")).isEqualTo(1.0);
    assertThat(deliveryCount("first_attempt", "false")).isEqualTo(2.0);
  }

  @Test
  void 선점이_만료된_전송은_재시도로_끝난_실패로_센다() {
    webhookDeliveryMetrics.countExpiredLeases("live", 2);

    assertThat(
            meterRegistry
                .get(WebhookDeliveryMetrics.DELIVERY_COUNTER_NAME)
                .tags(
                    "result",
                    "failed",
                    "reason",
                    "lease_expired",
                    "environment",
                    "live",
                    "first_attempt",
                    "false",
                    "status_group",
                    "none")
                .counter()
                .count())
        .isEqualTo(2.0);
  }

  @Test
  void 보낸_전송은_걸린_시간을_수신_측_응답_시간으로_기록한다() {
    webhookDeliveryMetrics.recordResponse(
        "live", new WebhookOutcome(503, "unexpected_status", 1500));

    assertThat(responseTimer("live").count()).isEqualTo(1);
    assertThat(responseTimer("live").totalTime(TimeUnit.MILLISECONDS)).isEqualTo(1500);
  }

  @Test
  void 수신_주소에_보내지_않은_전송은_수신_측_응답_시간을_기록하지_않는다() {
    for (String failureReason :
        List.of("endpoint_disabled", "secret_unavailable", "blocked_address")) {
      webhookDeliveryMetrics.recordResponse("live", new WebhookOutcome(null, failureReason, 0));
    }

    assertThat(meterRegistry.find(WebhookDeliveryMetrics.RESPONSE_TIMER_NAME).timer()).isNull();
  }

  @Test
  void 전달_소요_시간을_환경별로_기록한다() {
    webhookDeliveryMetrics.recordDelivered("test", Duration.ofMinutes(5));

    assertThat(
            meterRegistry
                .get(WebhookDeliveryMetrics.LATENCY_TIMER_NAME)
                .tag("environment", "test")
                .timer()
                .totalTime(TimeUnit.SECONDS))
        .isEqualTo(300);
  }

  @Test
  void 전달_성공은_첫_전달_전에도_환경과_첫_시도별로_0으로_낸다() {
    for (String environment : List.of("legacy", "test", "live")) {
      for (String firstAttempt : List.of("true", "false")) {
        assertThat(
                meterRegistry
                    .get(WebhookDeliveryMetrics.DELIVERY_COUNTER_NAME)
                    .tags(
                        "result",
                        "done",
                        "reason",
                        "none",
                        "environment",
                        environment,
                        "first_attempt",
                        firstAttempt,
                        "status_group",
                        "none")
                    .counter()
                    .count())
            .as("%s %s", environment, firstAttempt)
            .isZero();
      }
    }
  }

  @Test
  void 적체를_환경별로_내고_적체가_없는_환경은_0으로_둔다() {
    webhookDeliveryMetrics.updateBacklog(Map.of("live", new WebhookBacklog(3, 900, 2, 3600)));
    webhookDeliveryMetrics.updateBacklog(Map.of("test", new WebhookBacklog(1, 60, 4, 7200)));

    assertThat(gauge(WebhookDeliveryMetrics.BACKLOG_GAUGE_NAME, "test")).isEqualTo(1);
    assertThat(gauge(WebhookDeliveryMetrics.BACKLOG_AGE_GAUGE_NAME, "test")).isEqualTo(60);
    assertThat(gauge(WebhookDeliveryMetrics.RETRY_WAITING_GAUGE_NAME, "test")).isEqualTo(4);
    assertThat(gauge(WebhookDeliveryMetrics.PENDING_OLDEST_AGE_GAUGE_NAME, "test")).isEqualTo(7200);
    for (String environment : new String[] {"legacy", "live"}) {
      assertThat(gauge(WebhookDeliveryMetrics.BACKLOG_GAUGE_NAME, environment)).isZero();
      assertThat(gauge(WebhookDeliveryMetrics.BACKLOG_AGE_GAUGE_NAME, environment)).isZero();
      assertThat(gauge(WebhookDeliveryMetrics.RETRY_WAITING_GAUGE_NAME, environment)).isZero();
      assertThat(gauge(WebhookDeliveryMetrics.PENDING_OLDEST_AGE_GAUGE_NAME, environment)).isZero();
    }
  }

  private double deliveryCount(String tag, String value) {
    return meterRegistry
        .get(WebhookDeliveryMetrics.DELIVERY_COUNTER_NAME)
        .tag(tag, value)
        .counters()
        .stream()
        .mapToDouble(Counter::count)
        .sum();
  }

  private Timer responseTimer(String environment) {
    return meterRegistry
        .get(WebhookDeliveryMetrics.RESPONSE_TIMER_NAME)
        .tag("environment", environment)
        .timer();
  }

  private double gauge(String name, String environment) {
    return meterRegistry.get(name).tag("environment", environment).gauge().value();
  }
}
