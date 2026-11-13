package com.meterengine.payment.client;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TossPaymentsPayment(
    // 토스페이먼츠가 발급한 상점아이디(MID). 응답의 이름이 mId다.
    @JsonProperty("mId") String merchantId,
    String paymentKey,
    String orderId,
    String status,
    long totalAmount,
    long balanceAmount,
    List<Cancel> cancels) {

  @JsonCreator
  public TossPaymentsPayment {
    cancels = cancels == null ? List.of() : List.copyOf(cancels);
  }

  public TossPaymentsPayment(
      String paymentKey,
      String orderId,
      String status,
      long totalAmount,
      long balanceAmount,
      List<Cancel> cancels) {
    this(null, paymentKey, orderId, status, totalAmount, balanceAmount, cancels);
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Cancel(
      String transactionKey, long cancelAmount, OffsetDateTime canceledAt, String cancelStatus) {}
}
