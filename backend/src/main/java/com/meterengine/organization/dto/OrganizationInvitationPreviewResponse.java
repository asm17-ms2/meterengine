package com.meterengine.organization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;

public record OrganizationInvitationPreviewResponse(
    @JsonProperty("organization_name") String organizationName,
    String email,
    @JsonProperty("expires_at") OffsetDateTime expiresAt,
    @JsonProperty("account_exists") boolean accountExists,
    List<String> scopes) {}
