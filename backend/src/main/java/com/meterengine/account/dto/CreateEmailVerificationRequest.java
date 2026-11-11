package com.meterengine.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.account.entity.UserAccount;
import com.meterengine.global.validation.StorableText;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateEmailVerificationRequest(
    @Schema(description = "가입한 이메일. 대소문자를 가리지 않는다")
        @NotBlank @Email @Pattern(regexp = UserAccount.EMAIL_PATTERN, message = UserAccount.EMAIL_FORMAT_MESSAGE) @Size(max = UserAccount.EMAIL_MAX_LENGTH) @StorableText
        String email,
    @Schema(description = "Cloudflare Turnstile 위젯이 준 토큰. 서버가 캡차를 켰을 때만 필요하다")
        @JsonProperty("turnstile_token")
        @Size(max = CreateSignupRequest.TURNSTILE_TOKEN_MAX_LENGTH) @StorableText
        String turnstileToken) {

  @Override
  public String toString() {
    return "CreateEmailVerificationRequest[email=" + email + ", turnstileToken=(redacted)]";
  }
}
