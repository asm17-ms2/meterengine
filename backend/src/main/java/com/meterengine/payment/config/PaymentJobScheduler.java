package com.meterengine.payment.config;

import com.meterengine.payment.dto.PaymentJobResult;
import com.meterengine.payment.service.PaymentAttemptConfirmationService;
import com.meterengine.payment.service.PaymentReconciliationService;
import com.meterengine.payment.service.PaymentRetryService;
import com.meterengine.scheduling.dto.ScheduledJobResult;
import com.meterengine.scheduling.service.ScheduledJobRunService;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBooleanProperty(name = "meterengine.scheduling.enabled", matchIfMissing = true)
public class PaymentJobScheduler {

  private static final String CONFIRMATION = "payment_attempt_confirmation";
  private static final String RETRY = "payment_retry";
  private static final String RECONCILIATION = "payment_reconciliation";
  private static final ZoneId BILLING_ZONE = ZoneId.of("Asia/Seoul");
  private static final Duration MAX_CATCH_UP = Duration.ofDays(7);

  private final ScheduledJobRunService scheduledJobRunService;
  private final PaymentAttemptConfirmationService paymentAttemptConfirmationService;
  private final PaymentRetryService paymentRetryService;
  private final PaymentReconciliationService paymentReconciliationService;
  private final Clock clock;

  PaymentJobScheduler(
      ScheduledJobRunService scheduledJobRunService,
      PaymentAttemptConfirmationService paymentAttemptConfirmationService,
      PaymentRetryService paymentRetryService,
      PaymentReconciliationService paymentReconciliationService,
      Clock clock) {
    this.scheduledJobRunService = scheduledJobRunService;
    this.paymentAttemptConfirmationService = paymentAttemptConfirmationService;
    this.paymentRetryService = paymentRetryService;
    this.paymentReconciliationService = paymentReconciliationService;
    this.clock = clock;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void register() {
    scheduledJobRunService.register(CONFIRMATION);
    scheduledJobRunService.register(RETRY);
    scheduledJobRunService.register(RECONCILIATION);
  }

  @Scheduled(cron = "${payment.confirmation-cron:0 * * * * *}", zone = "Asia/Seoul")
  public void confirm() {
    run(CONFIRMATION, minute(), paymentAttemptConfirmationService::confirm);
  }

  @Scheduled(cron = "${payment.retry-cron:0 */10 * * * *}", zone = "Asia/Seoul")
  public void retry() {
    run(RETRY, minute(), paymentRetryService::retry);
  }

  @Scheduled(cron = "${payment.reconciliation-cron:0 0 4 * * *}", zone = "Asia/Seoul")
  public void reconcile() {
    OffsetDateTime slot =
        OffsetDateTime.now(clock)
            .atZoneSameInstant(BILLING_ZONE)
            .toLocalDate()
            .atTime(4, 0)
            .atZone(BILLING_ZONE)
            .toOffsetDateTime();
    run(
        RECONCILIATION,
        slot,
        () -> paymentReconciliationService.reconcile(slot, reconciliationStart(slot)));
  }

  // 실패하거나 빠진 날은 마지막으로 실패 없이 끝난 슬롯부터 최대 7일 전까지 이어서 맞춘다.
  private OffsetDateTime reconciliationStart(OffsetDateTime slot) {
    OffsetDateTime earliest = slot.minus(MAX_CATCH_UP);
    return scheduledJobRunService
        .watermark(RECONCILIATION, slot)
        .map(clean -> clean.isBefore(earliest) ? earliest : clean)
        .orElse(slot.minusDays(1));
  }

  private OffsetDateTime minute() {
    return OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
  }

  private void run(String name, OffsetDateTime slot, Supplier<PaymentJobResult> operation) {
    scheduledJobRunService.run(
        name,
        slot,
        () -> {
          PaymentJobResult result = operation.get();
          return new ScheduledJobResult(
              result.processedCount(), result.skippedCount(), result.failedCount());
        });
  }
}
