package com.meterengine.webhook.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record WebhookEndpointResponse(
    UUID id,
    String url,
    @JsonProperty("event_types") List<String> eventTypes,
    boolean enabled,
    String description,
    @JsonProperty("created_at") OffsetDateTime createdAt,
    @JsonInclude(JsonInclude.Include.NON_NULL) String secret,
    @JsonProperty("archived_at") OffsetDateTime archivedAt,
    @Schema(description = "기록된 가장 최근 발송 시도가 실패했으면 true. 시도 기록이 없으면 false")
        @JsonProperty("latest_attempt_failed")
        boolean latestAttemptFailed) {
  public WebhookEndpointResponse(
      UUID id,
      String url,
      List<String> eventTypes,
      boolean enabled,
      String description,
      OffsetDateTime createdAt,
      String secret) {
    this(id, url, eventTypes, enabled, description, createdAt, secret, null, false);
  }
}
