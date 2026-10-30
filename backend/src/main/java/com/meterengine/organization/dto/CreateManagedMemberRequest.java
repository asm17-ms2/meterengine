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

public record CreateManagedMemberRequest(
    @NotBlank @Email @Pattern(
            regexp = OrganizationPerson.EMAIL_PATTERN,
            message = OrganizationPerson.EMAIL_FORMAT_MESSAGE)
        @Size(max = OrganizationPerson.EMAIL_MAX_LENGTH) @StorableText
        String email,
    @NotBlank @Size(max = OrganizationPerson.NAME_MAX_LENGTH) @StorableText String name,
    @NotBlank @Size(
            min = OrganizationPerson.PASSWORD_MIN_LENGTH,
            max = OrganizationPerson.PASSWORD_MAX_LENGTH)
        @StorableText
        String password,
    @Schema(description = "부여할 권한. 생략하면 조회 전용", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @NotEmpty List<@NotNull @StorableText @Pattern(regexp = AccessScopes.PATTERN) String> scopes) {
  public CreateManagedMemberRequest {
    if (scopes == null) scopes = AccessScopes.READ;
  }

  public CreateManagedMemberRequest(String email, String name, String password) {
    this(email, name, password, AccessScopes.READ);
  }

  @Override
  public String toString() {
    return "CreateManagedMemberRequest[password=(redacted)]";
  }
}
