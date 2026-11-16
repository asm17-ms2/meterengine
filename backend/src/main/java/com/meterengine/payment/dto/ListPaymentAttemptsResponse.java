package com.meterengine.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.payment.entity.PaymentAttempt;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ListPaymentAttemptsResponse(
    @Schema(description = "결제 시도 목록(요청 시각이 늦은 것부터)") @JsonProperty("payment_attempts")
        List<PaymentAttemptResponse> paymentAttempts) {

  public static ListPaymentAttemptsResponse from(List<PaymentAttempt> attempts) {
    return new ListPaymentAttemptsResponse(
        attempts.stream().map(PaymentAttemptResponse::from).toList());
  }
}
