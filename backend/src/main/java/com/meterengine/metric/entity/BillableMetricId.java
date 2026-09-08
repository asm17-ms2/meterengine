package com.meterengine.metric.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class BillableMetricId implements Serializable {

  private UUID organizationId;
  private String code;

  public BillableMetricId() {}

  public BillableMetricId(UUID organizationId, String code) {
    this.organizationId = organizationId;
    this.code = code;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public String getCode() {
    return code;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof BillableMetricId id
        && Objects.equals(organizationId, id.organizationId)
        && Objects.equals(code, id.code);
  }

  @Override
  public int hashCode() {
    return Objects.hash(organizationId, code);
  }
}
