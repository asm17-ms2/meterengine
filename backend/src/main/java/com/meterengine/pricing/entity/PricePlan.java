package com.meterengine.pricing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

@Entity
public class PricePlan implements Persistable<UUID> {
  @Id private UUID id;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "customer_id")
  private UUID customerId;

  @Column(name = "effective_from", nullable = false)
  private String effectiveFrom;

  @Column(name = "effective_until")
  private String effectiveUntil;

  @Column(name = "base_fee", nullable = false)
  private long baseFee;

  @Column(name = "minimum_amount", nullable = false)
  private long minimumAmount;

  @Transient private boolean isNew = true;

  protected PricePlan() {}

  public PricePlan(
      UUID organizationId,
      UUID customerId,
      String effectiveFrom,
      String effectiveUntil,
      long baseFee,
      long minimumAmount) {
    this.id = UUID.randomUUID();
    this.organizationId = organizationId;
    this.customerId = customerId;
    this.effectiveFrom = effectiveFrom;
    this.effectiveUntil = effectiveUntil;
    this.baseFee = baseFee;
    this.minimumAmount = minimumAmount;
  }

  @PostLoad
  @PostPersist
  private void markNotNew() {
    isNew = false;
  }

  public void update(String effectiveUntil, long baseFee, long minimumAmount) {
    this.effectiveUntil = effectiveUntil;
    this.baseFee = baseFee;
    this.minimumAmount = minimumAmount;
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

  public UUID getCustomerId() {
    return customerId;
  }

  public String getEffectiveFrom() {
    return effectiveFrom;
  }

  public String getEffectiveUntil() {
    return effectiveUntil;
  }

  public long getBaseFee() {
    return baseFee;
  }

  public long getMinimumAmount() {
    return minimumAmount;
  }
}
