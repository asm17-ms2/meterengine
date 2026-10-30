package com.meterengine.webhook.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
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
    @JsonProperty("archived_at") OffsetDateTime archivedAt) {
  public WebhookEndpointResponse(
      UUID id,
      String url,
      List<String> eventTypes,
      boolean enabled,
      String description,
      OffsetDateTime createdAt,
      String secret) {
    this(id, url, eventTypes, enabled, description, createdAt, secret, null);
  }
}
