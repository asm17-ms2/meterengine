package com.meterengine.organization.controller;

import com.meterengine.global.error.ErrorResponse;
import com.meterengine.global.security.AuthenticatedPrincipal;
import com.meterengine.global.security.EnvironmentReady;
import com.meterengine.global.security.PublicAccess;
import com.meterengine.global.validation.StorableText;
import com.meterengine.organization.dto.AcceptOrganizationInvitationRequest;
import com.meterengine.organization.dto.OrganizationInvitationAcceptanceResponse;
import com.meterengine.organization.dto.OrganizationInvitationPreviewResponse;
import com.meterengine.organization.service.OrganizationPeopleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@EnvironmentReady
@RequestMapping("/v1/organization-invitations/{token}")
public class OrganizationInvitationController {
  private final OrganizationPeopleService organizationPeopleService;

  public OrganizationInvitationController(OrganizationPeopleService organizationPeopleService) {
    this.organizationPeopleService = organizationPeopleService;
  }

  @GetMapping
  @PublicAccess
  @Operation(summary = "초대 링크 확인")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "초대한 도입사, 초대 이메일, 만료 시각, 수락하면 받을 권한"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=validation_error: 경로의 token이 me_invite_ 뒤에 영문 대소문자와 숫자 32자인 형식이 아니다"),
    @ApiResponse(
        responseCode = "401",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=invalid_credentials: Authorization 헤더를 실었는데 그 값이 올바르지 않다"),
    @ApiResponse(
        responseCode = "403",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            """
            code=organization_not_active: 초대한 도입사가 활성 상태가 아니다.
            code=consent_required: Authorization 헤더에 실은 세션의 사용자가 필수 안내에 아직 동의하지 않았다.
            """),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=invitation_not_found: 그런 초대 링크가 없다"),
    @ApiResponse(
        responseCode = "409",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=invitation_unavailable: 만료됐거나 이미 수락 또는 취소된 초대다")
  })
  public OrganizationInvitationPreviewResponse previewOrganizationInvitation(
      @PathVariable @StorableText @Pattern(regexp = "me_invite_[A-Za-z0-9]{32}") String token) {
    return organizationPeopleService.preview(token);
  }

  @PostMapping("/accept")
  @PublicAccess
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "본인의 초대 수락")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "수락한 사용자와 소속된 도입사"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            """
            code=validation_error: 경로의 token 형식이 틀렸거나, 본문의 값이 형식 또는 길이 규칙을 벗어났다.
            초대 이메일로 가입한 계정이 없으면 name, password, consents가 모두 있어야 하고,
            password는 8자 이상이면서 72자와 UTF-8 72바이트 이하, consents는 terms_of_service와 privacy_policy를 한 번씩 담아야 한다.
            """),
    @ApiResponse(
        responseCode = "401",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=invalid_credentials: Authorization 헤더를 실었는데 그 값이 올바르지 않다"),
    @ApiResponse(
        responseCode = "403",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            """
            code=permission_denied: 초대 이메일로 가입한 계정이 있는데 그 사용자의 로그인 세션으로 부르지 않았다.
            code=consent_required: Authorization 헤더에 실은 세션의 사용자가 필수 안내에 아직 동의하지 않았다.
            code=organization_not_active: 초대한 도입사가 활성 상태가 아니다.
            """),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=invitation_not_found: 그런 초대 링크가 없다"),
    @ApiResponse(
        responseCode = "409",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            """
            code=invitation_unavailable: 만료됐거나 이미 수락 또는 취소된 초대다.
            code=organization_member_already_exists: 초대 이메일의 사용자가 이미 그 도입사에 소속돼 있다.
            code=email_already_exists: 수락을 처리하는 사이에 같은 이메일의 계정이 먼저 만들어졌다.
            """)
  })
  public OrganizationInvitationAcceptanceResponse acceptOrganizationInvitation(
      @PathVariable @StorableText @Pattern(regexp = "me_invite_[A-Za-z0-9]{32}") String token,
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @Valid @RequestBody(required = false) AcceptOrganizationInvitationRequest request) {
    return organizationPeopleService.accept(token, principal, request);
  }
}
