package com.meterengine.payment.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class TossPaymentsCredentialId implements Serializable {

  private UUID organizationId;
  private String mode;

  public TossPaymentsCredentialId() {}

  public TossPaymentsCredentialId(UUID organizationId, String mode) {
    this.organizationId = organizationId;
    this.mode = mode;
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
    return Objects.equals(organizationId, that.organizationId) && Objects.equals(mode, that.mode);
  }

  @Override
  public int hashCode() {
    return Objects.hash(organizationId, mode);
  }
}
