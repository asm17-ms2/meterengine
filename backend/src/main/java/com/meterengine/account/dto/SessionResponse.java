package com.meterengine.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.account.service.CurrentSession;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

public record SessionResponse(
    @JsonProperty("user_account") SessionUserAccount userAccount,
    @Schema(description = "세션이 고른 도입사. 고른 도입사가 없는 operator 세션이면 null")
        SessionOrganization organization,
    @Schema(description = "사용자가 소속된 도입사. 소속된 순서") List<SessionOrganization> organizations,
    @Schema(description = "세션이 끝나는 시각. 이 시각이 지나면 다시 로그인한다") @JsonProperty("expires_at")
        OffsetDateTime expiresAt,
    @JsonProperty("organization_role") String organizationRole,
    @JsonProperty("consent_required") boolean consentRequired,
    List<String> scopes) {

  public static SessionResponse from(CurrentSession current) {
    return new SessionResponse(
        SessionUserAccount.from(current.userAccount()),
        SessionOrganization.from(current.organization()),
        current.organizations().stream().map(SessionOrganization::from).toList(),
        current.session().getExpiresAt(),
        current.organizationRole(),
        current.userAccount().isConsentRequired(),
        current.scopes());
  }
}
