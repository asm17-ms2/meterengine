package com.meterengine.account.dto;

import com.meterengine.global.validation.StorableText;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record CreateSessionRequest(
    @Schema(description = "가입한 이메일입니다. 대소문자를 가리지 않습니다.") @NotBlank @StorableText String email,
    @Schema(format = "password", description = "계정의 비밀번호입니다.") @NotBlank @StorableText
        String password) {

  @Override
  public String toString() {
    return "CreateSessionRequest[email=" + email + ", password=(redacted)]";
  }
}
