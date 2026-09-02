package com.meterengine.invoice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
public class Invoice {

  @Id private UUID id;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Column(nullable = false)
  private String period;

  @Column(name = "supply_amount", nullable = false)
  private long supplyAmount;

  @Column(name = "tax_amount", nullable = false)
  private long taxAmount;

  @Column(name = "finalized_at", nullable = false)
  private OffsetDateTime finalizedAt;

  protected Invoice() {}

  public Invoice(
      UUID id,
      UUID organizationId,
      UUID customerId,
      String period,
      long supplyAmount,
      long taxAmount,
      OffsetDateTime finalizedAt) {
    this.id = id;
    this.organizationId = organizationId;
    this.customerId = customerId;
    this.period = period;
    this.supplyAmount = supplyAmount;
    this.taxAmount = taxAmount;
    this.finalizedAt = finalizedAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public String getPeriod() {
    return period;
  }

  public long getSupplyAmount() {
    return supplyAmount;
  }

  public long getTaxAmount() {
    return taxAmount;
  }

  public OffsetDateTime getFinalizedAt() {
    return finalizedAt;
  }
}
