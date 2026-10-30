package com.meterengine.organization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.organization.api.OrganizationPerson;
import com.meterengine.organization.entity.OrganizationMember;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ManagedMemberResponse(
    @JsonProperty("user_account_id") UUID userAccountId,
    String email,
    String name,
    @JsonProperty("organization_role") String organizationRole,
    @JsonProperty("created_at") OffsetDateTime createdAt,
    String status,
    @JsonProperty("revoked_at") OffsetDateTime revokedAt,
    List<String> scopes) {
  public static ManagedMemberResponse from(OrganizationMember member, OrganizationPerson user) {
    return new ManagedMemberResponse(
        user.id(),
        user.email(),
        user.name(),
        member.getRole(),
        member.getCreatedAt(),
        member.getRevokedAt() == null ? "active" : "archived",
        member.getRevokedAt(),
        member.getScopes());
  }
}
