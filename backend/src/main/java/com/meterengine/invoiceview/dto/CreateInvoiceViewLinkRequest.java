package com.meterengine.invoiceview.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateInvoiceViewLinkRequest(
    @Schema(description = "유효 기간(일). 생략하면 30일, 1일부터 90일까지")
        @JsonProperty("expires_in_days")
        @Min(1) @Max(90) Integer expiresInDays,
    @Schema(
            description = "고객의 실패 결제 재시도를 허용한다. 생략하면 false이며 기존 조회 링크는 허용하지 않는다",
            defaultValue = "false",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @JsonProperty("allow_payment_retry")
        @NotNull Boolean allowPaymentRetry) {
  public CreateInvoiceViewLinkRequest {
    if (allowPaymentRetry == null) allowPaymentRetry = false;
  }
}
