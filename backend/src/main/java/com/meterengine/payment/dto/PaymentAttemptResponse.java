package com.meterengine.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentAttemptResponse(
    UUID id,
    @JsonProperty("invoice_id") UUID invoiceId,
    String status,
    long amount,
    @JsonProperty("payment_key") String paymentKey,
    @JsonProperty("requested_at") OffsetDateTime requestedAt,
    @JsonProperty("completed_at") OffsetDateTime completedAt) {}
