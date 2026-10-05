package com.meterengine.invoice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.UUID;

public record InvoiceResponse(
    UUID id,
    @JsonProperty("customer_id") UUID customerId,
    @JsonProperty("customer_name") String customerName,
    String month,
    @JsonProperty("finalized_at") OffsetDateTime finalizedAt,
    @JsonProperty("total_amount") long totalAmount) {}
