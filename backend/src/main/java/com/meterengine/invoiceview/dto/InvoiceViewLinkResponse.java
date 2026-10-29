package com.meterengine.invoiceview.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.invoiceview.entity.InvoiceViewLink;
import com.meterengine.metric.service.BillableMetricUsageService;
import java.time.OffsetDateTime;
import java.util.UUID;

public record InvoiceViewLinkResponse(
    UUID id,
    @JsonProperty("created_at") OffsetDateTime createdAt,
    @JsonProperty("expires_at") OffsetDateTime expiresAt,
    @JsonProperty("revoked_at") OffsetDateTime revokedAt,
    String mode,
    @JsonProperty("allow_payment_retry") boolean allowPaymentRetry) {
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
