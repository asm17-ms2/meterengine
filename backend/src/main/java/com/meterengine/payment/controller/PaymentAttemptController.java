package com.meterengine.payment.controller;

import com.meterengine.global.error.ErrorResponse;
import com.meterengine.payment.dto.PaymentAttemptResponse;
import com.meterengine.payment.service.PaymentAttemptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentAttemptController {

  private final PaymentAttemptService paymentAttemptService;

  PaymentAttemptController(PaymentAttemptService paymentAttemptService) {
    this.paymentAttemptService = paymentAttemptService;
  }

  @PostMapping("/v1/invoices/{id}/payment-attempts")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "인보이스 결제 요청",
      description =
          """
          인보이스의 total_amount를 그 인보이스의 고객에게 청구한다. 본문은 받지 않는다.
          지금은 Mock PG라 항상 승인되고, status=done인 결제 시도가 남는다. payment_key는 PG가 준 승인 키다.
          인보이스 하나에 결제 중이거나 결제된 시도는 하나뿐이다. 이미 있으면 409다.
          requested_at과 completed_at은 KST(+09:00)로 나온다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "승인된 결제 시도"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            "code=validation_error: X-Organization-Id가 없거나 UUID가 아니거나, 경로의 id가 UUID가 아니다"),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=invoice_not_found: 그런 인보이스가 없다"),
    @ApiResponse(
        responseCode = "409",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            """
            code=invoice_already_paid: 이 인보이스에 결제 중이거나 결제된 시도가 이미 있다.
            code=invoice_amount_zero: 인보이스의 total_amount가 0원이다.
            """)
  })
  public PaymentAttemptResponse createPaymentAttempt(
      @Parameter(description = "도입사 ID. 인증이 붙기 전까지 쓰는 임시 헤더다.") @RequestHeader("X-Organization-Id")
          UUID organizationId,
      @Parameter(description = "결제할 인보이스의 id.") @PathVariable UUID id) {
    return paymentAttemptService.create(organizationId, id);
  }
}
