package com.meterengine.organization.dto;

import com.meterengine.global.validation.StorableText;
import com.meterengine.organization.entity.Organization;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateOrganizationStatusRequest(
    @Schema(
            description = "바꿀 상태",
            allowableValues = {
              Organization.PENDING,
              Organization.ACTIVE,
              Organization.REJECTED,
              Organization.SUSPENDED
            })
        @NotBlank @Pattern(regexp = Organization.STATUS_PATTERN) @StorableText
        String status) {}
