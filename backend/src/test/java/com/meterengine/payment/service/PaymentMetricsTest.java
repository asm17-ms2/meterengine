package com.meterengine.payment.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.TestcontainersConfiguration;
import com.meterengine.invoice.entity.Invoice;
import com.meterengine.invoice.repository.InvoiceRepository;
import com.meterengine.payment.client.TossPaymentsClient;
import com.meterengine.payment.config.PaymentProperties;
import com.meterengine.payment.entity.PaymentAttempt;
import com.meterengine.payment.repository.PaymentAttemptRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PaymentMetricsTest {

  private static final String STALE_PENDING = "meterengine.payment.stale.pending";
  private static final String STALE_PENDING_OLDEST_REQUESTED =
      "meterengine.payment.stale.pending.oldest.requested";
  private static final OffsetDateTime OLDEST_REQUESTED_AT =
      OffsetDateTime.parse("2026-10-30T10:00:00+09:00");
  private static final List<String> MODES = List.of("test", "live");
  private static final List<String> ATTEMPTS = List.of("first", "retry");
  private static final List<String> RESULTS = List.of("done", "failed", "unknown");
  private static final List<String> FAILURE_REASONS =
      List.of("card_declined", "request_error", "pg_error", "unconfirmed", "not_requested");
  private static final List<String> MISMATCH_KINDS =
      List.of(
          "missing_in_meterengine",
          "missing_in_toss_payments",
          "amount_mismatch",
          "status_mismatch");
  private static final List<String> WEBHOOK_EVENT_TYPES =
      List.of("PAYMENT_STATUS_CHANGED", "other");
  private static final List<String> WEBHOOK_OUTCOMES = List.of("applied", "ignored", "unknown");

  @Autowired private MeterRegistry meterRegistry;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private PaymentAttemptRepository paymentAttemptRepository;
  @Autowired private PaymentAttemptService paymentAttemptService;
  @Autowired private PaymentProperties paymentProperties;

  // 토스 클라이언트를 목으로 바꿔 이 테스트를 결제 통합 테스트와 같은 컨텍스트에서 돌린다.
  @MockitoBean private TossPaymentsClient tossPaymentsClient;

  @Test
  void 만든_직후_모든_태그_조합이_0으로_보이고_organization_id_태그는_없다() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    new PaymentMetrics(registry);

    for (String mode : MODES) {
      for (String attempt : ATTEMPTS) {
        assertThat(attemptCount(registry, mode, attempt, "done", "none")).isZero();
        assertThat(attemptCount(registry, mode, attempt, "unknown", "none")).isZero();
        for (String reason : FAILURE_REASONS) {
          assertThat(attemptCount(registry, mode, attempt, "failed", reason)).isZero();
        }
      }
      for (String result : RESULTS) {
        assertThat(
                count(registry, "meterengine.payment.cancellation", "mode", mode, "result", result))
            .isZero();
      }
      assertThat(count(registry, "meterengine.payment.retry.exhausted", "mode", mode)).isZero();
      for (String kind : MISMATCH_KINDS) {
        assertThat(
                count(
                    registry,
                    "meterengine.payment.reconciliation.mismatch",
                    "mode",
                    mode,
                    "kind",
                    kind))
            .isZero();
      }
    }
    for (String eventType : WEBHOOK_EVENT_TYPES) {
      for (String outcome : WEBHOOK_OUTCOMES) {
        assertThat(
                count(
                    registry,
                    "meterengine.toss.payments.webhook",
                    "event_type",
                    eventType,
                    "outcome",
                    outcome))
            .isZero();
      }
    }
    for (String mode : MODES) {
      assertThat(gauge(registry, STALE_PENDING, mode)).isZero();
      assertThat(gauge(registry, STALE_PENDING_OLDEST_REQUESTED, mode)).isZero();
    }
    assertThat(registry.getMeters()).hasSize(28 + 6 + 2 + 8 + 6 + 2 + 2);
    assertThat(registry.getMeters())
        .flatExtracting(meter -> meter.getId().getTags())
        .noneMatch(tag -> tag.getKey().equals("organization_id"));
  }

  @Test
  void 기동한_앱의_레지스트리에_모든_태그_조합이_등록되어_있다() {
    assertThat(meterRegistry.find("meterengine.payment.attempt").counters()).hasSize(28);
    assertThat(meterRegistry.find("meterengine.payment.cancellation").counters()).hasSize(6);
    assertThat(meterRegistry.find("meterengine.payment.retry.exhausted").counters()).hasSize(2);
    assertThat(meterRegistry.find("meterengine.payment.reconciliation.mismatch").counters())
        .hasSize(8);
    assertThat(meterRegistry.find("meterengine.toss.payments.webhook").counters()).hasSize(6);
    assertThat(meterRegistry.find(STALE_PENDING).gauges()).hasSize(2);
    assertThat(meterRegistry.find(STALE_PENDING_OLDEST_REQUESTED).gauges()).hasSize(2);
  }

  @Test
  void 같은_시도를_두_번_done으로_바꾸면_두_번째는_false이고_지표는_한_번_오른다() {
    PaymentAttempt attempt = pendingAttempt(payableInvoice());
    double doneBefore = attemptCount("first", "done", "none");

    assertThat(paymentAttemptService.markDone(attempt, "tgen_first")).isTrue();
    assertThat(paymentAttemptService.markDone(attempt, "tgen_second")).isFalse();
    assertThat(paymentAttemptService.markFailed(attempt, "REJECT_CARD_PAYMENT", "한도초과")).isFalse();

    assertThat(attemptCount("first", "done", "none")).isEqualTo(doneBefore + 1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT payment_key FROM payment_attempt WHERE id = ?",
                String.class,
                attempt.getId()))
        .isEqualTo("tgen_first");
  }

  @Test
  void 시도_수가_상한에_이르는_실패에서만_exhausted가_한_번_오른다() {
    Invoice invoice = payableInvoice();
    double exhaustedBefore = exhaustedCount("test");
    int retryMaxAttempts = paymentProperties.retryMaxAttempts();

    for (int i = 1; i < retryMaxAttempts; i++) {
      assertThat(
              paymentAttemptService.markFailed(
                  pendingAttempt(invoice), "REJECT_CARD_PAYMENT", "한도초과"))
          .isTrue();
    }
    assertThat(exhaustedCount("test")).isEqualTo(exhaustedBefore);

    PaymentAttempt last = pendingAttempt(invoice);
    assertThat(paymentAttemptService.markFailed(last, "REJECT_CARD_PAYMENT", "한도초과")).isTrue();
    assertThat(paymentAttemptService.markFailed(last, "REJECT_CARD_PAYMENT", "한도초과")).isFalse();

    assertThat(retryMaxAttempts).isEqualTo(3);
    assertThat(exhaustedCount("test")).isEqualTo(exhaustedBefore + 1);
  }

  @Test
  void 같은_인보이스의_첫_시도는_first로_이어진_시도는_retry로_실패_사유_묶음과_함께_센다() {
    Invoice invoice = payableInvoice();
    double firstBefore = attemptCount("first", "failed", "card_declined");
    double retryBefore = attemptCount("retry", "failed", "card_declined");

    for (int i = 0; i < 3; i++) {
      assertThat(
              paymentAttemptService.markFailed(
                  pendingAttempt(invoice), "REJECT_CARD_PAYMENT", "한도초과"))
          .isTrue();
    }

    assertThat(attemptCount("first", "failed", "card_declined")).isEqualTo(firstBefore + 1);
    assertThat(attemptCount("retry", "failed", "card_declined")).isEqualTo(retryBefore + 2);
  }

  @Test
  void 결과_모름_게이지는_mode마다_건수와_가장_오래된_요청_시각을_따로_둔다() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    PaymentMetrics paymentMetrics = new PaymentMetrics(registry);

    paymentMetrics.setStalePending("live", 3);
    paymentMetrics.setStalePendingOldestRequestedAt("live", OLDEST_REQUESTED_AT);

    assertThat(gauge(registry, STALE_PENDING, "live")).isEqualTo(3);
    assertThat(gauge(registry, STALE_PENDING_OLDEST_REQUESTED, "live"))
        .isEqualTo(OLDEST_REQUESTED_AT.toEpochSecond());
    assertThat(gauge(registry, STALE_PENDING, "test")).isZero();
  }

  @Test
  void 결과_모름이_모두_풀리면_가장_오래된_요청_시각을_0으로_되돌린다() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    PaymentMetrics paymentMetrics = new PaymentMetrics(registry);
    paymentMetrics.setStalePendingOldestRequestedAt("live", OLDEST_REQUESTED_AT);

    paymentMetrics.clearStalePendingOldestRequestedAt("live");

    assertThat(gauge(registry, STALE_PENDING_OLDEST_REQUESTED, "live")).isZero();
  }

  private static double gauge(MeterRegistry registry, String name, String mode) {
    return registry.get(name).tag("mode", mode).gauge().value();
  }

  private static double count(MeterRegistry registry, String name, String... tags) {
    return registry.get(name).tags(tags).counter().count();
  }

  private static double attemptCount(
      MeterRegistry registry, String mode, String attempt, String result, String reason) {
    return count(
        registry,
        "meterengine.payment.attempt",
        "mode",
        mode,
        "attempt",
        attempt,
        "result",
        result,
        "reason",
        reason);
  }

  private double attemptCount(String attempt, String result, String reason) {
    return attemptCount(meterRegistry, "test", attempt, result, reason);
  }

  private double exhaustedCount(String mode) {
    return count(meterRegistry, "meterengine.payment.retry.exhausted", "mode", mode);
  }

  private PaymentAttempt pendingAttempt(Invoice invoice) {
    return paymentAttemptRepository.saveAndFlush(
        PaymentAttempt.pending(
            UUID.randomUUID(),
            invoice.getOrganizationId(),
            invoice.getCustomerId(),
            invoice.getId(),
            "test",
            invoice.getTotalAmount(),
            "2026-08 사용료"));
  }

  private Invoice payableInvoice() {
    UUID organizationId =
        jdbcTemplate.queryForObject(
            "INSERT INTO organization (name) VALUES ('지표 도입사') RETURNING id", UUID.class);
    UUID customerId =
        jdbcTemplate.queryForObject(
            "INSERT INTO customer (organization_id, name) VALUES (?, '지표 고객') RETURNING id",
            UUID.class,
            organizationId);
    return invoiceRepository.save(
        new Invoice(
            UUID.randomUUID(),
            organizationId,
            customerId,
            "지표 고객",
            "2026-08",
            12000,
            1200,
            OffsetDateTime.parse("2026-09-01T04:00:00Z"),
            OffsetDateTime.parse("2026-09-01T05:00:00Z")));
  }
}
