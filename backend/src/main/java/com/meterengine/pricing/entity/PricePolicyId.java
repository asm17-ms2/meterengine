package com.meterengine.pricing.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class PricePolicyId implements Serializable {

  private UUID organizationId;
  private String billableMetricCode;

  public PricePolicyId() {}

  public PricePolicyId(UUID organizationId, String billableMetricCode) {
    this.organizationId = organizationId;
    this.billableMetricCode = billableMetricCode;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public String getBillableMetricCode() {
    return billableMetricCode;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof PricePolicyId id
        && Objects.equals(organizationId, id.organizationId)
        && Objects.equals(billableMetricCode, id.billableMetricCode);
  }

  @Override
  public int hashCode() {
    return Objects.hash(organizationId, billableMetricCode);
  }
}
