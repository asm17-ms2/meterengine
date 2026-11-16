package com.meterengine.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.payment.entity.TossPaymentsCredential;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ListTossPaymentsCredentialsResponse(
    @Schema(description = "토스페이먼츠 키 목록(지금 키는 mode 오름차순, 이전 키는 그 뒤에 교체되거나 보관된 시각 내림차순)")
        @JsonProperty("toss_payments_credentials")
        List<TossPaymentsCredentialResponse> tossPaymentsCredentials) {

  public static ListTossPaymentsCredentialsResponse from(
      List<TossPaymentsCredential> tossPaymentsCredentials) {
    return new ListTossPaymentsCredentialsResponse(
        tossPaymentsCredentials.stream().map(TossPaymentsCredentialResponse::from).toList());
  }
}
