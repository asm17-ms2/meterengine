package com.meterengine.payment.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TossPaymentsPayment(
    String paymentKey,
    String orderId,
    String status,
    long totalAmount,
    long balanceAmount,
    List<Cancel> cancels) {

  public TossPaymentsPayment {
    cancels = cancels == null ? List.of() : List.copyOf(cancels);
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Cancel(
      String transactionKey, long cancelAmount, OffsetDateTime canceledAt, String cancelStatus) {}
}
