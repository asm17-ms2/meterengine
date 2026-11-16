package com.meterengine.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.metric.service.BillableMetricUsageService;
import com.meterengine.payment.entity.TossPaymentsCredential;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

public record TossPaymentsCredentialResponse(
    @Schema(
            description = "키를 쓰는 결제 모드(test 또는 live)",
            allowableValues = {"test", "live"})
        String mode,
    @Schema(description = "토스페이먼츠 클라이언트 키 원문") @JsonProperty("client_key") String clientKey,
    @Schema(description = "시크릿 키의 마지막 네 자입니다. 원문은 돌려주지 않습니다.") @JsonProperty("secret_key_last_four")
        String secretKeyLastFour,
    @Schema(description = "토스페이먼츠에 키가 유효한지 확인한 시각(KST, +09:00)") @JsonProperty("verified_at")
        OffsetDateTime verifiedAt,
    @Schema(description = "키 값이 마지막으로 바뀐 시각(KST, +09:00)입니다. 같은 키를 다시 저장하면 바뀌지 않습니다.")
        @JsonProperty("updated_at")
        OffsetDateTime updatedAt,
    @Schema(description = "보관되거나 새 키로 바뀐 시각(ISO 8601)입니다. 지금 쓰는 키면 null입니다.")
        @JsonProperty("archived_at")
        OffsetDateTime archivedAt) {

  public static TossPaymentsCredentialResponse from(TossPaymentsCredential credential) {
    return of(
        credential.getMode(),
        credential.getClientKey(),
        credential.getSecretKeyLastFour(),
        credential.getVerifiedAt(),
        credential.getUpdatedAt(),
        credential.getArchivedAt());
  }

  public static TossPaymentsCredentialResponse of(
      String mode,
      String clientKey,
      String secretKeyLastFour,
      OffsetDateTime verifiedAt,
      OffsetDateTime updatedAt,
      OffsetDateTime archivedAt) {
    return new TossPaymentsCredentialResponse(
        mode,
        clientKey,
        secretKeyLastFour,
        inBillingZone(verifiedAt),
        inBillingZone(updatedAt),
        archivedAt);
  }

  private static OffsetDateTime inBillingZone(OffsetDateTime time) {
    return time.atZoneSameInstant(BillableMetricUsageService.BILLING_ZONE).toOffsetDateTime();
  }
}
