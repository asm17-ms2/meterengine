package com.meterengine.organization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.organization.entity.Organization;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

public record OrganizationResponse(
    @Schema(description = "조직 ID(UUID)") UUID id,
    @Schema(description = "조직 이름") String name,
    @Schema(
            description = "조직 상태입니다. active가 아니면 업무 요청이 403 organization_not_active입니다.",
            allowableValues = {"pending", "active", "rejected", "suspended"})
        String status,
    @Schema(description = "조직을 만든 시각(ISO 8601)") @JsonProperty("created_at")
        OffsetDateTime createdAt,
    @Schema(
            description = "라이브 환경을 연 시각(ISO 8601)입니다. null이면 라이브 환경이 잠겨 있어 테스트 환경만 씁니다.",
            nullable = true)
        @JsonProperty("live_environment_opened_at")
        OffsetDateTime liveEnvironmentOpenedAt) {

  public static OrganizationResponse from(Organization organization) {
    return new OrganizationResponse(
        organization.getId(),
        organization.getName(),
        organization.getStatus(),
        organization.getCreatedAt(),
        organization.getLiveEnvironmentOpenedAt());
  }
}
