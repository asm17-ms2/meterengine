package com.meterengine.organization.dto;

public record OrganizationInvitationSecretResponse(
    OrganizationInvitationResponse invitation, String token) {
  @Override
  public String toString() {
    return "OrganizationInvitationSecretResponse[token=(redacted)]";
  }
}
