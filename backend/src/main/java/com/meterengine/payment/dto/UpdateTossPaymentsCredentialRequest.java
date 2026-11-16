package com.meterengine.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.global.crypto.SecretMask;
import com.meterengine.global.validation.StorableText;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record UpdateTossPaymentsCredentialRequest(
    @Schema(description = "토스페이먼츠 클라이언트 키입니다. 경로의 mode가 test면 test_ck_, live면 live_ck_로 시작합니다.")
        @NotBlank @StorableText
        @JsonProperty("client_key")
        String clientKey,
    @Schema(
            description =
                "토스페이먼츠 시크릿 키입니다. 경로의 mode가 test면 test_sk_, live면 live_sk_로 시작합니다. 암호화해 저장하고 어떤 응답에도 원문을 싣지 않습니다.")
        @NotBlank @StorableText
        @JsonProperty("secret_key")
        String secretKey) {

  @Override
  public String toString() {
    return "UpdateTossPaymentsCredentialRequest[clientKey="
        + clientKey
        + ", secretKey="
        + SecretMask.mask(secretKey)
        + "]";
  }
}
