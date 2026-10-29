package com.meterengine.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.metric.service.BillableMetricUsageService;
import com.meterengine.payment.entity.BillingKey;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentMethodResponse(
    @JsonProperty("customer_id") UUID customerId,
    @Schema(description = "토스페이먼츠가 준 카드 발급사 코드") @JsonProperty("card_issuer_code")
        String cardIssuerCode,
    @Schema(description = "토스페이먼츠가 준 일부를 가린 카드 번호") @JsonProperty("card_number") String cardNumber,
    @Schema(
            description = "카드를 등록한 결제 모드. test 또는 live",
            allowableValues = {"test", "live"})
        String mode,
    @Schema(description = "토스페이먼츠가 카드를 인증한 시각") @JsonProperty("authenticated_at")
        OffsetDateTime authenticatedAt) {

  public static PaymentMethodResponse from(BillingKey billingKey) {
    return new PaymentMethodResponse(
        billingKey.getCustomerId(),
        billingKey.getCardIssuerCode(),
        billingKey.getCardNumber(),
        billingKey.getMode(),
        billingKey
            .getAuthenticatedAt()
            .atZoneSameInstant(BillableMetricUsageService.BILLING_ZONE)
            .toOffsetDateTime());
  }
}
