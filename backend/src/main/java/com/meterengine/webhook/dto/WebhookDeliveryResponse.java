package com.meterengine.webhook.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record WebhookDeliveryResponse(
    UUID id,
    @JsonProperty("webhook_event_id") UUID webhookEventId,
    String type,
    String status,
    @JsonProperty("attempt_count") int attemptCount,
    @JsonProperty("next_attempt_at") OffsetDateTime nextAttemptAt,
    @JsonProperty("last_attempted_at") OffsetDateTime lastAttemptedAt,
    @JsonProperty("response_status") Integer responseStatus,
    @JsonProperty("failure_reason") String failureReason,
    @JsonProperty("created_at") OffsetDateTime createdAt,
    List<Attempt> attempts) {
  public record Attempt(
      @JsonProperty("attempted_at") OffsetDateTime attemptedAt,
      @JsonProperty("response_status") Integer responseStatus,
      @JsonProperty("failure_reason") String failureReason,
      @JsonProperty("duration_ms") long durationMs) {}
}
