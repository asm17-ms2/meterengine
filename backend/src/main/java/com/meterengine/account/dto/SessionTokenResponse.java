package com.meterengine.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.account.service.IssuedSession;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

public record SessionTokenResponse(
    @Schema(description = "세션 토큰 원문. 이 응답에서만 볼 수 있다. Authorization: Bearer 값으로 싣는다") String token,
    @Schema(description = "세션이 끝나는 시각. 이 시각이 지나면 다시 로그인한다") @JsonProperty("expires_at")
        OffsetDateTime expiresAt,
    @JsonProperty("user_account") SessionUserAccount userAccount,
    @Schema(description = "세션이 고른 도입사. 활성 소속이 없는 operator면 null")
        SessionOrganization organization) {

  public static SessionTokenResponse from(IssuedSession issued) {
    return new SessionTokenResponse(
        issued.token(),
        issued.session().session().getExpiresAt(),
        SessionUserAccount.from(issued.session().userAccount()),
        SessionOrganization.from(issued.session().organization()));
  }

  @Override
  public String toString() {
    return "SessionTokenResponse[token=(redacted), expiresAt=" + expiresAt + "]";
  }
}
