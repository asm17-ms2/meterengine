package com.meterengine.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ListInvoicePaymentsResponse(
    @Schema(description = "청구서별 결제 상태 목록(청구 월 내림차순, 같은 달은 고객 이름 오름차순)")
        @JsonProperty("invoice_payments")
        List<InvoicePaymentResponse> invoicePayments) {}
