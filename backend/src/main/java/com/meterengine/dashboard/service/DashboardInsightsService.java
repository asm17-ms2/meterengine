package com.meterengine.dashboard.service;

import com.meterengine.dashboard.dto.DashboardInsightsResponse;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DashboardInsightsService {

  private final DashboardInsightsCalculationService dashboardInsightsCalculationService;

  DashboardInsightsService(
      DashboardInsightsCalculationService dashboardInsightsCalculationService) {
    this.dashboardInsightsCalculationService = dashboardInsightsCalculationService;
  }

  public DashboardInsightsResponse get(UUID organizationId, YearMonth month) {
    return dashboardInsightsCalculationService.calculate(organizationId, month);
  }
}
