package com.meterengine.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.payment.entity.PaymentCancellation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ListPaymentCancellationsResponse(
    @Schema(description = "결제 취소 목록(요청 시각이 늦은 것부터)") @JsonProperty("payment_cancellations")
        List<PaymentCancellationResponse> paymentCancellations) {

  public static ListPaymentCancellationsResponse from(List<PaymentCancellation> cancellations) {
    return new ListPaymentCancellationsResponse(
        cancellations.stream().map(PaymentCancellationResponse::from).toList());
  }
}
