package com.meterengine.organization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.organization.entity.OrganizationInvitation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record OrganizationInvitationResponse(
    @Schema(description = "초대 ID(UUID)") UUID id,
    @Schema(description = "초대 이메일입니다. 소문자로 바꿔 저장합니다.") String email,
    @Schema(
            description =
                "초대 상태입니다. pending은 초대 대기, accepted는 수락 완료, revoked는 초대 취소, expired는 만료입니다.")
        String status,
    @Schema(description = "초대 링크를 발급한 시각(ISO 8601)") @JsonProperty("created_at")
        OffsetDateTime createdAt,
    @Schema(description = "초대가 만료되는 시각(ISO 8601)입니다. 발급하고 48시간 뒤입니다.") @JsonProperty("expires_at")
        OffsetDateTime expiresAt,
    @Schema(description = "수락한 시각(ISO 8601)입니다. 수락하지 않았으면 null입니다.") @JsonProperty("accepted_at")
        OffsetDateTime acceptedAt,
    @Schema(description = "취소한 시각(ISO 8601)입니다. 취소하지 않았으면 null입니다.") @JsonProperty("revoked_at")
        OffsetDateTime revokedAt,
    @Schema(description = "수락하면 받을 권한") List<String> scopes) {
  public static OrganizationInvitationResponse from(OrganizationInvitation i) {
    String state =
        i.getAcceptedAt() != null
            ? "accepted"
            : i.getRevokedAt() != null
                ? "revoked"
                : i.isUsable(OffsetDateTime.now()) ? "pending" : "expired";
    return new OrganizationInvitationResponse(
        i.getId(),
        i.getEmail(),
        state,
        i.getCreatedAt(),
        i.getExpiresAt(),
        i.getAcceptedAt(),
        i.getRevokedAt(),
        i.getScopes());
  }
}
