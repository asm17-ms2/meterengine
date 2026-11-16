package com.meterengine.organization.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ListOrganizationInvitationsResponse(
    @Schema(description = "조직 초대 목록입니다. 최근에 발급한 것부터입니다.")
        List<OrganizationInvitationResponse> invitations) {}
