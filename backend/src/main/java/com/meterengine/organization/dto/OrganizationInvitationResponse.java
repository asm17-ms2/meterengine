package com.meterengine.organization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.organization.entity.OrganizationInvitation;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record OrganizationInvitationResponse(
    UUID id,
    String email,
    String status,
    @JsonProperty("created_at") OffsetDateTime createdAt,
    @JsonProperty("expires_at") OffsetDateTime expiresAt,
    @JsonProperty("accepted_at") OffsetDateTime acceptedAt,
    @JsonProperty("revoked_at") OffsetDateTime revokedAt,
    List<String> scopes) {
  public static OrganizationInvitationResponse from(OrganizationInvitation i) {
    String state =
        i.getAcceptedAt() != null
            ? "accepted"
            : i.getRevokedAt() != null
                ? "revoked"
                : i.isUsable(OffsetDateTime.now()) ? "pending" : "expired";
    return new OrganizationInvitationResponse(
        i.getId(),
        i.getEmail(),
        state,
        i.getCreatedAt(),
        i.getExpiresAt(),
        i.getAcceptedAt(),
        i.getRevokedAt(),
        i.getScopes());
  }
}
