package com.meterengine.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.organization.entity.Organization;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

public record SessionOrganization(
    UUID id,
    String name,
    @Schema(
            description = "도입사 상태",
            allowableValues = {"pending", "active", "rejected", "suspended"})
        String status,
    @Schema(description = "실제 환경을 연 시각. null이면 실제 환경이 잠겨 있어 테스트 환경만 쓴다", nullable = true)
        @JsonProperty("live_environment_opened_at")
        OffsetDateTime liveEnvironmentOpenedAt) {

  public static SessionOrganization from(Organization organization) {
    if (organization == null) {
      return null;
    }
    return new SessionOrganization(
        organization.getId(),
        organization.getName(),
        organization.getStatus(),
        organization.getLiveEnvironmentOpenedAt());
  }
}
