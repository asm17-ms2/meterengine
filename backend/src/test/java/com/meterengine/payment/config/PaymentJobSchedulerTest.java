package com.meterengine.payment.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class PaymentJobSchedulerTest {

  private final ScheduledJobRunService scheduledJobRunService = mock(ScheduledJobRunService.class);
  private final PaymentAttemptConfirmationService paymentAttemptConfirmationService =
      mock(PaymentAttemptConfirmationService.class);
  private final PaymentRetryService paymentRetryService = mock(PaymentRetryService.class);
  private final PaymentReconciliationService paymentReconciliationService =
      mock(PaymentReconciliationService.class);
  private final PaymentJobScheduler paymentJobScheduler =
      new PaymentJobScheduler(
          scheduledJobRunService,
          paymentAttemptConfirmationService,
          paymentRetryService,
          paymentReconciliationService,
          Clock.fixed(Instant.parse("2026-10-26T19:00:15Z"), ZoneOffset.UTC));

  @Test
  void 기동할_때_작업_셋을_등록한다() {
    paymentJobScheduler.register();

    verify(scheduledJobRunService).register("payment_attempt_confirmation");
    verify(scheduledJobRunService).register("payment_retry");
    verify(scheduledJobRunService).register("payment_reconciliation");
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
    when(paymentReconciliationService.reconcile(slot)).thenReturn(new PaymentJobResult(2, 3, 1));
    assertResult("payment_reconciliation", slot);

    paymentJobScheduler.reconcile();

    verify(paymentReconciliationService).reconcile(slot);
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
