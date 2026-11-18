package com.meterengine.dashboard.service;

import com.meterengine.dashboard.dto.DashboardInsightsResponse;
import com.meterengine.dashboard.dto.DashboardInsightsResponse.DailyEventCount;
import com.meterengine.dashboard.dto.DashboardInsightsResponse.MonthlyAmount;
import com.meterengine.dashboard.repository.DailyEventCountRepository;
import com.meterengine.global.security.BusinessEnvironmentContext;
import com.meterengine.global.time.BillingMonth;
import com.meterengine.payment.service.InvoicePaymentService;
import com.meterengine.payment.service.PaymentSettingService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardInsightsCalculationService {
  private static final int MONTHLY_AMOUNT_MONTHS = 3;
  private final InvoicePaymentService invoicePaymentService;
  private final PaymentSettingService paymentSettingService;
  private final DailyEventCountRepository dailyEventCountRepository;
  private final Clock clock;

  DashboardInsightsCalculationService(
      InvoicePaymentService invoicePaymentService,
      PaymentSettingService paymentSettingService,
      DailyEventCountRepository dailyEventCountRepository,
      Clock clock) {
    this.invoicePaymentService = invoicePaymentService;
    this.paymentSettingService = paymentSettingService;
    this.dailyEventCountRepository = dailyEventCountRepository;
    this.clock = clock;
  }

  /** 지금 업무 환경의 인사이트를 DB에서 계산한다. */
  @Transactional(readOnly = true)
  public DashboardInsightsResponse calculate(UUID organizationId, YearMonth month) {
    String mode =
        BusinessEnvironmentContext.mode() == null
            ? paymentSettingService.get(organizationId).mode()
            : BusinessEnvironmentContext.mode();
    List<MonthlyAmount> monthlyAmounts =
        IntStream.range(0, MONTHLY_AMOUNT_MONTHS)
            .mapToObj(index -> month.minusMonths(MONTHLY_AMOUNT_MONTHS - 1L - index))
            .map(amountMonth -> monthlyAmount(organizationId, amountMonth, mode))
            .toList();
    return new DashboardInsightsResponse(
        month.toString(),
        OffsetDateTime.now(clock),
        monthlyAmounts,
        dailyEventCounts(organizationId, month, BillingMonth.now(clock).toLocalDate()));
  }

  private MonthlyAmount monthlyAmount(UUID organizationId, YearMonth month, String mode) {
    PaymentTotals totals =
        PaymentTotals.from(
            invoicePaymentService.list(organizationId, month, null, mode).invoicePayments());
    return new MonthlyAmount(
        month.toString(), totals.finalizedTotalAmount(), totals.collectedAmount());
  }

  // 오늘은 아직 끝나지 않아 하루 한 번 계산하는 값에 넣으면 반쪽이 된다. 어제까지만 센다.
  private List<DailyEventCount> dailyEventCounts(
      UUID organizationId, YearMonth month, LocalDate today) {
    LocalDate yesterday = today.minusDays(1);
    LocalDate lastDate =
        yesterday.isBefore(month.atEndOfMonth()) ? yesterday : month.atEndOfMonth();
    if (lastDate.isBefore(month.atDay(1))) return List.of();
    Map<LocalDate, Long> eventCountByDate =
        dailyEventCountRepository.countByEventDate(
            organizationId,
            BusinessEnvironmentContext.scopeId(),
            startOf(month.atDay(1)),
            startOf(lastDate.plusDays(1)));
    return month
        .atDay(1)
        .datesUntil(lastDate.plusDays(1))
        .map(
            eventDate ->
                new DailyEventCount(eventDate, eventCountByDate.getOrDefault(eventDate, 0L)))
        .toList();
  }

  private static OffsetDateTime startOf(LocalDate eventDate) {
    return eventDate.atStartOfDay(BillingMonth.ZONE).toOffsetDateTime();
  }
}
