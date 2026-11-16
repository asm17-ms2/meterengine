package com.meterengine.invoiceview.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.invoiceview.entity.InvoiceViewLink;
import com.meterengine.metric.service.BillableMetricUsageService;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

public record InvoiceViewLinkResponse(
    @Schema(description = "링크 ID(UUID)입니다. 폐기할 때 경로의 view_link_id로 씁니다.") UUID id,
    @Schema(description = "발급한 시각(KST, +09:00)") @JsonProperty("created_at")
        OffsetDateTime createdAt,
    @Schema(description = "링크가 만료되는 시각(KST, +09:00)") @JsonProperty("expires_at")
        OffsetDateTime expiresAt,
    @Schema(description = "폐기한 시각(KST, +09:00)입니다. 폐기하지 않았으면 null입니다.") @JsonProperty("revoked_at")
        OffsetDateTime revokedAt,
    @Schema(description = "링크가 결제 상태를 보여 줄 결제 모드(test 또는 live)입니다. 발급할 때의 결제 모드로 정해지고 바뀌지 않습니다.")
        String mode,
    @Schema(description = "고객의 실패 결제 재시도를 허용한 링크인지 여부") @JsonProperty("allow_payment_retry")
        boolean allowPaymentRetry) {
  public static InvoiceViewLinkResponse from(InvoiceViewLink link) {
    return new InvoiceViewLinkResponse(
        link.getId(),
        inBillingZone(link.getCreatedAt()),
        inBillingZone(link.getExpiresAt()),
        inBillingZone(link.getRevokedAt()),
        link.getMode(),
        link.isAllowPaymentRetry());
  }

  private static OffsetDateTime inBillingZone(OffsetDateTime time) {
    return time == null
        ? null
        : time.atZoneSameInstant(BillableMetricUsageService.BILLING_ZONE).toOffsetDateTime();
  }
}
