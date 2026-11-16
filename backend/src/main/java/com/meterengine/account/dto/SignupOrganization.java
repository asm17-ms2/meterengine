package com.meterengine.account.dto;

import com.meterengine.organization.entity.Organization;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record SignupOrganization(
    @Schema(description = "조직 ID(UUID)") UUID id,
    @Schema(description = "조직 이름") String name,
    @Schema(
            description = "조직 상태입니다. 가입 응답에서는 늘 active입니다.",
            allowableValues = {"pending", "active", "rejected", "suspended"})
        String status) {

  public static SignupOrganization from(Organization organization) {
    return new SignupOrganization(
        organization.getId(), organization.getName(), organization.getStatus());
  }
}
