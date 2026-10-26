package com.meterengine.organization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateOrganizationMemberRequest(
    @Schema(description = "도입사에 소속시킬 사용자") @JsonProperty("user_account_id") @NotNull UUID userAccountId) {}
