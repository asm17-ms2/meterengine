package com.meterengine.invoiceview.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ListInvoiceViewLinksResponse(
    @JsonProperty("invoice_view_links") List<InvoiceViewLinkResponse> invoiceViewLinks) {}
