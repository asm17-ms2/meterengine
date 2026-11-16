package com.meterengine.organization.controller;

import com.meterengine.global.security.CurrentOrganizationId;
import com.meterengine.global.security.EnvironmentReady;
import com.meterengine.global.security.MemberAdminAccess;
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
  @Operation(summary = "현재 조직 조회", description = "요청에 실은 API 키나 로그인 세션의 조직을 돌려줍니다.")
  @ApiResponses({@ApiResponse(responseCode = "200", description = "현재 조직")})
  public OrganizationResponse getOrganization(@CurrentOrganizationId UUID organizationId) {
    return OrganizationResponse.from(organizationService.get(organizationId));
  }

  @GetMapping("/supplier")
  @ReadAccess
  @Operation(
      summary = "공급자 정보 조회",
      description = "현재 업무 환경에 저장한 공급자 정보(조직의 사업자 정보)를 돌려줍니다. 저장하지 않은 값은 null입니다.")
  public SupplierResponse findSupplier(@CurrentOrganizationId UUID organizationId) {
    return organizationService.find(organizationId);
  }

  @PutMapping("/supplier")
  @MemberAdminAccess
  @Operation(
      summary = "공급자 정보 수정",
      description =
          "현재 업무 환경의 공급자 정보를 보낸 값으로 바꾸고 저장한 정보를 돌려줍니다. 공급자 정보는 업무 환경마다 따로 저장하며"
              + " 다른 환경에 복사하지 않습니다. 사업자등록번호는 자릿수만 확인합니다. 로그인 세션만 부를 수 있고 API 키로는 부를 수 없습니다.")
  public SupplierResponse updateSupplier(
      @CurrentOrganizationId UUID organizationId,
      @Valid @RequestBody UpdateSupplierRequest request) {
    return organizationService.update(organizationId, request);
  }
}
