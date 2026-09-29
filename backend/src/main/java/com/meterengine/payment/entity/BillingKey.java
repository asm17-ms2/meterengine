package com.meterengine.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
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

  @Convert(converter = BillingKeyConverter.class)
  @Column(name = "billing_key", nullable = false)
  private String billingKey;

  @Column(name = "card_issuer_code", nullable = false)
  private String cardIssuerCode;

  @Column(name = "card_number", nullable = false)
  private String cardNumber;

  @Column(name = "authenticated_at", nullable = false)
  private OffsetDateTime authenticatedAt;

  protected BillingKey() {}

  public BillingKey(
      UUID organizationId,
      UUID customerId,
      String billingKey,
      String cardIssuerCode,
      String cardNumber,
      OffsetDateTime authenticatedAt) {
    this.organizationId = organizationId;
    this.customerId = customerId;
    this.billingKey = billingKey;
    this.cardIssuerCode = cardIssuerCode;
    this.cardNumber = cardNumber;
    this.authenticatedAt = authenticatedAt;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public UUID getCustomerId() {
    return customerId;
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

  @Override
  public BillingKeyId getId() {
    return new BillingKeyId(organizationId, customerId);
  }

  @Override
  public boolean isNew() {
    return true;
  }
}
