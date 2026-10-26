package com.meterengine.organization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.organization.entity.Organization;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

public record OrganizationResponse(
    UUID id,
    String name,
    @Schema(
            description = "도입사 상태",
            allowableValues = {"pending", "active", "rejected", "suspended"})
        String status,
    @JsonProperty("created_at") OffsetDateTime createdAt) {

  public static OrganizationResponse from(Organization organization) {
    return new OrganizationResponse(
        organization.getId(),
        organization.getName(),
        organization.getStatus(),
        organization.getCreatedAt());
  }
}
