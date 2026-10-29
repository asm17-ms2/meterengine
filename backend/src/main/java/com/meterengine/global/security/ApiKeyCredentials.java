package com.meterengine.global.security;

import java.util.Optional;

public interface ApiKeyCredentials {
  Optional<AuthenticatedCredential> authenticate(String token);
}
