package com.meterengine.payment.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class TossPaymentsCredentialId implements Serializable {

  private UUID organizationId;
  private String mode;
  private UUID environmentScopeId;

  public TossPaymentsCredentialId() {}

  public TossPaymentsCredentialId(UUID organizationId, String mode) {
    this(organizationId, mode, null);
  }

  public TossPaymentsCredentialId(UUID organizationId, String mode, UUID environmentId) {
    this.organizationId = organizationId;
    this.mode = mode;
    this.environmentScopeId = environmentId == null ? new UUID(0, 0) : environmentId;
  }

  public UUID getEnvironmentId() {
    return new UUID(0, 0).equals(environmentScopeId) ? null : environmentScopeId;
  }

  public UUID getEnvironmentScopeId() {
    return environmentScopeId;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public String getMode() {
    return mode;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof TossPaymentsCredentialId that)) {
      return false;
    }
    return Objects.equals(organizationId, that.organizationId)
        && Objects.equals(mode, that.mode)
        && Objects.equals(environmentScopeId, that.environmentScopeId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(organizationId, mode, environmentScopeId);
  }
}
