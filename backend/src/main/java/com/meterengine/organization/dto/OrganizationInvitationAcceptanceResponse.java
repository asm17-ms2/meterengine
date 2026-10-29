package com.meterengine.organization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record OrganizationInvitationAcceptanceResponse(
    @JsonProperty("user_account_id") UUID userAccountId,
    @JsonProperty("organization_id") UUID organizationId) {}
