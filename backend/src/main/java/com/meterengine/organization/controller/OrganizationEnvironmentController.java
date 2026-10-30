package com.meterengine.organization.controller;

import com.meterengine.global.security.CurrentOrganizationId;
import com.meterengine.global.security.EnvironmentReady;
import com.meterengine.global.security.ReadAccess;
import com.meterengine.organization.dto.ListOrganizationEnvironmentsResponse;
import com.meterengine.organization.dto.OrganizationEnvironmentResponse;
import com.meterengine.organization.service.OrganizationEnvironmentService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@EnvironmentReady
@RequestMapping("/v1/organization/environments")
public class OrganizationEnvironmentController {
  private final OrganizationEnvironmentService organizationEnvironmentService;

  public OrganizationEnvironmentController(
      OrganizationEnvironmentService organizationEnvironmentService) {
    this.organizationEnvironmentService = organizationEnvironmentService;
  }

  @GetMapping
  @ReadAccess
  @Operation(summary = "도입사의 테스트와 실제 환경 조회", description = "기존 미분류 데이터는 두 환경에 복사하거나 귀속시키지 않는다.")
  public ListOrganizationEnvironmentsResponse listOrganizationEnvironments(
      @CurrentOrganizationId UUID organizationId) {
    return new ListOrganizationEnvironmentsResponse(
        organizationEnvironmentService.list(organizationId).stream()
            .map(OrganizationEnvironmentResponse::from)
            .toList());
  }
}
