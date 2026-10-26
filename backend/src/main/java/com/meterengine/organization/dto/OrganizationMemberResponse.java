package com.meterengine.organization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.organization.entity.OrganizationMember;
import java.time.OffsetDateTime;
import java.util.UUID;

public record OrganizationMemberResponse(
    @JsonProperty("organization_id") UUID organizationId,
    @JsonProperty("user_account_id") UUID userAccountId,
    @JsonProperty("created_at") OffsetDateTime createdAt) {

  public static OrganizationMemberResponse from(OrganizationMember member) {
    return new OrganizationMemberResponse(
        member.getOrganizationId(), member.getUserAccountId(), member.getCreatedAt());
  }
}
