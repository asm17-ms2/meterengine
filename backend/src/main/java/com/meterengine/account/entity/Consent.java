package com.meterengine.account.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

@Entity
public class Consent implements Persistable<UUID> {

  public static final String TERMS_OF_SERVICE = "terms_of_service";
  public static final String PRIVACY_POLICY = "privacy_policy";

  public static final String DOCUMENT_PATTERN = "terms_of_service|privacy_policy";

  public static final int DOCUMENT_VERSION_MAX_LENGTH = 255;

  @Id private UUID id;

  @Column(name = "user_account_id", nullable = false, updatable = false)
  private UUID userAccountId;

  @Column(nullable = false, updatable = false)
  private String document;

  @Column(name = "document_version", nullable = false, updatable = false)
  private String documentVersion;

  @Column(name = "agreed_at", nullable = false, updatable = false)
  private OffsetDateTime agreedAt;

  protected Consent() {}

  public Consent(
      UUID id,
      UUID userAccountId,
      String document,
      String documentVersion,
      OffsetDateTime agreedAt) {
    this.id = id;
    this.userAccountId = userAccountId;
    this.document = document;
    this.documentVersion = documentVersion;
    this.agreedAt = agreedAt;
  }

  @Override
  public UUID getId() {
    return id;
  }

  @Override
  public boolean isNew() {
    return true;
  }

  public UUID getUserAccountId() {
    return userAccountId;
  }

  public String getDocument() {
    return document;
  }

  public String getDocumentVersion() {
    return documentVersion;
  }

  public OffsetDateTime getAgreedAt() {
    return agreedAt;
  }
}
