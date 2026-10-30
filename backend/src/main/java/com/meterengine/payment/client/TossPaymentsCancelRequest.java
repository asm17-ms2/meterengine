package com.meterengine.payment.client;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TossPaymentsCancelRequest(String cancelReason, Long cancelAmount) {}
