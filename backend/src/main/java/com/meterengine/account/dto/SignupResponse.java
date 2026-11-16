package com.meterengine.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.account.service.Signup;
import io.swagger.v3.oas.annotations.media.Schema;

public record SignupResponse(
    @Schema(description = "만든 조직입니다. status는 active입니다.") SignupOrganization organization,
    @Schema(description = "만든 계정입니다. 이 조직의 대표관리자이며 role은 member입니다.") @JsonProperty("user_account")
        SignupUserAccount userAccount) {

  public static SignupResponse from(Signup signup) {
    return new SignupResponse(
        SignupOrganization.from(signup.organization()),
        SignupUserAccount.from(signup.userAccount()));
  }
}
