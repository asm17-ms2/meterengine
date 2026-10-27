package com.meterengine.invoiceview.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
public class InvoiceViewLink {
  @Id private UUID id;

  @Column(name = "organization_id", nullable = false, updatable = false)
  private UUID organizationId;

  @Column(name = "invoice_id", nullable = false, updatable = false)
  private UUID invoiceId;

  @Column(name = "token_hash", nullable = false, updatable = false)
  private byte[] tokenHash;

  @Column(nullable = false, updatable = false)
  private String mode;

  @Column(name = "allow_payment_retry", nullable = false, updatable = false)
  private boolean allowPaymentRetry;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private OffsetDateTime expiresAt;

  @Column(name = "revoked_at")
  private OffsetDateTime revokedAt;

  protected InvoiceViewLink() {}

  public InvoiceViewLink(
      UUID id,
      UUID organizationId,
      UUID invoiceId,
      byte[] tokenHash,
      String mode,
      OffsetDateTime createdAt,
      OffsetDateTime expiresAt) {
    this(id, organizationId, invoiceId, tokenHash, mode, createdAt, expiresAt, false);
  }

  public InvoiceViewLink(
      UUID id,
      UUID organizationId,
      UUID invoiceId,
      byte[] tokenHash,
      String mode,
      OffsetDateTime createdAt,
      OffsetDateTime expiresAt,
      boolean allowPaymentRetry) {
    this.id = id;
    this.organizationId = organizationId;
    this.invoiceId = invoiceId;
    this.tokenHash = tokenHash;
    this.mode = mode;
    this.allowPaymentRetry = allowPaymentRetry;
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public UUID getInvoiceId() {
    return invoiceId;
  }

  public String getMode() {
    return mode;
  }

  public boolean isAllowPaymentRetry() {
    return allowPaymentRetry;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getExpiresAt() {
    return expiresAt;
  }

  public OffsetDateTime getRevokedAt() {
    return revokedAt;
  }

  public boolean isUsable(OffsetDateTime now) {
    return revokedAt == null && now.isBefore(expiresAt);
  }

  public void revoke(OffsetDateTime now) {
    if (revokedAt == null) revokedAt = now;
  }
}
