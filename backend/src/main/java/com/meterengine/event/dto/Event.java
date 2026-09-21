package com.meterengine.event.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Event(
    String transactionId,
    UUID customerId,
    String customerName,
    String type,
    String propertiesJson,
    OffsetDateTime occurredAt,
    OffsetDateTime receivedAt) {}
