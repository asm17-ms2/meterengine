package com.meterengine.invoiceview.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.global.validation.StorableText;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateInvoicePaymentRetryRequest(
    @Schema(description = "청구서 조회 링크의 token(me_invoice_로 시작하는 값)") @StorableText String token,
    @Schema(description = "조회 응답의 payment_retry.version 값(64자리 16진수)")
        @JsonProperty("retry_version")
        @NotBlank @Size(max = 64) @Pattern(regexp = "[a-f0-9]{64}") @StorableText
        String retryVersion) {}
