package com.meterengine.organization.controller;

import com.meterengine.global.security.AuthenticatedPrincipal;
import com.meterengine.global.security.PublicAccess;
import com.meterengine.global.validation.StorableText;
import com.meterengine.organization.dto.AcceptOrganizationInvitationRequest;
import com.meterengine.organization.service.OrganizationPeopleService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/organization-invitations/{token}")
public class OrganizationInvitationController {
  private final OrganizationPeopleService organizationPeopleService;

  public OrganizationInvitationController(OrganizationPeopleService organizationPeopleService) {
    this.organizationPeopleService = organizationPeopleService;
  }

  @GetMapping
  @PublicAccess
  @Operation(summary = "초대 링크 확인")
  public OrganizationPeopleService.InvitationPreview previewOrganizationInvitation(
      @PathVariable @StorableText @Pattern(regexp = "me_invite_[A-Za-z0-9]{32}") String token) {
    return organizationPeopleService.preview(token);
  }

  @PostMapping("/accept")
  @PublicAccess
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "본인의 초대 수락")
  public OrganizationPeopleService.AcceptedInvitation acceptOrganizationInvitation(
      @PathVariable @StorableText @Pattern(regexp = "me_invite_[A-Za-z0-9]{32}") String token,
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @Valid @RequestBody(required = false) AcceptOrganizationInvitationRequest request) {
    return organizationPeopleService.accept(token, principal, request);
  }
}
