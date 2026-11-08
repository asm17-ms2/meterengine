package com.meterengine.apikey.service;

import com.meterengine.apikey.entity.ApiKey;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

public record ApiKeySnapshot(
    ApiKeyHash keyHash,
    UUID apiKeyId,
    UUID organizationId,
    UUID environmentId,
    String mode,
    Set<String> scopes,
    Integer rateLimitPerMinute,
    OffsetDateTime expiresAt,
    OffsetDateTime revokedAt) {

  public ApiKeySnapshot {
    scopes = Set.copyOf(scopes);
  }

  public static ApiKeySnapshot of(ApiKey apiKey) {
    return new ApiKeySnapshot(
        new ApiKeyHash(apiKey.getKeyHash()),
        apiKey.getId(),
        apiKey.getOrganizationId(),
        apiKey.getEnvironmentId(),
        apiKey.getMode(),
        Set.copyOf(apiKey.getScopes()),
        apiKey.getRateLimitPerMinute(),
        apiKey.getExpiresAt(),
        apiKey.getRevokedAt());
  }

  public boolean isUsableAt(OffsetDateTime now) {
    return revokedAt == null && (expiresAt == null || expiresAt.isAfter(now));
  }
}
