package com.meterengine.organization.controller;

import com.meterengine.global.error.ErrorResponse;
import com.meterengine.global.security.AuthenticatedPrincipal;
import com.meterengine.global.security.EnvironmentReady;
import com.meterengine.global.security.OperatorAccess;
import com.meterengine.global.validation.StorableText;
import com.meterengine.organization.dto.CreateOrganizationMemberRequest;
import com.meterengine.organization.dto.ListOrganizationsResponse;
import com.meterengine.organization.dto.OrganizationMemberResponse;
import com.meterengine.organization.dto.OrganizationResponse;
import com.meterengine.organization.dto.UpdateOrganizationStatusRequest;
import com.meterengine.organization.entity.Organization;
import com.meterengine.organization.service.OrganizationMemberService;
import com.meterengine.organization.service.OrganizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@EnvironmentReady
@RequestMapping("/v1/organizations")
public class OrganizationController {

  private final OrganizationService organizationService;
  private final OrganizationMemberService organizationMemberService;

  OrganizationController(
      OrganizationService organizationService,
      OrganizationMemberService organizationMemberService) {
    this.organizationService = organizationService;
    this.organizationMemberService = organizationMemberService;
  }

  @GetMapping
  @OperatorAccess
  @Operation(
      summary = "도입사 목록 조회",
      description =
          """
          도입사를 만든 순서로 돌려준다. status를 주면 그 상태의 도입사만 돌려준다.
          operator 로그인 세션만 부를 수 있다. 페이지를 나누지 않는다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "도입사 목록. 없으면 organizations가 빈 배열이다"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=validation_error: status가 pending, active, rejected, suspended가 아니다")
  })
  public ListOrganizationsResponse listOrganizations(
      @Parameter(
              description = "상태로 좁힌다. 생략하면 모든 상태다.",
              schema =
                  @Schema(
                      allowableValues = {
                        Organization.PENDING,
                        Organization.ACTIVE,
                        Organization.REJECTED,
                        Organization.SUSPENDED
                      }))
          @RequestParam(required = false)
          @Pattern(regexp = Organization.STATUS_PATTERN) @StorableText
          String status) {
    return ListOrganizationsResponse.from(organizationService.list(status));
  }

  @PutMapping("/{id}/status")
  @OperatorAccess
  @Operation(
      summary = "도입사 상태 변경",
      description =
          """
          도입사의 상태를 바꾸고 바뀐 도입사를 돌려준다. 승인은 active, 거절은 rejected, 정지는 suspended, 재개는 active로 보낸다.
          허용되는 전이는 pending에서 active나 rejected, active에서 suspended, suspended에서 active뿐이다.
          정지하면 그 도입사의 키와 세션으로 도입사의 데이터를 다루는 요청이 403(organization_not_active)이다.
          operator 로그인 세션만 부를 수 있다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "바뀐 도입사"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            "code=validation_error: status가 pending, active, rejected, suspended가 아니거나, 경로의 UUID 형식이 틀렸다"),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=organization_not_found: 그런 도입사가 없다"),
    @ApiResponse(
        responseCode = "409",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            "code=organization_status_transition_invalid: 지금 상태에서 그 상태로는 바꿀 수 없다. 지금과 같은 상태도 여기에 든다")
  })
  public OrganizationResponse updateOrganizationStatus(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @Parameter(description = "상태를 바꿀 도입사의 ID.") @PathVariable UUID id,
      @Valid @RequestBody UpdateOrganizationStatusRequest request) {
    return OrganizationResponse.from(organizationService.update(principal, id, request.status()));
  }

  @PostMapping("/{id}/members")
  @ResponseStatus(HttpStatus.CREATED)
  @OperatorAccess
  @Operation(
      summary = "도입사 소속 추가",
      description =
          """
          사용자를 도입사에 소속시킨다. 소속된 사용자는 로그인 세션의 도입사를 이 도입사로 바꿀 수 있다.
          operator 로그인 세션만 부를 수 있다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "만든 소속"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            "code=validation_error: user_account_id가 없거나 UUID 형식이 아니거나, 경로의 UUID 형식이 틀렸다"),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            """
            code=organization_not_found: 그런 도입사가 없다.
            code=user_account_not_found: 그런 사용자가 없다.
            """),
    @ApiResponse(
        responseCode = "409",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=organization_member_already_exists: 이미 이 도입사에 소속된 사용자다")
  })
  public OrganizationMemberResponse createOrganizationMember(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @Parameter(description = "사용자를 소속시킬 도입사의 ID.") @PathVariable UUID id,
      @Valid @RequestBody CreateOrganizationMemberRequest request) {
    return OrganizationMemberResponse.from(
        organizationMemberService.create(principal, id, request.userAccountId(), request.scopes()));
  }
}
