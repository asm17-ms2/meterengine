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
    UUID environmentId,
    OffsetDateTime eventCreatedAt,
    boolean attemptedBefore) {
  // 수동 재시도는 시도 횟수를 0으로 되돌리므로 남은 시도 기록으로도 첫 시도인지 가린다.
  public boolean isFirstAttempt() {
    return attemptCount == 1 && !attemptedBefore;
  }

  public boolean sameOrganizationEnvironment(WebhookClaim other) {
    return organizationId.equals(other.organizationId)
        && Objects.equals(environmentId, other.environmentId);
  }
}
