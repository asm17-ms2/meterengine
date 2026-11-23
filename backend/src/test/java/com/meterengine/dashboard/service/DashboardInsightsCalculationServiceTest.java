package com.meterengine.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.meterengine.dashboard.dto.DashboardInsightsResponse.MonthlyAmount;
import com.meterengine.dashboard.repository.DailyEventCountRepository;
import com.meterengine.invoice.repository.InvoiceRepository;
import com.meterengine.invoice.service.DraftInvoiceService;
import com.meterengine.payment.dto.ListInvoicePaymentsResponse;
import com.meterengine.payment.service.InvoicePaymentService;
import com.meterengine.payment.service.PaymentSettingService;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DashboardInsightsCalculationServiceTest {
  private static final YearMonth OCTOBER = YearMonth.of(2026, 10);
  private final UUID organizationId = UUID.randomUUID();
  private final InvoicePaymentService invoicePaymentService = mock(InvoicePaymentService.class);
  private final PaymentSettingService paymentSettingService =
      mock(PaymentSettingService.class, RETURNS_DEEP_STUBS);
  private final DraftInvoiceService draftInvoiceService =
      mock(DraftInvoiceService.class, RETURNS_DEEP_STUBS);
  private final DashboardInsightsCalculationService service =
      new DashboardInsightsCalculationService(
          invoicePaymentService,
          paymentSettingService,
          mock(DailyEventCountRepository.class),
          draftInvoiceService,
          mock(InvoiceRepository.class),
          Clock.fixed(Instant.parse("2026-10-15T03:00:00Z"), ZoneOffset.UTC));

  @Test
  void 달별_금액의_발행_전_금액은_그_달의_청구_예정액이다() {
    when(paymentSettingService.get(organizationId).mode()).thenReturn("test");
    when(invoicePaymentService.list(eq(organizationId), any(), any(), eq("test")))
        .thenReturn(new ListInvoicePaymentsResponse(List.of()));
    when(draftInvoiceService.preview(organizationId, OCTOBER).totalAmount()).thenReturn(700L);
    when(draftInvoiceService.preview(organizationId, OCTOBER.minusMonths(1)).totalAmount())
        .thenReturn(30L);

    List<MonthlyAmount> monthlyAmounts =
        service.calculate(organizationId, OCTOBER).monthlyAmounts();

    assertThat(monthlyAmounts)
        .extracting(MonthlyAmount::draftSupplyAmount)
        .containsExactly(0L, 30L, 700L);
  }
}
