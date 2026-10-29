package com.meterengine.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.payment.entity.PaymentAttempt;
import java.util.List;

public record ListPaymentAttemptsResponse(
    @JsonProperty("payment_attempts") List<PaymentAttemptResponse> paymentAttempts) {

  public static ListPaymentAttemptsResponse from(List<PaymentAttempt> attempts) {
    return new ListPaymentAttemptsResponse(
        attempts.stream().map(PaymentAttemptResponse::from).toList());
  }
}
