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
    @Schema(description = "로그인에 쓸 이메일입니다. 254자까지이며, 대소문자를 가리지 않고 같은 이메일의 계정이 이미 있으면 409입니다.")
        @NotBlank @Email @Pattern(
            regexp = OrganizationPerson.EMAIL_PATTERN,
            message = OrganizationPerson.EMAIL_FORMAT_MESSAGE)
        @Size(max = OrganizationPerson.EMAIL_MAX_LENGTH) @StorableText
        String email,
    @Schema(description = "사용자 이름입니다. 255자까지입니다.")
        @NotBlank @Size(max = OrganizationPerson.NAME_MAX_LENGTH) @StorableText
        String name,
    @Schema(description = "처음 로그인할 때 쓸 비밀번호입니다. 8자 이상 72자 이하이고 UTF-8로 72바이트 이하여야 합니다.")
        @NotBlank @Size(
            min = OrganizationPerson.PASSWORD_MIN_LENGTH,
            max = OrganizationPerson.PASSWORD_MAX_LENGTH)
        @StorableText
        String password,
    @Schema(
            description =
                "부여할 권한입니다. ingest, read, admin, payments 가운데 하나 이상을 담고, 생략하면 read(조회)만 줍니다. admin을 담으면"
                    + " 역할이 관리자, 아니면 일반 사용자가 됩니다.",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
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
