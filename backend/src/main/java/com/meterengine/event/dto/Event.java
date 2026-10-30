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
    OffsetDateTime receivedAt,
    UUID requestId,
    String actorType,
    UUID actorId) {
  public Event(
      String transactionId,
      UUID customerId,
      String customerName,
      String type,
      String propertiesJson,
      OffsetDateTime occurredAt,
      OffsetDateTime receivedAt) {
    this(
        transactionId,
        customerId,
        customerName,
        type,
        propertiesJson,
        occurredAt,
        receivedAt,
        null,
        null,
        null);
  }
}
