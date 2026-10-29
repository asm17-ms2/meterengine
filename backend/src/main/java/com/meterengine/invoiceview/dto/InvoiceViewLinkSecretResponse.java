package com.meterengine.invoiceview.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.UUID;

public record InvoiceViewLinkSecretResponse(
    UUID id,
    String token,
    @JsonProperty("created_at") OffsetDateTime createdAt,
    @JsonProperty("expires_at") OffsetDateTime expiresAt,
    @JsonProperty("revoked_at") OffsetDateTime revokedAt,
    String mode,
    @JsonProperty("allow_payment_retry") boolean allowPaymentRetry) {
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
