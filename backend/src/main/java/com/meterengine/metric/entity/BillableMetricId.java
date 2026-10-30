package com.meterengine.metric.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class BillableMetricId implements Serializable {

  private UUID organizationId;
  private UUID environmentScopeId;
  private String code;

  public BillableMetricId() {}

  public BillableMetricId(UUID organizationId, String code) {
    this(organizationId, code, null);
  }

  public BillableMetricId(UUID organizationId, String code, UUID environmentId) {
    this.organizationId = organizationId;
    this.environmentScopeId = environmentId == null ? new UUID(0, 0) : environmentId;
    this.code = code;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public UUID getEnvironmentScopeId() {
    return environmentScopeId;
  }

  public String getCode() {
    return code;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof BillableMetricId id
        && Objects.equals(organizationId, id.organizationId)
        && Objects.equals(environmentScopeId, id.environmentScopeId)
        && Objects.equals(code, id.code);
  }

  @Override
  public int hashCode() {
    return Objects.hash(organizationId, environmentScopeId, code);
  }
}
