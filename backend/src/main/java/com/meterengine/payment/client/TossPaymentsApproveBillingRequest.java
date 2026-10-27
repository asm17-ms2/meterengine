package com.meterengine.payment.client;

public record TossPaymentsApproveBillingRequest(
    String customerKey, long amount, String orderId, String orderName) {}
