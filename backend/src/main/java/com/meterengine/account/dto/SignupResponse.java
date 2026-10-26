package com.meterengine.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.account.service.Signup;
import io.swagger.v3.oas.annotations.media.Schema;

public record SignupResponse(
    @Schema(description = "만든 도입사. 운영자가 승인할 때까지 pending이다") SignupOrganization organization,
    @Schema(description = "만든 사용자. 역할은 member다") @JsonProperty("user_account")
        SignupUserAccount userAccount) {

  public static SignupResponse from(Signup signup) {
    return new SignupResponse(
        SignupOrganization.from(signup.organization()),
        SignupUserAccount.from(signup.userAccount()));
  }
}
