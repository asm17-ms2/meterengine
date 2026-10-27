package com.meterengine.metric.controller;

import com.meterengine.global.error.ErrorResponse;
import com.meterengine.global.security.CurrentOrganizationId;
import com.meterengine.global.security.ReadAccess;
import com.meterengine.global.validation.FourDigitYear;
import com.meterengine.metric.dto.ListBillableMetricUsagesResponse;
import com.meterengine.metric.service.BillableMetricUsageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.time.Clock;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/usage")
public class BillableMetricUsageController {

  private final BillableMetricUsageService billableMetricUsageService;
  private final Clock clock;

  BillableMetricUsageController(
      BillableMetricUsageService billableMetricUsageService, Clock clock) {
    this.billableMetricUsageService = billableMetricUsageService;
    this.clock = clock;
  }

  @GetMapping
  @ReadAccess
  @Operation(
      summary = "고객별 월 사용량 조회",
      description =
          """
          미터마다 type이 그 미터의 event_type과 같은 이벤트를 고객별로 합산해 돌려준다.
          기간은 KST 기준의 달이다. occurred_at 2026-08-31T23:59:59+09:00 이벤트는 8월에,
          2026-09-01T00:00:00+09:00 이벤트는 9월에 든다.
          지금 이후 시각의 이벤트는 집계에 들지 않는다.
          그 달 이벤트 중 집계 마감 시각(다음 달 1일 13:00 KST) 전에 서버가 받은(received_at) 것만 집계한다. 마감 뒤에 받은 그 달 이벤트는 저장되지만 집계와 청구에 들지 않는다.
          모든 고객이 응답에 나오며, 이벤트가 없는 고객은 quantity가 0이고 dimension_usages가 빈 배열이다.
          dimension_usages는 미터의 dimension_properties마다 이벤트 properties에서 뽑은 값의 조합별로 수량을 나눈 것이고,
          고객의 quantity는 그 합이다. 미터에 dimension_properties가 없으면 dimension_values가 빈 객체인 요소 하나다.
          properties의 target_property 값이 숫자가 아닌 이벤트는 합계에서 빠진다.
          금액은 싣지 않는다. 금액은 청구 예정액 조회(GET /v1/invoices/draft)가 준다.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "미터별, 고객별 사용량. 등록한 미터가 없으면 billable_metric_usages가 빈 배열이다"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=validation_error: month가 yyyy-MM이 아니거나 0001-01부터 9999-12까지의 범위 밖이다")
  })
  public ListBillableMetricUsagesResponse aggregateBillableMetricUsages(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = "집계할 달(yyyy-MM, KST). 생략하면 이번 달이다.", example = "2026-08")
          @RequestParam(required = false)
          @DateTimeFormat(pattern = "yyyy-MM")
          @FourDigitYear
          YearMonth month) {
    YearMonth targetMonth = month == null ? BillableMetricUsageService.currentMonth(clock) : month;
    return ListBillableMetricUsagesResponse.of(
        targetMonth, billableMetricUsageService.aggregate(organizationId, targetMonth));
  }
}
