package com.meterengine.organization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

public record OrganizationInvitationPreviewResponse(
    @Schema(description = "초대한 조직 이름") @JsonProperty("organization_name") String organizationName,
    @Schema(description = "초대 이메일") String email,
    @Schema(description = "초대가 만료되는 시각(ISO 8601)") @JsonProperty("expires_at")
        OffsetDateTime expiresAt,
    @Schema(description = "초대 이메일로 가입한 계정이 있으면 true입니다. true면 그 계정으로 로그인한 뒤 수락합니다.")
        @JsonProperty("account_exists")
        boolean accountExists,
    @Schema(description = "수락하면 받을 권한") List<String> scopes) {}
