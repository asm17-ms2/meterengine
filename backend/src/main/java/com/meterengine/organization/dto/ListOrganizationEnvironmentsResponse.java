package com.meterengine.organization.dto;

import java.util.List;

public record ListOrganizationEnvironmentsResponse(
    List<OrganizationEnvironmentResponse> environments) {}
