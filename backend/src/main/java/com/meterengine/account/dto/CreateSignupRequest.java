package com.meterengine.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.account.entity.UserAccount;
import com.meterengine.global.validation.StorableText;
import com.meterengine.organization.entity.Organization;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateSignupRequest(
    @Schema(description = "가입하는 도입사 이름")
        @JsonProperty("organization_name")
        @NotBlank @Size(max = Organization.NAME_MAX_LENGTH) @StorableText
        String organizationName,
    @Schema(description = "가입하는 사람 이름")
        @NotBlank @Size(max = UserAccount.NAME_MAX_LENGTH) @StorableText
        String name,
    @Schema(description = "로그인에 쓸 이메일. 소문자로 바꿔 저장하고 대소문자를 가리지 않고 중복을 막는다")
        @NotBlank @Email @Pattern(regexp = UserAccount.EMAIL_PATTERN, message = UserAccount.EMAIL_FORMAT_MESSAGE) @Size(max = UserAccount.EMAIL_MAX_LENGTH) @StorableText
        String email,
    @Schema(format = "password", description = "8자 이상 72자 이하이고 UTF-8로 72바이트 이하")
        @NotBlank @Size(min = UserAccount.PASSWORD_MIN_LENGTH, max = UserAccount.PASSWORD_MAX_LENGTH) @StorableText
        String password,
    @Schema(description = "필수 동의. terms_of_service와 privacy_policy를 한 번씩 담는다") @NotEmpty @Valid List<@NotNull CreateSignupConsentRequest> consents) {

  @Override
  public String toString() {
    return "CreateSignupRequest[organizationName="
        + organizationName
        + ", name="
        + name
        + ", email="
        + email
        + ", password=(redacted), consents="
        + consents
        + "]";
  }
}
