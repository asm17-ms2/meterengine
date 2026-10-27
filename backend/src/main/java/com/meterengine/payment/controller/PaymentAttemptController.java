package com.meterengine.payment.controller;

import com.meterengine.global.error.ErrorResponse;
import com.meterengine.global.security.AdminAccess;
import com.meterengine.global.security.AuthenticatedPrincipal;
import com.meterengine.global.security.CurrentOrganizationId;
import com.meterengine.global.security.EnvironmentReady;
import com.meterengine.global.security.ReadAccess;
import com.meterengine.global.validation.StorableText;
import com.meterengine.payment.dto.ListPaymentAttemptsResponse;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@EnvironmentReady
public class PaymentAttemptController {

  private final PaymentAttemptService paymentAttemptService;

  PaymentAttemptController(PaymentAttemptService paymentAttemptService) {
    this.paymentAttemptService = paymentAttemptService;
  }

  @PostMapping("/v1/invoices/{id}/payment-attempts")
  @AdminAccess
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "인보이스 결제 요청",
      description =
          """
          인보이스의 total_amount를 그 고객의 등록 카드로 토스페이먼츠 자동결제 승인을 요청한다. 본문은 받지 않는다.
          도입사의 현재 결제 모드(test 또는 live)의 토스페이먼츠 키와 그 모드로 등록한 카드를 쓴다.
          승인되면 status=done과 payment_key, 거절되면 status=failed와 failure_code, failure_message가 남고 둘 다 201이다.
          토스페이먼츠 응답으로 결과를 확인하지 못하면 status=pending인 시도를 201로 돌려준다.
          결제 모드마다 인보이스 하나에 결제 중이거나 결제된 시도는 하나뿐이다. 이미 있으면 409다.
          requested_at과 completed_at은 KST(+09:00)로 나온다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "결제 시도. status는 done, failed, pending 중 하나"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            """
            code=validation_error: 경로의 id가 UUID가 아니거나, TossPayments-Test-Code에 NUL 문자나 짝이 없는 UTF-16 서로게이트가 있다.
            code=toss_payments_test_code_in_live_mode: 결제 모드가 live인데 TossPayments-Test-Code를 보냈다.
            """),
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
            code=invoice_already_paid: 이 결제 모드로 결제 중이거나 결제된 시도가 이미 있다.
            code=payment_result_unconfirmed: 이전 결제 시도가 미청구로 확인되지 않았다. 이전 결제 결과를 확인한 뒤 다시 요청한다.
            code=invoice_amount_zero: 인보이스의 total_amount가 0원이다.
            code=toss_payments_credential_missing: 결제 모드의 토스페이먼츠 키가 없다.
            code=payment_method_missing: 인보이스의 고객이 결제 모드로 등록한 카드가 없다.
            """)
  })
  public PaymentAttemptResponse createPaymentAttempt(
      @CurrentOrganizationId UUID organizationId,
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @Parameter(description = "결제할 인보이스의 id.") @PathVariable UUID id,
      @Parameter(description = "test 모드에서만 받는다. 값을 그대로 토스페이먼츠에 넘겨 실패 응답을 재현한다. 빈 값은 보내지 않은 것으로 본다.")
          @RequestHeader(name = "TossPayments-Test-Code", required = false)
          @StorableText
          String testCode) {
    return paymentAttemptService.create(
        organizationId, id, principal.mode(), StringUtils.hasText(testCode) ? testCode : null);
  }

  @GetMapping("/v1/invoices/{id}/payment-attempts")
  @ReadAccess
  @Operation(
      summary = "인보이스 결제 시도 목록",
      description =
          """
          인보이스의 결제 시도를 모든 결제 모드에 걸쳐 요청 시각이 늦은 것부터 돌려준다.
          requested_at과 completed_at은 KST(+09:00)로 나온다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "결제 시도 목록"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=validation_error: 경로의 id가 UUID가 아니다"),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=invoice_not_found: 그런 인보이스가 없다")
  })
  public ListPaymentAttemptsResponse listPaymentAttempts(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = "인보이스의 id.") @PathVariable UUID id) {
    return paymentAttemptService.list(organizationId, id);
  }
}
