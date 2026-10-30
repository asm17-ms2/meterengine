package com.meterengine.organization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.global.validation.StorableText;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateOrganizationMemberRoleRequest(
    @JsonProperty("organization_role") @NotBlank @Pattern(regexp = "owner|admin") @StorableText
        String organizationRole) {}
