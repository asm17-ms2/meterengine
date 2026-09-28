package com.meterengine.payment.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TossPaymentsBilling(String billingKey, OffsetDateTime authenticatedAt, Card card) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Card(String issuerCode, String number) {}
}
