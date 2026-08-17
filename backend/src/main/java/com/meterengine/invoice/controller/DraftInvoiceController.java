package com.meterengine.invoice.controller;

import com.meterengine.invoice.dto.DraftInvoiceResponse;
import com.meterengine.invoice.service.DraftInvoiceService;
import com.meterengine.metric.service.UsageAggregationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 청구 예정액 조회 (MS2-124).
 *
 * <p><b>X-Organization-Id는 임시물이다.</b> 지금은 요청이 도입사를 자칭하기만 하면 통과하므로 누구나 아무 도입사를 사칭할 수 있다. MS2-126이
 * Bearer API 키 인증을 붙이면서 이 헤더를 인증 주체에서 꺼내는 형태로 바꾼다. 그때 헤더 누락은 400이 아니라 401이 된다 (수집, 사용량 API와 같은 사정).
 *
 * <p>전용 {@code @RestControllerAdvice}를 두지 않는다. 여기서 나는 4xx는 헤더 누락과 month 형식 오류뿐이고 둘 다 프레임워크 예외라,
 * {@code spring.mvc.problemdetails.enabled=true}가 이미 problem+json으로 내보낸다. 등록되지 않은 도입사는 고객도 미터도 없어 빈
 * 결과가 된다 (사용량 API와 같은 동작).
 */
@RestController
@RequestMapping("/v1/invoice")
public class DraftInvoiceController {

  private final DraftInvoiceService invoiceService;

  DraftInvoiceController(DraftInvoiceService invoiceService) {
    this.invoiceService = invoiceService;
  }

  @GetMapping
  @Operation(
      summary = "청구 예정액 조회",
      description =
          """
          도입사의 모든 고객에 대해 미터별 사용량 x 단가로 청구 예정액을 계산해 돌려준다.
          집계는 조회 시점에 계산하며 저장하지 않는다. 기간은 KST 기준의 달이다.
          금액은 원 단위 정수이고, 라인(고객 x 미터)마다 원 미만을 절사한 뒤 합산한다.
          이벤트가 없는 고객도 모든 미터 라인이 수량 0, 금액 0으로 들어간다.
          고객 순서는 이름 오름차순, 라인 순서는 미터 code 오름차순으로 고정이다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "고객별 청구 예정액. 미터가 없는 도입사는 각 고객의 lines가 빈 배열이다"),
    // content를 주지 않으면 400 스키마가 200의 것(DraftInvoiceResponse)으로 문서에 나간다 (MS2-140 실측).
    // 실제로는 spring.mvc.problemdetails.enabled=true가 problem+json을 내보낸다. 다만 이 엔드포인트에는
    // 전용 advice가 없어 code 확장 멤버가 붙지 않는다.
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)),
        description = "X-Organization-Id 누락/형식 오류, 또는 month 형식 오류. code 확장 멤버는 없다")
  })
  public DraftInvoiceResponse preview(
      @Parameter(description = "도입사 ID. MS2-126의 Bearer 인증으로 대체될 임시 헤더다.")
          @RequestHeader("X-Organization-Id")
          UUID organizationId,
      @Parameter(description = "집계할 달(yyyy-MM, KST). 생략하면 이번 달이다.", example = "2026-08")
          @RequestParam(required = false)
          @DateTimeFormat(pattern = "yyyy-MM")
          YearMonth month) {
    YearMonth target = month == null ? UsageAggregationService.currentMonth() : month;
    return invoiceService.preview(organizationId, target);
  }
}
