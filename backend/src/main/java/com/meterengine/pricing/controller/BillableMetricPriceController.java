package com.meterengine.pricing.controller;

import com.meterengine.global.security.CurrentOrganizationId;
import com.meterengine.global.security.EnvironmentReady;
import com.meterengine.global.security.ReadAccess;
import com.meterengine.global.validation.FourDigitYear;
import com.meterengine.pricing.dto.ListBillableMetricPricesResponse;
import com.meterengine.pricing.service.BillableMetricPriceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
      summary = "미터별 단가 조회",
      description =
          """
          지정한 달에 적용되는 구분 값별 최신 단가를 미터마다 돌려줍니다. customer_id가 없으면 공통 단가만, 있으면 그 고객의 고객 단가만 돌려줍니다.
          미터는 보관한 미터를 포함해 미터 코드 오름차순으로 모두 나오고, 페이지를 나누지 않습니다.
          달과 고객으로만 거르면 이 경로를 씁니다. 검색어, 여러 조건, 정렬, 페이지가 필요하면 GET /v1/billable-metric-prices/search를 씁니다.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "미터별 단가 목록입니다. 미터가 없으면 billable_metric_prices가 빈 배열입니다.")
  })
  public ListBillableMetricPricesResponse listBillableMetricPrices(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = "단가를 볼 달(yyyy-MM, KST)입니다. 생략하면 이번 달입니다.", example = "2026-08")
          @RequestParam(required = false)
          @FourDigitYear
          @DateTimeFormat(pattern = "yyyy-MM")
          YearMonth month,
      @Parameter(description = "이 고객의 고객 단가만 돌려줍니다. 생략하면 공통 단가만 돌려줍니다.")
          @RequestParam(name = "customer_id", required = false)
          UUID customerId) {
    return billableMetricPriceService.list(organizationId, month, customerId);
  }
}
