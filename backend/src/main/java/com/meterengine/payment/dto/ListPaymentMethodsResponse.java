package com.meterengine.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.payment.entity.BillingKey;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ListPaymentMethodsResponse(
    @Schema(description = "고객 카드 목록(지금 카드는 customer_id 오름차순, 이전 카드는 그 뒤에 교체되거나 보관된 시각 내림차순)")
        @JsonProperty("payment_methods")
        List<PaymentMethodResponse> paymentMethods) {

  public static ListPaymentMethodsResponse from(List<BillingKey> billingKeys) {
    return new ListPaymentMethodsResponse(
        billingKeys.stream().map(PaymentMethodResponse::from).toList());
  }
}
