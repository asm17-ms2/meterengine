package com.meterengine.pricing.controller;

import com.meterengine.global.error.ErrorResponse;
import com.meterengine.pricing.dto.ListBillableMetricPricesResponse;
import com.meterengine.pricing.service.BillableMetricPriceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BillableMetricPriceController {

  private final BillableMetricPriceService billableMetricPriceService;

  BillableMetricPriceController(BillableMetricPriceService billableMetricPriceService) {
    this.billableMetricPriceService = billableMetricPriceService;
  }

  @GetMapping("/v1/billable-metric-prices")
  @Operation(
      summary = "미터별 가격 목록 조회",
      description =
          """
          이 도입사의 미터를 code 오름차순으로 전부 싣고, 미터마다 가격 정책의 축 선언과 기본 단가를 붙인다.
          정책이 없는 미터도 실리며 dimension_properties가 null이다. 빈 배열은 무차원 정책이라는 뜻이다.
          unit_price는 무차원 조합에 붙은 기본 단가이고, 없으면 null이다. 조합별 단가는 싣지 않는다.
          페이지를 나누지 않는다.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "미터별 가격 목록. 미터가 없으면 billable_metric_prices가 빈 배열이다"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=validation_error: X-Organization-Id가 없거나 UUID가 아니다")
  })
  public ListBillableMetricPricesResponse listBillableMetricPrices(
      @Parameter(description = "도입사 ID. Bearer 인증이 붙으면 대체될 임시 헤더다.")
          @RequestHeader("X-Organization-Id")
          UUID organizationId) {
    return billableMetricPriceService.list(organizationId);
  }
}
