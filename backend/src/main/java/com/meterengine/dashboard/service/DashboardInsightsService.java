package com.meterengine.dashboard.service;

import com.meterengine.dashboard.client.DashboardInsightsStore;
import com.meterengine.dashboard.client.DashboardInsightsStoreException;
import com.meterengine.dashboard.dto.DashboardInsightsKey;
import com.meterengine.dashboard.dto.DashboardInsightsResponse;
import com.meterengine.dashboard.dto.DashboardInsightsResponse.DailyEventCount;
import com.meterengine.global.security.BusinessEnvironmentContext;
import com.meterengine.global.time.BillingMonth;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DashboardInsightsService {
  private static final Logger log = LoggerFactory.getLogger(DashboardInsightsService.class);
  private final DashboardInsightsMemory dashboardInsightsMemory;
  private final DashboardInsightsStore dashboardInsightsStore;
  private final DashboardInsightsCalculationService dashboardInsightsCalculationService;
  private final Clock clock;

  DashboardInsightsService(
      DashboardInsightsMemory dashboardInsightsMemory,
      DashboardInsightsStore dashboardInsightsStore,
      DashboardInsightsCalculationService dashboardInsightsCalculationService,
      Clock clock) {
    this.dashboardInsightsMemory = dashboardInsightsMemory;
    this.dashboardInsightsStore = dashboardInsightsStore;
    this.dashboardInsightsCalculationService = dashboardInsightsCalculationService;
    this.clock = clock;
  }

  /** 계산해 둔 인사이트에, 그 뒤부터 오늘까지의 날짜별 이벤트 수를 지금 세어 붙여 준다. */
  public DashboardInsightsResponse get(UUID organizationId, YearMonth month) {
    DashboardInsightsResponse insights = calculatedInsights(organizationId, month);
    return insights.withRecentEventCounts(recentEventCounts(organizationId, month, insights));
  }

  // 계산해 둔 값은 어제까지라 오늘 보낸 이벤트가 내일 새벽까지 보이지 않는다. 오늘 칸은 조회할 때 센다.
  private List<DailyEventCount> recentEventCounts(
      UUID organizationId, YearMonth month, DashboardInsightsResponse insights) {
    LocalDate today = BillingMonth.now(clock).toLocalDate();
    if (!YearMonth.from(today).equals(month)) return List.of();
    List<DailyEventCount> calculated = insights.dailyEventCounts();
    LocalDate firstDate =
        calculated.isEmpty() ? month.atDay(1) : calculated.getLast().eventDate().plusDays(1);
    return dashboardInsightsCalculationService.eventCountsBetween(organizationId, firstDate, today);
  }

  // 오늘 값이 이 서버 메모리에 있으면 그것을, 없으면 저장소의 것을, 그것도 없으면 DB에서 계산한다.
  private DashboardInsightsResponse calculatedInsights(UUID organizationId, YearMonth month) {
    DashboardInsightsKey key =
        new DashboardInsightsKey(organizationId, BusinessEnvironmentContext.scopeId(), month);
    OffsetDateTime refreshedAt =
        DashboardInsightsSchedule.latestRefreshAt(OffsetDateTime.now(clock));
    Optional<DashboardInsightsResponse> held = dashboardInsightsMemory.find(key, refreshedAt);
    if (held.isPresent()) return held.get();
    DashboardInsightsResponse insights = stored(key, refreshedAt).orElseGet(() -> calculated(key));
    dashboardInsightsMemory.hold(key, insights, refreshedAt);
    return insights;
  }

  private Optional<DashboardInsightsResponse> stored(
      DashboardInsightsKey key, OffsetDateTime refreshedAt) {
    try {
      return dashboardInsightsStore
          .find(key)
          .filter(insights -> !insights.calculatedAt().isBefore(refreshedAt));
    } catch (DashboardInsightsStoreException e) {
      // 저장해 둔 값은 다시 계산할 수 있으므로 읽지 못하면 DB에서 계산한다
      log.warn("인사이트를 저장소에서 읽지 못해 다시 계산합니다", e);
      return Optional.empty();
    }
  }

  private DashboardInsightsResponse calculated(DashboardInsightsKey key) {
    DashboardInsightsResponse insights =
        dashboardInsightsCalculationService.calculate(key.organizationId(), key.month());
    try {
      dashboardInsightsStore.save(key, insights);
    } catch (DashboardInsightsStoreException e) {
      // 저장하지 못해도 이번 조회는 방금 계산한 값으로 답한다
      log.warn("인사이트를 저장소에 쓰지 못했습니다", e);
    }
    return insights;
  }
}
