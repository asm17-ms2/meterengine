package com.meterengine.dashboard.controller;

import com.meterengine.dashboard.dto.DashboardResponse;
import com.meterengine.dashboard.service.DashboardService;
import com.meterengine.global.security.CurrentOrganizationId;
import com.meterengine.global.security.EnvironmentReady;
import com.meterengine.global.security.ReadAccess;
import com.meterengine.global.validation.FourDigitYear;
import com.meterengine.metric.service.BillableMetricUsageService;
import io.swagger.v3.oas.annotations.Operation;
import java.time.Clock;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@EnvironmentReady
public class DashboardController {
  private final DashboardService dashboardService;
  private final Clock clock;

  DashboardController(DashboardService dashboardService, Clock clock) {
    this.dashboardService = dashboardService;
    this.clock = clock;
  }

  @ReadAccess
  @GetMapping("/v1/dashboard")
  @Operation(
      summary = "월별 과금과 수납 현황 조회",
      description =
          "로그인 세션 또는 read API 키의 도입사 현황을 조회한다. 예정액은 공급가액이고, 확정액과 수납액은 세금 포함 금액이다. 취소는 결제 대기액으로 다시"
              + " 잡지 않는다.")
  public DashboardResponse getDashboard(
      @CurrentOrganizationId UUID organizationId,
      @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") @FourDigitYear
          YearMonth month) {
    return dashboardService.get(
        organizationId,
        month == null
            ? YearMonth.now(clock.withZone(BillableMetricUsageService.BILLING_ZONE))
            : month);
  }
}
