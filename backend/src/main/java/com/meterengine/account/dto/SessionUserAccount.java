package com.meterengine.account.dto;

import com.meterengine.account.entity.UserAccount;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record SessionUserAccount(
    @Schema(description = "계정 ID(UUID)") UUID id,
    @Schema(description = "로그인에 쓰는 이메일") String email,
    @Schema(description = "사용자 이름") String name,
    @Schema(
            description =
                "계정의 역할입니다. operator는 MeterEngine 운영자 계정이고, 그 밖의 계정은 member입니다. 조직 안의 역할은 organization_role에 있습니다.",
            allowableValues = {"member", "operator"})
        String role) {

  public static SessionUserAccount from(UserAccount userAccount) {
    return new SessionUserAccount(
        userAccount.getId(), userAccount.getEmail(), userAccount.getName(), userAccount.getRole());
  }
}
