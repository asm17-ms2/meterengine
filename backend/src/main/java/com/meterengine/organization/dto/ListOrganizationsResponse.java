package com.meterengine.organization.dto;

import com.meterengine.organization.entity.Organization;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ListOrganizationsResponse(
    @Schema(description = "조직 목록입니다. 만든 순서입니다.") List<OrganizationResponse> organizations) {

  public static ListOrganizationsResponse from(List<Organization> organizations) {
    return new ListOrganizationsResponse(
        organizations.stream().map(OrganizationResponse::from).toList());
  }
}
