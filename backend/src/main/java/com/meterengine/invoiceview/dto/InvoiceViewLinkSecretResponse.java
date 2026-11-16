package com.meterengine.invoiceview.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

public record InvoiceViewLinkSecretResponse(
    @Schema(description = "링크 ID(UUID)입니다. 폐기할 때 경로의 view_link_id로 씁니다.") UUID id,
    @Schema(description = "고객에게 전달할 링크의 token(me_invoice_로 시작)입니다. 이 발급 응답에만 실리고 다시 볼 수 없습니다.")
        String token,
    @Schema(description = "발급한 시각(KST, +09:00)") @JsonProperty("created_at")
        OffsetDateTime createdAt,
    @Schema(description = "링크가 만료되는 시각(KST, +09:00)") @JsonProperty("expires_at")
        OffsetDateTime expiresAt,
    @Schema(description = "폐기한 시각(KST, +09:00)입니다. 발급 응답에서는 늘 null입니다.") @JsonProperty("revoked_at")
        OffsetDateTime revokedAt,
    @Schema(description = "링크가 결제 상태를 보여 줄 결제 모드(test 또는 live)입니다. 발급할 때의 결제 모드로 정해지고 바뀌지 않습니다.")
        String mode,
    @Schema(description = "고객의 실패 결제 재시도를 허용한 링크인지 여부") @JsonProperty("allow_payment_retry")
        boolean allowPaymentRetry) {
  public static InvoiceViewLinkSecretResponse from(String token, InvoiceViewLinkResponse link) {
    return new InvoiceViewLinkSecretResponse(
        link.id(),
        token,
        link.createdAt(),
        link.expiresAt(),
        link.revokedAt(),
        link.mode(),
        link.allowPaymentRetry());
  }
}
