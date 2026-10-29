package com.meterengine.payment.controller;

import com.meterengine.global.error.ErrorResponse;
import com.meterengine.global.security.CurrentOrganizationId;
import com.meterengine.global.security.ReadAccess;
import com.meterengine.global.validation.StorableText;
import com.meterengine.payment.dto.ListPaymentMethodsResponse;
import com.meterengine.payment.dto.PaymentMethodResponse;
import com.meterengine.payment.service.PaymentMethodService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentMethodController {

  private final PaymentMethodService paymentMethodService;

  PaymentMethodController(PaymentMethodService paymentMethodService) {
    this.paymentMethodService = paymentMethodService;
  }

  @GetMapping("/v1/customers/{id}/payment-method")
  @ReadAccess
  @Operation(
      summary = "고객 카드 조회",
      description =
          """
          고객이 지금 결제 모드로 등록한 카드를 돌려준다. 빌링키 원문은 싣지 않는다.
          authenticated_at은 KST(+09:00)로 나온다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "고객 카드"),
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
        description =
            """
            code=customer_not_found: 그런 고객이 없다.
            code=payment_method_not_found: 고객이 지금 결제 모드로 등록한 카드가 없다.
            """)
  })
  public PaymentMethodResponse getPaymentMethod(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = "카드를 조회할 고객의 id.") @PathVariable UUID id) {
    return paymentMethodService.get(organizationId, id);
  }

  @GetMapping("/v1/payment-methods")
  @ReadAccess
  @Operation(
      summary = "고객 카드 목록 조회",
      description =
          """
          지금 결제 모드로 등록한 고객 카드를 customer_id 오름차순으로 전부 돌려준다. 빌링키 원문은 싣지 않는다.
          페이지를 나누지 않는다. authenticated_at은 KST(+09:00)로 나온다.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "고객 카드 목록. 등록한 카드가 없으면 payment_methods가 빈 배열이다"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=validation_error: 요청 값이 올바르지 않다")
  })
  public ListPaymentMethodsResponse listPaymentMethods(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(
              description = "보관 상태. 생략하면 active",
              schema =
                  @Schema(
                      allowableValues = {"active", "archived", "all"},
                      defaultValue = "active"))
          @org.springframework.web.bind.annotation.RequestParam(defaultValue = "active")
          @StorableText
          String status) {
    return paymentMethodService.list(organizationId, status);
  }

  @org.springframework.web.bind.annotation.DeleteMapping("/v1/customers/{id}/payment-method")
  @com.meterengine.global.security.AdminAccess
  @org.springframework.web.bind.annotation.ResponseStatus(
      org.springframework.http.HttpStatus.NO_CONTENT)
  @Operation(
      summary = "고객 카드 보관",
      description = "새 자동 결제를 중단한다. 복원하려면 고객이 카드를 다시 인증해야 한다. 기존 결제와 취소 이력은 유지한다.")
  public void deletePaymentMethod(
      @CurrentOrganizationId UUID organizationId, @PathVariable UUID id) {
    paymentMethodService.delete(organizationId, id);
  }
}
