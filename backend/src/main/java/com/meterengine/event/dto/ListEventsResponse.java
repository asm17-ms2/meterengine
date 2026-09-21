package com.meterengine.event.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ListEventsResponse(
    String month, int page, int size, long total, List<EventResponse> events) {

  public record EventResponse(
      @JsonProperty("transaction_id") String transactionId,
      @JsonProperty("customer_id") UUID customerId,
      @JsonProperty("customer_name") String customerName,
      @JsonProperty("type") String type,
      @JsonRawValue @Schema(implementation = Map.class) String properties,
      @JsonProperty("occurred_at") OffsetDateTime occurredAt,
      @JsonProperty("received_at") OffsetDateTime receivedAt) {}
}
