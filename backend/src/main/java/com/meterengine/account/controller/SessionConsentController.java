package com.meterengine.account.controller;

import com.meterengine.account.dto.AcceptSessionConsentsRequest;
import com.meterengine.account.dto.SessionResponse;
import com.meterengine.account.service.SessionConsentService;
import com.meterengine.account.service.SessionService;
import com.meterengine.global.security.AuthenticatedPrincipal;
import com.meterengine.global.security.EnvironmentReady;
import com.meterengine.global.security.MemberAccess;
import io.swagger.v3.oas.annotations.Operation;
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
  @Operation(summary = "본인의 필수 안내 동의")
  public SessionResponse acceptSessionConsents(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @Valid @RequestBody AcceptSessionConsentsRequest request) {
    sessionConsentService.accept(principal, request);
    return SessionResponse.from(sessionService.get(principal.userSessionId()));
  }
}
