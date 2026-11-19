package com.meterengine.metric.controller;

import com.meterengine.global.error.ErrorResponse;
import com.meterengine.global.security.CurrentOrganizationId;
import com.meterengine.global.security.EnvironmentReady;
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
@EnvironmentReady
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
      summary = "사용량 조회",
      description =
          """
          과금 미터마다 event_type이 같은 이벤트를 과금 미터의 집계 함수로 계산합니다.
          달 하나의 모든 과금 미터와 고객을 한 번에 보려면 이 경로를 씁니다. 검색어, 여러 조건, 정렬, 페이지가 필요하면 GET /v1/usage/search를 씁니다.
          기간은 KST 기준의 달입니다. occurred_at 2026-08-31T23:59:59+09:00 이벤트는 8월에,
          2026-09-01T00:00:00+09:00 이벤트는 9월에 듭니다.
          지금 이후 시각의 이벤트는 집계에 들지 않습니다.
          그 달 이벤트 가운데 집계 마감 시각(다음 달 2일 00:00 KST) 전에 서버가 받은(received_at) 것만 집계합니다. 마감 뒤에 받은 그 달 이벤트는 저장되지만 집계와 청구에 들지 않습니다.
          그 달에 집계하는 과금 미터는 과금 미터 코드 오름차순으로, 고객은 이름 오름차순으로 모두 나오며, 이벤트가 없는 고객은 quantity가 0이고 dimension_usages가 빈 배열입니다. 페이지를 나누지 않습니다.
          dimension_usages는 과금 미터의 dimension_properties마다 이벤트 properties에서 뽑은 구분 값별로 수량을 나눈 것입니다.
          고객 전체 수량은 원본 이벤트를 따로 집계하므로 구분 값별 수량의 합과 다를 수 있습니다. 청구는 구분 값별 정확한 수량에 단가를 곱합니다. rounding_mode가 none이면 raw_quantity_numerator / (raw_quantity_denominator * unit_size)를, 그 밖의 끝수 처리면 처리한 quantity를 씁니다.
          raw_quantity는 단위 변환 전 집계량, quantity는 unit_size 나눗셈과 끝수 처리를 적용한 결과입니다.
          count는 속성과 관계없이 전체 이벤트를 셉니다. count_distinct는 숫자, 문자열, 불리언 고유값만 세고 나머지는 뺍니다.
          다른 집계 함수는 target_property가 숫자인 이벤트만 계산합니다. 구분 키가 없으면 dimension_values가 빈 객체입니다.
          금액은 싣지 않습니다. 금액은 청구 예정액 조회(GET /v1/invoices/draft)가 줍니다.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "과금 미터별, 고객별 사용량입니다. 그 달에 집계하는 과금 미터가 없으면 billable_metric_usages가 빈 배열입니다."),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=validation_error: month가 yyyy-MM이 아니거나 0001-01부터 9999-12까지의 범위 밖입니다.")
  })
  public ListBillableMetricUsagesResponse aggregateBillableMetricUsages(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = "집계할 달(yyyy-MM, KST)입니다. 생략하면 이번 달입니다.", example = "2026-08")
          @RequestParam(required = false)
          @DateTimeFormat(pattern = "yyyy-MM")
          @FourDigitYear
          YearMonth month) {
    YearMonth targetMonth = month == null ? BillableMetricUsageService.currentMonth(clock) : month;
    return ListBillableMetricUsagesResponse.of(
        targetMonth, billableMetricUsageService.aggregate(organizationId, targetMonth));
  }
}
