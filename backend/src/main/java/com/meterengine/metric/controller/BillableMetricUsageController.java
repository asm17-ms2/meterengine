package com.meterengine.metric.controller;

import com.meterengine.global.error.ErrorResponse;
import com.meterengine.metric.dto.ListBillableMetricUsagesResponse;
import com.meterengine.metric.service.BillableMetricUsageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/usage")
public class BillableMetricUsageController {

  private final BillableMetricUsageService billableMetricUsageService;

  BillableMetricUsageController(BillableMetricUsageService billableMetricUsageService) {
    this.billableMetricUsageService = billableMetricUsageService;
  }

  @GetMapping
  @Operation(
      summary = "고객별 월 사용량 조회",
      description =
          """
          도입사의 미터마다 그 미터의 event_type과 맞는 이벤트를 고객별로 합산해 돌려준다.
          기간은 KST 기준의 달이다. occurred_at 2026-08-31T23:59:59+09:00 이벤트는 8월에,
          2026-09-01T00:00:00+09:00 이벤트는 9월에 귀속된다.
          도입사의 모든 고객이 응답에 들어가며, 이벤트가 없는 고객은 quantity가 0이다.
          properties의 target_property 값이 숫자가 아닌 이벤트는 합계에서 빠진다.
          금액은 내지 않는다 (사용량 x 단가는 청구 예정액 조회의 몫).
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "미터별/고객별 사용량. 미터가 없는 도입사는 billable_metric_usages가 빈 배열이다"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            "X-Organization-Id 누락/형식 오류, 또는 month 형식 오류. code=validation_error이고 errors에 필드명과 사유가 들어 있다")
  })
  public ListBillableMetricUsagesResponse aggregateBillableMetricUsages(
      @Parameter(description = "도입사 ID. 인증이 붙으면 대체될 임시 헤더다.") @RequestHeader("X-Organization-Id")
          UUID organizationId,
      @Parameter(description = "집계할 달(yyyy-MM, KST). 생략하면 이번 달이다.", example = "2026-08")
          @RequestParam(required = false)
          @DateTimeFormat(pattern = "yyyy-MM")
          YearMonth month) {
    YearMonth target = month == null ? BillableMetricUsageService.currentMonth() : month;
    return ListBillableMetricUsagesResponse.of(
        target, billableMetricUsageService.aggregate(organizationId, target));
  }
}
