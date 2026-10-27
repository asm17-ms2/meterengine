package com.meterengine.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.payment.entity.PaymentCancellation;
import java.util.List;

public record ListPaymentCancellationsResponse(
    @JsonProperty("payment_cancellations") List<PaymentCancellationResponse> paymentCancellations) {

  public static ListPaymentCancellationsResponse from(List<PaymentCancellation> cancellations) {
    return new ListPaymentCancellationsResponse(
        cancellations.stream().map(PaymentCancellationResponse::from).toList());
  }
}
