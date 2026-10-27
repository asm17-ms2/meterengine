package com.meterengine.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

@Entity
public class PaymentMethodRegistration implements Persistable<UUID> {

  @Id private UUID id;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Column(name = "token_hash", nullable = false)
  private String tokenHash;

  @Column(nullable = false)
  private String mode;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "expires_at", nullable = false)
  private OffsetDateTime expiresAt;

  @Column(name = "used_at")
  private OffsetDateTime usedAt;

  protected PaymentMethodRegistration() {}

  public PaymentMethodRegistration(
      UUID id,
      UUID organizationId,
      UUID customerId,
      String tokenHash,
      String mode,
      OffsetDateTime createdAt,
      OffsetDateTime expiresAt) {
    this.id = id;
    this.organizationId = organizationId;
    this.customerId = customerId;
    this.tokenHash = tokenHash;
    this.mode = mode;
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
  }

  @Override
  public UUID getId() {
    return id;
  }

  @Override
  public boolean isNew() {
    return true;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public String getMode() {
    return mode;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getExpiresAt() {
    return expiresAt;
  }

  public OffsetDateTime getUsedAt() {
    return usedAt;
  }
}
