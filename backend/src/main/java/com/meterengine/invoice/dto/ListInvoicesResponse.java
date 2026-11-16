package com.meterengine.invoice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ListInvoicesResponse(
    @Schema(description = "조건에 맞는 확정 청구서입니다. 달 내림차순, 같은 달은 고객 이름 오름차순입니다.")
        List<InvoiceResponse> invoices) {}
