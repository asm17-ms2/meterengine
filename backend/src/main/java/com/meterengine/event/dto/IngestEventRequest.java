package com.meterengine.event.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record IngestEventRequest(
    @JsonProperty("transaction_id") @NotBlank @Size(max = 255) String transactionId,
    @JsonProperty("customer_id") @NotNull UUID customerId,
    @JsonProperty("type") @NotBlank String type,
    @NotNull Map<String, Object> properties,
    @JsonProperty("timestamp") @NotNull OffsetDateTime occurredAt) {}
