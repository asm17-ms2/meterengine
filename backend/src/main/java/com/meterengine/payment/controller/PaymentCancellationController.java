package com.meterengine.payment.controller;

import com.meterengine.global.error.ErrorResponse;
import com.meterengine.global.security.AdminAccess;
import com.meterengine.global.security.AuthenticatedPrincipal;
import com.meterengine.global.security.CurrentOrganizationId;
import com.meterengine.global.security.EnvironmentReady;
import com.meterengine.global.security.ReadAccess;
import com.meterengine.payment.dto.CreatePaymentCancellationRequest;
import com.meterengine.payment.dto.ListPaymentCancellationsResponse;
import com.meterengine.payment.dto.PaymentCancellationResponse;
import com.meterengine.payment.service.PaymentCancellationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@EnvironmentReady
public class PaymentCancellationController {

  private final PaymentCancellationService paymentCancellationService;

  PaymentCancellationController(PaymentCancellationService paymentCancellationService) {
    this.paymentCancellationService = paymentCancellationService;
  }

  @PostMapping("/v1/payment-attempts/{id}/cancellations")
  @AdminAccess
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "결제 취소 요청",
      description =
          """
          done인 결제 시도의 금액 전부나 일부를 토스페이먼츠에 취소 요청한다. amount를 빼면 남은 결제 금액 전부를 취소한다.
          남은 결제 금액은 시도 금액에서 done인 취소의 합을 뺀 값이다. 시도의 결제 모드 키로 토스페이먼츠를 부른다.
          취소되면 status=done, 거절되면 status=failed와 failure_code, failure_message가 남고 둘 다 201이다.
          토스페이먼츠 응답으로 결과를 확인하지 못하면 status=pending인 취소를 201로 돌려준다.
          시도 하나에 pending인 취소는 하나뿐이다. 있으면 409다. API 키의 모드가 취소 대상과 다르면 409다.
          Idempotency-Key를 주면 같은 도입사와 환경의 같은 요청을 다시 보내도 그 취소를 지금 상태로 201로 돌려준다. 같은 키로 다른 시도, 금액, 사유를 보내면 409다.
          이 키는 고객과 미터 등록의 Idempotency-Key와 계약이 다르다. UUID만 받고, 충돌 code는 payment_cancellation_idempotency_conflict이며, 다시 돌려준 응답에 Idempotency-Replayed 헤더를 붙이지 않는다.
          requested_at과 completed_at은 KST(+09:00)로 나온다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "결제 취소. status는 done, failed, pending 중 하나"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            """
            code=validation_error: 경로의 id나 Idempotency-Key가 UUID가 아니거나, amount가 양수가 아니거나, reason이 비었거나 200자를 넘거나 NUL 문자나 짝이 없는 UTF-16 서로게이트가 있다.
            code=malformed_request_body: 본문을 JSON으로 읽을 수 없거나 amount가 정수가 아니다.
            """),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=payment_attempt_not_found: 그런 결제 시도가 없다"),
    @ApiResponse(
        responseCode = "409",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            """
            code=payment_mode_mismatch: API 키의 모드가 취소 대상의 모드와 다르다.
            code=payment_attempt_not_cancelable: 시도가 done이 아니다.
            code=payment_attempt_fully_canceled: done인 시도를 이미 전액 취소해 남은 결제 금액이 0원이다.
            code=payment_cancellation_in_progress: 이 시도에 pending인 취소가 있다.
            code=payment_cancellation_amount_exceeded: amount가 남은 결제 금액보다 크다.
            code=payment_cancellation_idempotency_conflict: 같은 Idempotency-Key를 다른 취소 요청에 사용했다.
            """)
  })
  public PaymentCancellationResponse createPaymentCancellation(
      @CurrentOrganizationId UUID organizationId,
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @Parameter(description = "취소할 결제 시도의 id.") @PathVariable UUID id,
      @Parameter(description = "취소 요청마다 발급한 UUID. 응답을 받지 못해 다시 요청할 때는 같은 값을 보낸다.")
          @RequestHeader(name = "Idempotency-Key", required = false)
          UUID idempotencyKey,
      @Valid @RequestBody CreatePaymentCancellationRequest request) {
    return paymentCancellationService.create(
        organizationId, id, request, idempotencyKey, principal.mode());
  }

  @GetMapping("/v1/payment-attempts/{id}/cancellations")
  @ReadAccess
  @Operation(
      summary = "결제 취소 목록",
      description =
          """
          결제 시도의 취소를 요청 시각이 늦은 것부터 돌려준다.
          requested_at과 completed_at은 KST(+09:00)로 나온다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "결제 취소 목록"),
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
        description = "code=payment_attempt_not_found: 그런 결제 시도가 없다")
  })
  public ListPaymentCancellationsResponse listPaymentCancellations(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = "결제 시도의 id.") @PathVariable UUID id) {
    return paymentCancellationService.list(organizationId, id);
  }
}
