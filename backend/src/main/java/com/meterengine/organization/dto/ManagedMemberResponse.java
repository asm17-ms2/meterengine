package com.meterengine.organization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.organization.api.OrganizationPerson;
import com.meterengine.organization.entity.OrganizationMember;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ManagedMemberResponse(
    @Schema(description = "사용자 계정 ID(UUID)") @JsonProperty("user_account_id") UUID userAccountId,
    @Schema(description = "로그인 이메일") String email,
    @Schema(description = "사용자 이름") String name,
    @Schema(description = "이 조직에서의 역할입니다. owner는 대표관리자, admin은 관리자, member는 일반 사용자입니다.")
        @JsonProperty("organization_role")
        String organizationRole,
    @Schema(description = "조직에 소속된 시각(ISO 8601)") @JsonProperty("created_at")
        OffsetDateTime createdAt,
    @Schema(description = "소속 상태입니다. active는 사용 중, archived는 소속을 보관한 사용자입니다.") String status,
    @Schema(description = "소속을 보관한 시각(ISO 8601)입니다. 보관하지 않았으면 null입니다.") @JsonProperty("revoked_at")
        OffsetDateTime revokedAt,
    @Schema(description = "이 조직에서의 권한(ingest, read, admin, payments)") List<String> scopes) {
  public static ManagedMemberResponse from(OrganizationMember member, OrganizationPerson user) {
    return new ManagedMemberResponse(
        user.id(),
        user.email(),
        user.name(),
        member.getRole(),
        member.getCreatedAt(),
        member.getRevokedAt() == null ? "active" : "archived",
        member.getRevokedAt(),
        member.getScopes());
  }
}
