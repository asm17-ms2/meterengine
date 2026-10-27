package com.meterengine.organization.dto;

import java.util.List;

public record ListOrganizationInvitationsResponse(
    List<OrganizationInvitationResponse> invitations) {}
