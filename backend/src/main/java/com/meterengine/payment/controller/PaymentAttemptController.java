package com.meterengine.payment.controller;

import com.meterengine.global.error.ErrorResponse;
import com.meterengine.global.security.AuthenticatedPrincipal;
import com.meterengine.global.security.CurrentOrganizationId;
import com.meterengine.global.security.EnvironmentReady;
import com.meterengine.global.security.PaymentsAccess;
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
  @PaymentsAccess
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "청구서 결제 요청",
      description =
          """
          청구서의 total_amount만큼 고객의 등록 카드로 토스페이먼츠 자동결제 승인을 요청합니다. 본문은 받지 않습니다.
          현재 업무 환경의 결제 모드(test 또는 live)에 저장한 토스페이먼츠 키와 그 결제 모드로 등록한 카드를 씁니다.
          승인되면 status=done과 payment_key, 거절되면 status=failed와 failure_code, failure_message가 남고 둘 다 201입니다.
          토스페이먼츠 응답으로 결과를 확인하지 못하면 status=pending인 시도를 201로 돌려줍니다.
          결제 모드마다 청구서 하나에 결제 중이거나 결제된 시도는 하나뿐입니다. 이미 있으면 409입니다.
          requested_at과 completed_at은 KST(+09:00)로 나옵니다.
          payments 권한이 필요합니다. admin 권한만으로는 부를 수 없습니다.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "결제 시도입니다. status는 done, failed, pending 중 하나입니다."),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            """
            code=validation_error: 경로의 id가 UUID가 아니거나, TossPayments-Test-Code에 NUL 문자나 짝이 없는 UTF-16 서로게이트가 있습니다.
            code=toss_payments_test_code_in_live_mode: 결제 모드가 live인데 TossPayments-Test-Code를 보냈습니다.
            """),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=invoice_not_found: 그런 청구서가 없습니다."),
    @ApiResponse(
        responseCode = "409",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            """
            code=invoice_already_paid: 이 결제 모드로 결제 중이거나 결제된 시도가 이미 있습니다.
            code=payment_result_unconfirmed: 결과를 모르는 이전 결제 시도가 있습니다. 토스페이먼츠에서 결과를 확인한 뒤 다시 요청합니다.
            code=invoice_amount_zero: 청구서의 total_amount가 0원입니다.
            code=toss_payments_credential_missing: 이 결제 모드의 토스페이먼츠 키가 없습니다.
            code=payment_method_missing: 청구서의 고객이 이 결제 모드로 등록한 카드가 없습니다.
            code=payment_mode_mismatch: 테스트 키인지 라이브 키인지가 업무 환경의 결제 모드와 다릅니다.
            """)
  })
  public PaymentAttemptResponse createPaymentAttempt(
      @CurrentOrganizationId UUID organizationId,
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @Parameter(description = "결제할 청구서의 ID") @PathVariable UUID id,
      @Parameter(
              description =
                  "결제 모드가 test일 때만 받습니다. 값을 그대로 토스페이먼츠에 넘겨 실패 응답을 재현합니다. 빈 값은 보내지 않은 것으로 봅니다.")
          @RequestHeader(name = "TossPayments-Test-Code", required = false)
          @StorableText
          String testCode) {
    return paymentAttemptService.create(
        organizationId, id, principal.mode(), StringUtils.hasText(testCode) ? testCode : null);
  }

  @GetMapping("/v1/invoices/{id}/payment-attempts")
  @ReadAccess
  @Operation(
      summary = "청구서 결제 시도 목록 조회",
      description =
          """
          청구서의 결제 시도를 모든 결제 모드에 걸쳐 요청 시각이 늦은 것부터 돌려줍니다.
          requested_at과 completed_at은 KST(+09:00)로 나옵니다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "결제 시도 목록"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=validation_error: 경로의 id가 UUID가 아닙니다."),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=invoice_not_found: 그런 청구서가 없습니다.")
  })
  public ListPaymentAttemptsResponse listPaymentAttempts(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = "결제 시도를 볼 청구서의 ID") @PathVariable UUID id) {
    return paymentAttemptService.list(organizationId, id);
  }
}
