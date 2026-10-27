package com.meterengine.organization.dto;

import com.meterengine.global.validation.StorableText;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AcceptOrganizationInvitationRequest(
    @Size(max = 255) @StorableText String name,
    @Size(min = 8, max = 72) @StorableText String password,
    @NotEmpty(groups = NewAccount.class) @Valid List<@NotNull InvitationConsentRequest> consents) {
  public interface NewAccount {}

  @Override
  public String toString() {
    return "AcceptOrganizationInvitationRequest[password=(redacted)]";
  }
}
