package com.meterengine.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.payment.entity.BillingKey;
import java.util.List;

public record ListPaymentMethodsResponse(
    @JsonProperty("payment_methods") List<PaymentMethodResponse> paymentMethods) {

  public static ListPaymentMethodsResponse from(List<BillingKey> billingKeys) {
    return new ListPaymentMethodsResponse(
        billingKeys.stream().map(PaymentMethodResponse::from).toList());
  }
}
