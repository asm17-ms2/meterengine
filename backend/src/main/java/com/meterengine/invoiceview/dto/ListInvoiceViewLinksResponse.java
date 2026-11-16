package com.meterengine.invoiceview.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ListInvoiceViewLinksResponse(
    @Schema(description = "발급한 링크 목록입니다. 최근 발급 순이고 token은 싣지 않습니다.")
        @JsonProperty("invoice_view_links")
        List<InvoiceViewLinkResponse> invoiceViewLinks) {}
