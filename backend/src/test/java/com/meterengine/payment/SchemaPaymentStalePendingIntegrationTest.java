package com.meterengine.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import com.meterengine.SideEffectTestSupport;
import com.meterengine.global.database.DatabaseScope;
import com.meterengine.global.database.DatabaseScopes;
import com.meterengine.invoice.entity.Invoice;
import com.meterengine.invoice.repository.InvoiceRepository;
import com.meterengine.payment.client.TossPaymentsClient;
import com.meterengine.payment.repository.PaymentAttemptRepository;
import com.meterengine.payment.repository.PaymentCancellationRepository;
import com.meterengine.payment.service.PaymentAttemptConfirmationService;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class SchemaPaymentStalePendingIntegrationTest extends SideEffectTestSupport {
  @Autowired PaymentAttemptConfirmationService confirmation;
  @Autowired InvoiceRepository invoices;
  @Autowired PaymentAttemptRepository attempts;
  @Autowired PaymentCancellationRepository cancellations;
  @Autowired MeterRegistry registry;
  @MockitoBean TossPaymentsClient toss;

  @Test
  @DirtiesContext
  void TEST에만_오래된_pending이_있어도_뒤의_LIVE_0이_게이지를_덮지_않는다() {
    seed("test", 1, 0);
    assertCounts("test", 1, 0);
    assertCounts("live", 0, 0);
    confirmAll(1, 0);
  }

  @Test
  @DirtiesContext
  void LIVE에만_오래된_pending_취소가_있으면_합계가_유지된다() {
    seed("live", 0, 1);
    assertCounts("test", 0, 0);
    assertCounts("live", 0, 1);
    confirmAll(0, 1);
  }

  @Test
  @DirtiesContext
  void 두_schema의_오래된_pending_시도와_취소를_모두_합산한다() {
    seed("test", 2, 1);
    seed("live", 1, 2);
    assertCounts("test", 2, 1);
    assertCounts("live", 1, 2);
    confirmAll(3, 3);
  }

  @Test
  @DirtiesContext
  void 한_scope만_다시_확인하면_다른_scope의_마지막값을_유지한다() {
    seed("test", 1, 1);
    seed("live", 1, 1);
    confirmAll(2, 2);
    seed("test", 1, 0);
    in(
        "test",
        () -> {
          confirmation.confirm();
          assertThat(DatabaseScopes.current()).isEqualTo(DatabaseScope.TEST);
        });
    assertCounts("test", 2, 1);
    assertCounts("live", 1, 1);
    assertGauge(3, 2);
    seed("live", 0, 1);
    in(
        "live",
        () -> {
          confirmation.confirm();
          assertThat(DatabaseScopes.current()).isEqualTo(DatabaseScope.LIVE);
        });
    assertCounts("test", 2, 1);
    assertCounts("live", 1, 2);
    assertGauge(3, 3);
    verifyNoInteractions(toss);
  }

  private void confirmAll(long expectedTest, long expectedLive) {
    assertThat(DatabaseScopes.current()).isEqualTo(DatabaseScope.SHARED);
    var result = confirmation.confirm();
    assertThat(result.processedCount()).isZero();
    assertThat(result.skippedCount()).isZero();
    assertThat(result.failedCount()).isZero();
    assertThat(DatabaseScopes.current()).isEqualTo(DatabaseScope.SHARED);
    assertGauge(expectedTest, expectedLive);
    verifyNoInteractions(toss);
  }

  private void assertGauge(long expectedTest, long expectedLive) {
    var test = registry.get("meterengine.payment.stale.pending").tag("mode", "test").gauge();
    var live = registry.get("meterengine.payment.stale.pending").tag("mode", "live").gauge();
    assertThat(test.value()).isEqualTo(expectedTest);
    assertThat(live.value()).isEqualTo(expectedLive);
    assertThat(test.value() + live.value()).isEqualTo(expectedTest + expectedLive);
    for (var gauge : java.util.List.of(test, live)) {
      assertThat(gauge.getId().getTags()).hasSize(1);
      assertThat(gauge.getId().getTags().getFirst().getKey()).isEqualTo("mode");
    }
    assertThat(registry.find("meterengine.payment.stale.pending").gauges()).hasSize(2);
  }

  private void assertCounts(String mode, long attemptCount, long cancellationCount) {
    in(
        mode,
        () -> {
          var before = OffsetDateTime.now().minusMinutes(2);
          assertThat(attempts.countPendingRequestedBefore(mode, before)).isEqualTo(attemptCount);
          assertThat(cancellations.countPendingRequestedBefore(mode, before))
              .isEqualTo(cancellationCount);
          assertThat(
                  jdbc.queryForObject(
                      "SELECT count(*) FROM payment_attempt WHERE status='pending'", Long.class))
              .isEqualTo(attemptCount);
          assertThat(
                  jdbc.queryForObject(
                      "SELECT count(*) FROM payment_cancellation WHERE status='pending'",
                      Long.class))
              .isEqualTo(cancellationCount);
        });
  }

  private void seed(String mode, int attemptCount, int cancellationCount) {
    transactions.write(
        scope(mode),
        () -> {
          for (int index = 0; index < attemptCount + cancellationCount; index++) {
            UUID customer = UUID.randomUUID();
            jdbc.update(
                "INSERT INTO customer(id,organization_id,name) VALUES (?,?,'stale fixture')",
                customer,
                organizationId);
            var invoice =
                invoices.saveAndFlush(
                    new Invoice(
                        UUID.randomUUID(),
                        organizationId,
                        customer,
                        "stale fixture",
                        "2026-08",
                        10000,
                        1000,
                        OffsetDateTime.now().minusDays(9)));
            UUID attempt = UUID.randomUUID();
            boolean pending = index < attemptCount;
            jdbc.update(
                "INSERT INTO"
                    + " payment_attempt(id,organization_id,customer_id,invoice_id,mode,amount,order_name,status,payment_key,requested_at,completed_at)"
                    + " VALUES (?,?,?,?,?,11000,'stale fixture',?,?,?,?)",
                attempt,
                organizationId,
                customer,
                invoice.getId(),
                mode,
                pending ? "pending" : "done",
                pending ? null : "payment-" + attempt,
                OffsetDateTime.now().minusDays(8),
                pending ? null : OffsetDateTime.now().minusDays(8));
            if (!pending)
              jdbc.update(
                  "INSERT INTO"
                      + " payment_cancellation(id,organization_id,payment_attempt_id,mode,amount,reason,status,requested_at)"
                      + " VALUES (?,?,?,?,1000,'stale fixture','pending',?)",
                  UUID.randomUUID(),
                  organizationId,
                  attempt,
                  mode,
                  OffsetDateTime.now().minusDays(8));
          }
          return null;
        });
  }
}
