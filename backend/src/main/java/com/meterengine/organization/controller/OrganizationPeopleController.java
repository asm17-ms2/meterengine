package com.meterengine.organization.controller;

import com.meterengine.global.error.ErrorResponse;
import com.meterengine.global.security.AuthenticatedPrincipal;
import com.meterengine.global.security.CurrentOrganizationId;
import com.meterengine.global.security.EnvironmentReady;
import com.meterengine.global.security.MemberAdminAccess;
import com.meterengine.global.security.MemberReadAccess;
import com.meterengine.global.validation.StorableText;
import com.meterengine.organization.dto.CreateManagedMemberRequest;
import com.meterengine.organization.dto.CreateOrganizationInvitationRequest;
import com.meterengine.organization.dto.ListManagedMembersResponse;
import com.meterengine.organization.dto.ListOrganizationInvitationsResponse;
import com.meterengine.organization.dto.ManagedMemberResponse;
import com.meterengine.organization.dto.OrganizationInvitationSecretResponse;
import com.meterengine.organization.dto.UpdateOrganizationMemberRoleRequest;
import com.meterengine.organization.dto.UpdateOrganizationMemberScopesRequest;
import com.meterengine.organization.service.OrganizationPeopleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/v1/organization")
public class OrganizationPeopleController {
  private final OrganizationPeopleService organizationPeopleService;

  public OrganizationPeopleController(OrganizationPeopleService organizationPeopleService) {
    this.organizationPeopleService = organizationPeopleService;
  }

