package com.meterengine.invoice.controller;

import com.meterengine.ProblemResponse;
import com.meterengine.invoice.dto.DraftInvoiceResponse;
import com.meterengine.invoice.service.DraftInvoiceService;
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
          이벤트가 없는 고객도 단가가 있는 모든 미터 라인이 수량 0, 금액 0으로 들어간다.
          고객 순서는 이름 오름차순, 라인 순서는 미터 code 오름차순으로 고정이다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "고객별 청구 예정액. 미터가 없는 도입사는 각 고객의 lines가 빈 배열이다"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemResponse.class)),
        description =
            "X-Organization-Id 누락/형식 오류, 또는 month 형식 오류. code=validation_error이고 errors에 필드명과 사유가 들어 있다")
  })
  public DraftInvoiceResponse preview(
      @Parameter(description = "도입사 ID. 인증이 아직 없어서 쓰는 임시 헤더다.") @RequestHeader("X-Organization-Id")
          UUID organizationId,
      @Parameter(description = "집계할 달(yyyy-MM, KST). 생략하면 이번 달이다.", example = "2026-08")
          @RequestParam(required = false)
          @DateTimeFormat(pattern = "yyyy-MM")
          YearMonth month) {
    YearMonth target = month == null ? BillableMetricUsageService.currentMonth() : month;
    return invoiceService.preview(organizationId, target);
  }
}
