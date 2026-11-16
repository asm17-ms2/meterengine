package com.meterengine.organization.dto;

import com.meterengine.global.security.BusinessEnvironment;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record OrganizationEnvironmentResponse(
    @Schema(description = "업무 환경 ID(UUID)") UUID id,
    @Schema(
            description = "업무 환경의 종류입니다. test는 테스트 환경, live는 라이브 환경입니다.",
            allowableValues = {"test", "live"})
        String mode) {
  public static OrganizationEnvironmentResponse from(BusinessEnvironment environment) {
    return new OrganizationEnvironmentResponse(environment.environmentId(), environment.mode());
  }
}
