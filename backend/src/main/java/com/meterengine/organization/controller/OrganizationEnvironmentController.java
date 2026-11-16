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
  @Operation(
      summary = "조직의 테스트 환경과 라이브 환경 조회",
      description =
          "조직의 테스트 환경과 라이브 환경을 id와 mode로 돌려줍니다. 테스트 환경이 먼저입니다. 라이브 환경이 잠겨 있어도 두 환경을"
              + " 모두 돌려주며, 잠겼는지는 GET /v1/organization의 live_environment_opened_at으로 봅니다. 기존 데이터는 두"
              + " 환경에 복사하거나 귀속시키지 않습니다.")
  public ListOrganizationEnvironmentsResponse listOrganizationEnvironments(
      @CurrentOrganizationId UUID organizationId) {
    return new ListOrganizationEnvironmentsResponse(
        organizationEnvironmentService.list(organizationId).stream()
            .map(OrganizationEnvironmentResponse::from)
            .toList());
  }
}
