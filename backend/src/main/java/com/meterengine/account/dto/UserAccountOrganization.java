package com.meterengine.account.dto;

import com.meterengine.organization.entity.Organization;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record UserAccountOrganization(
    @Schema(description = "조직 ID(UUID)") UUID id,
    @Schema(description = "조직 이름") String name,
    @Schema(
            description = "조직 상태입니다. active가 아니면 업무 요청이 403 organization_not_active입니다.",
            allowableValues = {"pending", "active", "rejected", "suspended"})
        String status) {

  public static UserAccountOrganization from(Organization organization) {
    return new UserAccountOrganization(
        organization.getId(), organization.getName(), organization.getStatus());
  }
}
