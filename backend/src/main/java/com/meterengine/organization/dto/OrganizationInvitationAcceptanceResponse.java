package com.meterengine.organization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record OrganizationInvitationAcceptanceResponse(
    @Schema(description = "수락한 사용자의 계정 ID(UUID)") @JsonProperty("user_account_id")
        UUID userAccountId,
    @Schema(description = "소속된 조직 ID(UUID)") @JsonProperty("organization_id")
        UUID organizationId) {}
