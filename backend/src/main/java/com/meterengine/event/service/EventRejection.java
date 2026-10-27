package com.meterengine.event.service;

import com.meterengine.global.error.ErrorCode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record EventRejection(
    String transactionId,
    UUID customerId,
    String type,
    OffsetDateTime occurredAt,
    ErrorCode errorCode,
    List<String> errorFields,
    Integer batchIndex) {}
