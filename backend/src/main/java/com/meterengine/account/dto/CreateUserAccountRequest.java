package com.meterengine.account.dto;

import com.meterengine.account.entity.UserAccount;
import com.meterengine.global.validation.StorableText;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserAccountRequest(
    @Schema(description = "새 운영자의 이메일. 소문자로 저장하며 기존 계정은 변경하지 않는다")
        @NotBlank @Email @Pattern(regexp = UserAccount.EMAIL_PATTERN, message = UserAccount.EMAIL_FORMAT_MESSAGE) @Size(max = UserAccount.EMAIL_MAX_LENGTH) @StorableText
        String email,
    @Schema(description = "새 운영자의 이름")
        @NotBlank @Size(max = UserAccount.NAME_MAX_LENGTH) @StorableText
        String name,
    @Schema(format = "password", description = "초기 비밀번호. 8자 이상 72자 이하이고 UTF-8로 72바이트 이하")
        @NotBlank @Size(min = UserAccount.PASSWORD_MIN_LENGTH, max = UserAccount.PASSWORD_MAX_LENGTH) @StorableText
        String password) {

  @Override
  public String toString() {
    return "CreateUserAccountRequest[password=(redacted)]";
  }
}
