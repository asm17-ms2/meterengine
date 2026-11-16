package com.meterengine.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.account.service.UserAccountMemberships;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record UserAccountResponse(
    @Schema(description = "계정 ID(UUID)") UUID id,
    @Schema(description = "로그인에 쓰는 이메일") String email,
    @Schema(description = "사용자 이름") String name,
    @Schema(
            description = "계정의 역할입니다. operator는 MeterEngine 운영자 계정이고, 그 밖의 계정은 member입니다.",
            allowableValues = {"member", "operator"})
        String role,
    @Schema(description = "사용자가 소속된 조직입니다. 소속된 순서로 담습니다.")
        List<UserAccountOrganization> organizations,
    @Schema(description = "계정을 만든 시각(ISO 8601)") @JsonProperty("created_at")
        OffsetDateTime createdAt) {

  public static UserAccountResponse from(UserAccountMemberships memberships) {
    return new UserAccountResponse(
        memberships.userAccount().getId(),
        memberships.userAccount().getEmail(),
        memberships.userAccount().getName(),
        memberships.userAccount().getRole(),
        memberships.organizations().stream().map(UserAccountOrganization::from).toList(),
        memberships.userAccount().getCreatedAt());
  }
}
