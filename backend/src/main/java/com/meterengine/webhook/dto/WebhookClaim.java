package com.meterengine.webhook.dto;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

public record WebhookClaim(
    UUID id,
    UUID organizationId,
    UUID webhookEventId,
    UUID claimToken,
    String url,
    String secret,
    String payload,
    boolean enabled,
    boolean deleted,
    int attemptCount,
    OffsetDateTime lastAttemptedAt,
    UUID environmentId) {
  public WebhookClaim(
      UUID id,
      UUID organizationId,
      UUID webhookEventId,
      UUID claimToken,
      String url,
      String secret,
      String payload,
      boolean enabled,
      boolean deleted,
      int attemptCount,
      OffsetDateTime lastAttemptedAt) {
    this(
        id,
        organizationId,
        webhookEventId,
        claimToken,
        url,
        secret,
        payload,
        enabled,
        deleted,
        attemptCount,
        lastAttemptedAt,
        null);
  }

  public boolean sameOrganizationEnvironment(WebhookClaim other) {
    return organizationId.equals(other.organizationId)
        && Objects.equals(environmentId, other.environmentId);
  }
}
