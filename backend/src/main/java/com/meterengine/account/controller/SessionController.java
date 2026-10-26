package com.meterengine.account.controller;

import com.meterengine.account.dto.CreateSessionRequest;
import com.meterengine.account.dto.SessionResponse;
import com.meterengine.account.dto.SessionTokenResponse;
import com.meterengine.account.dto.UpdateSessionOrganizationRequest;
import com.meterengine.account.service.SessionService;
import com.meterengine.global.error.ErrorResponse;
import com.meterengine.global.security.AuthenticatedPrincipal;
import com.meterengine.global.security.MemberAccess;
import com.meterengine.global.security.PublicAccess;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/sessions")
public class SessionController {

  private final SessionService sessionService;

  SessionController(SessionService sessionService) {
    this.sessionService = sessionService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PublicAccess
  @Operation(
      summary = "로그인",
      description =
          """
          이메일과 비밀번호로 로그인하고 세션 토큰을 돌려준다. 토큰 원문은 이 응답에서만 볼 수 있다.
          이후 요청의 Authorization 헤더에 Bearer 값으로 싣는다. 세션은 expires_at에 끝난다.
          세션의 도입사는 소속된 활성 도입사 가운데 가장 먼저 소속된 곳이다.
          활성 소속이 없는 operator는 도입사 없이 로그인하고 organization이 null이다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "만든 세션과 토큰"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            "code=validation_error: email이나 password가 비었거나 NUL 문자나 짝이 없는 UTF-16 서로게이트를 담았다"),
    @ApiResponse(
        responseCode = "401",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            """
            code=invalid_credentials: 이메일이나 비밀번호가 틀렸다. 어느 쪽이 틀렸는지는 알려 주지 않는다.
            Authorization 헤더를 실었는데 그 값이 올바르지 않을 때도 같다.
            """),
    @ApiResponse(
        responseCode = "403",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=organization_not_active: member인데 소속된 도입사 가운데 활성 상태인 곳이 없다")
  })
  public SessionTokenResponse createSession(@Valid @RequestBody CreateSessionRequest request) {
    return SessionTokenResponse.from(sessionService.create(request.email(), request.password()));
  }

  @GetMapping("/current")
  @MemberAccess
  @Operation(
      summary = "현재 세션 조회",
      description =
          """
          요청에 실은 세션 토큰의 사용자, 세션이 고른 도입사, 소속된 도입사 목록, 만료 시각을 돌려준다.
          도입사가 활성 상태가 아니어도 조회할 수 있다. API 키로는 부를 수 없다.
          """)
  @ApiResponses({@ApiResponse(responseCode = "200", description = "현재 세션")})
  public SessionResponse getSession(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return SessionResponse.from(sessionService.get(principal.userSessionId()));
  }

  @DeleteMapping("/current")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @MemberAccess
  @Operation(
      summary = "로그아웃",
      description = "요청에 실은 세션을 끝낸다. 같은 토큰으로 다시 요청하면 401이다. API 키로는 부를 수 없다.")
  @ApiResponses({@ApiResponse(responseCode = "204", description = "끝냈다. 본문 없음")})
  public void deleteSession(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
    sessionService.delete(principal.userSessionId());
  }

  @PutMapping("/current/organization")
  @MemberAccess
  @Operation(
      summary = "도입사 전환",
      description =
          """
          세션의 도입사를 사용자가 소속된 다른 도입사로 바꾸고 바뀐 현재 세션을 돌려준다.
          활성 상태가 아닌 도입사로도 바꿀 수 있지만, 그 도입사의 데이터를 다루는 요청은 403(organization_not_active)이다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "바뀐 현재 세션"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=validation_error: organization_id가 없거나 UUID 형식이 아니다"),
    @ApiResponse(
        responseCode = "403",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=permission_denied: API 키로 부른 요청이거나, 사용자가 소속되지 않은 도입사다")
  })
  public SessionResponse updateSessionOrganization(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @Valid @RequestBody UpdateSessionOrganizationRequest request) {
    return SessionResponse.from(
        sessionService.update(principal.userSessionId(), request.organizationId()));
  }
}
