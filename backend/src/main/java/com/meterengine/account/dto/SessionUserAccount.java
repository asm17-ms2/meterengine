package com.meterengine.account.dto;

import com.meterengine.account.entity.UserAccount;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record SessionUserAccount(
    UUID id,
    String email,
    String name,
    @Schema(
            description = "사용자 역할",
            allowableValues = {"member", "operator"})
        String role) {

  public static SessionUserAccount from(UserAccount userAccount) {
    return new SessionUserAccount(
        userAccount.getId(), userAccount.getEmail(), userAccount.getName(), userAccount.getRole());
  }
}
