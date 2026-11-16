package com.meterengine.organization.dto;

import com.meterengine.global.validation.StorableText;
import com.meterengine.organization.api.OrganizationPerson;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AcceptOrganizationInvitationRequest(
    @Schema(description = "새로 만들 계정의 사용자 이름입니다. 초대 이메일로 가입한 계정이 없을 때 보내며 255자까지입니다.")
        @Size(max = OrganizationPerson.NAME_MAX_LENGTH) @StorableText
        String name,
    @Schema(
            description =
                "새로 만들 계정의 비밀번호입니다. 초대 이메일로 가입한 계정이 없을 때 보내며, 8자 이상 72자 이하이고 UTF-8로 72바이트 이하여야 합니다.")
        @Size(
            min = OrganizationPerson.PASSWORD_MIN_LENGTH,
            max = OrganizationPerson.PASSWORD_MAX_LENGTH)
        @StorableText
        String password,
    @Schema(
            description =
                "새로 만들 계정의 약관 동의입니다. 초대 이메일로 가입한 계정이 없을 때 보내며, terms_of_service와 privacy_policy를 한 번씩 담습니다.")
        @Valid List<@NotNull InvitationConsentRequest> consents) {

  @Override
  public String toString() {
    return "AcceptOrganizationInvitationRequest[password=(redacted)]";
  }
}
