package com.meterengine.payment.entity;

import com.meterengine.global.crypto.EncryptedStringConverter;
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

  @Id private String mode;

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

  @Transient private boolean isNew = true;

  protected TossPaymentsCredential() {}

  public TossPaymentsCredential(
      UUID organizationId,
      String mode,
      String clientKey,
      String secretKey,
      OffsetDateTime verifiedAt) {
    this.organizationId = organizationId;
    this.mode = mode;
    update(clientKey, secretKey, verifiedAt);
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
    this.clientKey = clientKey;
    this.secretKey = secretKey;
    this.secretKeyLastFour = secretKey.substring(secretKey.length() - SECRET_KEY_SUFFIX_LENGTH);
    this.verifiedAt = verifiedAt;
  }

  @Override
  public TossPaymentsCredentialId getId() {
    return new TossPaymentsCredentialId(organizationId, mode);
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
