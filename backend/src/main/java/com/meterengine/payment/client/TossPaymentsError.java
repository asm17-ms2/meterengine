package com.meterengine.payment.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TossPaymentsError(String code, String message) {

  public String singleLineMessage() {
    return message == null ? null : message.replaceAll("[\\r\\n]", " ");
  }
}
