package com.meterengine.invoiceview.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.global.validation.StorableText;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateInvoicePaymentRetryRequest(
    @StorableText String token,
    @JsonProperty("retry_version")
        @NotBlank @Size(max = 64) @Pattern(regexp = "[a-f0-9]{64}") @StorableText
        String retryVersion) {}
