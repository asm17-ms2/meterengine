package com.meterengine.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.account.entity.UserAccount;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record SignupUserAccount(
    UUID id,
    String email,
    String name,
    @Schema(
            description = "사용자 역할",
            allowableValues = {"member", "operator"})
        String role,
    @Schema(description = "이메일 인증을 마쳤는지. false면 받은 메일의 링크로 인증해야 로그인할 수 있다")
        @JsonProperty("email_verified")
        boolean emailVerified) {

  public static SignupUserAccount from(UserAccount userAccount) {
    return new SignupUserAccount(
        userAccount.getId(),
        userAccount.getEmail(),
        userAccount.getName(),
        userAccount.getRole(),
        userAccount.isEmailVerified());
  }
}
