package com.meterengine.ingestion;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * POST /v1/events 응답 봉투. Event 스키마 = EventInput + received_at + unresolved (openapi.yaml).
 *
 * <p>외부 필드명은 @JsonProperty로 명시한다 (ADR 0007).
 */
public record EventIngestResponse(EventBody event) {

  public record EventBody(
      @JsonProperty("transaction_id") String transactionId,
      @JsonProperty("external_customer_id") String externalCustomerId,
      String code,
      Map<String, Object> properties,
      @JsonProperty("occurred_at") OffsetDateTime occurredAt,
      @JsonProperty("received_at") OffsetDateTime receivedAt,
      List<String> unresolved) {}

  public static EventIngestResponse from(UsageEvent event) {
    List<String> unresolved = new ArrayList<>();
    if (event.customerId() == null) {
      unresolved.add("external_customer_id");
    }
    if (event.meterId() == null) {
      unresolved.add("code");
    }
    return new EventIngestResponse(
        new EventBody(
            event.transactionId(),
            event.externalCustomerId(),
            event.code(),
            event.properties(),
            event.occurredAt(),
            event.receivedAt(),
            List.copyOf(unresolved)));
  }
}
