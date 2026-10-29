package com.meterengine.payment.controller;

import com.meterengine.global.error.ErrorResponse;
import com.meterengine.global.security.CurrentOrganizationId;
import com.meterengine.global.security.ReadAccess;
import com.meterengine.global.validation.FourDigitYear;
import com.meterengine.payment.dto.ListInvoicePaymentsResponse;
import com.meterengine.payment.service.InvoicePaymentService;
import com.meterengine.payment.service.PaymentSettingService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InvoicePaymentController {

  private final InvoicePaymentService invoicePaymentService;
  private final PaymentSettingService paymentSettingService;

  InvoicePaymentController(
      InvoicePaymentService invoicePaymentService, PaymentSettingService paymentSettingService) {
    this.invoicePaymentService = invoicePaymentService;
    this.paymentSettingService = paymentSettingService;
  }

  @GetMapping("/v1/invoice-payments")
  @ReadAccess
  @Operation(
      summary = "인보이스별 결제 상태 목록",
      description =
          """
          인보이스마다 도입사의 현재 결제 모드(test 또는 live)로 본 결제 상태를 한 줄씩 돌려준다. 다른 결제 모드의 시도는 보지 않는다.
          상태는 그 모드에서 가장 늦게 요청한 시도로 정한다. total_amount가 0원이면 not_required, 시도가 없으면 unpaid, 시도가 pending이면 pending, failed면 failed다.
          시도가 done이면 그 시도에서 취소를 마친 금액의 합이 0원이면 paid, 결제 금액보다 적으면 partially_canceled, 결제 금액과 같으면 canceled다.
          순서와 거르는 조건은 인보이스 목록 조회와 같다. 달 내림차순이고, 같은 달은 고객 이름 오름차순, 이름이 같으면 고객 id 순이다.
          next_retry_at과 completed_at은 KST(+09:00)로 나온다. 페이지를 나누지 않는다.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "인보이스별 결제 상태. 조건에 맞는 인보이스가 없으면 invoice_payments가 빈 배열이다"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            "code=validation_error: customer_id가 UUID가 아니거나, month가 yyyy-MM이 아니거나 0001-01부터 9999-12까지의 범위 밖이다"),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=customer_not_found: customer_id가 가리키는 고객이 없다")
  })
  public ListInvoicePaymentsResponse listInvoicePayments(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = "인보이스의 달(yyyy-MM, KST). 생략하면 모든 달이다.", example = "2026-08")
          @RequestParam(required = false)
          @DateTimeFormat(pattern = "yyyy-MM")
          @FourDigitYear
          YearMonth month,
      @Parameter(description = "고객을 좁힌다. 생략하면 모든 고객이 대상이다.")
          @RequestParam(name = "customer_id", required = false)
          UUID customerId) {
    return invoicePaymentService.list(
        organizationId, month, customerId, paymentSettingService.get(organizationId).mode());
  }
}
