package com.meterengine.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.global.validation.StorableText;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record CreateBillingKeyRequest(
    @Schema(description = "토스페이먼츠 SDK의 카드 등록이 성공한 뒤 받은 authKey")
        @NotBlank @StorableText
        @JsonProperty("auth_key")
        String authKey,
    @Schema(description = "토스페이먼츠 SDK에 넣은 customerKey. 카드 등록 링크의 customer_key와 같아야 한다")
        @NotBlank @StorableText
        @JsonProperty("customer_key")
        String customerKey,
    @Schema(description = "고객이 동의한 자동결제 약관 문서의 버전")
        @NotBlank @StorableText
        @JsonProperty("consent_document_version")
        String consentDocumentVersion) {}
