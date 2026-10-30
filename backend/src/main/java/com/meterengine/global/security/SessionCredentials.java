package com.meterengine.global.security;

import java.util.Optional;
import java.util.UUID;

public interface SessionCredentials {
  Optional<AuthenticatedCredential> authenticate(String token);

  boolean requiresConsent(UUID userAccountId);
}
