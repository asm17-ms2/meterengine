package com.meterengine.liveenvironment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.global.crypto.SecretMask;
import com.meterengine.global.validation.StorableText;
import com.meterengine.liveenvironment.entity.LiveEnvironmentApplication;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateLiveEnvironmentApplicationRequest(
    @Schema(description = "사업자등록번호. 숫자 열 자리이고 하이픈과 공백은 빼고 읽는다", example = "1234567891")
        @NotBlank @Size(max = BUSINESS_REGISTRATION_NUMBER_INPUT_MAX_LENGTH) @StorableText
        @JsonProperty("business_registration_number")
        String businessRegistrationNumber,
    @Schema(description = "실제 결제에 쓸 토스페이먼츠 클라이언트 키. live_ck_로 시작한다")
        @NotBlank @StorableText
        @JsonProperty("toss_payments_client_key")
        String tossPaymentsClientKey,
    @Schema(description = "실제 결제에 쓸 토스페이먼츠 시크릿 키. live_sk_로 시작한다. 암호화해 저장하고 어떤 응답에도 원문을 싣지 않는다")
        @NotBlank @StorableText
        @JsonProperty("toss_payments_secret_key")
        String tossPaymentsSecretKey,
    @Schema(description = "토스페이먼츠가 발급한 상점아이디(MID). 승인 뒤 첫 결제 응답의 값과 견준다")
        @NotBlank @Size(max = LiveEnvironmentApplication.MERCHANT_ID_MAX_LENGTH) @StorableText
        @JsonProperty("toss_payments_merchant_id")
        String tossPaymentsMerchantId) {

  // 열 자리 숫자에 하이픈과 공백이 섞여 와도 받는 여유다.
  private static final int BUSINESS_REGISTRATION_NUMBER_INPUT_MAX_LENGTH = 20;

  @Override
  public String toString() {
    return "CreateLiveEnvironmentApplicationRequest[businessRegistrationNumber="
        + businessRegistrationNumber
        + ", tossPaymentsClientKey="
        + tossPaymentsClientKey
        + ", tossPaymentsSecretKey="
        + SecretMask.mask(tossPaymentsSecretKey)
        + ", tossPaymentsMerchantId="
        + tossPaymentsMerchantId
        + "]";
  }
}
