package com.meterengine.organization.dto;

import com.meterengine.global.validation.StorableText;
import com.meterengine.organization.entity.Organization;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateOrganizationStatusRequest(
    @Schema(
            description = "바꿀 조직 상태입니다. active가 아니면 그 조직의 업무 요청이 403 organization_not_active입니다.",
            allowableValues = {
              Organization.PENDING,
              Organization.ACTIVE,
              Organization.REJECTED,
              Organization.SUSPENDED
            })
        @NotBlank @Pattern(regexp = Organization.STATUS_PATTERN) @StorableText
        String status) {}
