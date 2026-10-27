package com.meterengine.payment.controller;

import com.meterengine.global.error.ErrorResponse;
import com.meterengine.global.security.AdminAccess;
import com.meterengine.global.security.CurrentOrganizationId;
import com.meterengine.global.security.PublicAccess;
import com.meterengine.global.validation.StorableText;
import com.meterengine.payment.dto.PaymentMethodRegistrationResponse;
import com.meterengine.payment.service.PaymentMethodRegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentMethodRegistrationController {

  private final PaymentMethodRegistrationService paymentMethodRegistrationService;

  PaymentMethodRegistrationController(
      PaymentMethodRegistrationService paymentMethodRegistrationService) {
    this.paymentMethodRegistrationService = paymentMethodRegistrationService;
  }

  @PostMapping("/v1/customers/{id}/payment-method-registrations")
  @AdminAccess
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "카드 등록 링크 생성",
      description =
          """
          고객이 카드를 등록할 일회용 링크를 만든다. 본문은 받지 않는다.
          token은 이 응답에만 실린다. 서버는 token 원문을 저장하지 않으므로 다시 조회할 수 없다.
          링크는 지금 결제 모드로 만들고 24시간 뒤 만료된다. 카드 등록에 한 번 쓰면 다시 쓸 수 없다.
          expires_at은 KST(+09:00)로 나온다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "만든 카드 등록 링크"),
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
        description = "code=customer_not_found: 그런 고객이 없다"),
    @ApiResponse(
        responseCode = "409",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=toss_payments_credential_missing: 지금 결제 모드의 토스페이먼츠 키가 없다")
  })
  public PaymentMethodRegistrationResponse createPaymentMethodRegistration(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = "카드를 등록할 고객의 id.") @PathVariable UUID id) {
    return paymentMethodRegistrationService.create(organizationId, id);
  }

  @GetMapping("/v1/payment-method-registrations/{token}")
  @PublicAccess
  @Operation(
      summary = "카드 등록 링크 조회",
      description =
          """
          카드 등록 페이지가 토스페이먼츠 SDK를 띄우는 데 쓸 값을 돌려준다. 일회용 카드 등록 토큰으로 인증하는 공개 API다.
          token은 응답에 싣지 않는다.
          없거나, 만료됐거나, 이미 카드 등록에 쓴 링크는 모두 404다.
          expires_at은 KST(+09:00)로 나온다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "카드 등록 링크"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=validation_error: token에 NUL 문자나 짝이 없는 UTF-16 서로게이트가 있다"),
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
        description = "code=toss_payments_credential_missing: 링크를 만든 결제 모드의 토스페이먼츠 키가 없다")
  })
  public PaymentMethodRegistrationResponse getPaymentMethodRegistration(
      @Parameter(description = "카드 등록 링크를 만들 때 받은 token.") @PathVariable @StorableText
          String token) {
    return paymentMethodRegistrationService.get(token);
  }
}
