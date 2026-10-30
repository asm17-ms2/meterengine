package com.meterengine.payment.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class BillingKeyId implements Serializable {

  private UUID organizationId;
  private UUID customerId;
  private String mode;

  public BillingKeyId() {}

  public BillingKeyId(UUID organizationId, UUID customerId, String mode) {
    this.organizationId = organizationId;
    this.customerId = customerId;
    this.mode = mode;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public String getMode() {
    return mode;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof BillingKeyId that)) {
      return false;
    }
    return Objects.equals(organizationId, that.organizationId)
        && Objects.equals(customerId, that.customerId)
        && Objects.equals(mode, that.mode);
  }

  @Override
  public int hashCode() {
    return Objects.hash(organizationId, customerId, mode);
  }
}
