package com.meterengine.organization.dto;

import com.meterengine.global.security.AccessScopes;
import com.meterengine.global.validation.StorableText;
import com.meterengine.organization.api.OrganizationPerson;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateOrganizationInvitationRequest(
    @Schema(description = "초대할 이메일입니다. 254자까지이며 소문자로 바꿔 저장합니다.")
        @NotBlank @Email @Pattern(
            regexp = OrganizationPerson.EMAIL_PATTERN,
            message = OrganizationPerson.EMAIL_FORMAT_MESSAGE)
        @Size(max = OrganizationPerson.EMAIL_MAX_LENGTH) @StorableText
        String email,
    @Schema(
            description =
                "수락하면 부여할 권한입니다. ingest, read, admin, payments 가운데 하나 이상을 담고, 생략하면 read(조회)만 줍니다.",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @NotEmpty List<@NotNull @StorableText @Pattern(regexp = AccessScopes.PATTERN) String> scopes) {
  public CreateOrganizationInvitationRequest {
    if (scopes == null) scopes = AccessScopes.READ;
  }

  public CreateOrganizationInvitationRequest(String email) {
    this(email, AccessScopes.READ);
  }
}
