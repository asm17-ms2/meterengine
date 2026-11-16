package com.meterengine.payment.controller;

import com.meterengine.global.error.ErrorResponse;
import com.meterengine.global.security.CurrentOrganizationId;
import com.meterengine.global.security.EnvironmentReady;
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
@EnvironmentReady
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
          고객 한 명이 현재 업무 환경에 등록한 카드를 돌려줍니다. 빌링키 원문은 싣지 않습니다.
          고객 한 명의 지금 카드만 확인할 때 씁니다. 카드가 없으면 404이고, 보관한 카드도 archived_at에 보관 시각을 담아 돌려주므로 결제에 쓸 수 있는지는 archived_at이 null인지로 판단합니다.
          여러 고객의 카드를 한 번에 보거나 교체되거나 보관된 이전 카드까지 보려면 GET /v1/payment-methods를 씁니다.
          authenticated_at은 KST(+09:00)로 나옵니다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "고객 카드"),
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
        description =
            """
            code=customer_not_found: 그런 고객이 없습니다.
            code=payment_method_not_found: 고객이 현재 업무 환경에 등록한 카드가 없습니다.
            """)
  })
  public PaymentMethodResponse getPaymentMethod(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = "카드를 조회할 고객의 ID") @PathVariable UUID id) {
    return paymentMethodService.get(organizationId, id);
  }

  @GetMapping("/v1/payment-methods")
  @ReadAccess
  @Operation(
      summary = "고객 카드 목록 조회",
      description =
          """
          현재 업무 환경에 등록한 고객 카드를 customer_id 오름차순으로 전부 돌려줍니다. customer_id를 주면 그 고객의 카드만 돌려줍니다.
          여러 고객의 카드를 한 번에 보거나 교체되거나 보관된 이전 카드까지 볼 때 씁니다. 카드가 없으면 404가 아니라 빈 배열을 돌려줍니다.
          고객 한 명의 지금 카드만 확인하려면 GET /v1/customers/{id}/payment-method를 씁니다.
          빌링키 원문은 싣지 않습니다.
          페이지를 나누지 않습니다. authenticated_at은 KST(+09:00)로 나옵니다.
          status가 archived나 all이면 교체되거나 보관된 이전 카드를 현재 행 뒤에 교체되거나 보관된 시각 내림차순으로 붙이고, archived_at에 그 시각을 담습니다. 이전 카드는 새 결제에 다시 쓰이지 않습니다.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "고객 카드 목록입니다. 등록한 카드가 없으면 payment_methods가 빈 배열입니다."),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=validation_error: 요청 값이 올바르지 않습니다.")
  })
  public ListPaymentMethodsResponse listPaymentMethods(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(
              description = "보관 상태입니다. 생략하면 active입니다.",
              schema =
                  @Schema(
                      allowableValues = {"active", "archived", "all"},
                      defaultValue = "active"))
          @org.springframework.web.bind.annotation.RequestParam(defaultValue = "active")
          @StorableText
          String status,
      @Parameter(description = "주면 이 고객의 카드만 돌려줍니다. 생략하면 모든 고객의 카드를 돌려줍니다.")
          @org.springframework.web.bind.annotation.RequestParam(
              name = "customer_id",
              required = false)
          UUID customerId) {
    return paymentMethodService.list(organizationId, status, customerId);
  }

  @org.springframework.web.bind.annotation.DeleteMapping("/v1/customers/{id}/payment-method")
  @com.meterengine.global.security.AdminAccess
  @org.springframework.web.bind.annotation.ResponseStatus(
      org.springframework.http.HttpStatus.NO_CONTENT)
  @Operation(
      summary = "고객 카드 보관",
      description =
          "현재 업무 환경에 등록한 고객 카드를 보관하고 새 자동결제를 멈춥니다. 다시 결제하려면 고객이 카드를 다시 등록해야 합니다."
              + " 그 업무 환경의 토스페이먼츠 키가 저장되어 있으면 토스페이먼츠에서도 그 빌링키를 지웁니다."
              + " 지난 결제와 취소 기록은 남습니다. 이미 보관한 카드면 바꾸지 않고 204를 돌려주고, 카드가 없으면 404 payment_method_not_found입니다.")
  @ApiResponses({
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
        description = "code=payment_method_not_found: 고객이 현재 업무 환경에 등록한 카드가 없습니다.")
  })
  public void deletePaymentMethod(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = "카드를 보관할 고객의 ID") @PathVariable UUID id) {
    paymentMethodService.delete(organizationId, id);
  }
}
