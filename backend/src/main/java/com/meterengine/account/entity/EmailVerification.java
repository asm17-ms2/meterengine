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
public class EmailVerification implements Persistable<UUID> {

  @Id private UUID id;

  @Column(name = "user_account_id", nullable = false, updatable = false)
  private UUID userAccountId;

  @Column(name = "token_hash", nullable = false, updatable = false)
  private byte[] tokenHash;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private OffsetDateTime expiresAt;

  @Column(name = "used_at")
  private OffsetDateTime usedAt;

  @Transient private boolean isNew = true;

  protected EmailVerification() {}

  public EmailVerification(
      UUID id,
      UUID userAccountId,
      byte[] tokenHash,
      OffsetDateTime createdAt,
      OffsetDateTime expiresAt) {
    this.id = id;
    this.userAccountId = userAccountId;
    this.tokenHash = tokenHash;
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

  public OffsetDateTime getExpiresAt() {
    return expiresAt;
  }

  public OffsetDateTime getUsedAt() {
    return usedAt;
  }

  public boolean isUsableAt(OffsetDateTime now) {
    return usedAt == null && expiresAt.isAfter(now);
  }

  public void use(OffsetDateTime now) {
    usedAt = now;
  }
}
