package com.meterengine.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.account.entity.UserAccount;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record SignupUserAccount(
    @Schema(description = "계정 ID(UUID)") UUID id,
    @Schema(description = "로그인에 쓰는 이메일(소문자로 바꿔 저장한 값)") String email,
    @Schema(description = "가입한 사람 이름") String name,
    @Schema(
            description =
                "계정의 역할입니다. operator는 MeterEngine 운영자 계정이고, 그 밖의 계정은 member입니다. 가입으로 만든 계정은 member입니다.",
            allowableValues = {"member", "operator"})
        String role,
    @Schema(description = "이메일 인증을 마쳤는지 나타냅니다. false면 받은 메일의 링크로 인증해야 로그인할 수 있습니다.")
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
