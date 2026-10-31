package com.meterengine.invoice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

@Entity
public class Invoice {

  @Id private UUID id;

  @Generated(event = EventType.INSERT)
  @Column(name = "display_number", nullable = false, insertable = false, updatable = false)
  private Long displayNumber;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "environment_id", updatable = false)
  private UUID environmentId;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Column(name = "customer_name", nullable = false)
  private String customerName;

  @Column(nullable = false)
  private String period;

  @Column(name = "supply_amount", nullable = false)
  private long supplyAmount;

  @Column(name = "tax_amount", nullable = false)
  private long taxAmount;

  @Column(name = "total_amount", nullable = false)
  private long totalAmount;

  @Column(name = "cutoff_at", nullable = false)
  private OffsetDateTime cutoffAt;

  @Column(name = "finalized_at", nullable = false)
  private OffsetDateTime finalizedAt;

  protected Invoice() {}

  public Invoice(
      UUID id,
      UUID organizationId,
      UUID customerId,
      String customerName,
      String period,
      long supplyAmount,
      long taxAmount,
      OffsetDateTime cutoffAt,
      OffsetDateTime finalizedAt) {
    this.id = id;
    this.organizationId = organizationId;
    this.customerId = customerId;
    this.customerName = customerName;
    this.period = period;
    this.supplyAmount = supplyAmount;
    this.taxAmount = taxAmount;
    this.totalAmount = supplyAmount + taxAmount;
    this.cutoffAt = cutoffAt;
    this.finalizedAt = finalizedAt;
  }

  public UUID getId() {
    return id;
  }

  public Long getDisplayNumber() {
    return displayNumber;
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

  public String getCustomerName() {
    return customerName;
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

  public long getTotalAmount() {
    return totalAmount;
  }

  public OffsetDateTime getCutoffAt() {
    return cutoffAt;
  }

  public OffsetDateTime getFinalizedAt() {
    return finalizedAt;
  }

  public Invoice(
      UUID id,
      UUID organizationId,
      UUID customerId,
      String customerName,
      String period,
      long supplyAmount,
      long taxAmount,
      OffsetDateTime cutoffAt,
      OffsetDateTime finalizedAt,
      UUID environmentId) {
    this(
        id,
        organizationId,
        customerId,
        customerName,
        period,
        supplyAmount,
        taxAmount,
        cutoffAt,
        finalizedAt);
    this.environmentId = environmentId;
  }
}
