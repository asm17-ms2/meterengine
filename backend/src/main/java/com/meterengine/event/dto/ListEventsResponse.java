package com.meterengine.event.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ListEventsResponse(
    String month,
    int page,
    int size,
    long total,
    List<EventResponse> events,
    @JsonProperty("from") OffsetDateTime fromAt,
    @JsonProperty("to") OffsetDateTime toAt) {

  public ListEventsResponse(
      String month, int page, int size, long total, List<EventResponse> events) {
    this(month, page, size, total, events, null, null);
  }

  public record EventResponse(
      @JsonProperty("transaction_id") String transactionId,
      @JsonProperty("customer_id") UUID customerId,
      @JsonProperty("customer_name") String customerName,
      @JsonProperty("type") String type,
      @JsonRawValue @Schema(implementation = Map.class) String properties,
      @JsonProperty("occurred_at") OffsetDateTime occurredAt,
      @JsonProperty("received_at") OffsetDateTime receivedAt,
      @JsonProperty("request_id")
          @JsonInclude(JsonInclude.Include.NON_NULL)
          @Schema(
              description = "최초 저장 요청의 감사 ID. 기존 원본에는 없다",
              requiredMode = Schema.RequiredMode.NOT_REQUIRED)
          UUID requestId,
      @JsonProperty("actor_type")
          @JsonInclude(JsonInclude.Include.NON_NULL)
          @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
          String actorType,
      @JsonProperty("actor_id")
          @JsonInclude(JsonInclude.Include.NON_NULL)
          @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
          UUID actorId) {
    public EventResponse(
        String transactionId,
        UUID customerId,
        String customerName,
        String type,
        String properties,
        OffsetDateTime occurredAt,
        OffsetDateTime receivedAt) {
      this(
          transactionId,
          customerId,
          customerName,
          type,
          properties,
          occurredAt,
          receivedAt,
          null,
          null,
          null);
    }
  }
}
