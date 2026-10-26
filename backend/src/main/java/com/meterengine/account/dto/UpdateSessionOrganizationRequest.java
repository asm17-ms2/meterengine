package com.meterengine.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record UpdateSessionOrganizationRequest(
    @Schema(description = "바꿀 도입사. 사용자가 소속된 도입사여야 한다") @JsonProperty("organization_id") @NotNull UUID organizationId) {}
