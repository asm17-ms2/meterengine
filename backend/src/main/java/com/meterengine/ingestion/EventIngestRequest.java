package com.meterengine.ingestion;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.Map;

/** POST /v1/events 요청 봉투 (openapi.yaml ingestEvent). 외부 필드명은 @JsonProperty로 명시한다 (ADR 0007). */
public record EventIngestRequest(EventInput event) {

  public record EventInput(
      @JsonProperty("transaction_id") String transactionId,
      @JsonProperty("external_customer_id") String externalCustomerId,
      String code,
      Map<String, Object> properties,
      @JsonProperty("occurred_at") OffsetDateTime occurredAt) {

    /** properties 생략은 빈 객체로 저장한다 (openapi.yaml: default {}). */
    public Map<String, Object> propertiesOrEmpty() {
      return properties == null ? Map.of() : properties;
    }
  }
}
