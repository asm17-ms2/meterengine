package com.meterengine.invoiceview.dto;

import com.meterengine.global.validation.StorableText;
import io.swagger.v3.oas.annotations.media.Schema;

public record GetPublicInvoiceRequest(
    @Schema(description = "청구서 조회 링크의 token(me_invoice_로 시작하는 값)") @StorableText String token) {}
