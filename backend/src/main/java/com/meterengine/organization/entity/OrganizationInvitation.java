package com.meterengine.organization.entity;

import com.meterengine.global.security.AccessScopes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
public class OrganizationInvitation {
  @Id private UUID id;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(nullable = false)
  private String email;

  @Column(name = "token_hash", nullable = false)
  private byte[] tokenHash;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(nullable = false, updatable = false)
  private List<String> scopes = AccessScopes.ADMIN;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "expires_at", nullable = false)
  private OffsetDateTime expiresAt;

  @Column(name = "accepted_at")
  private OffsetDateTime acceptedAt;

  @Column(name = "revoked_at")
  private OffsetDateTime revokedAt;

  protected OrganizationInvitation() {}

  public OrganizationInvitation(
      UUID id,
      UUID organizationId,
      String email,
      byte[] tokenHash,
      OffsetDateTime createdAt,
      OffsetDateTime expiresAt) {
    this.id = id;
    this.organizationId = organizationId;
    this.email = email;
    this.tokenHash = tokenHash;
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
  }

  public UUID getId() {
    return id;
  }

  public OrganizationInvitation(
      UUID id,
      UUID organizationId,
      String email,
      byte[] tokenHash,
      OffsetDateTime createdAt,
      OffsetDateTime expiresAt,
      List<String> scopes) {
    this(id, organizationId, email, tokenHash, createdAt, expiresAt);
    this.scopes = AccessScopes.normalize(scopes);
  }

  public List<String> getScopes() {
    return List.copyOf(scopes);
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public String getEmail() {
    return email;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getExpiresAt() {
    return expiresAt;
  }

  public OffsetDateTime getAcceptedAt() {
    return acceptedAt;
  }

  public OffsetDateTime getRevokedAt() {
    return revokedAt;
  }

  public boolean isUsable(OffsetDateTime now) {
    return acceptedAt == null && revokedAt == null && now.isBefore(expiresAt);
  }

  public void accept(OffsetDateTime now) {
    acceptedAt = now;
  }

  public void revoke(OffsetDateTime now) {
    revokedAt = now;
  }
}
