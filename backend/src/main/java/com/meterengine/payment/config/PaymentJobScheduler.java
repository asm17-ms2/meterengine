package com.meterengine.payment.config;

import com.meterengine.invoice.service.InvoiceFinalizationBacklogService;
import com.meterengine.payment.dto.PaymentJobResult;
import com.meterengine.payment.service.PaymentAttemptConfirmationService;
import com.meterengine.payment.service.PaymentReconciliationService;
import com.meterengine.payment.service.PaymentRetryService;
import com.meterengine.scheduling.dto.ScheduledJobResult;
import com.meterengine.scheduling.service.ScheduledJobRunService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBooleanProperty(name = "meterengine.scheduling.enabled", matchIfMissing = true)
public class PaymentJobScheduler {

  private static final Logger logger = LoggerFactory.getLogger(PaymentJobScheduler.class);
  private static final String CONFIRMATION = "payment_attempt_confirmation";
  private static final String RETRY = "payment_retry";
  private static final String RECONCILIATION = "payment_reconciliation";
  // 운영 현황과 경보에 올리는 업무 작업이 아니라 지표 계산이라 register하지 않고 슬롯과 실행 기록만 쓴다.
  private static final String FINALIZATION_BACKLOG = "invoice_finalization_backlog";
  private static final ZoneId BILLING_ZONE = ZoneId.of("Asia/Seoul");
  private static final Duration MAX_CATCH_UP = Duration.ofDays(7);

  private final ScheduledJobRunService scheduledJobRunService;
  private final PaymentAttemptConfirmationService paymentAttemptConfirmationService;
  private final PaymentRetryService paymentRetryService;
  private final PaymentReconciliationService paymentReconciliationService;
  private final InvoiceFinalizationBacklogService invoiceFinalizationBacklogService;
  private final Clock clock;

  PaymentJobScheduler(
      ScheduledJobRunService scheduledJobRunService,
      PaymentAttemptConfirmationService paymentAttemptConfirmationService,
      PaymentRetryService paymentRetryService,
      PaymentReconciliationService paymentReconciliationService,
      InvoiceFinalizationBacklogService invoiceFinalizationBacklogService,
      Clock clock) {
    this.scheduledJobRunService = scheduledJobRunService;
    this.paymentAttemptConfirmationService = paymentAttemptConfirmationService;
    this.paymentRetryService = paymentRetryService;
    this.paymentReconciliationService = paymentReconciliationService;
    this.invoiceFinalizationBacklogService = invoiceFinalizationBacklogService;
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
    try {
      run(
          RECONCILIATION,
          slot,
          () -> paymentReconciliationService.reconcile(slot, reconciliationStart(slot)));
    } finally {
      measureFinalizationBacklog(slot);
    }
  }

  // 확정 대기는 청구 예정액을 다시 계산하는 무거운 일이라 하루 한 번 도는 대사 틱에 붙이고, 슬롯을 잡은 인스턴스만 센다.
  // 실패는 예약 작업 지표에 남기고, 대사 예외를 가리지 않게 여기서 잡는다.
  private void measureFinalizationBacklog(OffsetDateTime slot) {
    try {
      scheduledJobRunService.run(FINALIZATION_BACKLOG, slot, this::measureAndLogElapsed);
    } catch (RuntimeException exception) {
      logger.warn("invoice finalization backlog failed", exception);
    }
  }

  private ScheduledJobResult measureAndLogElapsed() {
    Instant startedAt = clock.instant();
    ScheduledJobResult result = invoiceFinalizationBacklogService.measure();
    logger.info(
        "invoice finalization backlog measured: processed={}, skipped={}, failed={},"
            + " elapsed_ms={}",
        result.processedCount(),
        result.skippedCount(),
        result.failedCount(),
        Duration.between(startedAt, clock.instant()).toMillis());
    return result;
  }

  // 실패하거나 빠진 날은 마지막으로 실패 없이 끝난 슬롯부터 최대 7일 전까지 이어서 맞춘다.
  // 실패 없이 끝난 적이 없으면 가장 이른 실행의 창부터 맞춘다.
  private OffsetDateTime reconciliationStart(OffsetDateTime slot) {
    OffsetDateTime limit = slot.minus(MAX_CATCH_UP);
    OffsetDateTime start =
        scheduledJobRunService
            .watermark(RECONCILIATION, slot)
            .or(
                () ->
                    scheduledJobRunService
                        .earliest(RECONCILIATION, slot)
                        .map(first -> first.minusDays(1)))
            .orElse(slot.minusDays(1));
    return start.isBefore(limit) ? limit : start;
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
