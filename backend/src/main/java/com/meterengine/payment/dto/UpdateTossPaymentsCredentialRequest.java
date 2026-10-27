package com.meterengine.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.global.crypto.SecretMask;
import com.meterengine.global.validation.StorableText;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record UpdateTossPaymentsCredentialRequest(
    @Schema(description = "토스페이먼츠 클라이언트 키. test 모드는 test_ck_, live 모드는 live_ck_로 시작한다")
        @NotBlank @StorableText
        @JsonProperty("client_key")
        String clientKey,
    @Schema(
            description =
                "토스페이먼츠 시크릿 키. test 모드는 test_sk_, live 모드는 live_sk_로 시작한다. 암호화해 저장하고 어떤 응답에도 원문을 싣지 않는다")
        @NotBlank @StorableText
        @JsonProperty("secret_key")
        String secretKey) {

  @Override
  public String toString() {
    return "UpdateTossPaymentsCredentialRequest[clientKey="
        + clientKey
        + ", secretKey="
        + SecretMask.lastFour(secretKey)
        + "]";
  }
}
