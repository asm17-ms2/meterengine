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
@IdClass(BillingKeyId.class)
public class BillingKey implements Persistable<BillingKeyId> {

  @Id
  @Column(name = "organization_id")
  private UUID organizationId;

  @Id
  @Column(name = "customer_id")
  private UUID customerId;

  @Id private String mode;

  @Convert(converter = EncryptedStringConverter.class)
  @Column(name = "billing_key", nullable = false)
  private String billingKey;

  @Column(name = "card_issuer_code", nullable = false)
  private String cardIssuerCode;

  @Column(name = "card_number", nullable = false)
  private String cardNumber;

  @Column(name = "authenticated_at", nullable = false)
  private OffsetDateTime authenticatedAt;

  @Column(name = "consent_document_version", nullable = false)
  private String consentDocumentVersion;

  @Column(name = "consented_at", nullable = false)
  private OffsetDateTime consentedAt;

  @Column(name = "archived_at")
  private OffsetDateTime archivedAt;

  public OffsetDateTime getArchivedAt() {
    return archivedAt;
  }

  public void archive() {
    if (archivedAt == null) archivedAt = OffsetDateTime.now();
  }

  @Transient private boolean isNew = true;

  protected BillingKey() {}

  public BillingKey(
      UUID organizationId,
      UUID customerId,
      String mode,
      String billingKey,
      String cardIssuerCode,
      String cardNumber,
      OffsetDateTime authenticatedAt,
      String consentDocumentVersion,
      OffsetDateTime consentedAt) {
    this.organizationId = organizationId;
    this.customerId = customerId;
    this.mode = mode;
    update(
        billingKey,
        cardIssuerCode,
        cardNumber,
        authenticatedAt,
        consentDocumentVersion,
        consentedAt);
  }

  @PostLoad
  @PostPersist
  private void markNotNew() {
    this.isNew = false;
  }

  public void update(
      String billingKey,
      String cardIssuerCode,
      String cardNumber,
      OffsetDateTime authenticatedAt,
      String consentDocumentVersion,
      OffsetDateTime consentedAt) {
    this.archivedAt = null;
    this.billingKey = billingKey;
    this.cardIssuerCode = cardIssuerCode;
    this.cardNumber = cardNumber;
    this.authenticatedAt = authenticatedAt;
    this.consentDocumentVersion = consentDocumentVersion;
    this.consentedAt = consentedAt;
  }

  @Override
  public BillingKeyId getId() {
    return new BillingKeyId(organizationId, customerId, mode);
  }

  @Override
  public boolean isNew() {
    return isNew;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public String getMode() {
    return mode;
  }

  public String getBillingKey() {
    return billingKey;
  }

  public String getCardIssuerCode() {
    return cardIssuerCode;
  }

  public String getCardNumber() {
    return cardNumber;
  }

  public OffsetDateTime getAuthenticatedAt() {
    return authenticatedAt;
  }

  public String getConsentDocumentVersion() {
    return consentDocumentVersion;
  }

  public OffsetDateTime getConsentedAt() {
    return consentedAt;
  }
}
