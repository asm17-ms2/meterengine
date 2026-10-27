package com.meterengine.payment.controller;

import com.meterengine.global.error.ErrorResponse;
import com.meterengine.global.security.PublicAccess;
import com.meterengine.global.validation.StorableText;
import com.meterengine.payment.dto.CreateBillingKeyRequest;
import com.meterengine.payment.dto.PaymentMethodResponse;
import com.meterengine.payment.service.BillingKeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BillingKeyController {

  private final BillingKeyService billingKeyService;

  BillingKeyController(BillingKeyService billingKeyService) {
    this.billingKeyService = billingKeyService;
  }

  @PostMapping("/v1/payment-method-registrations/{token}/billing-keys")
  @PublicAccess
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "빌링키 발급",
      description =
          """
          카드 등록 페이지가 토스페이먼츠 SDK로 받은 authKey를 보내면 빌링키를 발급하고 고객의 카드로 저장한다.
          일회용 카드 등록 토큰으로 인증하는 공개 API다.
          링크를 만든 결제 모드의 카드로 저장한다. 그 모드에 카드가 있으면 새 카드로 바꾸고 옛 빌링키는 토스페이먼츠에서 지운다.
          성공하면 링크는 쓰인 것이 되어 다시 쓸 수 없다. 토스페이먼츠가 거절했거나 결과를 알 수 없으면 링크는 쓰이지 않은 채 남는다.
          빌링키 원문은 응답에 싣지 않는다. authenticated_at은 KST(+09:00)로 나온다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "저장한 고객 카드"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            """
            code=validation_error: auth_key, customer_key, consent_document_version 중 비었거나, 문자열에 NUL 문자나 짝이 없는 UTF-16 서로게이트가 있거나, customer_key가 링크의 고객과 다르다.
            code=payment_method_rejected: 토스페이먼츠가 카드 인증을 거절했다.
            """),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=payment_method_registration_not_found: 링크가 없거나, 만료됐거나, 이미 쓰였다"),
    @ApiResponse(
        responseCode = "409",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=toss_payments_credential_missing: 링크를 만든 결제 모드의 토스페이먼츠 키가 없다"),
    @ApiResponse(
        responseCode = "503",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=toss_payments_unavailable: 토스페이먼츠 응답으로 빌링키 발급 결과를 확인하지 못했다")
  })
  public PaymentMethodResponse createBillingKey(
      @Parameter(description = "카드 등록 링크를 만들 때 받은 token.") @PathVariable @StorableText String token,
      @Valid @RequestBody CreateBillingKeyRequest request) {
    return billingKeyService.create(token, request);
  }
}
