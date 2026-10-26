package com.meterengine.account.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

@Entity
public class UserSession implements Persistable<UUID> {

  @Id private UUID id;

  @Column(name = "user_account_id", nullable = false, updatable = false)
  private UUID userAccountId;

  @Column(name = "token_hash", nullable = false, updatable = false)
  private byte[] tokenHash;

  @Column(name = "organization_id")
  private UUID organizationId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private OffsetDateTime expiresAt;

  @Column(name = "revoked_at")
  private OffsetDateTime revokedAt;

  @Transient private boolean isNew = true;

  protected UserSession() {}

  public UserSession(
      UUID id,
      UUID userAccountId,
      byte[] tokenHash,
      UUID organizationId,
      OffsetDateTime createdAt,
      OffsetDateTime expiresAt) {
    this.id = id;
    this.userAccountId = userAccountId;
    this.tokenHash = tokenHash.clone();
    this.organizationId = organizationId;
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
  }

  @PostLoad
  @PostPersist
  private void markNotNew() {
    this.isNew = false;
  }

  @Override
  public UUID getId() {
    return id;
  }

  @Override
  public boolean isNew() {
    return isNew;
  }

  public UUID getUserAccountId() {
    return userAccountId;
  }

  public UUID getOrganizationId() {
    return organizationId;
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

  public boolean isUsableAt(OffsetDateTime now) {
    return revokedAt == null && expiresAt.isAfter(now);
  }

  public void revoke(OffsetDateTime now) {
    if (revokedAt == null) {
      revokedAt = now;
    }
  }

  public void switchOrganization(UUID organizationId) {
    this.organizationId = organizationId;
  }
}
