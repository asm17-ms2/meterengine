package com.meterengine.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.account.service.UserAccountMemberships;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record UserAccountResponse(
    UUID id,
    String email,
    String name,
    @Schema(
            description = "사용자 역할",
            allowableValues = {"member", "operator"})
        String role,
    @Schema(description = "사용자가 소속된 도입사. 소속된 순서") List<UserAccountOrganization> organizations,
    @JsonProperty("created_at") OffsetDateTime createdAt) {

  public static UserAccountResponse from(UserAccountMemberships memberships) {
    return new UserAccountResponse(
        memberships.userAccount().getId(),
        memberships.userAccount().getEmail(),
        memberships.userAccount().getName(),
        memberships.userAccount().getRole(),
        memberships.organizations().stream().map(UserAccountOrganization::from).toList(),
        memberships.userAccount().getCreatedAt());
  }
}
