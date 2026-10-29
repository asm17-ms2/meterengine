package com.meterengine.pricing.entity;

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
public class PricePlan implements Persistable<UUID> {
  @Id private UUID id;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "environment_id", updatable = false)
  private UUID environmentId;

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

  @Column(name = "archived_at")
  private OffsetDateTime archivedAt;

  @Column(name = "archived_from")
  private String archivedFrom;

  public OffsetDateTime getArchivedAt() {
    return archivedAt;
  }

  public String getArchivedFrom() {
    return archivedFrom;
  }

  public void archive(String cutoff) {
    archivedAt = OffsetDateTime.now();
    archivedFrom = cutoff;
  }

  public void restore() {
    archivedAt = null;
    archivedFrom = null;
  }

  public boolean applies(String month) {
    return effectiveFrom.compareTo(month) <= 0
        && (effectiveUntil == null || effectiveUntil.compareTo(month) > 0)
        && (archivedFrom == null || archivedFrom.compareTo(month) > 0);
  }

  public boolean stops(String month) {
    return archivedFrom != null
        && archivedFrom.compareTo(month) <= 0
        && effectiveFrom.compareTo(archivedFrom) < 0
        && (effectiveUntil == null || effectiveUntil.compareTo(month) > 0);
  }

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

  public UUID getEnvironmentId() {
    return environmentId;
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

  public PricePlan(
      UUID organizationId,
      UUID customerId,
      String effectiveFrom,
      String effectiveUntil,
      long baseFee,
      long minimumAmount,
      UUID environmentId) {
    this(organizationId, customerId, effectiveFrom, effectiveUntil, baseFee, minimumAmount);
    this.environmentId = environmentId;
  }
}
