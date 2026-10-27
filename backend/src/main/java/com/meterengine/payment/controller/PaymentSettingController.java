package com.meterengine.payment.controller;

import com.meterengine.global.error.ErrorResponse;
import com.meterengine.global.security.AdminAccess;
import com.meterengine.global.security.CurrentOrganizationId;
import com.meterengine.global.security.ReadAccess;
import com.meterengine.payment.dto.PaymentSettingResponse;
import com.meterengine.payment.dto.UpdatePaymentSettingRequest;
import com.meterengine.payment.service.PaymentSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/payment-setting")
public class PaymentSettingController {

  private final PaymentSettingService paymentSettingService;

  PaymentSettingController(PaymentSettingService paymentSettingService) {
    this.paymentSettingService = paymentSettingService;
  }

  @GetMapping
  @ReadAccess
  @Operation(
      summary = "결제 모드 조회",
      description =
          """
          결제에 쓰는 모드를 돌려준다. 한 번도 바꾸지 않았으면 mode=test, updated_at=null이다.
          updated_at은 KST(+09:00)로 나온다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "결제 모드"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=validation_error: 요청 값이 올바르지 않다")
  })
  public PaymentSettingResponse getPaymentSetting(@CurrentOrganizationId UUID organizationId) {
    return paymentSettingService.get(organizationId);
  }

  @PutMapping
  @AdminAccess
  @Operation(
      summary = "결제 모드 변경",
      description =
          """
          결제에 쓸 모드를 test나 live로 바꾼다. live로 바꾸려면 live 모드의 토스페이먼츠 키가 먼저 저장돼 있어야 한다.
          updated_at은 KST(+09:00)로 나온다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "바뀐 결제 모드"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            "code=validation_error: mode가 비었거나 test나 live가 아니거나, NUL 문자나 짝이 없는 UTF-16 서로게이트가 있다"),
    @ApiResponse(
        responseCode = "409",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=toss_payments_credential_missing: live로 바꾸려는데 live 모드의 토스페이먼츠 키가 없다")
  })
  public PaymentSettingResponse updatePaymentSetting(
      @CurrentOrganizationId UUID organizationId,
      @Valid @RequestBody UpdatePaymentSettingRequest request) {
    return paymentSettingService.update(organizationId, request.mode());
  }
}
