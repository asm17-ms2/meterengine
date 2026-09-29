package com.meterengine.payment.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TossPaymentsPayment(String paymentKey, String orderId, String status) {}
