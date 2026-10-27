package com.meterengine.payment.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.meterengine.global.crypto.SecretMask;
import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TossPaymentsBilling(String billingKey, OffsetDateTime authenticatedAt, Card card) {

  @Override
  public String toString() {
    return "TossPaymentsBilling[billingKey="
        + SecretMask.lastFour(billingKey)
        + ", authenticatedAt="
        + authenticatedAt
        + ", card="
        + card
        + "]";
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Card(String issuerCode, String number) {}
}
