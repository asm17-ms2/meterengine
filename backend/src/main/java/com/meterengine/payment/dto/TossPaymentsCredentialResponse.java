package com.meterengine.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.metric.service.BillableMetricUsageService;
import com.meterengine.payment.entity.TossPaymentsCredential;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

public record TossPaymentsCredentialResponse(
    @Schema(
            description = "결제 모드. test 또는 live",
            allowableValues = {"test", "live"})
        String mode,
    @JsonProperty("client_key") String clientKey,
    @Schema(description = "시크릿 키의 마지막 네 자. 원문은 돌려주지 않는다") @JsonProperty("secret_key_last_four")
        String secretKeyLastFour,
    @Schema(description = "토스페이먼츠에 키를 확인한 시각") @JsonProperty("verified_at")
        OffsetDateTime verifiedAt,
    @JsonProperty("updated_at") OffsetDateTime updatedAt) {

  public static TossPaymentsCredentialResponse from(TossPaymentsCredential credential) {
    return new TossPaymentsCredentialResponse(
        credential.getMode(),
        credential.getClientKey(),
        credential.getSecretKeyLastFour(),
        inBillingZone(credential.getVerifiedAt()),
        inBillingZone(credential.getUpdatedAt()));
  }

  private static OffsetDateTime inBillingZone(OffsetDateTime time) {
    return time.atZoneSameInstant(BillableMetricUsageService.BILLING_ZONE).toOffsetDateTime();
  }
}
