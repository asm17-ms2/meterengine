package com.meterengine.apikey.entity;

import com.meterengine.global.security.AccessScopes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

@Entity
public class ApiKey implements Persistable<UUID> {

  public static final int LABEL_MAX_LENGTH = 255;

  public static final String MODE_PATTERN = "^(live|test)$";

  public static final String SCOPE_PATTERN = AccessScopes.PATTERN;

  @Id private UUID id;

  @Column(name = "organization_id", nullable = false, updatable = false)
  private UUID organizationId;

  @Column(name = "environment_id", updatable = false)
  private UUID environmentId;

  @Column(nullable = false, updatable = false)
  private String mode;

  @Column(nullable = false)
  private String label;

  @Column(name = "key_hash", nullable = false, updatable = false)
  private byte[] keyHash;

  @Column(name = "last_four", nullable = false, updatable = false)
  private String lastFour;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(nullable = false)
  private List<String> scopes = List.of();

  @Column(name = "rate_limit_per_minute")
  private Integer rateLimitPerMinute;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "expires_at")
  private OffsetDateTime expiresAt;

  @Column(name = "revoked_at")
  private OffsetDateTime revokedAt;

  @Transient private boolean isNew = true;

  protected ApiKey() {}

  public ApiKey(
      UUID id,
      UUID organizationId,
      String mode,
      String label,
      byte[] keyHash,
      String lastFour,
      List<String> scopes,
      Integer rateLimitPerMinute,
      OffsetDateTime createdAt,
      OffsetDateTime expiresAt) {
    this(
        id,
        organizationId,
        mode,
        label,
        keyHash,
        lastFour,
        scopes,
        rateLimitPerMinute,
        createdAt,
        expiresAt,
        null);
  }

  public ApiKey(
      UUID id,
      UUID organizationId,
      String mode,
      String label,
      byte[] keyHash,
      String lastFour,
      List<String> scopes,
      Integer rateLimitPerMinute,
      OffsetDateTime createdAt,
      OffsetDateTime expiresAt,
      UUID environmentId) {
    this.id = id;
    this.organizationId = organizationId;
    this.environmentId = environmentId;
    this.mode = mode;
    this.label = label;
    this.keyHash = keyHash.clone();
    this.lastFour = lastFour;
    this.scopes = List.copyOf(scopes);
    this.rateLimitPerMinute = rateLimitPerMinute;
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

  public UUID getOrganizationId() {
    return organizationId;
  }

  public String getMode() {
    return mode;
  }

  public UUID getEnvironmentId() {
    return environmentId;
  }

  public String getLabel() {
    return label;
  }

  public String getLastFour() {
    return lastFour;
  }

  public List<String> getScopes() {
    return scopes;
  }

  public void changeScopes(List<String> scopes) {
    this.scopes = List.copyOf(scopes);
  }

  public Integer getRateLimitPerMinute() {
    return rateLimitPerMinute;
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

  // 폐기되지 않았고 주어진 시각에 아직 만료되지 않았으면 참이다.
  public boolean isUsableAt(OffsetDateTime now) {
    return revokedAt == null && (expiresAt == null || expiresAt.isAfter(now));
  }

  public String statusAt(OffsetDateTime now) {
    if (revokedAt != null) return "revoked";
    return isUsableAt(now) ? "active" : "expired";
  }

  public void revoke(OffsetDateTime now) {
    if (revokedAt == null) {
      revokedAt = now;
    }
  }

  // 만료 시각을 주어진 시각으로 당기고, 이미 더 이른 만료 시각이 있으면 그대로 둔다.
  public void expireNoLaterThan(OffsetDateTime deadline) {
    if (expiresAt == null || expiresAt.isAfter(deadline)) {
      expiresAt = deadline;
    }
  }
}
