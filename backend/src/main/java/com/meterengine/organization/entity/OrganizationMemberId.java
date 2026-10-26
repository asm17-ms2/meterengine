package com.meterengine.organization.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class OrganizationMemberId implements Serializable {

  private UUID organizationId;
  private UUID userAccountId;

  public OrganizationMemberId() {}

  public OrganizationMemberId(UUID organizationId, UUID userAccountId) {
    this.organizationId = organizationId;
    this.userAccountId = userAccountId;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public UUID getUserAccountId() {
    return userAccountId;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof OrganizationMemberId id
        && Objects.equals(organizationId, id.organizationId)
        && Objects.equals(userAccountId, id.userAccountId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(organizationId, userAccountId);
  }
}
