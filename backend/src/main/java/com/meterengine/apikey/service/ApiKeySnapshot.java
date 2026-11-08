package com.meterengine.apikey.service;

import com.meterengine.apikey.entity.ApiKey;
import com.meterengine.global.security.AuthenticatedCredential;
import com.meterengine.global.security.AuthenticatedPrincipal;
import com.meterengine.global.security.PrincipalKind;
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

  // 커밋 이벤트는 순서 없이 도착하므로 늦게 온 옛 값이 폐기와 당겨진 만료를 되돌리지 못하게 한다.
  ApiKeySnapshot mergeNewer(ApiKeySnapshot newer) {
    return new ApiKeySnapshot(
        newer.keyHash,
        newer.apiKeyId,
        newer.organizationId,
        newer.environmentId,
        newer.mode,
        newer.scopes,
        newer.rateLimitPerMinute,
        earlierOf(expiresAt, newer.expiresAt),
        revokedAt != null ? revokedAt : newer.revokedAt);
  }

  private static OffsetDateTime earlierOf(OffsetDateTime first, OffsetDateTime second) {
    if (first == null) {
      return second;
    }
    if (second == null || first.isBefore(second)) {
      return first;
    }
    return second;
  }

  public boolean isUsableAt(OffsetDateTime now) {
    return revokedAt == null && (expiresAt == null || expiresAt.isAfter(now));
  }

  public AuthenticatedCredential credential(boolean organizationActive) {
    AuthenticatedPrincipal principal =
        new AuthenticatedPrincipal(
            PrincipalKind.API_KEY,
            organizationId,
            apiKeyId,
            null,
            null,
            mode,
            null,
            scopes,
            environmentId);
    return new AuthenticatedCredential(principal, rateLimitPerMinute, organizationActive);
  }
}
