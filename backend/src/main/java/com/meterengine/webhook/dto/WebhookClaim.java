package com.meterengine.webhook.dto;

import java.time.OffsetDateTime;
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
    OffsetDateTime lastAttemptedAt) {}
