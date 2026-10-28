package com.meterengine.payment.controller;

import com.meterengine.global.error.ErrorResponse;
import com.meterengine.global.security.AuthenticatedPrincipal;
import com.meterengine.global.security.CurrentOrganizationId;
import com.meterengine.global.security.EnvironmentReady;
import com.meterengine.global.security.MemberAdminAccess;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@EnvironmentReady
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
          선택한 환경의 결제 모드를 돌려준다. test/live 환경의 모드는 고정이며 updated_at=null이다.
          기존 범위는 저장한 모드를 쓰고, 바꾼 적 없으면 mode=test다.
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
  @MemberAdminAccess
  @Operation(
      summary = "결제 모드 변경",
      description =
          """
          기존 범위의 결제 모드를 바꾼다. live로 바꾸려면 live 키가 먼저 저장돼 있어야 한다.
          test/live 환경은 선택한 환경과 같은 mode만 받는다. 다른 mode는 payment_mode_mismatch로 거절한다.
          로그인 세션만 부를 수 있고 API 키로는 부를 수 없다.
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
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @Valid @RequestBody UpdatePaymentSettingRequest request) {
    return paymentSettingService.update(organizationId, request.mode(), principal.mode());
  }
}
