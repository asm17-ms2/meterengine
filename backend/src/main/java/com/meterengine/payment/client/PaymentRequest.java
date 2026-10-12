package com.meterengine.payment.client;

import java.util.UUID;

public record PaymentRequest(
    UUID attemptId, UUID organizationId, UUID customerId, long amount, String orderName) {}
