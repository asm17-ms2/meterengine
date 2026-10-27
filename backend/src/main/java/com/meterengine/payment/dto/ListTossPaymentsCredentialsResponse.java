package com.meterengine.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.payment.entity.TossPaymentsCredential;
import java.util.List;

public record ListTossPaymentsCredentialsResponse(
    @JsonProperty("toss_payments_credentials")
        List<TossPaymentsCredentialResponse> tossPaymentsCredentials) {

  public static ListTossPaymentsCredentialsResponse from(
      List<TossPaymentsCredential> tossPaymentsCredentials) {
    return new ListTossPaymentsCredentialsResponse(
        tossPaymentsCredentials.stream().map(TossPaymentsCredentialResponse::from).toList());
  }
}
