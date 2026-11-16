package com.meterengine.account.dto;

import com.meterengine.account.entity.UserAccount;
import com.meterengine.global.validation.StorableText;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateUserAccountRoleRequest(
    @Schema(
            description = "바꿀 역할입니다. operator는 MeterEngine 운영자 계정, member는 그 밖의 계정입니다.",
            allowableValues = {UserAccount.MEMBER, UserAccount.OPERATOR})
        @NotBlank @Pattern(regexp = UserAccount.ROLE_PATTERN) @StorableText
        String role) {}
