package com.meterengine.invoice.controller;

import com.meterengine.global.error.ErrorResponse;
import com.meterengine.global.validation.FourDigitYear;
import com.meterengine.invoice.dto.ListInvoicesResponse;
import com.meterengine.invoice.service.InvoiceService;
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
@RequestMapping("/v1/invoices")
public class InvoiceController {

  private final InvoiceService invoiceService;

  InvoiceController(InvoiceService invoiceService) {
    this.invoiceService = invoiceService;
  }

  @GetMapping
  @Operation(
      summary = "인보이스 목록 조회",
      description =
          """
          확정된 인보이스를 (고객, 달)마다 한 줄씩 돌려준다. 확정 전의 금액은 청구 예정액 조회로 본다.
          customer_id와 month를 함께 주면 둘 다 만족하는 인보이스만 나온다. month를 생략하면 모든 달이다.
          달 내림차순이고, 같은 달은 고객 이름 오름차순, 이름이 같으면 고객 id 순이다.
          total_amount는 확정할 때 저장한 합계(공급가액 + 세액)이고 원 단위 정수다. 라인은 싣지 않는다.
          finalized_at은 KST(+09:00)로 나온다. customer_name은 확정할 때 저장한 고객 이름이라 뒤에 이름을 고쳐도 바뀌지 않는다. 페이지를 나누지 않는다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "인보이스 목록. 조건에 맞는 것이 없으면 invoices가 빈 배열이다"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            "code=validation_error: X-Organization-Id가 없거나 UUID가 아니거나, customer_id가 UUID가 아니거나, month가 yyyy-MM이 아니거나 0001-01부터 9999-12까지의 범위 밖이다"),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=customer_not_found: customer_id가 가리키는 고객이 없다")
  })
  public ListInvoicesResponse listInvoices(
      @Parameter(description = "도입사 ID. 인증이 붙기 전까지 쓰는 임시 헤더다.") @RequestHeader("X-Organization-Id")
          UUID organizationId,
      @Parameter(description = "고객을 좁힌다. 생략하면 모든 고객이 대상이다.")
          @RequestParam(name = "customer_id", required = false)
          UUID customerId,
      @Parameter(description = "인보이스의 달(yyyy-MM, KST). 생략하면 모든 달이다.", example = "2026-08")
          @RequestParam(required = false)
          @DateTimeFormat(pattern = "yyyy-MM")
          @FourDigitYear
          YearMonth month) {
    return invoiceService.list(organizationId, customerId, month);
  }
}
