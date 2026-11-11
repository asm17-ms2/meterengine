package com.meterengine.account.dto;

import com.meterengine.global.validation.StorableText;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmEmailVerificationRequest(
    @Schema(description = "인증 메일 링크에 든 토큰(me_verify_로 시작)") @NotBlank @Size(max = 64) @StorableText
        String token) {

  @Override
  public String toString() {
    return "ConfirmEmailVerificationRequest[token=(redacted)]";
  }
}
