package com.meterengine.organization.dto;

import com.meterengine.global.security.BusinessEnvironment;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record OrganizationEnvironmentResponse(
    UUID id, @Schema(allowableValues = {"test", "live"}) String mode) {
  public static OrganizationEnvironmentResponse from(BusinessEnvironment environment) {
    return new OrganizationEnvironmentResponse(environment.environmentId(), environment.mode());
  }
}
