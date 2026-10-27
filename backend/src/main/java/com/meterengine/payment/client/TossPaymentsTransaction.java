package com.meterengine.payment.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TossPaymentsTransaction(
    String transactionKey,
    String paymentKey,
    String orderId,
    String status,
    OffsetDateTime transactionAt,
    long amount) {}
