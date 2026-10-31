package com.meterengine.payment.controller;

import com.meterengine.global.security.CurrentOrganizationId;
import com.meterengine.global.security.EnvironmentReady;
import com.meterengine.global.security.ReadAccess;
import com.meterengine.payment.dto.PaymentSettingResponse;
import com.meterengine.payment.service.PaymentSettingService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
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
  @Operation(summary = "결제 모드 조회", description = "선택한 환경의 고정 결제 모드를 돌려준다. updated_at은 null이다.")
  public PaymentSettingResponse getPaymentSetting(@CurrentOrganizationId UUID organizationId) {
    return paymentSettingService.get(organizationId);
  }
}
