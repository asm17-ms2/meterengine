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
          현재 업무 환경의 결제 모드를 돌려줍니다. 테스트 환경과 라이브 환경의 결제 모드는 고정이며 updated_at=null입니다.
          기존 데이터는 저장한 결제 모드를 쓰고, 바꾼 적 없으면 mode=test입니다.
          updated_at은 KST(+09:00)로 나옵니다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "결제 모드"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=validation_error: 요청 값이 올바르지 않습니다.")
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
          기존 데이터의 결제 모드를 바꿉니다. live로 바꾸려면 live 결제 모드의 토스페이먼츠 키가 먼저 저장되어 있어야 합니다.
          테스트 환경과 라이브 환경은 현재 업무 환경과 같은 mode만 받습니다. 다른 mode는 payment_mode_mismatch로 거절합니다.
          로그인 세션만 부를 수 있고 API 키로는 부를 수 없습니다.
          updated_at은 KST(+09:00)로 나옵니다.
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
            "code=validation_error: mode가 비었거나 test나 live가 아니거나, NUL 문자나 짝이 없는 UTF-16 서로게이트가 있습니다."),
    @ApiResponse(
        responseCode = "409",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            "code=toss_payments_credential_missing: live로 바꾸려는데 live 결제 모드의 토스페이먼츠 키가 없습니다.")
  })
  public PaymentSettingResponse updatePaymentSetting(
      @CurrentOrganizationId UUID organizationId,
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @Valid @RequestBody UpdatePaymentSettingRequest request) {
    return paymentSettingService.update(organizationId, request.mode(), principal.mode());
  }
}
