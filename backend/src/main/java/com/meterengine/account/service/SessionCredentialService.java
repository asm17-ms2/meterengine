package com.meterengine.account.service;

import com.meterengine.global.security.AuthenticatedCredential;
import com.meterengine.global.security.SessionCredentials;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SessionCredentialService implements SessionCredentials {
  private final SessionService sessionService;

  public SessionCredentialService(SessionService sessionService) {
    this.sessionService = sessionService;
  }

  @Override
  public Optional<AuthenticatedCredential> authenticate(String token) {
    return sessionService.authenticate(token);
  }

  @Override
  public boolean requiresConsent(UUID userAccountId) {
    return sessionService.requiresConsent(userAccountId);
  }
}
