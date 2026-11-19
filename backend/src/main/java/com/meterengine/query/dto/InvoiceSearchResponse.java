package com.meterengine.query.dto;

import com.meterengine.invoice.dto.InvoiceResponse;
import com.meterengine.payment.dto.InvoicePaymentResponse;
import io.swagger.v3.oas.annotations.media.Schema;

public record InvoiceSearchResponse(
    @Schema(description = "발행 청구서입니다. GET /v1/invoices의 항목과 같습니다.") InvoiceResponse invoice,
    @Schema(description = "summary의 mode로 본 이 청구서의 결제 상태입니다.") InvoicePaymentResponse payment) {}
