package com.meterengine.pricing.controller;

import com.meterengine.global.security.CurrentOrganizationId;
import com.meterengine.global.security.ReadAccess;
import com.meterengine.pricing.dto.ListBillableMetricPricesResponse;
import com.meterengine.pricing.service.BillableMetricPriceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BillableMetricPriceController {

  private final BillableMetricPriceService billableMetricPriceService;

  BillableMetricPriceController(BillableMetricPriceService billableMetricPriceService) {
    this.billableMetricPriceService = billableMetricPriceService;
  }

  @GetMapping("/v1/billable-metric-prices")
  @ReadAccess
  @Operation(
      summary = "미터별 가격 목록 조회",
      description =
          """
          등록한 미터를 code 오름차순으로 전부 돌려주고, 미터마다 dimension_properties와 조합별 단가를 붙인다.
          dimension_properties가 빈 배열이면 이벤트 속성에 따라 단가가 갈리지 않는다는 뜻이다.
          price_rates는 그 미터의 단가 전부를 dimension_values 오름차순으로 실은 배열이고, 단가가 없으면 빈 배열이다.
          dimension_values가 빈 객체인 요소가 속성 조건 없이 적용되는 기본 단가다.
          페이지를 나누지 않는다.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "미터별 가격 목록. 미터가 없으면 billable_metric_prices가 빈 배열이다")
  })
  public ListBillableMetricPricesResponse listBillableMetricPrices(
      @CurrentOrganizationId UUID organizationId) {
    return billableMetricPriceService.list(organizationId);
  }
}
