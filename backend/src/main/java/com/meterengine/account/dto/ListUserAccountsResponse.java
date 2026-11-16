package com.meterengine.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.account.service.UserAccountMemberships;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ListUserAccountsResponse(
    @Schema(description = "모든 사용자입니다. 만든 순서로 담습니다.") @JsonProperty("user_accounts")
        List<UserAccountResponse> userAccounts) {

  public static ListUserAccountsResponse from(List<UserAccountMemberships> userAccounts) {
    return new ListUserAccountsResponse(
        userAccounts.stream().map(UserAccountResponse::from).toList());
  }
}
