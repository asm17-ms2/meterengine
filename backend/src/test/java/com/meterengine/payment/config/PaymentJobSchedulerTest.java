package com.meterengine.payment.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.meterengine.invoice.service.InvoiceFinalizationBacklogService;
import com.meterengine.payment.dto.PaymentJobResult;
import com.meterengine.payment.service.PaymentAttemptConfirmationService;
import com.meterengine.payment.service.PaymentReconciliationService;
import com.meterengine.payment.service.PaymentRetryService;
import com.meterengine.scheduling.dto.ScheduledJobResult;
import com.meterengine.scheduling.service.ScheduledJobRunService;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class PaymentJobSchedulerTest {

  private final ScheduledJobRunService scheduledJobRunService = mock(ScheduledJobRunService.class);
  private final PaymentAttemptConfirmationService paymentAttemptConfirmationService =
      mock(PaymentAttemptConfirmationService.class);
  private final PaymentRetryService paymentRetryService = mock(PaymentRetryService.class);
  private final PaymentReconciliationService paymentReconciliationService =
      mock(PaymentReconciliationService.class);
  private final InvoiceFinalizationBacklogService invoiceFinalizationBacklogService =
      mock(InvoiceFinalizationBacklogService.class);
  private final PaymentJobScheduler paymentJobScheduler =
      new PaymentJobScheduler(
          scheduledJobRunService,
          paymentAttemptConfirmationService,
          paymentRetryService,
          paymentReconciliationService,
          invoiceFinalizationBacklogService,
          Clock.fixed(Instant.parse("2026-10-26T19:00:15Z"), ZoneOffset.UTC));

  @Test
  void 기동할_때_작업_셋을_등록한다() {
    paymentJobScheduler.register();

    verify(scheduledJobRunService).register("payment_attempt_confirmation");
    verify(scheduledJobRunService).register("payment_retry");
    verify(scheduledJobRunService).register("payment_reconciliation");
    verifyNoMoreInteractions(scheduledJobRunService);
  }

  @Test
  void 확정은_분_슬롯을_선점하고_처리_건수를_돌려준다() {
    when(paymentAttemptConfirmationService.confirm()).thenReturn(new PaymentJobResult(2, 3, 1));
    assertResult("payment_attempt_confirmation", OffsetDateTime.parse("2026-10-26T19:00Z"));

    paymentJobScheduler.confirm();

    verify(paymentAttemptConfirmationService).confirm();
  }

  @Test
  void 재시도도_공유_실행기를_거쳐_처리_건수를_돌려준다() {
    when(paymentRetryService.retry()).thenReturn(new PaymentJobResult(2, 3, 1));
    assertResult("payment_retry", OffsetDateTime.parse("2026-10-26T19:00Z"));

    paymentJobScheduler.retry();

    verify(paymentRetryService).retry();
  }

  @Test
  void 대사는_KST_날짜의_새벽_4시_슬롯을_쓴다() {
    OffsetDateTime slot = OffsetDateTime.parse("2026-10-27T04:00+09:00");
    when(paymentReconciliationService.reconcile(slot, slot.minusDays(1)))
        .thenReturn(new PaymentJobResult(2, 3, 1));
    assertResult("payment_reconciliation", slot);

    paymentJobScheduler.reconcile();

    verify(paymentReconciliationService).reconcile(slot, slot.minusDays(1));
  }

  @Test
  void 대사는_마지막으로_실패_없이_끝난_슬롯부터_이어서_맞춘다() {
    OffsetDateTime slot = OffsetDateTime.parse("2026-10-27T04:00+09:00");
    when(scheduledJobRunService.watermark("payment_reconciliation", slot))
        .thenReturn(Optional.of(slot.minusDays(3)));
    when(paymentReconciliationService.reconcile(slot, slot.minusDays(3)))
        .thenReturn(new PaymentJobResult(2, 3, 1));
    assertResult("payment_reconciliation", slot);

    paymentJobScheduler.reconcile();

    verify(paymentReconciliationService).reconcile(slot, slot.minusDays(3));
  }

  @Test
  void 대사는_7일보다_오래된_기록이면_7일_전까지만_본다() {
    OffsetDateTime slot = OffsetDateTime.parse("2026-10-27T04:00+09:00");
    when(scheduledJobRunService.watermark("payment_reconciliation", slot))
        .thenReturn(Optional.of(slot.minusDays(30)));
    when(paymentReconciliationService.reconcile(slot, slot.minusDays(7)))
        .thenReturn(new PaymentJobResult(2, 3, 1));
    assertResult("payment_reconciliation", slot);

    paymentJobScheduler.reconcile();

    verify(paymentReconciliationService).reconcile(slot, slot.minusDays(7));
  }

  @Test
  void 성공_기록_없이_첫_대사가_실패했으면_다음_날은_그_실행의_창부터_맞춘다() {
    OffsetDateTime firstSlot = OffsetDateTime.parse("2026-10-26T04:00+09:00");
    OffsetDateTime slot = OffsetDateTime.parse("2026-10-27T04:00+09:00");
    when(scheduledJobRunService.earliest("payment_reconciliation", slot))
        .thenReturn(Optional.of(firstSlot));
    when(paymentReconciliationService.reconcile(slot, firstSlot.minusDays(1)))
        .thenReturn(new PaymentJobResult(2, 3, 1));
    assertResult("payment_reconciliation", slot);

    paymentJobScheduler.reconcile();

    verify(paymentReconciliationService).reconcile(slot, firstSlot.minusDays(1));
  }

  @Test
  void 대사_틱마다_같은_4시_슬롯을_잡아_확정_대기를_다시_센다() {
    when(invoiceFinalizationBacklogService.measure()).thenReturn(new ScheduledJobResult(2, 3, 1));
    assertResult("invoice_finalization_backlog", OffsetDateTime.parse("2026-10-27T04:00+09:00"));

    paymentJobScheduler.reconcile();

    verify(invoiceFinalizationBacklogService).measure();
  }

  @Test
  void 대사가_예외로_끝나도_확정_대기를_센다() {
    failJob("payment_reconciliation", "대사 실패");

    assertThatThrownBy(paymentJobScheduler::reconcile).isInstanceOf(IllegalStateException.class);

    verify(scheduledJobRunService).run(eq("invoice_finalization_backlog"), any(), any());
  }

  @Test
  void 확정_대기가_실패해도_대사_예외를_그대로_던진다() {
    failJob("payment_reconciliation", "대사 실패");
    failJob("invoice_finalization_backlog", "확정 대기 실패");

    assertThatThrownBy(paymentJobScheduler::reconcile).hasMessage("대사 실패");
  }

  @Test
  void 확정_대기가_실패해도_대사_틱은_예외_없이_끝난다() {
    failJob("invoice_finalization_backlog", "확정 대기 실패");

    assertThatCode(paymentJobScheduler::reconcile).doesNotThrowAnyException();
  }

  private void failJob(String name, String message) {
    doThrow(new IllegalStateException(message))
        .when(scheduledJobRunService)
        .run(eq(name), any(), any());
  }

  private void assertResult(String name, OffsetDateTime slot) {
    doAnswer(
            invocation -> {
              Supplier<ScheduledJobResult> operation = invocation.getArgument(2);
              assertThat(operation.get()).isEqualTo(new ScheduledJobResult(2, 3, 1));
              return null;
            })
        .when(scheduledJobRunService)
        .run(eq(name), eq(slot), any());
  }
}
