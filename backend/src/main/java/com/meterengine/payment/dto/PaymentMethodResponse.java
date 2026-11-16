package com.meterengine.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.metric.service.BillableMetricUsageService;
import com.meterengine.payment.entity.BillingKey;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentMethodResponse(
    @Schema(description = "카드를 등록한 고객 ID(UUID)") @JsonProperty("customer_id") UUID customerId,
    @Schema(description = "토스페이먼츠가 준 카드 발급사 코드") @JsonProperty("card_issuer_code")
        String cardIssuerCode,
    @Schema(description = "토스페이먼츠가 준 일부를 가린 카드 번호") @JsonProperty("card_number") String cardNumber,
    @Schema(
            description = "카드를 등록한 결제 모드(test 또는 live)",
            allowableValues = {"test", "live"})
        String mode,
    @Schema(description = "토스페이먼츠가 카드를 인증한 시각(KST, +09:00)") @JsonProperty("authenticated_at")
        OffsetDateTime authenticatedAt,
    @Schema(description = "보관되거나 새 카드로 바뀐 시각(ISO 8601)입니다. 지금 결제에 쓰는 카드면 null입니다.")
        @JsonProperty("archived_at")
        OffsetDateTime archivedAt) {

  public static PaymentMethodResponse from(BillingKey billingKey) {
    return of(
        billingKey.getCustomerId(),
        billingKey.getCardIssuerCode(),
        billingKey.getCardNumber(),
        billingKey.getMode(),
        billingKey.getAuthenticatedAt(),
        billingKey.getArchivedAt());
  }

  public static PaymentMethodResponse of(
      UUID customerId,
      String cardIssuerCode,
      String cardNumber,
      String mode,
      OffsetDateTime authenticatedAt,
      OffsetDateTime archivedAt) {
    return new PaymentMethodResponse(
        customerId,
        cardIssuerCode,
        cardNumber,
        mode,
        authenticatedAt
            .atZoneSameInstant(BillableMetricUsageService.BILLING_ZONE)
            .toOffsetDateTime(),
        archivedAt);
  }
}
