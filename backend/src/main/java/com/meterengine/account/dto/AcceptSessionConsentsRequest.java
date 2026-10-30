package com.meterengine.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.account.entity.UserAccount;
import com.meterengine.global.validation.StorableText;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AcceptSessionConsentsRequest(
    @NotEmpty @Valid List<@NotNull CreateSignupConsentRequest> consents,
    @Schema(
            description = "관리자나 운영자가 만든 계정이 처음 동의할 때 정하는 새 비밀번호. 처음 받은 비밀번호와 달라야 한다",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @JsonProperty("new_password")
        @Size(min = UserAccount.PASSWORD_MIN_LENGTH, max = UserAccount.PASSWORD_MAX_LENGTH) @StorableText
        String newPassword) {
  @Override
  public String toString() {
    return "AcceptSessionConsentsRequest[consents=" + consents + ", newPassword=(redacted)]";
  }
}
