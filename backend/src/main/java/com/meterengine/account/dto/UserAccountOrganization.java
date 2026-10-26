package com.meterengine.account.dto;

import com.meterengine.organization.entity.Organization;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record UserAccountOrganization(
    UUID id,
    String name,
    @Schema(
            description = "도입사 상태",
            allowableValues = {"pending", "active", "rejected", "suspended"})
        String status) {

  public static UserAccountOrganization from(Organization organization) {
    return new UserAccountOrganization(
        organization.getId(), organization.getName(), organization.getStatus());
  }
}
