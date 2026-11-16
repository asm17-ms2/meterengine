package com.meterengine.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record UpdateSessionOrganizationRequest(
    @Schema(description = "바꿀 조직 ID(UUID)입니다. 사용자가 소속된 조직이어야 하며, 소속이 보관된 조직으로는 바꿀 수 없습니다.")
        @JsonProperty("organization_id")
        @NotNull UUID organizationId) {}
