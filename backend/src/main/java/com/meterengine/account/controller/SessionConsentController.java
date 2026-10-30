package com.meterengine.account.controller;

import com.meterengine.account.dto.AcceptSessionConsentsRequest;
import com.meterengine.account.dto.SessionResponse;
import com.meterengine.account.service.SessionConsentService;
import com.meterengine.account.service.SessionService;
import com.meterengine.global.error.ErrorResponse;
import com.meterengine.global.security.AuthenticatedPrincipal;
import com.meterengine.global.security.EnvironmentReady;
import com.meterengine.global.security.MemberAccess;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@EnvironmentReady
public class SessionConsentController {
  private final SessionConsentService sessionConsentService;
  private final SessionService sessionService;

  public SessionConsentController(
      SessionConsentService sessionConsentService, SessionService sessionService) {
    this.sessionConsentService = sessionConsentService;
    this.sessionService = sessionService;
  }

  @PostMapping("/v1/sessions/current/consents")
  @MemberAccess
  @Operation(
      summary = "본인의 필수 안내 동의",
      description = "관리자나 운영자가 만든 계정은 처음 동의할 때 new_password로 본인 비밀번호를 정한다. 그때 이 세션 밖의 세션은 모두 끝낸다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "현재 세션. 이미 동의한 사용자면 새로 기록하지 않는다"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            """
            code=validation_error: consents가 없거나 비었거나, document가 terms_of_service나 privacy_policy가 아니거나,
            document_version이 비었거나 255자를 넘거나, 문자열에 NUL 문자나 짝이 없는 UTF-16 서로게이트를 담았다.
            동의가 남은 사용자면 consents에 terms_of_service와 privacy_policy가 한 번씩 있어야 하고,
            new_password가 없거나 처음 받은 비밀번호와 같거나 8자 미만, 72자 또는 UTF-8 72바이트를 넘는다.
            """)
  })
  public SessionResponse acceptSessionConsents(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @Valid @RequestBody AcceptSessionConsentsRequest request) {
    sessionConsentService.accept(principal, request);
    return SessionResponse.from(sessionService.get(principal.userSessionId()));
  }
}
