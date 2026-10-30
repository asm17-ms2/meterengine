package com.meterengine.event.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record EventRejectionResponse(
    UUID id,
    @JsonProperty("transaction_id") String transactionId,
    @JsonProperty("customer_id") UUID customerId,
    String type,
    @JsonProperty("occurred_at") OffsetDateTime occurredAt,
    @JsonProperty("error_code") String errorCode,
    @JsonProperty("error_fields") List<String> errorFields,
    @JsonProperty("request_id") UUID requestId,
    @JsonProperty("batch_index") Integer batchIndex,
    @JsonProperty("recorded_at") OffsetDateTime recordedAt) {}
