package com.meterengine.organization.dto;

import com.meterengine.global.security.AccessScopes;
import com.meterengine.global.validation.StorableText;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;

public record UpdateOrganizationMemberScopesRequest(
    @Schema(
            description =
                "바꿀 권한입니다. ingest, read, admin, payments 가운데 하나 이상을 담습니다. admin을 담으면 역할이 관리자, 빼면 일반 사용자가 됩니다. 대표관리자는 역할이 그대로이고 admin을 빼면 409 owner_scopes_required입니다.")
        @NotEmpty List<@NotNull @StorableText @Pattern(regexp = AccessScopes.PATTERN) String> scopes) {}
