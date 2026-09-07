package com.meterengine.pricing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

@Entity
@IdClass(PricePolicyId.class)
public class PricePolicy implements Persistable<PricePolicyId> {

  @Id
  @Column(name = "organization_id")
  private UUID organizationId;

  @Id
  @Column(name = "billable_metric_code")
  private String billableMetricCode;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "dimension_properties", nullable = false)
  private List<String> dimensionProperties;

  /** Hibernate 전용. */
  protected PricePolicy() {}

  public PricePolicy(
      UUID organizationId, String billableMetricCode, List<String> dimensionProperties) {
    this.organizationId = organizationId;
    this.billableMetricCode = billableMetricCode;
    this.dimensionProperties = List.copyOf(dimensionProperties);
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public String getBillableMetricCode() {
    return billableMetricCode;
  }

  public List<String> getDimensionProperties() {
    return dimensionProperties;
  }

  @Override
  public PricePolicyId getId() {
    return new PricePolicyId(organizationId, billableMetricCode);
  }

  @Override
  public boolean isNew() {
    return true;
  }
}
