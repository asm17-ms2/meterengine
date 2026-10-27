package com.meterengine.pricing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

@Entity
public class PriceRate implements Persistable<UUID> {
  @Id private UUID id;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "environment_id", updatable = false)
  private UUID environmentId;

  @Column(name = "billable_metric_code", nullable = false)
  private String billableMetricCode;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "dimension_values", nullable = false)
  private String dimensionValues;

  @Column(name = "unit_price")
  private BigDecimal unitPrice;

  @Column(name = "customer_id")
  private UUID customerId;

  @Column(name = "effective_from", nullable = false)
  private String effectiveFrom;

  @Column(name = "effective_until")
  private String effectiveUntil;

  @Column(nullable = false)
  private String model;

  @JdbcTypeCode(SqlTypes.JSON)
  private String tiers;

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
        && (effectiveUntil == null || effectiveUntil.compareTo(archivedFrom) > 0);
  }

  @Transient private boolean isNew = true;

  protected PriceRate() {}

  public PriceRate(
      UUID organizationId,
      String billableMetricCode,
      String dimensionValues,
      BigDecimal unitPrice) {
    this(
        UUID.randomUUID(),
        organizationId,
        billableMetricCode,
        dimensionValues,
        unitPrice,
        null,
        "2000-01",
        null,
        "per_unit",
        null);
  }

  public PriceRate(
      UUID id,
      UUID organizationId,
      String billableMetricCode,
      String dimensionValues,
      BigDecimal unitPrice,
      UUID customerId,
      String effectiveFrom,
      String effectiveUntil,
      String model,
      String tiers) {
    this.id = id;
    this.organizationId = organizationId;
    this.billableMetricCode = billableMetricCode;
    this.dimensionValues = dimensionValues;
    this.unitPrice = unitPrice;
    this.customerId = customerId;
    this.effectiveFrom = effectiveFrom;
    this.effectiveUntil = effectiveUntil;
    this.model = model;
    this.tiers = tiers;
  }

  @PostLoad
  @PostPersist
  private void markNotNew() {
    this.isNew = false;
  }

  public void update(BigDecimal unitPrice, String effectiveUntil, String model, String tiers) {
    this.unitPrice = unitPrice;
    this.effectiveUntil = effectiveUntil;
    this.model = model;
    this.tiers = tiers;
  }

  public void close(String effectiveUntil) {
    this.effectiveUntil = effectiveUntil;
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

  public String getBillableMetricCode() {
    return billableMetricCode;
  }

  public String getDimensionValues() {
    return dimensionValues;
  }

  public BigDecimal getUnitPrice() {
    return unitPrice;
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

  public String getModel() {
    return model;
  }

  public String getTiers() {
    return tiers;
  }

  public PriceRate(
      UUID id,
      UUID organizationId,
      String billableMetricCode,
      String dimensionValues,
      BigDecimal unitPrice,
      UUID customerId,
      String effectiveFrom,
      String effectiveUntil,
      String model,
      String tiers,
      UUID environmentId) {
    this(
        id,
        organizationId,
        billableMetricCode,
        dimensionValues,
        unitPrice,
        customerId,
        effectiveFrom,
        effectiveUntil,
        model,
        tiers);
    this.environmentId = environmentId;
  }
}
