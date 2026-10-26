package com.meterengine.account.dto;

import com.meterengine.account.entity.UserAccount;
import com.meterengine.global.validation.StorableText;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateUserAccountRoleRequest(
    @Schema(
            description = "바꿀 역할",
            allowableValues = {UserAccount.MEMBER, UserAccount.OPERATOR})
        @NotBlank @Pattern(regexp = UserAccount.ROLE_PATTERN) @StorableText
        String role) {}