  @GetMapping("/members")
  @MemberReadAccess
  @Operation(summary = "도입사 사용자 목록", description = "소속이 사용 중이거나 보관된 사용자를 조회한다. 기본은 전체다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "도입사 사용자 목록"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=validation_error: status가 active, archived, all 가운데 하나가 아니다")
  })
  public ListManagedMembersResponse listOrganizationMembers(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @CurrentOrganizationId UUID organizationId,
      @Parameter(
              description = "소속 관리 상태. 생략하면 전체",
              schema = @Schema(allowableValues = {"active", "archived", "all"}))
          @RequestParam(defaultValue = "all")
          @StorableText
          String status) {
    return new ListManagedMembersResponse(
        organizationPeopleService.list(principal, organizationId, status));
  }

  @PostMapping("/members")
  @MemberAdminAccess
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "하위 관리자 계정 직접 생성")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "만든 사용자와 소속"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            """
            code=validation_error: email이 비었거나 이메일 형식이 아니거나 254자를 넘거나, name이 비었거나 255자를 넘거나,
            password가 8자보다 짧거나 72자나 UTF-8 72바이트를 넘거나, scopes가 비었거나 ingest, read, admin이 아닌 값을 담았거나,
            문자열에 NUL 문자나 짝이 없는 UTF-16 서로게이트를 담았다.
            """),
    @ApiResponse(
        responseCode = "409",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=email_already_exists: 대소문자를 가리지 않고 같은 이메일로 가입한 사용자가 있다")
  })
  public ManagedMemberResponse createManagedOrganizationMember(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @CurrentOrganizationId UUID organizationId,
      @Valid @RequestBody CreateManagedMemberRequest request) {
    return organizationPeopleService.create(principal, organizationId, request);
  }

  @PutMapping("/members/{id}/role")
  @MemberAdminAccess
  @Operation(summary = "도입사 사용자 역할 변경")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "바뀐 사용자"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            "code=validation_error: organization_role이 owner나 admin이 아니거나, 경로의 UUID 형식이 틀렸다"),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=user_account_not_found: 세션 도입사에 소속된 그런 사용자가 없다. 소속을 보관한 사용자도 같다"),
    @ApiResponse(
        responseCode = "409",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            "code=last_owner_required: 마지막 대표관리자의 organization_role을 owner가 아닌 값으로 바꾸려 했다")
  })
  public ManagedMemberResponse updateOrganizationMemberRole(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @CurrentOrganizationId UUID organizationId,
      @PathVariable UUID id,
      @Valid @RequestBody UpdateOrganizationMemberRoleRequest request) {
    return organizationPeopleService.update(
        principal, organizationId, id, request.organizationRole());
  }

  @PutMapping("/members/{id}/scopes")
  @MemberAdminAccess
  @Operation(
      summary = "도입사 사용자 권한 변경",
      description = "대표관리자가 도입사별 사용자 권한을 변경한다. 기존 세션도 다음 요청부터 변경한 권한을 사용한다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "바뀐 사용자"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            "code=validation_error: scopes가 비었거나 ingest, read, admin이 아닌 값을 담았거나, 경로의 UUID 형식이 틀렸다"),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=user_account_not_found: 세션 도입사에 소속된 그런 사용자가 없다. 소속을 보관한 사용자도 같다"),
    @ApiResponse(
        responseCode = "409",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=owner_scopes_required: 대표관리자의 scopes에 admin이 없다")
  })
  public ManagedMemberResponse updateOrganizationMemberScopes(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @CurrentOrganizationId UUID organizationId,
      @PathVariable UUID id,
      @Valid @RequestBody UpdateOrganizationMemberScopesRequest request) {
    return organizationPeopleService.update(principal, organizationId, id, request.scopes());
  }

  @DeleteMapping("/members/{id}")
  @MemberAdminAccess
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "도입사 사용자 소속 제거")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "소속을 보관했다. 본문 없음"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=validation_error: 경로의 UUID 형식이 틀렸다"),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=user_account_not_found: 세션 도입사에 소속된 그런 사용자가 없다. 소속을 보관한 사용자도 같다"),
    @ApiResponse(
        responseCode = "409",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=last_owner_required: 마지막 대표관리자의 소속을 제거하려 했다")
  })
  public void removeOrganizationMember(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @CurrentOrganizationId UUID organizationId,
      @PathVariable UUID id) {
    organizationPeopleService.remove(principal, organizationId, id);
  }

  @GetMapping("/invitations")
  @MemberAdminAccess
  @Operation(summary = "도입사 초대 목록")
  @ApiResponses({@ApiResponse(responseCode = "200", description = "도입사 초대 목록")})
  public ListOrganizationInvitationsResponse listOrganizationInvitations(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @CurrentOrganizationId UUID organizationId) {
    return new ListOrganizationInvitationsResponse(
        organizationPeopleService.invitations(principal, organizationId));
  }

  @PostMapping("/invitations")
  @MemberAdminAccess
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "도입사 초대 링크 발급")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "발급한 초대와 링크 토큰. 토큰 원문은 이 응답에서만 볼 수 있다"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            """
            code=validation_error: email이 비었거나 이메일 형식이 아니거나 254자를 넘거나,
            scopes가 비었거나 ingest, read, admin이 아닌 값을 담았거나, 문자열에 NUL 문자나 짝이 없는 UTF-16 서로게이트를 담았다.
            """),
    @ApiResponse(
        responseCode = "409",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=organization_member_already_exists: 그 이메일의 사용자가 이미 세션 도입사에 소속돼 있다")
  })
  public OrganizationInvitationSecretResponse inviteOrganizationUser(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @CurrentOrganizationId UUID organizationId,
      @Valid @RequestBody CreateOrganizationInvitationRequest request) {
    return organizationPeopleService.invite(
        principal, organizationId, request.email(), request.scopes());
  }

  @DeleteMapping("/invitations/{id}")
  @MemberAdminAccess
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "도입사 초대 취소")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "취소했다. 본문 없음"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=validation_error: 경로의 UUID 형식이 틀렸다"),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=invitation_not_found: 세션 도입사에 그런 초대가 없다"),
    @ApiResponse(
        responseCode = "409",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=invitation_unavailable: 만료됐거나 이미 수락 또는 취소된 초대다")
  })
  public void revokeOrganizationInvitation(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @CurrentOrganizationId UUID organizationId,
      @PathVariable UUID id) {
    organizationPeopleService.revoke(principal, organizationId, id);
  }
}
