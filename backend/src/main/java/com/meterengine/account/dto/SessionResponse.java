package com.meterengine.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.account.service.CurrentSession;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

public record SessionResponse(
    @Schema(description = "로그인한 계정") @JsonProperty("user_account") SessionUserAccount userAccount,
    @Schema(
            description =
                "세션이 고른 조직입니다. 고른 조직이 없는 세션(소속 조직이 없는 MeterEngine 운영자 계정, 초대를 수락하기 전인 계정)이면 null입니다.")
        SessionOrganization organization,
    @Schema(description = "사용자가 소속된 조직입니다. 소속된 순서로 담습니다.") List<SessionOrganization> organizations,
    @Schema(description = "세션이 끝나는 시각(ISO 8601)입니다. 이 시각이 지나면 다시 로그인합니다.")
        @JsonProperty("expires_at")
        OffsetDateTime expiresAt,
    @Schema(
            description =
                "세션이 고른 조직에서의 역할입니다. owner는 대표관리자, admin은 관리자, member는 일반 사용자입니다."
                    + " 고른 조직이 없거나 소속이 보관됐으면 null입니다.")
        @JsonProperty("organization_role")
        String organizationRole,
    @Schema(
            description =
                "이용약관 동의와 개인정보 처리방침 확인이 남았는지 나타냅니다. true면 POST /v1/sessions/current/consents로 동의하기 전까지"
                    + " 업무 요청이 403(consent_required)입니다.")
        @JsonProperty("consent_required")
        boolean consentRequired,
    @Schema(description = "세션이 고른 조직에서 이 사용자가 가진 업무 권한입니다. 고른 조직이 없거나 소속이 보관됐으면 빈 배열입니다.")
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
