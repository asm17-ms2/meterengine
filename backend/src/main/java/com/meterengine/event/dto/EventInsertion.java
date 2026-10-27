package com.meterengine.event.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EventInsertion(
    String transactionId,
    UUID customerId,
    String type,
    String properties,
    OffsetDateTime occurredAt) {}
