package com.meterengine.organization.dto;

import com.meterengine.organization.entity.Organization;
import java.util.List;

public record ListOrganizationsResponse(List<OrganizationResponse> organizations) {

  public static ListOrganizationsResponse from(List<Organization> organizations) {
    return new ListOrganizationsResponse(
        organizations.stream().map(OrganizationResponse::from).toList());
  }
}
