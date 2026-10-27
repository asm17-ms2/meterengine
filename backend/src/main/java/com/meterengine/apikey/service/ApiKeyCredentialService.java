package com.meterengine.apikey.service;

import com.meterengine.global.security.ApiKeyCredentials;
import com.meterengine.global.security.AuthenticatedCredential;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ApiKeyCredentialService implements ApiKeyCredentials {
  private final ApiKeyService apiKeyService;

  public ApiKeyCredentialService(ApiKeyService apiKeyService) {
    this.apiKeyService = apiKeyService;
  }

  @Override
  public Optional<AuthenticatedCredential> authenticate(String token) {
    return apiKeyService.authenticate(token);
  }
}
