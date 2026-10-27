package com.meterengine.payment.entity;

import com.meterengine.global.crypto.EncryptedStringConverter;
import com.meterengine.global.security.BusinessEnvironmentContext;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

@Entity
@IdClass(TossPaymentsCredentialId.class)
public class TossPaymentsCredential implements Persistable<TossPaymentsCredentialId> {

  private static final int SECRET_KEY_SUFFIX_LENGTH = 4;

  @Id
  @Column(name = "organization_id")
  private UUID organizationId;

  @Column(name = "environment_id")
  private UUID environmentId;

  @Id private String mode;

  @Id
  @Column(name = "environment_scope_id", updatable = false)
  private UUID environmentScopeId = new UUID(0, 0);

  @Column(name = "client_key", nullable = false)
  private String clientKey;

  @Convert(converter = EncryptedStringConverter.class)
  @Column(name = "secret_key", nullable = false)
  private String secretKey;

  @Column(name = "secret_key_last_four", nullable = false)
  private String secretKeyLastFour;

  @Column(name = "verified_at", nullable = false)
  private OffsetDateTime verifiedAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Column(name = "archived_at")
  private OffsetDateTime archivedAt;

  public OffsetDateTime getArchivedAt() {
    return archivedAt;
  }

  public void archive() {
    if (archivedAt == null) archivedAt = OffsetDateTime.now();
  }

  @Transient private boolean isNew = true;

  protected TossPaymentsCredential() {}

  public TossPaymentsCredential(
      UUID organizationId,
      String mode,
      String clientKey,
      String secretKey,
      OffsetDateTime verifiedAt) {
    this.organizationId = organizationId;
    this.environmentId = BusinessEnvironmentContext.environmentId();
    this.environmentScopeId = environmentId == null ? new UUID(0, 0) : environmentId;
    this.mode = mode;
    update(clientKey, secretKey, verifiedAt);
  }

  public TossPaymentsCredential(
      UUID organizationId,
      String mode,
      String clientKey,
      String secretKey,
      OffsetDateTime verifiedAt,
      OffsetDateTime updatedAt) {
    this(organizationId, mode, clientKey, secretKey, verifiedAt);
    this.updatedAt = updatedAt;
  }

  @PostLoad
  @PostPersist
  private void markNotNew() {
    this.isNew = false;
  }

  public void update(String clientKey, String secretKey, OffsetDateTime verifiedAt) {
    if (!clientKey.equals(this.clientKey) || !secretKey.equals(this.secretKey)) {
      this.updatedAt = verifiedAt;
    }
    this.archivedAt = null;
    this.clientKey = clientKey;
    this.secretKey = secretKey;
    this.secretKeyLastFour = secretKey.substring(secretKey.length() - SECRET_KEY_SUFFIX_LENGTH);
    this.verifiedAt = verifiedAt;
  }

  @Override
  public TossPaymentsCredentialId getId() {
    return new TossPaymentsCredentialId(organizationId, mode, environmentId);
  }

  @Override
  public boolean isNew() {
    return isNew;
  }

  public UUID getEnvironmentId() {
    return environmentId;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public String getMode() {
    return mode;
  }

  public String getClientKey() {
    return clientKey;
  }

  public String getSecretKey() {
    return secretKey;
  }

  public String getSecretKeyLastFour() {
    return secretKeyLastFour;
  }

  public OffsetDateTime getVerifiedAt() {
    return verifiedAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }
}
