package com.meterengine.organization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.global.validation.StorableText;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateOrganizationMemberRoleRequest(
    @Schema(description = "바꿀 역할입니다. owner(대표관리자)나 admin(관리자)이고, 바꾸면 권한이 admin과 payments가 됩니다.")
        @JsonProperty("organization_role")
        @NotBlank @Pattern(regexp = "owner|admin") @StorableText
        String organizationRole) {}
