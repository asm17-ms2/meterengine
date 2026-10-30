package com.meterengine.event.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.UUID;

public record EventIssueResponse(
    @JsonProperty("transaction_id") String transactionId,
    @JsonProperty("customer_id") UUID customerId,
    @JsonProperty("customer_name") String customerName,
    String type,
    @JsonProperty("occurred_at") OffsetDateTime occurredAt,
    @JsonProperty("received_at") OffsetDateTime receivedAt,
    String reason,
    @JsonProperty("billable_metric_code") String billableMetricCode,
    @JsonProperty("target_property") String targetProperty,
    @JsonProperty("action_hint") String actionHint,
    @JsonProperty("negative_quantity") boolean negativeQuantity) {}
