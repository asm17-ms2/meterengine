package com.meterengine.organization.controller;

import com.meterengine.global.security.AdminAccess;
import com.meterengine.global.security.CurrentOrganizationId;
import com.meterengine.global.security.EnvironmentReady;
import com.meterengine.global.security.ReadAccess;
import com.meterengine.organization.dto.OrganizationResponse;
import com.meterengine.organization.dto.SupplierResponse;
import com.meterengine.organization.dto.UpdateSupplierRequest;
import com.meterengine.organization.service.OrganizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@EnvironmentReady
@RequestMapping("/v1/organization")
public class CurrentOrganizationController {

  private final OrganizationService organizationService;

  CurrentOrganizationController(OrganizationService organizationService) {
    this.organizationService = organizationService;
  }

  @GetMapping
  @ReadAccess
  @Operation(summary = "현재 도입사 조회", description = "요청에 실은 키나 로그인 세션의 도입사를 돌려준다.")
  @ApiResponses({@ApiResponse(responseCode = "200", description = "현재 도입사")})
  public OrganizationResponse getOrganization(@CurrentOrganizationId UUID organizationId) {
    return OrganizationResponse.from(organizationService.get(organizationId));
  }

  @GetMapping("/supplier")
  @ReadAccess
  @Operation(summary = "공급자 정보 조회")
  public SupplierResponse findSupplier(@CurrentOrganizationId UUID organizationId) {
    return organizationService.find(organizationId);
  }

  @PutMapping("/supplier")
  @AdminAccess
  @Operation(summary = "공급자 정보 수정")
  public SupplierResponse updateSupplier(
      @CurrentOrganizationId UUID organizationId,
      @Valid @RequestBody UpdateSupplierRequest request) {
    return organizationService.update(organizationId, request);
  }
}
