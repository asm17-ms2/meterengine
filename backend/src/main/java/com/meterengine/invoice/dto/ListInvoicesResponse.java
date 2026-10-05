package com.meterengine.invoice.dto;

import java.util.List;

public record ListInvoicesResponse(List<InvoiceResponse> invoices) {}
