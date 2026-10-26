package com.meterengine.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.account.service.UserAccountMemberships;
import java.util.List;

public record ListUserAccountsResponse(
    @JsonProperty("user_accounts") List<UserAccountResponse> userAccounts) {

  public static ListUserAccountsResponse from(List<UserAccountMemberships> userAccounts) {
    return new ListUserAccountsResponse(
        userAccounts.stream().map(UserAccountResponse::from).toList());
  }
}
