package com.meterengine.payment.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class BillingKeyId implements Serializable {

  private UUID organizationId;
  private UUID customerId;

  public BillingKeyId() {}

  public BillingKeyId(UUID organizationId, UUID customerId) {
    this.organizationId = organizationId;
    this.customerId = customerId;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof BillingKeyId id
        && Objects.equals(organizationId, id.organizationId)
        && Objects.equals(customerId, id.customerId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(organizationId, customerId);
  }
}
