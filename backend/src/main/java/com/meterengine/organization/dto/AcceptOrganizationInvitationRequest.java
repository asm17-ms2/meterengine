package com.meterengine.organization.dto;

import com.meterengine.global.validation.StorableText;
import com.meterengine.organization.api.OrganizationPerson;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AcceptOrganizationInvitationRequest(
    @Size(max = OrganizationPerson.NAME_MAX_LENGTH) @StorableText String name,
    @Size(
            min = OrganizationPerson.PASSWORD_MIN_LENGTH,
            max = OrganizationPerson.PASSWORD_MAX_LENGTH)
        @StorableText
        String password,
    @Valid List<@NotNull InvitationConsentRequest> consents) {

  @Override
  public String toString() {
    return "AcceptOrganizationInvitationRequest[password=(redacted)]";
  }
}
