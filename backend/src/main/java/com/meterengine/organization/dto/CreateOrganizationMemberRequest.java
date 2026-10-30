package com.meterengine.organization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.global.security.AccessScopes;
import com.meterengine.global.validation.StorableText;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.UUID;

public record CreateOrganizationMemberRequest(
    @Schema(description = "도입사에 소속시킬 사용자") @JsonProperty("user_account_id") @NotNull UUID userAccountId,
    @Schema(
            description = "부여할 권한. 생략하면 조회 전용. 첫 대표관리자는 관리 권한을 받는다",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @NotEmpty List<@NotNull @StorableText @Pattern(regexp = AccessScopes.PATTERN) String> scopes) {
  public CreateOrganizationMemberRequest {
    if (scopes == null) scopes = AccessScopes.READ;
  }

  public CreateOrganizationMemberRequest(UUID userAccountId) {
    this(userAccountId, AccessScopes.READ);
  }
}
