package com.meterengine.organization.controller;

import com.meterengine.global.security.AuthenticatedPrincipal;
import com.meterengine.global.security.CurrentOrganizationId;
import com.meterengine.global.security.MemberAdminAccess;
import com.meterengine.global.security.MemberReadAccess;
import com.meterengine.global.validation.StorableText;
import com.meterengine.organization.dto.CreateManagedMemberRequest;
import com.meterengine.organization.dto.CreateOrganizationInvitationRequest;
import com.meterengine.organization.dto.ListManagedMembersResponse;
import com.meterengine.organization.dto.ListOrganizationInvitationsResponse;
import com.meterengine.organization.dto.ManagedMemberResponse;
import com.meterengine.organization.dto.UpdateOrganizationMemberRoleRequest;
import com.meterengine.organization.dto.UpdateOrganizationMemberScopesRequest;
import com.meterengine.organization.service.OrganizationPeopleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
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
@RequestMapping("/v1/organization")
public class OrganizationPeopleController {
  private final OrganizationPeopleService organizationPeopleService;

  public OrganizationPeopleController(OrganizationPeopleService organizationPeopleService) {
    this.organizationPeopleService = organizationPeopleService;
  }

  @GetMapping("/members")
  @MemberReadAccess
  @Operation(summary = "도입사 사용자 목록", description = "소속이 사용 중이거나 보관된 사용자를 조회한다. 기본은 전체다.")
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
  public ManagedMemberResponse createManagedOrganizationMember(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @CurrentOrganizationId UUID organizationId,
      @Valid @RequestBody CreateManagedMemberRequest request) {
    return organizationPeopleService.create(principal, organizationId, request);
  }

  @PutMapping("/members/{id}/role")
  @MemberAdminAccess
  @Operation(summary = "도입사 사용자 역할 변경")
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
  public void removeOrganizationMember(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @CurrentOrganizationId UUID organizationId,
      @PathVariable UUID id) {
    organizationPeopleService.remove(principal, organizationId, id);
  }

  @GetMapping("/invitations")
  @MemberAdminAccess
  @Operation(summary = "도입사 초대 목록")
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
  public OrganizationPeopleService.IssuedInvitation inviteOrganizationUser(
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
  public void revokeOrganizationInvitation(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @CurrentOrganizationId UUID organizationId,
      @PathVariable UUID id) {
    organizationPeopleService.revoke(principal, organizationId, id);
  }
}
