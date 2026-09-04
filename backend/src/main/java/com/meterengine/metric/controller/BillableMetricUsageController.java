package com.meterengine.metric.controller;

import com.meterengine.ProblemResponse;
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

/**
 * 고객별 월 사용량 조회 (MS2-138).
 *
 * <p>집계는 조회 시점에 계산한다. 스냅샷 테이블을 두지 않는다 (MS2-124 인수 조건 "집계시점은 조회시 계산으로 한다").
 *
 * <p><b>X-Organization-Id는 임시물이다.</b> 지금은 요청이 도입사를 자칭하기만 하면 통과하므로 누구나 아무 도입사를 사칭할 수 있다. MS2-126이
 * Bearer API 키 인증을 붙이면서 이 파라미터를 인증 주체에서 꺼내는 형태로 바꾼다. 그때 헤더 누락은 400이 아니라 401이 된다 (수집 API와 같은 사정).
 *
 * <p>전용 {@code @RestControllerAdvice}를 두지 않는다. 여기서 나는 4xx는 헤더 누락과 month 형식 오류뿐이고 둘 다 프레임워크 예외라,
 * {@code spring.mvc.problemdetails.enabled=true}가 이미 problem+json으로 내보낸다.
 */
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
    @ApiResponse(responseCode = "200", description = "미터별/고객별 사용량. 미터가 없는 도입사는 metrics가 빈 배열이다"),
    // content를 주지 않으면 400 스키마가 200의 것(ListBillableMetricUsagesResponse)으로 문서에 나간다 (MS2-140 실측).
    // 실제로는 spring.mvc.problemdetails.enabled=true가 problem+json을 내보낸다.
    //
    // [2026-08-17, MS2-150 7단계] 예전 주석은 "전용 advice가 없어 code 확장 멤버가 붙지 않는다"고 적었는데
    // 4단계 이후로 거짓이다. FrameworkExceptionHandler가 프레임워크 4xx 전부에 code를 붙이므로 이 엔드포인트도
    // /v1/events와 같은 모양으로 답한다. 그래서 스키마도 하나(ProblemResponse)로 합쳤다.
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemResponse.class)),
        description =
            "X-Organization-Id 누락/형식 오류, 또는 month 형식 오류. code=validation_error이고 errors에 필드명과 사유가 들어 있다")
  })
  public ListBillableMetricUsagesResponse aggregateBillableMetricUsages(
      @Parameter(description = "도입사 ID. MS2-126의 Bearer 인증으로 대체될 임시 헤더다.")
          @RequestHeader("X-Organization-Id")
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
