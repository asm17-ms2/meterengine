package com.meterengine.pricing.controller;

import com.meterengine.global.security.CurrentOrganizationId;
import com.meterengine.global.security.EnvironmentReady;
import com.meterengine.global.security.ReadAccess;
import com.meterengine.global.validation.FourDigitYear;
import com.meterengine.pricing.dto.ListBillableMetricPricesResponse;
import com.meterengine.pricing.service.BillableMetricPriceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@EnvironmentReady
public class BillableMetricPriceController {

  private final BillableMetricPriceService billableMetricPriceService;

  BillableMetricPriceController(BillableMetricPriceService billableMetricPriceService) {
    this.billableMetricPriceService = billableMetricPriceService;
  }

  @GetMapping("/v1/billable-metric-prices")
  @ReadAccess
  @Operation(
      summary = "미터별 가격 목록 조회",
      description = "지정 월에 유효한 조합별 최신 단가를 돌려준다. 월은 기본 이번 달이며 customer_id가 없으면 도입사 공통 단가만 조회한다.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "미터별 가격 목록. 미터가 없으면 billable_metric_prices가 빈 배열이다")
  })
  public ListBillableMetricPricesResponse listBillableMetricPrices(
      @CurrentOrganizationId UUID organizationId,
      @RequestParam(required = false) @FourDigitYear @DateTimeFormat(pattern = "yyyy-MM")
          YearMonth month,
      @RequestParam(name = "customer_id", required = false) UUID customerId) {
    return billableMetricPriceService.list(organizationId, month, customerId);
  }
}
