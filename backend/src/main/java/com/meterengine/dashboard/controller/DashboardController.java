package com.meterengine.dashboard.controller;

import com.meterengine.dashboard.dto.DashboardResponse;
import com.meterengine.dashboard.service.DashboardService;
import com.meterengine.global.security.CurrentOrganizationId;
import com.meterengine.global.security.EnvironmentReady;
import com.meterengine.global.security.ReadAccess;
import com.meterengine.global.validation.FourDigitYear;
import com.meterengine.metric.service.BillableMetricUsageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
          "로그인 세션이나 read 권한 API 키로 현재 업무 환경의 월별 현황을 돌려줍니다. 청구 예정액은 공급가액이고, 확정 청구액과 받은 금액은 부가세 포함 금액입니다."
              + " 취소한 금액은 받을 금액에 다시 더하지 않습니다.")
  public DashboardResponse getDashboard(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = "조회할 달(yyyy-MM, KST)입니다. 생략하면 이번 달입니다.")
          @RequestParam(required = false)
          @DateTimeFormat(pattern = "yyyy-MM")
          @FourDigitYear
          YearMonth month) {
    return dashboardService.get(
        organizationId,
        month == null
            ? YearMonth.now(clock.withZone(BillableMetricUsageService.BILLING_ZONE))
            : month);
  }
}
