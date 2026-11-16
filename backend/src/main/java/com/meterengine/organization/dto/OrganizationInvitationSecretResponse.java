package com.meterengine.organization.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record OrganizationInvitationSecretResponse(
    @Schema(description = "발급한 초대") OrganizationInvitationResponse invitation,
    @Schema(description = "초대 링크의 토큰입니다. 원문은 이 응답에서만 볼 수 있습니다.") String token) {
  @Override
  public String toString() {
    return "OrganizationInvitationSecretResponse[token=(redacted)]";
  }
}
