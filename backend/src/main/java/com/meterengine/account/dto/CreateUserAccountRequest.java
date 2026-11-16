package com.meterengine.account.dto;

import com.meterengine.account.entity.UserAccount;
import com.meterengine.global.validation.StorableText;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserAccountRequest(
    @Schema(
            description =
                "새 MeterEngine 운영자 계정(operator)의 이메일입니다. 소문자로 저장하며, 이미 가입한 이메일이면 기존 계정을 바꾸지 않습니다.")
        @NotBlank @Email @Pattern(regexp = UserAccount.EMAIL_PATTERN, message = UserAccount.EMAIL_FORMAT_MESSAGE) @Size(max = UserAccount.EMAIL_MAX_LENGTH) @StorableText
        String email,
    @Schema(description = "새 MeterEngine 운영자의 이름")
        @NotBlank @Size(max = UserAccount.NAME_MAX_LENGTH) @StorableText
        String name,
    @Schema(format = "password", description = "초기 비밀번호입니다. 8자 이상 72자 이하이고 UTF-8로 72바이트 이하여야 합니다.")
        @NotBlank @Size(min = UserAccount.PASSWORD_MIN_LENGTH, max = UserAccount.PASSWORD_MAX_LENGTH) @StorableText
        String password) {

  @Override
  public String toString() {
    return "CreateUserAccountRequest[password=(redacted)]";
  }
}
